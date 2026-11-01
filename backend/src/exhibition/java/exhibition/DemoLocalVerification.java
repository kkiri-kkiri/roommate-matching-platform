package exhibition;

import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import tools.jackson.databind.json.JsonMapper;

/** Read-only verification of the explicitly approved personal localhost:3306 database. Never runs DML/DDL. */
public final class DemoLocalVerification {
    private static final String MERGE="kkirikkiri_demo50_merge_20261101";
    private static final String LATEST="kkirikkiri_demo50_20261101";
    private static final String ORIGINAL="kkirikkiri_exhibition_20261031";
    private static final JsonMapper JSON=JsonMapper.builder().build();
    private static final List<String> SCHEMA=List.of(
            "SELECT table_name,table_type,engine,table_collation,create_options FROM information_schema.tables WHERE table_schema=DATABASE() ORDER BY table_name",
            "SELECT table_name,column_name,column_type,is_nullable,column_default,extra,character_set_name,collation_name,generation_expression FROM information_schema.columns WHERE table_schema=DATABASE() ORDER BY table_name,ordinal_position",
            "SELECT table_name,index_name,non_unique,seq_in_index,column_name,collation,sub_part,index_type,is_visible,expression FROM information_schema.statistics WHERE table_schema=DATABASE() ORDER BY table_name,index_name,seq_in_index",
            "SELECT table_name,constraint_name,column_name,referenced_table_name,referenced_column_name FROM information_schema.key_column_usage WHERE table_schema=DATABASE() ORDER BY table_name,constraint_name,ordinal_position",
            "SELECT table_name,constraint_name,update_rule,delete_rule FROM information_schema.referential_constraints WHERE constraint_schema=DATABASE() ORDER BY table_name,constraint_name",
            "SELECT constraint_name,check_clause FROM information_schema.check_constraints WHERE constraint_schema=DATABASE() ORDER BY constraint_name",
            "SELECT table_name,view_definition,check_option,is_updatable,security_type FROM information_schema.views WHERE table_schema=DATABASE() ORDER BY table_name",
            "SELECT trigger_name,event_manipulation,event_object_table,action_statement,action_timing FROM information_schema.triggers WHERE trigger_schema=DATABASE() ORDER BY trigger_name",
            "SELECT routine_name,routine_type,routine_definition,sql_data_access,security_type FROM information_schema.routines WHERE routine_schema=DATABASE() ORDER BY routine_name",
            "SELECT event_name,event_definition,status FROM information_schema.events WHERE event_schema=DATABASE() ORDER BY event_name");
    private record Database(Connection connection,JdbcTemplate jdbc) implements AutoCloseable {
        @Override public void close() throws SQLException {connection.rollback();connection.close();}
    }
    private DemoLocalVerification() {}
    public static void main(String[] args) throws Exception {
        if(args.length!=4 || !Set.of("preflight","restore","after-apply","after-runtime").contains(args[0]))
            throw new IllegalArgumentException("operation, merge state, private result path and copy/source required");
        String mode=args[0];var receipt=DemoDataMerge.read(Path.of(args[1]).resolve("merge.json"));
        if(!"COMPLETE".equals(receipt.status()))throw new IllegalStateException("Completed rehearsal required");
        Map<String,Object> result=new TreeMap<>();result.put("operation",mode);result.put("endpoint","localhost:3306/kkirikkiri");
        try(var base=open("kkirikkiri");var merged=open(MERGE);var latest=open(LATEST);var original=open(ORIGINAL)) {
            if(!DemoDataExpansion.identity(base.jdbc).equals(receipt.base()) || !DemoDataExpansion.identity(merged.jdbc).equals(receipt.database())
                    || !DemoDataExpansion.identity(latest.jdbc).equals(receipt.latest()))throw new IllegalStateException("Local server/schema identity differs from rehearsal");
            equal(receipt.completed(),DemoDataSnapshot.capture(merged.jdbc),"Reviewed merge changed");
            equal(receipt.source(),DemoDataSnapshot.capture(latest.jdbc),"Latest source changed");
            var expansion=DemoDataExpansion.read(Path.of(args[1]).getParent().resolve(LATEST).resolve("receipt.json"));
            equal(expansion.baseline(),DemoDataSnapshot.capture(original.jdbc),"Original exhibition changed");
            compareSchema(original.jdbc,latest.jdbc,false);
            switch(mode) {
                case "preflight" -> {
                    equal(receipt.originalBase(),DemoDataSnapshot.capture(base.jdbc),"Base rows changed since rehearsal; stop");
                    try(var oldRestore=open("kkirikkiri_demo50_restore_20261101")) {
                        equal(receipt.originalBase(),DemoDataSnapshot.capture(oldRestore.jdbc),"Historical restore changed");
                        compareSchema(oldRestore.jdbc,base.jdbc,true);
                    }
                    result.put("originalAccounts",33);result.put("schemaAndDataUnchanged",true);
                }
                case "restore" -> {
                    String[] pair=args[3].split(":",2);
                    if(pair.length!=2 || !Set.of("kkirikkiri",LATEST,ORIGINAL).contains(pair[0])
                            || !pair[1].matches("kkirikkiri_demo50_final_restore_[a-zA-Z0-9_]+")) throw new IllegalArgumentException("Only an explicit new final restore copy is allowed");
                    JdbcTemplate source=switch(pair[0]){case "kkirikkiri" -> base.jdbc;case LATEST -> latest.jdbc;default -> original.jdbc;};
                    try(var restored=open(pair[1])) {
                        equal(DemoDataSnapshot.capture(source),DemoDataSnapshot.capture(restored.jdbc),"Fresh backup restore rows differ");
                        compareSchema(source,restored.jdbc,true);
                    }
                    result.put("source",pair[0]);result.put("restoredCopy",pair[1]);result.put("allRowsAndPhysicalSchemaEqual",true);
                }
                case "after-apply" -> {
                    equal(receipt.completed(),DemoDataSnapshot.capture(base.jdbc),"Applied base differs from reviewed merge");
                    compareSchema(merged.jdbc,base.jdbc,true);
                    result.put("exactReviewedCheckpoint",true);
                }
                case "after-runtime" -> {
                    compareSchema(merged.jdbc,base.jdbc,false);
                    result.put("runtimeChanges",verifyRuntime(base.jdbc,merged.jdbc,receipt));
                }
            }
            var actual=DemoDataSnapshot.capture(base.jdbc);
            var accounts=actual.tables().get("user");
            receipt.originalBase().tables().get("user").forEach((key,hash) -> {if(!hash.equals(accounts.get(key)))throw new IllegalStateException("An original account changed");});
            Map<String,Integer> counts=new TreeMap<>();actual.tables().forEach((table,rows)->counts.put(table,rows.size()));
            result.put("counts",counts);result.put("originalAccountRowsPreserved",33);result.put("sourcesUnchanged",true);
        }
        Path output=Path.of(args[2]);Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output,JSON.writeValueAsString(result),StandardOpenOption.CREATE_NEW);
        System.out.println("Verified "+mode+": fixed local endpoint, original accounts, source databases and reviewed data/schema checks passed.");
    }
    private static Database open(String schema) throws SQLException {
        if(!Set.of("kkirikkiri",MERGE,LATEST,ORIGINAL,"kkirikkiri_demo50_restore_20261101").contains(schema)
                && !schema.matches("kkirikkiri_demo50_final_restore_[a-zA-Z0-9_]+"))throw new IllegalArgumentException("Unexpected database");
        var connection=DriverManager.getConnection("jdbc:mysql://127.0.0.1:3306/"+schema+"?useSSL=false&allowPublicKeyRetrieval=true",required("DEMO_DB_USERNAME"),required("DEMO_DB_PASSWORD"));
        connection.setReadOnly(true);connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);connection.setAutoCommit(false);
        var jdbc=new JdbcTemplate(new SingleConnectionDataSource(connection,true));
        if(!schema.equals(jdbc.queryForObject("SELECT DATABASE()",String.class)) || jdbc.queryForObject("SELECT @@port",Integer.class)!=3306)
            throw new IllegalStateException("Unexpected local endpoint");
        return new Database(connection,jdbc);
    }
    private static void compareSchema(JdbcTemplate a,JdbcTemplate b,boolean counters) {
        var first=new ExhibitionDatabase(a);var second=new ExhibitionDatabase(b);
        for(String query:SCHEMA)equal(first.rows(query),second.rows(query),"Physical schema differs; stop");
        if(counters) {
            String query="SELECT table_name,auto_increment FROM information_schema.tables WHERE table_schema=DATABASE() ORDER BY table_name";
            equal(first.rows(query),second.rows(query),"AUTO_INCREMENT state differs; stop");
        }
    }
    private static Map<String,Map<String,Integer>> verifyRuntime(JdbcTemplate base,JdbcTemplate reference,DemoDataMerge.Receipt receipt) {
        var current=DemoDataSnapshot.capture(base);equal(receipt.completed().tables().keySet(),current.tables().keySet(),"Runtime table set changed");
        Map<String,Map<String,Integer>> summary=new TreeMap<>();
        for(String table:current.tables().keySet()) {
            var oldHashes=receipt.completed().tables().get(table);var hashes=current.tables().get(table);
            if(oldHashes.equals(hashes))continue;
            if(Set.of("auth_limit_bucket","auth_email_identity").contains(table)) {
                summary.put(table,Map.of("added",(int)hashes.keySet().stream().filter(k->!oldHashes.containsKey(k)).count(),"changed",(int)oldHashes.keySet().stream().filter(hashes::containsKey).filter(k->!oldHashes.get(k).equals(hashes.get(k))).count()));continue;
            }
            Set<String> allowed=switch(table){case "community_post" -> Set.of("view_count","updated_at");case "chat_room_member" -> Set.of("last_read_at");case "chat_message" -> Set.of("is_read");default -> Set.of();};
            if(allowed.isEmpty() || !oldHashes.keySet().equals(hashes.keySet()))throw new IllegalStateException("Unexpected runtime rows changed: "+table);
            var old=new DemoMergeRows(reference).rows(table);var actual=new DemoMergeRows(base).rows(table);
            Map<Long,Map<String,Object>> expected=new TreeMap<>();old.forEach(r->expected.put(DemoMergeRows.id(table,r),r));
            Set<Long> imported=new HashSet<>(receipt.ids().getOrDefault(table,Map.of()).values());
            int changed=0;
            for(var row:actual) {
                long id=DemoMergeRows.id(table,row);var before=expected.get(id);
                if(before.equals(row))continue;
                if(!imported.contains(id))throw new IllegalStateException("An original manual runtime row changed: "+table);
                var clean=new TreeMap<>(row);var prior=new TreeMap<>(before);allowed.forEach(c->{clean.remove(c);prior.remove(c);});
                if(!clean.equals(prior))throw new IllegalStateException("Unexpected runtime column changed: "+table);
                if(table.equals("community_post") && DemoMergeRows.number(row.get("view_count"))<DemoMergeRows.number(before.get("view_count")))throw new IllegalStateException("View count regressed");
                if(table.equals("chat_message") && DemoMergeRows.truth(before.get("is_read")) && !DemoMergeRows.truth(row.get("is_read")))throw new IllegalStateException("Read receipt regressed");
                changed++;
            }
            summary.put(table,Map.of("changed",changed));
        }
        return summary;
    }
    private static void equal(Object a,Object b,String message) {if(!Objects.equals(a,b))throw new IllegalStateException(message);}
    private static String required(String name){String value=System.getenv(name);if(value==null||value.isBlank())throw new IllegalStateException("Private setting missing");return value;}
}
