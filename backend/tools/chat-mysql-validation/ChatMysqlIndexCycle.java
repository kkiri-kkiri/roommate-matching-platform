import java.nio.file.Files;
import java.util.*;

/** Only the historically recorded migration indexes in the retained legacy schema. */
public class ChatMysqlIndexCycle extends ChatMysqlValidation {
    record Candidate(String table,String name,List<String> columns) { }
    static final List<Candidate> CANDIDATES=List.of(
        new Candidate("chat_message","idx_chat_message_room_latest",List.of("chat_room_id","sent_at","message_id")),
        new Candidate("chat_message","idx_chat_message_room_unread",List.of("chat_room_id","is_read","sender_id")),
        new Candidate("chat_room_member","idx_chat_member_user_active_room",List.of("user_id","is_left","chat_room_id")));
    static List<ChatMysqlPreservation.Bound> original;
    static List<Map<String,Object>> foreignKeys;
    static final List<Map<String,Object>> ddl=new ArrayList<>();
    static List<Map<String,Object>> indexes() {
        return jdbc.queryForList("select table_name,index_name,non_unique,seq_in_index,column_name,sub_part,collation,index_type,is_visible from information_schema.statistics where table_schema=database() and table_name in ('chat_message','chat_room_member') order by table_name,index_name,seq_in_index");
    }
    static List<Map<String,Object>> fks() {
        return jdbc.queryForList("select k.table_name,k.constraint_name,k.ordinal_position,k.column_name,k.referenced_table_schema,k.referenced_table_name,k.referenced_column_name,r.update_rule,r.delete_rule from information_schema.key_column_usage k join information_schema.referential_constraints r on r.constraint_schema=k.constraint_schema and r.constraint_name=k.constraint_name and r.table_name=k.table_name where k.constraint_schema=database() and k.table_name in ('chat_message','chat_room_member','chat_room') order by k.table_name,k.constraint_name,k.ordinal_position");
    }
    static List<Map<String,Object>> named(Candidate candidate,List<Map<String,Object>> all) {
        return all.stream().filter(r->candidate.table().equals(r.get("TABLE_NAME"))&&candidate.name().equals(r.get("INDEX_NAME"))).toList();
    }
    static void definition(Candidate candidate,List<Map<String,Object>> rows) {
        check(rows.stream().map(r->r.get("COLUMN_NAME")).toList().equals(candidate.columns()),"Index definition mismatch: "+candidate.name());
        check(rows.stream().allMatch(r->((Number)r.get("NON_UNIQUE")).intValue()==1&&r.get("SUB_PART")==null
                &&"A".equals(r.get("COLLATION"))&&"BTREE".equals(r.get("INDEX_TYPE"))&&"YES".equals(r.get("IS_VISIBLE"))),"Unexpected prefix/type/direction/visibility: "+candidate.name());
    }
    static boolean supports(String table,List<String> columns,Set<String> excluding) {
        var groups=new LinkedHashMap<String,List<Map<String,Object>>>();
        for(var row:indexes())if(table.equals(row.get("TABLE_NAME"))&&!excluding.contains(row.get("INDEX_NAME")))
            groups.computeIfAbsent(row.get("INDEX_NAME").toString(),k->new ArrayList<>()).add(row);
        return groups.values().stream().anyMatch(rows->rows.size()>=columns.size()
                &&rows.subList(0,columns.size()).stream().map(r->r.get("COLUMN_NAME")).toList().equals(columns)
                &&rows.stream().allMatch(r->r.get("SUB_PART")==null&&"YES".equals(r.get("IS_VISIBLE"))&&"BTREE".equals(r.get("INDEX_TYPE"))));
    }
    static void checkSupport(Set<String> excluding) {
        var groups=new LinkedHashMap<String,List<Map<String,Object>>>();
        for(var fk:foreignKeys)if(Set.of("chat_message","chat_room_member").contains(fk.get("TABLE_NAME")))
            groups.computeIfAbsent(fk.get("TABLE_NAME")+":"+fk.get("CONSTRAINT_NAME"),k->new ArrayList<>()).add(fk);
        for(var group:groups.values())check(supports(group.getFirst().get("TABLE_NAME").toString(),group.stream().map(r->r.get("COLUMN_NAME").toString()).toList(),excluding),"Missing FK support excluding planned drops");
    }
    static void execute(String sql) throws Exception {
        jdbc.execute(sql); ddl.add(Map.of("completedSql",sql));
        Files.writeString(output.resolve("index-cycle-ddl-completed.json"),JSON.writeValueAsString(ddl));
    }
    static void apply() throws Exception {
        for(var candidate:CANDIDATES) {
            var existing=named(candidate,indexes());
            if(!existing.isEmpty()){definition(candidate,existing);continue;}
            // Never create a full duplicate under another name.
            var others=indexes().stream().filter(r->candidate.table().equals(r.get("TABLE_NAME"))).collect(java.util.stream.Collectors.groupingBy(r->r.get("INDEX_NAME")));
            check(others.values().stream().noneMatch(rows->rows.stream().map(r->r.get("COLUMN_NAME")).toList().equals(candidate.columns())),"Equivalent index under another name; ownership ambiguous");
            execute("CREATE INDEX "+candidate.name()+" ON "+candidate.table()+" ("+String.join(",",candidate.columns())+")");
            definition(candidate,named(candidate,indexes())); checkSupport(Set.of());
        }
    }
    static void restore(String table,String column,String name,Set<String> planned) throws Exception {
        if(supports(table,List.of(column),planned))return;
        check(indexes().stream().noneMatch(r->name.equals(r.get("INDEX_NAME"))),"Restore-name collision; stop without DROP");
        execute("CREATE INDEX "+name+" ON "+table+" ("+column+")");
        check(supports(table,List.of(column),planned),"Restored FK index not present; stop without DROP");
    }
    static void phase(String name) throws Exception {
        var state=new LinkedHashMap<String,Object>();state.put("indexes",indexes());state.put("foreignKeys",fks());
        Files.writeString(output.resolve("index-cycle-"+name+".json"),JSON.writeValueAsString(state));
        check(foreignKeys.equals(fks()),"FK definition/rules changed");checkSupport(Set.of());
        ChatMysqlPreservation.verify(original,"index-cycle-"+name);
    }
    static void critical(String name,Case action) throws Exception {
        int position=results.size();test(name,action);
        check("PASS".equals(results.get(position).get("result")),"Index stage failed; stop. Inspect saved state before recovery");
    }
    static void references() {
        tx().executeWithoutResult(status->{
            var fixture=fixture(); long id=message(fixture,"isolated-rollback-FK-probe",FIXED);
            check(jdbc.queryForObject("select count(*) from CHAT_MESSAGE where message_id=?",Integer.class,id)==1,"Valid FK insert rejected");
            expectedSql(Set.of(1216,1452),()->jdbc.update("insert into CHAT_MESSAGE(chat_room_id,sender_id,content,sent_at,is_read) values(?,?,?,?,false)",Long.MAX_VALUE,fixture.writer(),"orphan-probe",FIXED));
            expectedSql(Set.of(1217,1451),()->jdbc.update("delete from CHAT_ROOM where chat_room_id=?",fixture.room()));
            status.setRollbackOnly();
        });
    }
    static void run() throws Exception {
        check(schema.equals(text(manifest,"legacyDb")),"Index cycle restricted to existing legacy schema");
        original=ChatMysqlPreservation.capture("index-cycle-before");foreignKeys=fks();
        critical("index-cycle-ownership-and-initial-FK-data",()->{
            var ledger=root.resolve("backend/build/chat-mysql-validation/run-de097290-477d-4018-afa3-646669d8b414/"+schema+".indexes.json");
            check(Files.exists(ledger),"Missing historical migration ownership evidence");
            var history=JSON.readValue(Files.readString(ledger),List.class);
            for(var candidate:CANDIDATES){definition(candidate,named(candidate,indexes()));
                check(history.stream().filter(row->candidate.name().equals(((Map)row).get("INDEX_NAME"))).count()==candidate.columns().size(),"Historical ownership mismatch");}
            phase("before");references();
        });
        critical("index-cycle-apply-existing-equivalents-skip",()->{apply();phase("applied");});
        Set<String> planned=new HashSet<>(CANDIDATES.stream().map(Candidate::name).toList());
        critical("index-cycle-restore-FK-before-any-DROP",()->{
            restore("chat_message","chat_room_id","idx_chat_message_fk_restore",planned);
            restore("chat_room_member","user_id","idx_chat_member_fk_restore",planned);
            checkSupport(planned);phase("support-restored");
        });
        critical("index-cycle-safe-rollback-and-FK-data",()->{
            for(int position:List.of(1,0,2)){
                var candidate=CANDIDATES.get(position);definition(candidate,named(candidate,indexes()));
                execute("DROP INDEX "+candidate.name()+" ON "+candidate.table());phase("drop-"+position);
            }
            references();phase("rolled-back");
        });
        critical("index-cycle-partial-reapply-failure-recovery",()->{
            var first=CANDIDATES.getFirst();execute("CREATE INDEX "+first.name()+" ON "+first.table()+" ("+String.join(",",first.columns())+")");
            expectedSql(1061,()->jdbc.execute("CREATE INDEX "+first.name()+" ON "+first.table()+" ("+String.join(",",first.columns())+")"));
            phase("partial-reapply");apply();references();phase("reapplied");
        });
        critical("index-cycle-rerun-idempotence-no-full-duplicates",()->{
            var before=indexes();apply();check(before.equals(indexes()),"Idempotent reapply changed indexes");
            var definitions=new HashSet<String>();var groups=indexes().stream().collect(java.util.stream.Collectors.groupingBy(r->r.get("TABLE_NAME")+":"+r.get("INDEX_NAME")));
            for(var rows:groups.values())check(definitions.add(rows.getFirst().get("TABLE_NAME")+":"+rows.getFirst().get("NON_UNIQUE")+":"+rows.stream().map(r->r.get("COLUMN_NAME")+":"+r.get("SUB_PART")+":"+r.get("COLLATION")).toList()),"Full duplicate index accumulated");
            phase("final");
        });
    }
}
