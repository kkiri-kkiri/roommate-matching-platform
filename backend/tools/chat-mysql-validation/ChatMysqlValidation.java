import com.kkirikkiri.chat.dto.*;
import com.kkirikkiri.chat.entity.*;
import com.kkirikkiri.chat.service.ChatService;
import com.kkirikkiri.chat.repository.*;
import com.kkirikkiri.user.entity.*;
import com.kkirikkiri.community.post.entity.CommunityPost;
import com.kkirikkiri.community.comment.entity.CommunityComment;
import com.kkirikkiri.global.exception.*;
import com.zaxxer.hikari.*;
import jakarta.persistence.*;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import javax.sql.DataSource;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.*;
import tools.jackson.databind.json.JsonMapper;

/** Opt-in MySQL verifier: no Boot/.env import, HTTP/SMTP/seed/scheduler, global settings or cleanup. */
public class ChatMysqlValidation {
    static final JsonMapper JSON = JsonMapper.builder().build();
    static Map<String,Object> manifest;
    static HikariDataSource pool;
    static AnnotationConfigApplicationContext context;
    static JdbcTemplate jdbc;
    static EntityManager em;
    static PlatformTransactionManager manager;
    static ChatService chat;
    static Path root, output;
    static final List<Map<String,Object>> results = new ArrayList<>();
    static final List<Map<String,Object>> sqlFailures = new ArrayList<>();
    static final ExecutorService workers = Executors.newFixedThreadPool(2);
    static final List<Long> connections = new CopyOnWriteArrayList<>();
    static final List<String> order = new CopyOnWriteArrayList<>();
    static String schema;
    static final LocalDateTime FIXED = LocalDateTime.of(2026,1,1,12,0);
    static String text(Map<?,?> map, String key) { return Objects.toString(map.get(key), ""); }
    static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    static String url(String db) {
        check(db.equals(text(manifest,"currentDb")) || db.equals(text(manifest,"legacyDb")),"Schema outside approved pair");
        check(db.matches("kkirichat(validation|legacy)[0-9a-f]+"), "Unsafe schema identifier");
        return "jdbc:mysql://127.0.0.1:3306/"+db+"?serverTimezone=Asia/Seoul&characterEncoding=UTF-8&autoReconnect=false&allowMultiQueries=false&allowLoadLocalInfile=false&connectTimeout=5000&socketTimeout=20000";
    }
    static Connection connection(String db) throws SQLException {
        return DriverManager.getConnection(url(db), text(manifest,"username"), System.getenv("KKIRI_CHAT_TEST_PASSWORD"));
    }
    static void safety(Connection c, String db, boolean empty) throws Exception {
        var j = new JdbcTemplate(new org.springframework.jdbc.datasource.SingleConnectionDataSource(c,true));
        check(db.equals(j.queryForObject("select database()",String.class)),"Wrong selected schema");
        check(text(manifest,"serverUuid").equals(j.queryForObject("select @@server_uuid",String.class)),"Wrong server UUID");
        check(text(manifest,"hostname").equals(j.queryForObject("select @@hostname",String.class)),"Wrong hostname");
        check("8.0.44".equals(j.queryForObject("select version()",String.class)),"Unexpected MySQL version");
        check((text(manifest,"username")+"@127.0.0.1").equals(j.queryForObject("select current_user()",String.class)),"Wrong current account");
        check("NONE".equals(j.queryForObject("select current_role()",String.class)),"Unexpected inherited role");
        var grants = j.queryForList("show grants",String.class);
        check(grants.size()==3,"Unexpected grant count");
        int databaseGrants=0;
        for (String grant : grants) {
            if (grant.startsWith("GRANT USAGE ON *.* TO ")) continue;
            check(!grant.contains("WITH GRANT OPTION") && !grant.contains("ALL PRIVILEGES"),"Excess privilege");
            String privileges = "SELECT, INSERT, UPDATE, DELETE, CREATE, REFERENCES, INDEX, ALTER";
            String prefix = grant.substring(0,grant.indexOf(" ON "));
            check(new HashSet<>(Arrays.asList(prefix.substring(6).split(", "))).equals(new HashSet<>(Arrays.asList(privileges.split(", ")))),"Unexpected privileges");
            check(grant.contains(" ON `"+text(manifest,"currentDb")+"`.* TO ") || grant.contains(" ON `"+text(manifest,"legacyDb")+"`.* TO "),"Grant outside approved pair");
            databaseGrants++;
        }
        check(databaseGrants==2,"Missing exact schema grants");
        // A harmless SELECT must fail: never probe isolation by attempting a write to a protected DB.
        for (String protectedDb : List.of("kkirikkiri","kkiri_kkiri")) {
            try { j.queryForList("select * from `"+protectedDb+"`.chat_message limit 0"); throw new AssertionError("Protected schema accessible"); }
            catch (org.springframework.dao.DataAccessException expected) {
                var cause = expected.getMostSpecificCause();
                check(cause instanceof SQLException && Set.of(1044,1142).contains(((SQLException)cause).getErrorCode()),"Expected permission denial");
            }
        }
        if (empty) check(j.queryForObject("select count(*) from information_schema.tables where table_schema=database()",Integer.class)==0,"Preexisting tables: refuse initialization");
        var proof = new LinkedHashMap<String,Object>(j.queryForMap("select version() version,database() db,current_user() account,@@server_uuid serverUuid,@@hostname hostname,@@port port,@@transaction_isolation isolationLevel,@@sql_mode sqlMode,@@default_storage_engine engine,@@lower_case_table_names lowerCaseTableNames"));
        proof.put("jdbcUrl",url(db)); proof.put("grants",grants); proof.put("protectedSchemaSelectDenied",true);
        proof.put("globalSettings",j.queryForMap("select @@global.innodb_lock_wait_timeout lockWaitSeconds,@@global.max_execution_time selectTimeoutMs,@@global.lock_wait_timeout metadataLockWaitSeconds,@@global.transaction_isolation isolationLevel"));
        Files.writeString(output.resolve(db+".safety.json"), JSON.writeValueAsString(proof));
    }
    static void open(String db, boolean create) throws Exception {
        open(db, create, false);
    }
    static void open(String db, boolean create, boolean readOnly) throws Exception {
        schema=db;
        try(var c=connection(db)) { safety(c,db,create); }
        var cfg=new HikariConfig(); cfg.setJdbcUrl(url(db)); cfg.setUsername(text(manifest,"username"));
        cfg.setPassword(System.getenv("KKIRI_CHAT_TEST_PASSWORD")); cfg.setMaximumPoolSize(4); cfg.setMinimumIdle(0);
        cfg.setReadOnly(readOnly);
        cfg.setConnectionTimeout(5000); cfg.setTransactionIsolation("TRANSACTION_REPEATABLE_READ");
        cfg.setConnectionInitSql("set session innodb_lock_wait_timeout=3, lock_wait_timeout=3, max_execution_time=3000"
                + (readOnly ? ", transaction_read_only=1" : "")); pool=new HikariDataSource(cfg);
        Config.ddl=create ? "create-only" : "validate";
        context=new AnnotationConfigApplicationContext();
        context.getEnvironment().setActiveProfiles("chat-mysql-validation"); context.register(Config.class); context.refresh();
        jdbc=context.getBean(JdbcTemplate.class); em=context.getBean(EntityManager.class);
        manager=context.getBean(PlatformTransactionManager.class); chat=context.getBean(ChatService.class);
        check(jdbc.queryForObject("select count(*) from information_schema.tables where table_schema=database() and engine<>'InnoDB'",Integer.class)==0,"Non-InnoDB table");
        Files.writeString(output.resolve(db+".ddl.json"), JSON.writeValueAsString(jdbc.queryForList("select table_name,column_name,column_type,is_nullable,column_default from information_schema.columns where table_schema=database() order by table_name,ordinal_position")));
    }
    static void close() { if(context!=null){context.close();context=null;} if(pool!=null){pool.close();pool=null;} }
    public static void main(String[] args) throws Exception {
        ((ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).setLevel(ch.qos.logback.classic.Level.OFF);
        check(args.length==3 && System.getenv("KKIRI_CHAT_TEST_PASSWORD")!=null,"Use the PowerShell runner");
        check(Set.of("Full","Regression","Inspect","QueryComparison","ExistingRegression","FinalPerformance","IndexCycle","Optimized100").contains(args[2]),"Unsupported validation stage");
        root=Path.of(args[0]); manifest=JSON.readValue(Files.readString(Path.of(args[1])).replace("\uFEFF",""),Map.class);
        check("created-retain-no-automatic-deletion".equals(text(manifest,"state")),"Unapproved/incomplete environment");
        output=root.resolve("backend/build/chat-mysql-validation/run-"+UUID.randomUUID()); Files.createDirectories(output);
        Files.writeString(output.resolve("source-hashes.json"),JSON.writeValueAsString(sourceHashes()));
        boolean passed=false;
        try {
            boolean initial=args[2].equals("Full");
            open(text(manifest,"currentDb"),initial,Set.of("Inspect","QueryComparison","Optimized100").contains(args[2]));
            test(initial?"safety-and-empty-schema-creation":"safety-and-existing-owned-schema-validation",()->{});
            if (args[2].equals("Inspect")) {
                ChatMysqlQueryComparison.inventory(); close();
                open(text(manifest,"legacyDb"),false,true); ChatMysqlQueryComparison.inventory(); close();
            } else if (args[2].equals("QueryComparison")) {
                ChatMysqlQueryComparison.run(); close();
            } else if (args[2].equals("FinalPerformance")) {
                ChatMysqlFinalPerformance.run(); close();
            } else if (args[2].equals("Optimized100")) {
                ChatMysqlQueryComparison.optimizedHundred();close();
            } else if (args[2].equals("IndexCycle")) {
                close(); open(text(manifest,"legacyDb"),false); ChatMysqlIndexCycle.run(); close();
            } else if (args[2].equals("ExistingRegression")) {
                var original=ChatMysqlPreservation.capture("regression-original-before");
                try { ChatMysqlChecks.run(); }
                finally { test("regression-original-data-preserved",()->ChatMysqlPreservation.verify(original,"regression-original-after")); }
                close();
            } else if(args[2].equals("Regression")) {
                test("lock-timeout-rollback-and-retry-corrected-harness",ChatMysqlChecks::timeout);
                test("current-index-final-metadata",ChatMysqlPerformance::indexMetadata);close();
                open(text(manifest,"legacyDb"),false);ChatMysqlMigrations.followUp();close();
            } else {
            ChatMysqlChecks.run(); ChatMysqlPerformance.runBefore();
            test("current-schema-receipt-alter-rerun-conflict",()->expectedSql(1060,()->jdbc.execute("ALTER TABLE CHAT_MESSAGE ADD COLUMN is_read BOOLEAN NOT NULL DEFAULT FALSE")));
            if(initial || jdbc.queryForObject("select count(*) from information_schema.statistics where table_schema=database() and index_name='idx_chat_message_room_latest'",Integer.class)==0)
                test("query-index-migration-current",()->applySql("20261019_chat_query_indexes.sql",jdbc));
            ChatMysqlPerformance.runAfter();
            test("index-metadata-and-rerun-conflict",ChatMysqlPerformance::indexMetadata);
            close();
            open(text(manifest,"legacyDb"),true);
            ChatMysqlMigrations.run();close();
            }
            // Final validate only against both owned schemas. No create/update/cleanup at shutdown.
            open(text(manifest,"currentDb"),false,true); close();
            open(text(manifest,"legacyDb"),false,true); close();
            passed=results.stream().noneMatch(r->"FAIL".equals(r.get("result")));
        } catch(Throwable failure) { recordFailure("harness",failure); }
        finally {
            workers.shutdownNow(); if(!workers.awaitTermination(20,TimeUnit.SECONDS)) passed=false;
            close();
            Files.writeString(output.resolve("results.json"),JSON.writeValueAsString(Map.of("passed",passed,"cases",results,"resourcesRetained",true)));
            Files.writeString(output.resolve("expected-sql-errors.json"),JSON.writeValueAsString(sqlFailures));
            System.out.println("Evidence: "+output); System.out.println("Cases="+results.size()+" passed="+results.stream().filter(r->"PASS".equals(r.get("result"))).count()+" failed="+results.stream().filter(r->"FAIL".equals(r.get("result"))).count());
        }
        if(!passed) System.exit(1);
    }
    static Map<String,String> sourceHashes() throws Exception {
        var result=new TreeMap<String,String>();
        for(String dir:List.of("backend/src/main/java/com/kkirikkiri/chat","backend/tools/chat-mysql-validation","backend/migrations")){
            try(var paths=Files.walk(root.resolve(dir))){for(Path p:paths.filter(Files::isRegularFile).toList()){
                result.put(root.relativize(p).toString(),HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p))));
            }}
        } return result;
    }
    interface Case { void run() throws Exception; }
    static void test(String name,Case action) throws Exception {
        connections.clear(); order.clear(); long started=System.nanoTime();
        try { action.run(); results.add(Map.of("case",name,"result","PASS","elapsedMs",(System.nanoTime()-started)/1e6,"connections",List.copyOf(connections),"order",List.copyOf(order))); System.out.println("PASS "+name); }
        catch(Throwable failure) { recordFailure(name,failure); System.out.println("FAIL "+name+" ("+failure.getClass().getSimpleName()+")"); }
        Files.writeString(output.resolve("results.partial.json"),JSON.writeValueAsString(results));
    }
    static void recordFailure(String name,Throwable failure) {
        var details=new ArrayList<String>(); Throwable current=failure;
        for(int n=0;current!=null&&n<6;n++,current=current.getCause()) {
            String item=current.getClass().getName();
            if(current instanceof AssertionError) item += ": "+current.getMessage();
            if(current instanceof SQLException sql) item += " SQLState="+sql.getSQLState()+" code="+sql.getErrorCode();
            details.add(item);
        }
        results.add(Map.of("case",name,"result","FAIL","causes",details,"connections",List.copyOf(connections),"order",List.copyOf(order)));
    }
    static TransactionTemplate tx() { var t=new TransactionTemplate(manager);t.setTimeout(12);return t; }
    static void session() { connections.add(jdbc.queryForObject("select connection_id()",Long.class)); }
    static void committed(String label) { TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){public void afterCommit(){order.add(label);}}); }
    static void await(CountDownLatch latch) { try{check(latch.await(8,TimeUnit.SECONDS),"Latch timeout");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);} }
    static void waiting(Future<?> job) throws Exception { try{job.get(200,TimeUnit.MILLISECONDS);throw new AssertionError("Expected row lock wait");}catch(TimeoutException expected){order.add("lock-wait-observed");} }
    static void error(ErrorCode code,Runnable action) { try{action.run();throw new AssertionError("Expected "+code);}catch(CustomException e){check(e.getErrorCode()==code,"Wrong business error: "+e.getErrorCode());} }
    static void expectedSql(int code,Runnable action) {
        expectedSql(Set.of(code),action);
    }
    static void expectedSql(Set<Integer> codes,Runnable action) {
        try{action.run();throw new AssertionError("Expected SQL code "+codes);}catch(org.springframework.dao.DataAccessException e){
            if(e.getMostSpecificCause() instanceof SQLException s)sqlFailures.add(Map.of("schema",schema,"expected",codes,"observed",s.getErrorCode(),"state",Objects.toString(s.getSQLState(),"")));
            check(e.getMostSpecificCause() instanceof SQLException s && codes.contains(s.getErrorCode()),"Expected SQL "+codes+" but observed "+(e.getMostSpecificCause() instanceof SQLException s?s.getErrorCode():e.getMostSpecificCause().getClass().getSimpleName()));
        }
    }
    static void applySql(String file,JdbcTemplate j) throws Exception {
        String sql=Files.readString(root.resolve("backend/migrations/"+file)).replaceAll("(?m)^--.*$","");
        script(sql,j);
    }
    static void script(String sql,JdbcTemplate j) {
        j.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) c->{
            try(var st=c.createStatement()){
                try{for(String statement:sql.split(";"))if(!statement.isBlank())st.execute(statement.trim());}
                catch(SQLException failure){st.execute("ROLLBACK");throw failure;}
            }return null;
        });
    }
    static long user() {return tx().execute(s->{var u=new User("synthetic-not-a-password-hash",UUID.randomUUID()+"@test.invalid");em.persist(u);return u.getUserId();});}
    record Fixture(long reader,long writer,long room,long member){}
    static Fixture fixture() {long a=user(),b=user();return room(a,b);}
    static Fixture room(long a,long b) {return tx().execute(s->{var r=new ChatRoom(a,b,ChatRoomCategory.ROOMMATE,null,null);em.persist(r);var m=new ChatRoomMember(r,em.getReference(User.class,a));em.persist(m);em.persist(new ChatRoomMember(r,em.getReference(User.class,b)));return new Fixture(a,b,r.getChatRoomId(),m.getChatRoomMemberId());});}
    static long message(Fixture f,String content,LocalDateTime at) {return tx().execute(s->{var m=new ChatMessage(em.getReference(ChatRoom.class,f.room),em.getReference(User.class,f.writer),content);em.persist(m);em.flush();if(at!=null)jdbc.update("update CHAT_MESSAGE set sent_at=? where message_id=?",at,m.getMessageId());return m.getMessageId();});}
    static void read(Fixture f,long... ids) {chat.markRead(f.reader,f.room,new ChatReadRequest(Arrays.stream(ids).boxed().toList()));}
    static boolean isRead(long id){return jdbc.queryForObject("select is_read from CHAT_MESSAGE where message_id=?",Boolean.class,id);}
    static long unread(Fixture f){return context.getBean(ChatMessageRepository.class).countUnreadMessages(f.room,f.reader);}
    public static class SqlCapture implements StatementInspector {
        static final ThreadLocal<List<String>> SQL=ThreadLocal.withInitial(ArrayList::new);
        public String inspect(String sql){SQL.get().add(sql);return sql;}
        static void reset(){SQL.get().clear();}
    }
    @Configuration @EnableTransactionManagement @EnableJpaRepositories(basePackages="com.kkirikkiri") @Import(ChatService.class)
    static class Config {
        static String ddl="validate";
        @Bean DataSource dataSource(){return new CapturingDataSource(pool);}
        @Bean JdbcTemplate jdbcTemplate(){return new JdbcTemplate(dataSource());}
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(){
            var f=new LocalContainerEntityManagerFactoryBean();f.setDataSource(dataSource());f.setPackagesToScan("com.kkirikkiri");f.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            f.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto",ddl,"hibernate.hbm2ddl.halt_on_error",true,"hibernate.session_factory.statement_inspector",SqlCapture.class.getName(),"hibernate.generate_statistics",true,"hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl"));return f;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory f){return new JpaTransactionManager(f);}
        @Bean @Primary EntityManager entityManager(EntityManagerFactory f){return SharedEntityManagerCreator.createSharedEntityManager(f);}
        @Bean(destroyMethod="close") ValidatorFactory validatorFactory(){return Validation.buildDefaultValidatorFactory();}
        @Bean Validator validator(ValidatorFactory f){return f.getValidator();}
    }
    record StatementData(String sql,Map<Integer,Object> parameters){}
    static class CapturingDataSource extends org.springframework.jdbc.datasource.AbstractDataSource {
        final DataSource delegate;
        static final ThreadLocal<Boolean> ENABLED=ThreadLocal.withInitial(()->false);
        static final ThreadLocal<List<StatementData>> STATEMENTS=ThreadLocal.withInitial(ArrayList::new);
        CapturingDataSource(DataSource d){delegate=d;}
        public Connection getConnection()throws SQLException{return wrap(delegate.getConnection());}
        public Connection getConnection(String u,String p)throws SQLException{throw new SQLException("Credentials cannot be overridden");}
        Connection wrap(Connection c){return (Connection)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(proxy,m,args)->{
            try{
                Object result=m.invoke(c,args);
                if(m.getName().equals("prepareStatement")&&result instanceof PreparedStatement st){
                    String sql=(String)args[0];var values=new TreeMap<Integer,Object>();
                    return java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{PreparedStatement.class},(p,method,a)->{
                        try{
                            if(method.getName().startsWith("set")&&a!=null&&a.length>=2&&a[0] instanceof Integer i)values.put(i,method.getName().equals("setNull")?null:a[1]);
                            if(method.getName().equals("clearParameters"))values.clear();
                            if(method.getName().startsWith("execute")&&ENABLED.get())STATEMENTS.get().add(new StatementData(sql,new TreeMap<>(values)));
                            return method.invoke(st,a);
                        }catch(java.lang.reflect.InvocationTargetException e){throw e.getCause();}
                    });
                }return result;
            }catch(java.lang.reflect.InvocationTargetException e){throw e.getCause();}
        });}
    }
}
