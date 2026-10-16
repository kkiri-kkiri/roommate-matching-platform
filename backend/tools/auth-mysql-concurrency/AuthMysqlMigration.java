import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.*;
import java.time.*;
import java.util.*;

/** Explicit, fixed-target DDL step, separate from the concurrency suite. */
public class AuthMysqlMigration {
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]).toRealPath();
        Path out=root.resolve("backend/tools/auth-mysql-concurrency/results/index-migration-"+LocalDate.now(ZoneId.of("Asia/Seoul"))+"-"+UUID.randomUUID());
        Files.createDirectories(out);
        Path file=root.resolve("backend/migrations/20261015_auth_email_identity_indexes.sql");
        String sql=Files.readString(file,StandardCharsets.UTF_8);
        var evidence=new LinkedHashMap<String,Object>();
        evidence.put("endpoint","127.0.0.1:3307/kkiri_auth_test");
        evidence.put("uuid",AuthMysqlConcurrency.SERVER_UUID);
        evidence.put("sqlSha256",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(sql.getBytes(StandardCharsets.UTF_8))));
        int number=0;
        try(Connection c=AuthMysqlConcurrency.open()) {
            AuthMysqlConcurrency.guard(c);AuthMysqlConcurrency.grants(c);
            AuthMysqlConcurrency.check(AuthMysqlConcurrency.scalar(c,"SELECT GET_LOCK('kkiri_auth_test.auth_concurrency_validation',0)")==1,"Another validation is active");
            AuthMysqlConcurrency.check(AuthMysqlConcurrency.scalar(c,"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()")==8,"Migration requires original 8 tables");
            var before=new TreeMap<String,String>();
            for(String t:AuthMysqlConcurrency.TABLES)if(!t.equals("auth_email_identity")) {
                AuthMysqlConcurrency.check(AuthMysqlConcurrency.scalar(c,"SELECT COUNT(*) FROM `"+t+"`")==0,"Migration requires empty isolated tables");
                before.put(t,AuthMysqlConcurrency.text(c,"SHOW CREATE TABLE `"+t+"`",2));
            }
            evidence.put("beforeDdl",before);
            evidence.put("beforePlan",plan(c));
            Files.writeString(out.resolve("before.json"),AuthMysqlConcurrency.json(evidence));
            for(String statement:split(sql)) {
                number++;
                try(var s=c.createStatement()){s.execute(statement);}
            }
            AuthMysqlConcurrency.check(AuthMysqlConcurrency.scalar(c,"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()")==9,"Expected 9 tables");
            var after=new TreeMap<String,String>();
            for(String t:AuthMysqlConcurrency.TABLES) {
                AuthMysqlConcurrency.check(AuthMysqlConcurrency.scalar(c,"SELECT COUNT(*) FROM `"+t+"`")==0,"Unexpected migration data");
                String ddl=AuthMysqlConcurrency.text(c,"SHOW CREATE TABLE `"+t+"`",2);after.put(t,ddl);
                if(!t.equals("email_verification") && before.containsKey(t))AuthMysqlConcurrency.check(before.get(t).equals(ddl),"Unrelated table DDL changed");
            }
            evidence.put("afterDdl",after);evidence.put("afterPlan",plan(c));
            evidence.put("columns",rows(c,"SELECT table_name,column_name,column_type,character_set_name,collation_name,is_nullable FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name IN ('user','email_verification','auth_email_identity') ORDER BY table_name,ordinal_position"));
            evidence.put("indexes",rows(c,"SELECT table_name,index_name,non_unique,seq_in_index,column_name,collation FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name IN ('email_verification','auth_email_identity') ORDER BY table_name,index_name,seq_in_index"));
            evidence.put("verdict","PASS");
            Files.writeString(out.resolve("migration.json"),AuthMysqlConcurrency.json(evidence));
            System.out.println("MIGRATION PASS evidence="+out);
        }catch(Throwable e){evidence.put("verdict","FAIL");evidence.put("statement",number);evidence.put("error",AuthMysqlConcurrency.describe(e));Files.writeString(out.resolve("migration.json"),AuthMysqlConcurrency.json(evidence));System.err.println("MIGRATION STOP statement="+number+" "+AuthMysqlConcurrency.describe(e)+" evidence="+out);System.exit(1);}
    }
    static List<Map<String,Object>> plan(Connection c)throws SQLException{return rows(c,"EXPLAIN SELECT verification_id FROM email_verification WHERE email='index-plan@example.invalid' AND purpose='SIGNUP' ORDER BY created_at DESC,verification_id DESC LIMIT 1 FOR UPDATE");}
    static List<Map<String,Object>> rows(Connection c,String q)throws SQLException{var list=new ArrayList<Map<String,Object>>();try(var s=c.createStatement();var r=s.executeQuery(q)){while(r.next()){var row=new LinkedHashMap<String,Object>();for(int i=1;i<=r.getMetaData().getColumnCount();i++)row.put(r.getMetaData().getColumnLabel(i),r.getObject(i));list.add(row);}}return list;}
    static List<String> split(String s){var out=new ArrayList<String>();var b=new StringBuilder();char quote=0;for(int i=0;i<s.length();i++){char x=s.charAt(i);if(quote==0 && x=='-' && i+1<s.length() && s.charAt(i+1)=='-'){while(i<s.length() && s.charAt(i)!='\n')i++;b.append('\n');continue;}if(quote!=0){b.append(x);if(x=='\\' && i+1<s.length())b.append(s.charAt(++i));else if(x==quote){if(i+1<s.length() && s.charAt(i+1)==quote)b.append(s.charAt(++i));else quote=0;}}else if(x=='\'' || x=='"' || x=='`'){quote=x;b.append(x);}else if(x==';'){if(!b.toString().isBlank())out.add(b.toString().trim());b.setLength(0);}else b.append(x);}if(quote!=0)throw new IllegalStateException("Unbalanced SQL");if(!b.toString().isBlank())out.add(b.toString().trim());return out;}
}
