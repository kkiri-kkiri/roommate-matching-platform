package exhibition;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Private, checkpoint-bound DML artifact. Export never executes it against either input. */
final class DemoMergeSqlExport {
    private static final String ARCHIVE="profile_survey_legacy_20261101";
    private static final List<String> ORDER=List.of("dormitory","faculty","major","user","user_profile","profile_survey",
            "profile_survey_option","roommate_preference","roommate_preference_option","preference_criterion",
            "community_post","community_comment","post_like","comment_like","community_anonymous_author",
            "calendar_category","calendar_category_setting","calendar_event","chat_room","chat_room_member","chat_message","report","roommate_match");
    private record Table(String name,String key,List<String> columns,Map<String,Map<String,String>> before,
            Map<String,Map<String,String>> after,String beforeDigest,String afterDigest) {}
    private DemoMergeSqlExport() {}
    static void export(JdbcTemplate base,JdbcTemplate merged,DemoDataMerge.Receipt receipt,Path directory) throws Exception {
        if(!"COMPLETE".equals(receipt.status()) || !DemoDataSnapshot.capture(base).equals(receipt.originalBase())
                || !DemoDataSnapshot.capture(merged).equals(receipt.completed())) throw new IllegalStateException("Export inputs differ from reviewed checkpoints");
        String patchName="reviewed-merge-"+UUID.randomUUID().toString().replace("-","")+".sql";
        Path patch=directory.resolve(patchName);
        for(var jdbc:List.of(base,merged)) jdbc.execute("SET SESSION group_concat_max_len=33554432");
        Map<String,Table> tables=new TreeMap<>();
        for(String name:receipt.completed().tables().keySet()) {
            var columns=merged.queryForList("SELECT column_name FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=? ORDER BY ordinal_position",String.class,name);
            var keys=merged.queryForList("SELECT column_name FROM information_schema.key_column_usage WHERE table_schema=DATABASE() AND table_name=? AND constraint_name='PRIMARY' ORDER BY ordinal_position",String.class,name);
            if(keys.size()!=1) throw new IllegalStateException("Patch requires a single-column primary key");
            String source=name.equals(ARCHIVE)?"(SELECT profile_survey_id,user_id,bug_handling,hair_dryer_use FROM profile_survey) archived":quote(name);
            var before=rows(base,source,columns,keys.getFirst()); var after=rows(merged,quote(name),columns,keys.getFirst());
            tables.put(name,new Table(name,keys.getFirst(),columns,before,after,digest(base,source,columns),digest(merged,quote(name),columns)));
        }
        var sql=new StringBuilder("-- PRIVATE: contains account password hashes. Never commit or print this file.\n")
                .append("-- Requires the separately verified legacy-column archive migration and stopped writers.\n")
                .append("-- Exact server, schema, physical schema and whole-database before/after guards; no FK bypass.\n")
                .append("-- Same session: SET @kkiri_demo_merge_approval='<reviewed preview token>'; never use --force.\n");
        String procedure="kkiri_demo_patch_"+receipt.plan().token().substring(0,12);
        sql.append("DELIMITER $$\nCREATE PROCEDURE ").append(procedure).append("()\nmain: BEGIN\n")
                .append("DECLARE patch_checkpoint_complete BOOLEAN DEFAULT TRUE;\nDECLARE patch_step VARCHAR(128) DEFAULT 'checkpoint guard';\n")
                .append("DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL SET MESSAGE_TEXT=patch_step; END;\n")
                .append("IF COALESCE(@kkiri_demo_merge_approval,'')<>").append(literal(receipt.plan().token()))
                .append(" OR @@server_uuid<>").append(literal(base.queryForObject("SELECT @@server_uuid",String.class)))
                .append(" OR NOT (DATABASE()='kkirikkiri' OR DATABASE() REGEXP '^kkirikkiri_demo50_replay_[a-zA-Z0-9_]+$')")
                .append(" OR @@autocommit<>1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Reviewed offline patch approval/server/target required'; END IF;\n")
                .append("SET SESSION group_concat_max_len=33554432;\nSET TRANSACTION ISOLATION LEVEL SERIALIZABLE;\nSTART TRANSACTION;\n");
        appendSchemaGuard(sql,merged);
        tables.values().forEach(t -> sql.append("SET patch_checkpoint_complete=patch_checkpoint_complete AND (").append(digestQuery(quote(t.name),t.columns))
                .append(")=").append(literal(t.afterDigest)).append(";\n"));
        sql.append("IF patch_checkpoint_complete THEN COMMIT; SELECT 'Already complete: 0 DML statements' AS result; LEAVE main; END IF;\n");
        tables.values().forEach(t -> assertDigest(sql,t,t.beforeDigest,"Before checkpoint mismatch"));
        int deleted=0,updated=0,inserted=0;
        for(var table:tables.values()) for(var row:table.before.entrySet()) if(!table.after.containsKey(row.getKey())) {
            if(!table.name.equals("profile_survey_option")) throw new IllegalStateException("Only proven obsolete survey options may be deleted");
            step(sql,"DELETE",table.name,row.getKey());
            sql.append("DELETE FROM ").append(quote(table.name)).append(" WHERE ").append(quote(table.key)).append("=").append(literal(row.getKey())).append(";\n");deleted++;
        }
        for(String name:ORDER) {
            Table table=tables.get(name);if(table==null)continue;
            var ordered=new ArrayList<>(table.after.entrySet());
            if(name.equals("community_comment")) ordered.sort(Comparator.comparingInt(e -> e.getValue().get("parent_comment_id")==null?0:1));
            for(var row:ordered) {
                var old=table.before.get(row.getKey());var value=row.getValue();
                if(old==null) {
                    if(!DemoMergeRows.PK.containsKey(name) || name.equals("community_post")) throw new IllegalStateException("Unexpected appended table");
                    step(sql,"INSERT",name,row.getKey());
                    sql.append("INSERT INTO ").append(quote(name)).append(" (").append(String.join(",",table.columns.stream().map(DemoMergeSqlExport::quote).toList()))
                            .append(") VALUES (").append(String.join(",",table.columns.stream().map(c -> literal(value.get(c))).toList())).append(");\n");inserted++;
                } else if(!old.equals(value)) {
                    if(!Set.of("user_profile","profile_survey","community_post").contains(name)) throw new IllegalStateException("Unexpected update; existing accounts cannot be updated");
                    var changed=table.columns.stream().filter(c -> !Objects.equals(old.get(c),value.get(c))).toList();
                    if(changed.contains(table.key) || changed.contains("user_id") || changed.contains("created_at")
                            || (name.equals("community_post") && !Set.of("title","content").containsAll(changed))) throw new IllegalStateException("Immutable column differs");
                    step(sql,"UPDATE",name,row.getKey());
                    sql.append("UPDATE ").append(quote(name)).append(" SET ")
                            .append(String.join(",",changed.stream().map(c -> quote(c)+"="+literal(value.get(c))).toList()))
                            .append(" WHERE ").append(quote(table.key)).append("=").append(literal(row.getKey())).append(";\n");updated++;
                }
            }
        }
        // Any changed table omitted from the dependency order is forbidden, rather than silently ignored.
        for(var table:tables.values()) if(!ORDER.contains(table.name) && !table.before.equals(table.after)) throw new IllegalStateException("Unsupported table delta");
        tables.values().forEach(t -> assertDigest(sql,t,t.afterDigest,"After checkpoint mismatch"));
        sql.append("COMMIT;\nSELECT 'Reviewed patch verified and committed' AS result;\nEND$$\nDELIMITER ;\nCALL ").append(procedure).append("();\nDROP PROCEDURE ").append(procedure).append(";\n");
        Files.writeString(patch,sql,StandardOpenOption.CREATE_NEW);
        Files.writeString(directory.resolve(patchName+".sha256"),HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(patch)))+"\n",StandardOpenOption.CREATE_NEW);
        var options=tables.get("profile_survey_option");
        Files.writeString(directory.resolve(patchName+".deleted-options.json"),tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(
                options.before.entrySet().stream().filter(e -> !options.after.containsKey(e.getKey())).map(Map.Entry::getValue).toList()),StandardOpenOption.CREATE_NEW);
        Files.writeString(directory.resolve("reviewed-patch-file.txt"),patchName+"\n");
        System.out.println("Private guarded patch exported: inserts="+inserted+", updates="+updated+", deletes="+deleted+". No source database was written.");
    }
    private static Map<String,Map<String,String>> rows(JdbcTemplate jdbc,String source,List<String> columns,String key) {
        Map<String,Map<String,String>> rows=new LinkedHashMap<>();
        String projection=String.join(",",columns.stream().map(c -> "CAST("+quote(c)+" AS CHAR CHARACTER SET utf8mb4) AS "+quote(c)).toList());
        // Qualify the native key: ORDER BY the CAST projection alias would sort numeric IDs as text.
        jdbc.query("SELECT "+projection+" FROM (SELECT * FROM "+source+") patch_rows ORDER BY patch_rows."+quote(key),rs -> {
            Map<String,String> row=new LinkedHashMap<>();for(String column:columns)row.put(column,rs.getString(column));
            if(rows.put(row.get(key),row)!=null)throw new IllegalStateException("Duplicate primary key");
        });return rows;
    }
    private static String digest(JdbcTemplate jdbc,String source,List<String> columns) {
        long count=jdbc.queryForObject("SELECT COUNT(*) FROM "+source,Long.class);
        if(count*64>33554432)throw new IllegalStateException("Digest capacity exceeded");
        return jdbc.queryForObject(digestQuery(source,columns),String.class);
    }
    private static String digestQuery(String source,List<String> columns) {
        String values=String.join(",",columns.stream().map(c -> "CAST("+quote(c)+" AS CHAR CHARACTER SET utf8mb4)").toList());
        return "SELECT CONCAT(COUNT(*),':',SHA2(COALESCE(GROUP_CONCAT(h ORDER BY h SEPARATOR ''),''),256)) FROM (SELECT SHA2(CAST(JSON_ARRAY("+values+") AS CHAR CHARACTER SET utf8mb4),256) h FROM "+source+") digested";
    }
    private static void assertDigest(StringBuilder sql,Table t,String expected,String message) {
        sql.append("SET patch_step='").append(message).append(": ").append(t.name).append("';\n");
        sql.append("IF NOT ((").append(digestQuery(quote(t.name),t.columns)).append(")<=>").append(literal(expected))
                .append(") THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='").append(message).append(": ").append(t.name).append("'; END IF;\n");
    }
    private static void step(StringBuilder sql,String operation,String table,String key) {
        if(!key.matches("[0-9]+"))throw new IllegalStateException("Changed rows require numeric keys");
        sql.append("SET patch_step='").append(operation).append(' ').append(table).append(" PK=").append(key).append("';\n");
    }
    private static void appendSchemaGuard(StringBuilder sql,JdbcTemplate merged) {
        List<String> sources=List.of(
                "(SELECT table_name,column_name,column_type,is_nullable,column_default,extra,character_set_name,collation_name,generation_expression FROM information_schema.columns WHERE table_schema=DATABASE()) physical_columns",
                "(SELECT table_name,index_name,non_unique,seq_in_index,column_name,collation,sub_part,index_type,is_visible,expression FROM information_schema.statistics WHERE table_schema=DATABASE()) physical_indexes",
                "(SELECT table_name,constraint_name,column_name,referenced_table_name,referenced_column_name FROM information_schema.key_column_usage WHERE table_schema=DATABASE()) physical_keys",
                "(SELECT table_name,constraint_name,update_rule,delete_rule FROM information_schema.referential_constraints WHERE constraint_schema=DATABASE()) physical_rules");
        for(String source:sources) {
            var columns=merged.query("SELECT * FROM "+source+" LIMIT 0",rs -> {
                List<String> names=new ArrayList<>();for(int i=1;i<=rs.getMetaData().getColumnCount();i++)names.add(rs.getMetaData().getColumnLabel(i).toLowerCase(Locale.ROOT));return names;
            });
            sql.append("IF NOT ((").append(digestQuery(source,columns)).append(")<=>").append(literal(digest(merged,source,columns)))
                    .append(") THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Physical schema mismatch'; END IF;\n");
        }
        sql.append("IF (SELECT COUNT(*) FROM information_schema.triggers WHERE trigger_schema=DATABASE())<>0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Unexpected trigger'; END IF;\n");
    }
    private static String quote(String name) {if(!name.matches("[a-z][a-z0-9_]*"))throw new IllegalArgumentException("Unsafe SQL identifier");return "`"+name+"`";}
    private static String literal(String value) {return value==null?"NULL":value.isEmpty()?"''":"CONVERT(0x"+HexFormat.of().formatHex(value.getBytes(StandardCharsets.UTF_8))+" USING utf8mb4)";}
}
