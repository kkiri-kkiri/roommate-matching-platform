package exhibition;

import java.nio.channels.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import tools.jackson.databind.json.JsonMapper;

/** Rehearsal only. Both input databases use read-only transactions; only an isolated merge clone is writable. */
public final class DemoMergeTool {
    private DemoMergeTool() {}
    public static void validate(Map<String,String> env) {
        DemoDataTool.validateTarget(env);
        if(!required(env,"DEMO_DB_SCHEMA").matches("kkirikkiri_demo50_merge_[a-zA-Z0-9_]+")) throw new IllegalStateException("Explicit merge clone required");
        if(!required(env,"DEMO_BASE_URL").matches("jdbc:mysql://(localhost|127\\.0\\.0\\.1):[0-9]{1,5}/kkirikkiri(\\?useSSL=false&allowPublicKeyRetrieval=true)?"))
            throw new IllegalStateException("Original base is a fixed read-only source");
        if(!required(env,"DEMO_LATEST_URL").matches("jdbc:mysql://(localhost|127\\.0\\.0\\.1):[0-9]{1,5}/kkirikkiri_demo50_[a-zA-Z0-9_]+(\\?useSSL=false&allowPublicKeyRetrieval=true)?")
                || required(env,"DEMO_LATEST_URL").equals(required(env,"DEMO_DB_URL"))) throw new IllegalStateException("Verified latest source must be separate");
    }
    public static void main(String[] args) throws Exception {
        if(args.length<2 || !Set.of("seal","preview","apply","verify","audit-sources","verify-restore","verify-runtime","export-patch","verify-replay","verify-replay-baseline").contains(args[0])) throw new IllegalArgumentException("Explicit operation required");
        String operation=args[0]; Map<String,String> options=new HashMap<>();
        for(int i=1;i<args.length;i++) {
            var pair=args[i].split("=",2);
            if(pair.length!=2 || !Set.of("--state","--latest-receipt","--latest-journal","--expect").contains(pair[0])
                    || pair[1].isBlank() || options.putIfAbsent(pair[0],pair[1])!=null) throw new IllegalArgumentException("Unsupported/duplicate option");
        }
        Map<String,String> env=System.getenv(); validate(env);
        if(Set.of("seal","apply").contains(operation) && !"CONFIRMED_ISOLATED_OFFLINE".equals(env.get("DEMO_WRITES"))) throw new IllegalStateException("Offline isolated write guard required");
        if(operation.equals("apply") && !options.getOrDefault("--expect","").matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Reviewed token required");
        Path directory=Path.of(Objects.requireNonNull(options.get("--state"))).toAbsolutePath(); Files.createDirectories(directory);
        Path receiptFile=directory.resolve("merge.json"); var json=JsonMapper.builder().build();
        try(var channel=FileChannel.open(directory.resolve("operation.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);
            var fileLock=channel.tryLock(); var base=connect(env,"DEMO_BASE_"); var latest=connect(env,"DEMO_LATEST_"); var lock=connect(env,"DEMO_DB_")) {
            if(fileLock==null) throw new IllegalStateException("Another merge operation holds the receipt");
            for(var input:List.of(base,latest)) {input.setReadOnly(true);input.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);input.setAutoCommit(false);}
            var baseJdbc=new JdbcTemplate(new SingleConnectionDataSource(base,true));
            var latestJdbc=new JdbcTemplate(new SingleConnectionDataSource(latest,true));
            String key="kkiri-demo50-"+ExhibitionJournal.hash(required(env,"DEMO_DB_SCHEMA")).substring(0,32);
            try(var statement=lock.prepareStatement("SELECT GET_LOCK(?,0)")) {
                statement.setString(1,key);try(var rs=statement.executeQuery()){if(!rs.next() || rs.getInt(1)!=1)throw new IllegalStateException("Target is locked");}
            }
            try(var context=DemoDataTool.open(env,false)) {
                var jdbc=context.getBean(JdbcTemplate.class); String identity=DemoDataExpansion.identity(jdbc);
                var sourceReceipt=DemoDataExpansion.read(Path.of(Objects.requireNonNull(options.get("--latest-receipt"))));
                var journal=json.readValue(Files.readString(Path.of(Objects.requireNonNull(options.get("--latest-journal")))),ExhibitionJournal.State.class);
                var merger=new DemoDataMerge(context,new DemoDataMerge.Source(latestJdbc,sourceReceipt,journal));
                merger.validateSource();
                if(operation.equals("seal")) {
                    if(Files.exists(receiptFile)) throw new IllegalStateException("Receipt already exists; never reseal changed data");
                    DemoDataMerge.verifyLegacyArchive(baseJdbc,jdbc);
                    var receipt=new DemoDataMerge.Receipt(DemoDataMerge.VERSION,identity,DemoDataExpansion.identity(baseJdbc),sourceReceipt.database(),"READY",
                            DemoDataSnapshot.capture(baseJdbc),DemoDataSnapshot.capture(jdbc),sourceReceipt.completed(),null,Map.of(),null);
                    var plan=merger.preview(receipt);
                    receipt=new DemoDataMerge.Receipt(receipt.version(),receipt.database(),receipt.base(),receipt.latest(),receipt.status(),receipt.originalBase(),receipt.baseline(),receipt.source(),null,Map.of(),plan);
                    DemoDataMerge.write(receiptFile,receipt); printPlan(plan);
                } else {
                    var receipt=DemoDataMerge.read(receiptFile);
                    if(!identity.equals(receipt.database()))throw new IllegalStateException("Receipt belongs to another target");
                    if(Set.of("verify-restore","verify-runtime","verify-replay","verify-replay-baseline").contains(operation)) {
                        String kind=operation.equals("verify-replay-baseline")?"replay":operation.substring("verify-".length());
                        if(!required(env,"DEMO_RESTORE_URL").matches("jdbc:mysql://(localhost|127\\.0\\.0\\.1):[0-9]{1,5}/kkirikkiri_demo50_"+kind+"_[a-zA-Z0-9_]+(\\?useSSL=false&allowPublicKeyRetrieval=true)?"))
                            throw new IllegalStateException("Only a separate isolated copy may be checked");
                        try(var copy=connect(env,"DEMO_RESTORE_")) {
                            copy.setReadOnly(true);copy.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);copy.setAutoCommit(false);
                            var copyJdbc=new JdbcTemplate(new SingleConnectionDataSource(copy,true));
                            var expected=operation.equals("verify-replay-baseline")?receipt.baseline():kind.equals("restore")?receipt.originalBase():receipt.completed();
                            if(expected==null || !expected.equals(DemoDataSnapshot.capture(copyJdbc))) throw new IllegalStateException("Restored rows differ from checkpoint");
                            verifySchema(kind.equals("restore")?baseJdbc:jdbc,copyJdbc);
                            copy.rollback();System.out.println("Verified "+(operation.equals("verify-replay-baseline")?"rolled-back replay":kind)+" copy: every row, column definition, index and foreign key agrees with its checkpoint.");
                        }
                    } else if(operation.equals("export-patch")) {
                        merger.verify(receipt);
                        DemoMergeSqlExport.export(baseJdbc,jdbc,receipt,directory);
                    } else if(operation.equals("audit-sources")) {
                        if(!DemoDataExpansion.identity(baseJdbc).equals(receipt.base()) || !DemoDataSnapshot.capture(baseJdbc).equals(receipt.originalBase())
                                || !DemoDataSnapshot.capture(latestJdbc).equals(receipt.source())) throw new IllegalStateException("An input database changed since rehearsal");
                        System.out.println("Verified: original base and latest source are unchanged, including every existing account row.");
                    } else {
                        var plan=merger.preview(receipt); printPlan(plan);
                        if(receipt.status().equals("COMPLETE"))System.out.println("COMPLETE checkpoint: applying again performs 0 database writes; counts below describe the original merge.");
                        if(operation.equals("apply")) receipt=merger.apply(receiptFile,receipt,plan,options.get("--expect"),env.get("DEMO_NEW_USER_PASSWORD"));
                        if(Set.of("apply","verify").contains(operation)) {
                            merger.verify(receipt); printChanges(receipt);
                            Files.writeString(directory.resolve("id-map.json"),json.writeValueAsString(receipt.ids()));
                            Files.writeString(directory.resolve("skipped.json"),json.writeValueAsString(receipt.plan().skipped()));
                            writeUiProjection(directory,receipt,sourceReceipt,journal);
                            System.out.println("Verified: original accounts and manual rows preserved; imported authors, parents, source keys and calculator results valid.");
                        }
                    }
                }
            } finally {
                try(var statement=lock.prepareStatement("SELECT RELEASE_LOCK(?)")){statement.setString(1,key);statement.executeQuery().close();}
                base.rollback(); latest.rollback();
            }
        }
    }
    private static void printPlan(DemoDataMerge.Plan plan) {
        System.out.println("Eligible existing students="+plan.slots().stream().filter(i -> i<=30).count()+"; new students="+plan.slots().stream().filter(i -> i>30).count()+"; owned posts="+plan.posts().size());
        plan.skipped().forEach((key,reason) -> System.out.println("SKIP "+key+": "+reason));
        System.out.println("Preview token: "+plan.token());
    }
    private static void writeUiProjection(Path directory,DemoDataMerge.Receipt receipt,DemoDataExpansion.Receipt source,ExhibitionJournal.State journal) throws Exception {
        Map<String,Object> projection=new TreeMap<>();
        projection.put("students",ExhibitionCatalog.students().stream().map(s -> {
            Map<String,Object> value=new TreeMap<>();value.put("slot",s.slot());value.put("email",s.email());value.put("nickname",s.nickname());
            value.put("id",receipt.ids().get("user").get(source.scenarios().get(s.key())));return value;
        }).toList());
        projection.put("posts",ExhibitionCatalog.posts().stream().map(p -> {
            Map<String,Object> value=new TreeMap<>();value.put("key",p.key());value.put("title",p.title());value.put("content",p.content());value.put("comment",p.comment());value.put("reply",p.reply());
            value.put("id",receipt.ids().get("community_post").get(journal.owned().get(p.key()).id()));return value;
        }).toList());
        projection.put("chats",ExhibitionCatalog.conversations().stream().map(c -> {
            Map<String,Object> value=new TreeMap<>();value.put("key",c.key());value.put("messages",c.messages());
            value.put("id",receipt.ids().get("chat_room").get(journal.owned().get(c.key()).id()));
            value.put("caller",receipt.ids().get("user").get(source.scenarios().get("user/"+c.caller())));return value;
        }).toList());
        Files.writeString(directory.resolve("ui-projection.json"),JsonMapper.builder().build().writeValueAsString(projection));
    }
    private static void printChanges(DemoDataMerge.Receipt receipt) {
        receipt.baseline().tables().forEach((table,before) -> {
            var after=receipt.completed().tables().get(table);
            long added=after.keySet().stream().filter(k -> !before.containsKey(k)).count();
            long deleted=before.keySet().stream().filter(k -> !after.containsKey(k)).count();
            long changed=before.keySet().stream().filter(after::containsKey).filter(k -> !before.get(k).equals(after.get(k))).count();
            if(added+deleted+changed>0)System.out.println(table+": added="+added+", changed="+changed+", deleted="+deleted+", total="+after.size());
        });
        System.out.println("Receipt state="+receipt.status()+"; skipped="+receipt.plan().skipped().size());
    }
    private static Connection connect(Map<String,String> env,String prefix) throws SQLException {return DriverManager.getConnection(required(env,prefix+"URL"),required(env,prefix+"USERNAME"),required(env,prefix+"PASSWORD"));}
    private static void verifySchema(JdbcTemplate original,JdbcTemplate copy) {
        for(String query:List.of(
                "SELECT table_name,column_name,column_type,is_nullable,column_default,extra,character_set_name,collation_name,generation_expression FROM information_schema.columns WHERE table_schema=DATABASE() ORDER BY table_name,ordinal_position",
                "SELECT table_name,index_name,non_unique,seq_in_index,column_name,collation,sub_part,index_type,is_visible,expression FROM information_schema.statistics WHERE table_schema=DATABASE() ORDER BY table_name,index_name,seq_in_index",
                "SELECT table_name,constraint_name,column_name,referenced_table_name,referenced_column_name FROM information_schema.key_column_usage WHERE table_schema=DATABASE() ORDER BY table_name,constraint_name,ordinal_position",
                "SELECT table_name,constraint_name,update_rule,delete_rule FROM information_schema.referential_constraints WHERE constraint_schema=DATABASE() ORDER BY table_name,constraint_name"))
            if(!new ExhibitionDatabase(original).rows(query).equals(new ExhibitionDatabase(copy).rows(query))) throw new IllegalStateException("Restored physical schema differs");
    }
    private static String required(Map<String,String> env,String key) {var value=env.get(key);if(value==null || value.isBlank())throw new IllegalStateException("Missing setting: "+key);return value;}
}
