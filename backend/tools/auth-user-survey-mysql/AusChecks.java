import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

/** Initialization/audit restricted to the five explicitly approved, initially empty schemas. */
public class AusChecks {
    static final String[] DBS={"kkiriausauth20261021","kkiriaususer20261021","kkiriaussurvey20261021","kkiri_auth_mi_a51021202601","kkiri_user_mi_a51021202602"};
    static final String[] USERS={"ausauth20261021","aususer20261021","aussurvey20261021","kkiri_mi_a51021202601","kkiri_umi_a51021202602"};
    static final String[] PASSWORDS={"KKIRI_AUTH_TEST_DB_PASSWORD","KKIRI_USER_TEST_DB_PASSWORD","KKIRI_SURVEY_TEST_PASSWORD","AUS_AUTH_HTTP_PASSWORD","AUS_USER_HTTP_PASSWORD"};
    static Connection open(int i)throws Exception {
        var c=DriverManager.getConnection("jdbc:mysql://127.0.0.1:13326/"+DBS[i]+"?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&allowMultiQueries=false&allowLoadLocalInfile=false",USERS[i],System.getenv(PASSWORDS[i]));
        var r=AuthMysqlMigration.rows(c,"SELECT DATABASE() db,CURRENT_USER() account,VERSION() version,@@server_uuid uuid,@@hostname hostname,@@port port,@@session.time_zone zone").getFirst();
        check(DBS[i].equals(r.get("db"))&&(USERS[i]+"@%").equals(r.get("account"))&&"8.0.46".equals(r.get("version"))&&System.getenv("AUS_UUID").equals(r.get("uuid"))&&"kkiri-aus-mysql-20261021".equals(r.get("hostname"))&&((Number)r.get("port")).intValue()==3306&&Set.of("UTC","+00:00").contains(r.get("zone")),"Approved target identity mismatch");
        var grants=AuthMysqlMigration.rows(c,"SHOW GRANTS");check(grants.size()==2,"Unexpected roles/grants");
        String schema=" ON `"+DBS[i].replace("_","\\_")+"`.* TO ";
        for(var grant:grants){String g=grant.values().iterator().next().toString();check((g.startsWith("GRANT USAGE ON *.* TO ")||g.contains(schema))&&!g.contains("WITH GRANT OPTION"),"Unexpected account scope");}
        return c;
    }
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        ((ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).setLevel(ch.qos.logback.classic.Level.OFF);
        Path root=Path.of(args[1]),work=root.resolve("backend/build/auth-user-survey-mysql");
        if(args[0].equals("init-auth")){
            try(var c=open(0)){
                check(AuthMysqlConcurrency.scalar(c,"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()")==0,"Initialization requires empty approved Auth schema");
                Path ddl=work.resolve("current-entity-schema.sql");Files.deleteIfExists(ddl);
                var f=new LocalContainerEntityManagerFactoryBean();f.setPackagesToScan("com.kkirikkiri");f.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
                f.setJpaPropertyMap(Map.of("hibernate.dialect","org.hibernate.dialect.MySQLDialect","hibernate.boot.allow_jdbc_metadata_access",false,"hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl","jakarta.persistence.schema-generation.database.action","none","jakarta.persistence.schema-generation.scripts.action","create","jakarta.persistence.schema-generation.scripts.create-target",ddl.toString()));f.afterPropertiesSet();f.destroy();
                var allowed=new HashSet<>(AuthMysqlConcurrency.TABLES);allowed.remove("auth_email_identity");allowed.remove("auth_limit_bucket");
                for(String sql:AuthMysqlMigration.split(Files.readString(ddl))){
                    var matcher=java.util.regex.Pattern.compile("(?is)^(?:create table|alter table)\\s+`?([a-z_]+)").matcher(sql);
                    boolean apply=matcher.find()&&allowed.contains(matcher.group(1));
                    if(sql.toLowerCase().startsWith("create index")&&sql.contains(" on email_verification "))apply=false;
                    if(apply)try(var s=c.createStatement()){s.execute(sql);}
                }
                check(AuthMysqlConcurrency.scalar(c,"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()")==7,"Expected seven legacy Auth/User tables");
                for(String sql:AuthMysqlMigration.split(Files.readString(root.resolve("backend/migrations/20261014_auth_limits.sql"))))try(var s=c.createStatement()){s.execute(sql);}
                check(AuthMysqlConcurrency.scalar(c,"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()")==8,"First manual auth migration failed");
            }
            AuthMysqlMigration.main(new String[]{root.toString()});
        } else if(args[0].equals("auth-constraints")){
            var result=new LinkedHashMap<String,Object>();int rejections=0;
            try(var c=open(0)){
                for(String table:AuthMysqlConcurrency.TABLES)check(AuthMysqlConcurrency.scalar(c,"SELECT COUNT(*) FROM `"+table+"`")==0,"Constraint fixture requires empty approved Auth tables");
                c.setAutoCommit(false);
                try{
                    MultiSupport.exec(c,"INSERT INTO user(password,school_email,email_verified,role,status,created_at,updated_at) VALUES('constraint-fixture-hash','constraint@hknu.ac.kr',true,'USER','ACTIVE',NOW(),NOW())");
                    long owner=AuthMysqlConcurrency.scalar(c,"SELECT user_id FROM user WHERE school_email='constraint@hknu.ac.kr'");
                    MultiSupport.exec(c,"INSERT INTO auth_email_identity(account_email) VALUES('constraint@hknu.ac.kr')");
                    sqlError(1062,()->MultiSupport.exec(c,"INSERT INTO auth_email_identity(account_email) VALUES('constraint@hknu.ac.kr')"));rejections++;
                    MultiSupport.exec(c,"INSERT INTO auth_limit_bucket VALUES(REPEAT('a',64),0,0,0,0,0,0,0)");
                    sqlError(1062,()->MultiSupport.exec(c,"INSERT INTO auth_limit_bucket VALUES(REPEAT('a',64),0,0,0,0,0,0,0)"));rejections++;
                    sqlError(1048,()->MultiSupport.exec(c,"INSERT INTO auth_limit_bucket VALUES(REPEAT('b',64),0,0,NULL,0,0,0,0)"));rejections++;
                    String verification="INSERT INTO email_verification(user_id,email,purpose,verification_code,expires_at,created_at) VALUES(?,?,'SIGNUP','constraint-code-hash',NOW()+INTERVAL 1 MINUTE,NOW())";
                    MultiSupport.exec(c,verification,owner,"constraint@hknu.ac.kr");
                    sqlError(1452,()->MultiSupport.exec(c,verification,-1L,"orphan@hknu.ac.kr"));rejections++;
                    sqlError(1048,()->MultiSupport.exec(c,verification,owner,null));rejections++;
                    String reset="INSERT INTO password_reset_token(user_id,token_hash,expires_at,created_at) VALUES(?,?,NOW()+INTERVAL 1 MINUTE,NOW())";
                    MultiSupport.exec(c,reset,owner,"constraint-token-hash");
                    sqlError(1062,()->MultiSupport.exec(c,reset,owner,"constraint-token-hash"));rejections++;
                    sqlError(1452,()->MultiSupport.exec(c,reset,-1L,"orphan-token-hash"));rejections++;
                    sqlError(1048,()->MultiSupport.exec(c,reset,owner,null));rejections++;
                }finally{c.rollback();}
                for(String table:AuthMysqlConcurrency.TABLES)check(AuthMysqlConcurrency.scalar(c,"SELECT COUNT(*) FROM `"+table+"`")==0,"Constraint rollback left rows");
                result.put("result","PASS");result.put("rejections",rejections);result.put("mysqlCodes",List.of(1062,1048,1452));result.put("allAuthTablesEmpty",true);result.put("wholeTransactionRolledBack",true);
            }catch(Throwable failure){result.put("result","FAIL");result.put("error",MultiSupport.error(failure));Files.writeString(work.resolve("auth-constraints.json"),AuthMysqlConcurrency.json(result));throw failure;}
            Files.writeString(work.resolve("auth-constraints.json"),AuthMysqlConcurrency.json(result));System.out.println("AUTH physical constraints PASS; rollback verified.");
        } else if(args[0].equals("audit")){
            var evidence=new ArrayList<Object>();
            for(int i=0;i<DBS.length;i++)try(var c=open(i)){
                var counts=new TreeMap<String,Long>();
                for(var row:AuthMysqlMigration.rows(c,"SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() ORDER BY table_name")){String table=row.values().iterator().next().toString();check(table.matches("[A-Za-z_]+"),"Unsafe identifier");counts.put(table,AuthMysqlConcurrency.scalar(c,"SELECT COUNT(*) FROM `"+table+"`"));}
                evidence.add(Map.of("database",DBS[i],"grants",AuthMysqlMigration.rows(c,"SHOW GRANTS"),"rows",counts,"allEmpty",counts.values().stream().allMatch(n->n==0)));
            }
            Files.writeString(work.resolve("database-audit.json"),AuthMysqlConcurrency.json(evidence));
            System.out.println("AUDIT written; identities/grants/row counts recorded for all five approved schemas.");
        } else throw new IllegalArgumentException("Unknown safe stage");
    }
    interface SqlAction {void run()throws SQLException;}
    static void sqlError(int code,SqlAction action)throws SQLException {
        try{action.run();throw new AssertionError("Expected physical constraint failure");}
        catch(SQLException failure){check(failure.getErrorCode()==code,"Unexpected MySQL constraint vendor code: "+failure.getErrorCode());}
    }
}
