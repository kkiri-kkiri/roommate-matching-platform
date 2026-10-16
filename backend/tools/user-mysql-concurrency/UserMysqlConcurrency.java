import com.kkirikkiri.user.entity.*;
import com.kkirikkiri.user.repository.*;
import com.kkirikkiri.user.service.UserService;
import com.kkirikkiri.user.dto.*;
import com.kkirikkiri.admin.service.AdminService;
import com.kkirikkiri.admin.dto.AdminMemberStatusUpdateRequest;
import com.kkirikkiri.auth.entity.PasswordResetToken;
import com.kkirikkiri.auth.repository.PasswordResetTokenRepository;
import com.kkirikkiri.auth.service.PasswordResetService;
import com.kkirikkiri.auth.dto.PasswordResetConfirmRequest;
import com.kkirikkiri.global.exception.*;
import com.kkirikkiri.matching.service.*;
import com.kkirikkiri.matching.repository.*;
import com.kkirikkiri.survey.entity.*;
import com.kkirikkiri.survey.repository.*;
import com.zaxxer.hikari.*;
import jakarta.persistence.*;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import javax.sql.DataSource;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.event.TransactionalEventListenerFactory;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/** Standalone opt-in verifier. Does not load Boot configuration, .env, SMTP, HTTP or schedulers. */
public class UserMysqlConcurrency {
    static final String URL="jdbc:mysql://127.0.0.1:3308/kkiriusertest?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&autoReconnect=false&allowMultiQueries=false&allowLoadLocalInfile=false&connectTimeout=5000&socketTimeout=15000";
    static final String RUN="um"+UUID.randomUUID().toString().replace("-", "").substring(0,10);
    static final String OLD=UUID.randomUUID()+"!aA1", NEW=UUID.randomUUID()+"!bB2";
    static final List<Map<String,Object>> results=new ArrayList<>();
    static final List<Map<String,Object>> dbErrors=new CopyOnWriteArrayList<>();
    static final Set<String> sqls=ConcurrentHashMap.newKeySet();
    static final List<Map<String,Object>> sqlTrace=new CopyOnWriteArrayList<>();
    static final Map<String,Object> observations=new ConcurrentHashMap<>();
    static volatile String currentCase="setup";
    record RefreshPause(CountDownLatch locked,CountDownLatch release,AtomicBoolean used){}
    static final ThreadLocal<RefreshPause> refreshPause=new ThreadLocal<>();
    static final List<Long> ownedUsers=new CopyOnWriteArrayList<>();
    static final AtomicInteger seq=new AtomicInteger(), peakActive=new AtomicInteger(), peakWaiting=new AtomicInteger();
    static final AtomicBoolean unsafe=new AtomicBoolean();
    static HikariDataSource pool; static DataSource guarded; static JdbcTemplate jdbc;
    static AnnotationConfigApplicationContext ctx; static PlatformTransactionManager tm;
    static UserRepository users; static UserProfileRepository profiles; static UserService userService;
    static AdminService admins; static PasswordResetService resets; static PasswordEncoder encoder;
    static MatchingRefreshService refresh; static Path out;
    static Long adminId, dormId, f1, f2, m1, m2; static List<String> tables;
    static Map<String,String> ddlBefore; static Map<String,String> baseline;
    static final JsonMapper JSON=JsonMapper.builder().build();

    public static void main(String[] args) throws Exception {
        ((ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).setLevel(ch.qos.logback.classic.Level.OFF);
        check(args.length==2 && System.getenv("KKIRI_USER_TEST_DB_PASSWORD")!=null,"Runner environment required");
        Path root=Path.of(args[0]).toRealPath();
        out=root.resolve("backend/tools/user-mysql-concurrency/results/"+LocalDate.now(ZoneId.of("Asia/Seoul"))+"-"+RUN);
        Files.createDirectories(out); baseline=protectedFiles(root); write("protected-files-before.json",baseline);
        boolean claimed=false,cleaned=false,unchanged=false; ScheduledExecutorService monitor=Executors.newSingleThreadScheduledExecutor();
        try(Connection claim=open()) {
            guard(claim); grants(claim);
            check(number(claim,"SELECT GET_LOCK('kkiriusertest.user_validation',0)")==1,"Another User run is active"); claimed=true;
            if(args[1].equals("Initialize")) {
                check(number(claim,"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()")==0,"Initialization requires a completely empty User schema");
                pool=newPool(16,10000,2); guarded=new Guarded(pool);
                var init=emf(guarded,"create-only"); init.afterPropertiesSet(); init.destroy();
            } else { pool=newPool(16,10000,2); guarded=new Guarded(pool); }
            tables=jdbcStrings(claim,"SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() ORDER BY table_name");
            write("discovered-tables.json",tables);
            check(tables.size()==30,"Expected full current 30-Entity schema, actual="+tables.size());
            for(String t:tables)check(number(claim,"SELECT COUNT(*) FROM `"+t+"`")==0,"User schema contains preexisting data");
            ddlBefore=ddl(claim); write("schema-before.json",ddlBefore);
            write("environment.json",Map.ofEntries(Map.entry("endpoint","127.0.0.1:3308/kkiriusertest"),Map.entry("account","kkiri_user_test"),Map.entry("containerId",System.getenv("KKIRI_USER_TEST_CONTAINER_ID")),Map.entry("identity",queryRow(claim,"SELECT DATABASE() db,CURRENT_USER() account,VERSION() version,@@server_uuid serverUuid,@@hostname hostname,@@port port,@@session.time_zone timeZone,@@transaction_isolation isolationLevel,@@collation_database collation,@@session.innodb_lock_wait_timeout lockWaitSeconds")),Map.entry("mode","single JVM, actual Spring services/repositories/transaction proxies, full Entity schema; no web server"),Map.entry("poolMaximum",16),Map.entry("poolBorrowTimeoutMs",10000),Map.entry("poolSessionLockWaitSeconds",2),Map.entry("tables",tables),Map.entry("externalServiceCapableBeans",0)));
            ctx=new AnnotationConfigApplicationContext(Config.class);
            check(ctx.getBean(EntityManagerFactory.class).getMetamodel().getEntities().size()==30,"Full Entity metamodel missing");
            tm=ctx.getBean(PlatformTransactionManager.class); jdbc=ctx.getBean(JdbcTemplate.class);
            users=ctx.getBean(UserRepository.class); profiles=ctx.getBean(UserProfileRepository.class);
            userService=ctx.getBean(UserService.class); admins=ctx.getBean(AdminService.class);
            resets=ctx.getBean(PasswordResetService.class); encoder=ctx.getBean(PasswordEncoder.class);refresh=ctx.getBean(MatchingRefreshService.class);
            monitor.scheduleAtFixedRate(()->{var p=pool.getHikariPoolMXBean();if(p!=null){peakActive.accumulateAndGet(p.getActiveConnections(),Math::max);peakWaiting.accumulateAndGet(p.getThreadsAwaitingConnection(),Math::max);}},0,10,TimeUnit.MILLISECONDS);
            fixtures();
            if(args[1].equals("Additional")) {
                test("password-reentrant-lock-retains-pending-account-and-other-entity",()->reentrant(false));
                test("password-reentrant-lock-full-rollback",()->reentrant(true));
                test("stale-managed-lock-current-read-sql",UserMysqlConcurrency::currentReadSql);
                test("password-fresh-lock-first-db-read-is-locking",UserMysqlConcurrency::firstReadSql);
                test("password-independent-users-overlap-and-commit",UserMysqlConcurrency::parallelUsers);
            } else {
            boolean full=!Set.of("Reproduce","FormerFailures").contains(args[1]);
            if(full)test("full-schema-unique-fk-null-constraints",UserMysqlConcurrency::constraints);
            for(boolean reset:new boolean[]{false,true})for(String state:List.of("SUSPENDED","WITHDRAWN"))for(boolean statusFirst:new boolean[]{false,true}) {
                if(args[1].equals("FormerFailures")&&!reset&&statusFirst)continue;
                final boolean r=reset,s=statusFirst; final String st=state;
                test((r?"reset":"password")+"-"+st.toLowerCase()+"-"+(s?"status-first":"password-first"),()->passwordStatus(r,st,s));
            }
            test("stale-managed-user-status-refresh",UserMysqlConcurrency::staleStatus);
            if(full) {
            test("same-current-password-concurrent-change",UserMysqlConcurrency::passwordTwice);
            test("same-reset-token-consumed-once",UserMysqlConcurrency::resetTwice);
            test("nickname-unique-race-loser-full-rollback",()->nicknameRace(false));
            test("nickname-case-equivalent-unique-race",()->nicknameRace(true));
            test("profile-outer-transaction-rollback-no-event",UserMysqlConcurrency::outerRollback);
            test("profile-invalid-reference-atomic-rollback",UserMysqlConcurrency::invalidReference);
            test("faculty-major-null-and-membership",UserMysqlConcurrency::academics);
            test("same-user-partial-updates-no-lost-change",UserMysqlConcurrency::partialUpdates);
            test("suspension-first-rejects-profile-update",UserMysqlConcurrency::inactiveProfile);
            test("matching-refresh-waits-for-profile-commit",()->matchingRace(true));
            test("profile-update-waits-for-matching-lock",()->matchingRace(false));
            test("matching-opposite-directions-sorted-locks",UserMysqlConcurrency::matchingSorted);
            test("matching-profile-rollback-retains-score",UserMysqlConcurrency::matchingRollback);
            test("user-row-lock-wait-and-unrelated-user",UserMysqlConcurrency::lockWait);
            test("profile-lock-timeout-full-rollback-409",()->lockTimeout(false));
            test("reset-token-lock-timeout-full-rollback-409",()->lockTimeout(true));
            test("forced-user-deadlock-victim-full-rollback-409",UserMysqlConcurrency::deadlock);
            test("pool-exhaustion-borrow-timeout-recovery",UserMysqlConcurrency::poolExhaustion);
            test("mixed-service-stress-resource-return",UserMysqlConcurrency::stress);
            }
            }
        } catch(Throwable failure) { var detail=new LinkedHashMap<String,Object>();detail.put("error",describe(failure));if(failure instanceof AssertionError)detail.put("assertion",failure.getMessage());results.add(row("harness","INCOMPLETE",detail)); }
        finally {
            monitor.shutdownNow();
            try {
                if(ctx!=null)drainEvents();
                if(claimed && ddlBefore!=null && !unsafe.get()) {
                    cleanup();
                    try(Connection c=open()) {
                        guard(c); var after=ddl(c);write("schema-after.json",after);
                        var counts=new TreeMap<String,Long>();for(String t:tables)counts.put(t,number(c,"SELECT COUNT(*) FROM `"+t+"`"));
                        write("cleanup.json",counts);check(counts.values().stream().allMatch(n->n==0),"Residual test rows");
                        check(normalize(ddlBefore).equals(normalize(after)),"Schema changed during verification");cleaned=true;
                    }
                }
            }catch(Throwable failure){results.add(row("cleanup","INCOMPLETE",Map.of("error",describe(failure))));}
            int finalActive=-1,finalWaiting=-1;
            try{if(pool!=null){idle(pool);finalActive=pool.getHikariPoolMXBean().getActiveConnections();finalWaiting=pool.getHikariPoolMXBean().getThreadsAwaitingConnection();}}catch(Throwable failure){results.add(row("resource-return","FAIL",Map.of("error",describe(failure))));}
            if(ctx!=null)ctx.close();if(pool!=null)pool.close();
            Map<String,String> after=protectedFiles(root);write("protected-files-after.json",after);unchanged=baseline.equals(after);
            if(!unchanged)results.add(row("protected-source-or-auth-evidence-changed","INCOMPLETE",Map.of()));
            write("sql-templates.json",new TreeSet<>(sqls));write("account-sql-trace.json",sqlTrace);write("database-errors.json",dbErrors);
            var artifacts=new TreeMap<String,String>();
            for(String p:List.of("backend/build/libs/backend-0.0.1-SNAPSHOT.jar","backend/tools/user-mysql-concurrency/UserMysqlConcurrency.java","backend/tools/user-mysql-concurrency/run.ps1"))artifacts.put(p,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(root.resolve(p)))));
            try(var walk=Files.walk(root.resolve("backend/build/classes/java/main"))){for(Path p:walk.filter(Files::isRegularFile).toList())artifacts.put(root.relativize(p).toString().replace('\\','/'),HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p))));}write("artifact-sha256.json",artifacts);
            String verdict=results.stream().anyMatch(r->r.get("status").equals("INCOMPLETE"))?"INSUFFICIENT":results.stream().anyMatch(r->r.get("status").equals("FAIL"))?"FAIL":"PASS";
            write("cases.json",results);write("summary.json",Map.ofEntries(Map.entry("run",RUN),Map.entry("verdict",verdict),Map.entry("results",results),Map.entry("cleanupAll30TablesEmptyAndDdlUnchanged",cleaned),Map.entry("productionSourceAndAuthEvidenceUnchanged",unchanged),Map.entry("peakActive",peakActive.get()),Map.entry("peakWaiting",peakWaiting.get()),Map.entry("finalActive",finalActive),Map.entry("finalWaiting",finalWaiting),Map.entry("poolClosed",pool!=null&&pool.isClosed()),Map.entry("realSmtpConnections",0),Map.entry("externalServiceCalls",0)));
            System.out.println("RESULT "+verdict+" evidence="+out);if(!verdict.equals("PASS"))System.exit(1);
        }
    }

    @Configuration @EnableTransactionManagement(proxyTargetClass=true)
    @EnableJpaRepositories(basePackages="com.kkirikkiri")
    @Import({UserService.class,AdminService.class,MatchingRefreshService.class,MatchingInputRepository.class,
        MatchingRefreshListener.class,MatchingRefreshConfiguration.class,RoommateMatchCalculator.class,
        com.kkirikkiri.user.repository.AdminMemberQueryRepository.class,
        com.kkirikkiri.community.repository.AdminPostQueryRepository.class,
        com.kkirikkiri.community.repository.AdminCommentQueryRepository.class,
        com.kkirikkiri.report.repository.AdminReportQueryRepository.class,
        com.kkirikkiri.calendar.repository.AdminCrawledScheduleQueryRepository.class,
        com.kkirikkiri.community.post.repository.CommunityPostReadRepository.class})
    static class Config {
        @Bean DataSource dataSource(){return guarded;}
        @Bean JdbcTemplate jdbcTemplate(DataSource d){return new JdbcTemplate(d);}
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource d){return emf(d,"validate");}
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory f,DataSource d){var t=new JpaTransactionManager(f);t.setDataSource(d);return t;}
        @Bean PasswordEncoder encoder(){return new BCryptPasswordEncoder();}
        @Bean(destroyMethod="close") ValidatorFactory validatorFactory(){return Validation.buildDefaultValidatorFactory();}
        @Bean Validator validator(ValidatorFactory f){return f.getValidator();}
        @Bean static TransactionalEventListenerFactory transactionalEventListenerFactory(){return new TransactionalEventListenerFactory();}
        // Only resetPassword(confirm) is exercised. Request/mail/limiter collaborators deliberately absent.
        @Bean PasswordResetService resets(UserRepository u,PasswordResetTokenRepository r,PasswordEncoder e,Validator v){return new PasswordResetService(u,r,null,e,v,null,null,300);}
    }
    static LocalContainerEntityManagerFactoryBean emf(DataSource d,String ddl) {
        var f=new LocalContainerEntityManagerFactoryBean();f.setDataSource(d);f.setPackagesToScan("com.kkirikkiri");f.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        f.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto",ddl,"hibernate.hbm2ddl.halt_on_error","true","hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl","hibernate.show_sql","false","hibernate.session_factory.statement_inspector",new SqlInspector()));return f;
    }
    public static class SqlInspector implements org.hibernate.resource.jdbc.spi.StatementInspector {
        public String inspect(String sql){sqls.add(sql);String n=currentCase;if(n.startsWith("password-")||n.startsWith("reset-")||n.startsWith("stale-managed"))sqlTrace.add(Map.of("case",n,"thread",Thread.currentThread().getName(),"sql",sql));RefreshPause pause=refreshPause.get();if(pause!=null&&sql.startsWith("select up1_0.user_profile_id")&&sql.contains(" in (")&&pause.used.compareAndSet(false,true)){pause.locked.countDown();await(pause.release);}return sql;}
    }
    static Connection open() throws SQLException {Properties p=new Properties();p.setProperty("user","kkiri_user_test");p.setProperty("password",System.getenv("KKIRI_USER_TEST_DB_PASSWORD"));return DriverManager.getConnection(URL,p);}
    static void guard(Connection c) throws SQLException {
        try(var s=c.createStatement();var r=s.executeQuery("SELECT DATABASE(),CURRENT_USER(),VERSION(),@@server_uuid,@@hostname,@@port,@@session.time_zone,@@global.read_only,@@global.super_read_only")) {
            if(!r.next()||!"kkiriusertest".equals(r.getString(1))||!r.getString(2).startsWith("kkiri_user_test@")||!"8.0.46".equals(r.getString(3))||!Objects.equals(System.getenv("KKIRI_USER_TEST_UUID"),r.getString(4))||!Objects.equals(System.getenv("KKIRI_USER_TEST_HOSTNAME"),r.getString(5))||r.getInt(6)!=3306||!Set.of("+00:00","UTC").contains(r.getString(7))||r.getInt(8)!=0||r.getInt(9)!=0){unsafe.set(true);throw new SQLException("Unsafe User database identity");}
        }
    }
    static void grants(Connection c) throws SQLException {
        List<String> g=jdbcStrings(c,"SHOW GRANTS FOR CURRENT_USER");
        check(g.size()==2&&g.stream().allMatch(s->(s.startsWith("GRANT USAGE ON *.* TO ")||s.contains(" ON `kkiriusertest`.* TO "))&&!s.contains("WITH GRANT OPTION")),"Privileges must be limited to literal User schema");write("grants.json",g);
    }
    static HikariDataSource newPool(int max,long timeout,int lockSeconds){var c=new HikariConfig();c.setJdbcUrl(URL);c.setUsername("kkiri_user_test");c.setPassword(System.getenv("KKIRI_USER_TEST_DB_PASSWORD"));c.setMaximumPoolSize(max);c.setMinimumIdle(1);c.setConnectionTimeout(timeout);c.setValidationTimeout(500);c.setInitializationFailTimeout(5000);c.setConnectionInitSql("SET SESSION innodb_lock_wait_timeout="+lockSeconds);c.setPoolName("UserValidation-"+max+"-"+System.nanoTime());return new HikariDataSource(c);}
    static class Guarded extends AbstractDataSource {
        final DataSource source;Guarded(DataSource s){source=s;}
        public Connection getConnection() throws SQLException {Connection c=source.getConnection();try{guard(c);return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(p,m,a)->{
            try {Object value=m.invoke(c,a);if(value instanceof PreparedStatement ps)return Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),new Class<?>[]{PreparedStatement.class},(pp,mm,aa)->{try{return mm.invoke(ps,aa);}catch(InvocationTargetException e){if(e.getCause() instanceof SQLException ex)dbErrors.add(Map.of("vendorCode",ex.getErrorCode(),"sqlState",String.valueOf(ex.getSQLState()),"thread",Thread.currentThread().getName()));throw e.getCause();}});return value;}catch(InvocationTargetException e){throw e.getCause();}
        });}catch(Throwable e){c.close();throw e;}}
        public Connection getConnection(String u,String p) throws SQLException{throw new SQLException("Arbitrary credentials forbidden");}
    }
    static void fixtures(){tx(()->{
        var dr=ctx.getBean(DormitoryRepository.class);var fr=ctx.getBean(FacultyRepository.class);var mr=ctx.getBean(MajorRepository.class);
        dormId=dr.saveAndFlush(new Dormitory(RUN,RUN,true)).getDormitoryId();
        Faculty a=fr.saveAndFlush(new Faculty(RUN+"A",true)),b=fr.saveAndFlush(new Faculty(RUN+"B",true));f1=a.getFacultyId();f2=b.getFacultyId();
        m1=mr.saveAndFlush(new Major(a,RUN+"A",true)).getMajorId();m2=mr.saveAndFlush(new Major(b,RUN+"B",true)).getMajorId();
        adminId=createUser(UserRole.ADMIN,false);
    });}
    static Long createUser(UserRole role,boolean profile) {
        User u=users.saveAndFlush(new User(encoder.encode(OLD),RUN+seq.incrementAndGet()+"@hknu.ac.kr",role));ownedUsers.add(u.getUserId());
        if(profile)profiles.saveAndFlush(new UserProfile(u,nick(),"남자",ctx.getBean(DormitoryRepository.class).findById(dormId).orElseThrow(),ctx.getBean(FacultyRepository.class).findById(f1).orElseThrow(),ctx.getBean(MajorRepository.class).findById(m1).orElseThrow(),(short)2002,"24","3학년","INTJ"));
        return u.getUserId();
    }
    static Long member(){return inTx(()->createUser(UserRole.USER,true));}
    static String nick(){return "N"+String.format("%07d",seq.incrementAndGet());}
    record Token(Long id,String value){}
    static Token token(Long id){String value=UUID.randomUUID()+"-"+UUID.randomUUID();Long tid=inTx(()->ctx.getBean(PasswordResetTokenRepository.class).saveAndFlush(new PasswordResetToken(users.findById(id).orElseThrow(),sha(value),LocalDateTime.now().plusMinutes(10))).getPasswordResetTokenId());return new Token(tid,value);}
    static void password(Long id,Token token,boolean reset){if(reset)resets.resetPassword(new PasswordResetConfirmRequest(token.value,NEW,NEW));else userService.changePassword(id,new PasswordChangeRequest(OLD,NEW,NEW));}
    static void status(Long id,String state){admins.updateMemberStatus(adminId,id,new AdminMemberStatusUpdateRequest(state));}
    static UserUpdateRequest request(String nick,String grade,String faculty,String major){return new UserUpdateRequest(nick,null,grade,faculty,major,null,null,null,null);}
    static UserUpdateRequest grade(String value){return request(null,value,null,null);}
    static Map<String,Object> snapshot(Long id){return jdbc.queryForMap("SELECT nickname,grade,faculty_id,major_id,mbti,dormitory_id FROM user_profile WHERE user_id=?",id);}
    static boolean used(Token t){return jdbc.queryForObject("SELECT used_at IS NOT NULL FROM password_reset_token WHERE password_reset_token_id=?",Boolean.class,t.id);}
    static void stored(Long id,String state,String pass){User u=users.findById(id).orElseThrow();check(u.getStatus().name().equals(state),"Wrong committed account status");check(encoder.matches(pass,u.getPassword()),"Wrong committed password");}
    static void tx(Runnable work){new TransactionTemplate(tm).executeWithoutResult(s->work.run());}
    static <T>T inTx(Supplier<T> work){return new TransactionTemplate(tm).execute(s->work.get());}
    static Throwable capture(Runnable work){try{work.run();return null;}catch(Throwable e){return e;}}
    static void business(Throwable e,ErrorCode code){check(e instanceof CustomException c&&c.getErrorCode()==code,"Expected "+code+"; actual="+describe(e));}
    static void await(CountDownLatch l){try{check(l.await(15,TimeUnit.SECONDS),"Coordination timeout");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError("Interrupted worker");}}
    static void blocked(Future<?> f)throws Exception{try{f.get(180,TimeUnit.MILLISECONDS);throw new AssertionError("Competing transaction did not wait");}catch(TimeoutException expected){}}
    static void stop(ExecutorService ex)throws Exception{ex.shutdownNow();check(ex.awaitTermination(20,TimeUnit.SECONDS),"Worker shutdown timeout");}

    static Map<String,Object> constraints(){
        long unique=jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='user_profile' AND column_name='nickname' AND non_unique=0",Long.class);
        long fk=jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.referential_constraints WHERE constraint_schema=DATABASE()",Long.class);
        check(unique==1&&fk>20,"Missing full-schema constraints");
        Long a=member(),b=member();String n=(String)snapshot(a).get("nickname");
        Throwable duplicate=capture(()->jdbc.update("UPDATE user_profile SET nickname=? WHERE user_id=?",n,b));check(sqlCode(duplicate)==1062,"MySQL UNIQUE must reject duplicate");
        Throwable nullRequired=capture(()->jdbc.update("UPDATE user_profile SET faculty_id=NULL WHERE user_id=?",b));check(sqlCode(nullRequired)==1048,"Faculty must be NOT NULL");
        Throwable orphan=capture(()->jdbc.update("UPDATE user_profile SET major_id=9223372036854775807 WHERE user_id=?",b));check(sqlCode(orphan)==1452,"Major FK must reject orphan");
        return Map.of("tables",30,"foreignKeys",fk,"nicknameUniqueIndexes",unique,"duplicateCode",1062,"notNullCode",1048,"orphanCode",1452);
    }
    static Map<String,Object> passwordStatus(boolean reset,String state,boolean statusFirst)throws Exception {
        Long id=member();Token t=token(id);var locked=new CountDownLatch(1);var release=new CountDownLatch(1);var started=new CountDownLatch(1);var ex=Executors.newFixedThreadPool(2);
        try{
            Future<Throwable> first=ex.submit(()->capture(()->tx(()->{if(statusFirst)status(id,state);else{users.findByIdForAccountUpdate(id).orElseThrow();password(id,t,reset);}locked.countDown();await(release);})));
            await(locked);Future<Throwable> second=ex.submit(()->{started.countDown();return capture(()->{if(statusFirst)password(id,t,reset);else status(id,state);});});await(started);
            try{blocked(second);}finally{release.countDown();}
            check(first.get(20,TimeUnit.SECONDS)==null,"First operation failed");Throwable e=second.get(20,TimeUnit.SECONDS);
            User finalUser=users.findById(id).orElseThrow();
            observations.putAll(Map.of("finalStatus",finalUser.getStatus().name(),"finalPasswordMatchesOld",encoder.matches(OLD,finalUser.getPassword()),"finalPasswordMatchesNew",encoder.matches(NEW,finalUser.getPassword()),"tokenUsed",used(t),"secondResult",describe(e),"waitingObserved",true));
            if(statusFirst)business(e,reset?ErrorCode.PASSWORD_RESET_ACCOUNT_INACTIVE:ErrorCode.UNAUTHORIZED);else check(e==null,"Status update failed after password commit");
            stored(id,state,statusFirst?OLD:NEW);check(used(t)==(reset&&!statusFirst),"Reset token consume must be atomic with password");
            return Map.of("reset",reset,"statusFirst",statusFirst,"finalStatus",state,"waitingObserved",true,"tokenUsed",used(t),"secondResult",describe(e));
        }finally{release.countDown();stop(ex);}
    }
    static Map<String,Object> staleStatus()throws Exception {
        Long id=member();var ex=Executors.newSingleThreadExecutor();try{tx(()->{User stale=users.findById(id).orElseThrow();check(stale.getStatus()==UserStatus.ACTIVE,"Fixture inactive");try{ex.submit(()->status(id,"SUSPENDED")).get(10,TimeUnit.SECONDS);}catch(Exception e){throw new RuntimeException(e);}String actualDbStatus=jdbc.queryForObject("SELECT status FROM user WHERE user_id=? FOR UPDATE",String.class,id);User after=users.findByIdForAccountUpdate(id).orElseThrow();observations.putAll(Map.of("currentLockingReadStatus",actualDbStatus,"entityStatusAfterAccountRefresh",after.getStatus().name()));check(after.getStatus()==UserStatus.SUSPENDED,"Managed status was stale after lock refresh");});return Map.of("refreshed",true);}finally{stop(ex);}
    }
    static Map<String,Object> passwordTwice()throws Exception{Long id=member();Token t=token(id);var r=concurrent(12,i->password(id,t,false));check(ok(r)==1&&count(r,ErrorCode.INVALID_REQUEST)==11,"Same current password must have one winner");stored(id,"ACTIVE",NEW);return outcome(r);}

    static Map<String,Object> reentrant(boolean rollback) {
        Long a=member(),b=member();
        new TransactionTemplate(tm).executeWithoutResult(s->{
            User other=users.findById(b).orElseThrow();other.changePassword(encoder.encode(NEW));
            User locked=users.findByIdForAccountUpdate(a).orElseThrow();
            locked.changePassword(encoder.encode(NEW));locked.changeStatus(UserStatus.SUSPENDED);
            User again=users.findByIdForAccountUpdate(a).orElseThrow();
            check(again==locked&&users.findById(b).orElseThrow()==other,"Managed identity or unrelated entity was detached");
            check(encoder.matches(NEW,again.getPassword())&&again.getStatus()==UserStatus.SUSPENDED,"Reentrant lock discarded pending account changes");
            check(encoder.matches(NEW,other.getPassword()),"Reload discarded another entity's pending change");
            if(rollback)s.setRollbackOnly();
        });
        stored(a,rollback?"ACTIVE":"SUSPENDED",rollback?OLD:NEW);stored(b,"ACTIVE",rollback?OLD:NEW);
        return Map.of("sameManagedInstances",true,"pendingPasswordAndStatusRetained",true,"otherEntityRetained",true,"rolledBack",rollback);
    }
    static Map<String,Object> currentReadSql() throws Exception {
        Long id=member();var ex=Executors.newSingleThreadExecutor();
        try {tx(()->{
            User before=users.findById(id).orElseThrow();check(before.getStatus()==UserStatus.ACTIVE,"Bad fixture");
            try{ex.submit(()->status(id,"SUSPENDED")).get(10,TimeUnit.SECONDS);}catch(Exception e){throw new RuntimeException(e);}
            int start=sqlTrace.size();User after=users.findByIdForAccountUpdate(id).orElseThrow();
            var selects=sqlTrace.subList(start,sqlTrace.size()).stream().filter(r->r.get("thread").equals(Thread.currentThread().getName())).map(r->(String)r.get("sql")).filter(sql->sql.startsWith("select")&&sql.contains(" from user ")).toList();
            check(before==after&&after.getStatus()==UserStatus.SUSPENDED,"Current reload must refresh the same managed entity");
            check(!selects.isEmpty()&&selects.getLast().contains("for update"),"Account reload ended with a non-locking snapshot read");
        });return Map.of("sameManagedInstance",true,"latestStatus","SUSPENDED","lastReloadSqlForUpdate",true);}
        finally{stop(ex);}
    }
    static Map<String,Object> firstReadSql() {
        Long id=member();tx(()->{
            int start=sqlTrace.size();users.findByIdForAccountUpdate(id).orElseThrow();
            var selects=sqlTrace.subList(start,sqlTrace.size()).stream().filter(r->r.get("thread").equals(Thread.currentThread().getName())).map(r->(String)r.get("sql")).filter(sql->sql.startsWith("select")&&sql.contains(" from user ")).toList();
            check(selects.size()==1&&selects.getFirst().contains("for update"),"Fresh account acquisition must not establish an earlier non-locking snapshot");
        });return Map.of("accountSelectCount",1,"firstSqlForUpdate",true);
    }
    static Map<String,Object> parallelUsers() throws Exception {
        List<Long> ids=new ArrayList<>();for(int i=0;i<8;i++)ids.add(member());var barrier=new CyclicBarrier(8);
        var outcomes=concurrent(8,i->tx(()->{
            Long id=ids.get(i);users.findByIdForAccountUpdate(id).orElseThrow();
            try{barrier.await(10,TimeUnit.SECONDS);}catch(Exception e){throw new RuntimeException(e);}
            userService.changePassword(id,new PasswordChangeRequest(OLD,NEW,NEW));
            userService.updateMyInfo(id,grade("4학년"));
        }));
        check(ok(outcomes)==8,"Different-user transactions did not overlap successfully");
        for(Long id:ids){stored(id,"ACTIVE",NEW);check(snapshot(id).get("grade").equals("4학년"),"Mixed update lost profile change");}
        return Map.of("simultaneouslyHeldDifferentUserLocks",8,"success",8,"passwordAndProfileBothCommitted",true);
    }
    static Map<String,Object> resetTwice()throws Exception{Long id=member();Token t=token(id);var r=concurrent(12,i->password(id,t,true));check(ok(r)==1&&count(r,ErrorCode.PASSWORD_RESET_TOKEN_ALREADY_USED)==11,"Reset token must have one winner");check(used(t),"Token not consumed");stored(id,"ACTIVE",NEW);return outcome(r);}
    static Map<String,Object> nicknameRace(boolean alias)throws Exception {
        Long a=member(),b=member();Map<String,Object> before=snapshot(b);String n=nick(),other=alias?n.toLowerCase(Locale.ROOT):n;
        var locked=new CountDownLatch(1);var release=new CountDownLatch(1);var started=new CountDownLatch(1);var ex=Executors.newFixedThreadPool(2);int errorStart=dbErrors.size();
        try{
            Future<Throwable> first=ex.submit(()->capture(()->tx(()->{userService.updateMyInfo(a,request(n,"4학년",null,null));locked.countDown();await(release);})));await(locked);
            Future<Throwable> second=ex.submit(()->{started.countDown();return capture(()->userService.updateMyInfo(b,request(other,"5학년",RUN+"B",RUN+"B")));});await(started);
            try{blocked(second);}finally{release.countDown();}
            check(first.get(20,TimeUnit.SECONDS)==null,"First nickname change failed");business(second.get(20,TimeUnit.SECONDS),ErrorCode.INVALID_REQUEST);
            check(before.equals(snapshot(b)),"Losing profile changes partially committed");check(snapshot(a).get("nickname").equals(n),"Winner nickname lost");
            check(dbErrors.subList(errorStart,dbErrors.size()).stream().anyMatch(e->e.get("vendorCode").equals(1062)),"Unique race did not reach physical 1062 constraint");
            return Map.of("success",1,"invalidRequest",1,"physicalMysqlCode",1062,"loserAllFieldsRolledBack",true,"caseAlias",alias);
        }finally{release.countDown();stop(ex);}
    }
    static Map<String,Object> outerRollback()throws Exception {
        Long id=member();Map<String,Object> before=snapshot(id);long completed=executor().getThreadPoolExecutor().getCompletedTaskCount();
        Throwable e=capture(()->tx(()->{userService.updateMyInfo(id,request(nick(),"5학년",RUN+"B",RUN+"B"));throw new IllegalStateException("synthetic rollback");}));
        check(e instanceof IllegalStateException,"Expected synthetic rollback");drainEvents();check(before.equals(snapshot(id)),"Outer rollback left profile mutation");
        check(completed==executor().getThreadPoolExecutor().getCompletedTaskCount(),"Rolled-back event dispatched matching work");return Map.of("allFieldsRestored",true,"matchingTasksDispatched",0);
    }
    static Map<String,Object> invalidReference(){Long id=member();var before=snapshot(id);business(capture(()->userService.updateMyInfo(id,request(nick(),"4학년",RUN+"missing",null))),ErrorCode.INVALID_REQUEST);check(before.equals(snapshot(id)),"Invalid reference partially changed profile");return Map.of("allFieldsRestored",true);}
    static Map<String,Object> academics(){
        Long id=member();Map<String,Object> before=snapshot(id);
        userService.updateMyInfo(id,request(null,null,null,null));check(before.equals(snapshot(id)),"NULL omitted fields must preserve values");
        business(capture(()->userService.updateMyInfo(id,request(nick(),"4학년",RUN+"B",null))),ErrorCode.INVALID_REQUEST);check(before.equals(snapshot(id)),"Omitted incompatible old major must roll back");
        userService.updateMyInfo(id,request(null,null,RUN+"B","세부전공 없음"));check(snapshot(id).get("major_id")==null&&((Number)snapshot(id).get("faculty_id")).longValue()==f2,"Explicit major clear failed");
        userService.updateMyInfo(id,request(null,null,null,RUN+"B"));check(((Number)snapshot(id).get("major_id")).longValue()==m2,"Major set failed");
        var current=snapshot(id);business(capture(()->userService.updateMyInfo(id,request(null,null,null,RUN+"A"))),ErrorCode.INVALID_REQUEST);check(current.equals(snapshot(id)),"Cross-faculty major accepted");
        business(capture(()->userService.updateMyInfo(id,request(null,null," ",null))),ErrorCode.VALIDATION_ERROR);
        return Map.of("nullPreserves",true,"explicitClearIsSqlNull",true,"facultyMajorMembershipChecked",true,"invalidChangesRolledBack",true);
    }
    static Map<String,Object> partialUpdates()throws Exception {
        Long id=member();var ex=Executors.newFixedThreadPool(2);var locked=new CountDownLatch(1);var release=new CountDownLatch(1);
        try{var first=ex.submit(()->tx(()->{userService.updateMyInfo(id,grade("4학년"));locked.countDown();await(release);}));await(locked);
            var second=ex.submit(()->userService.updateMyInfo(id,new UserUpdateRequest(null,null,null,null,null,"E",null,null,null)));
            try{blocked(second);}finally{release.countDown();}first.get(20,TimeUnit.SECONDS);second.get(20,TimeUnit.SECONDS);
            check(snapshot(id).get("grade").equals("4학년")&&snapshot(id).get("mbti").equals("ENTJ"),"Lost partial profile update");return Map.of("finalGrade","4학년","finalMbti","ENTJ");
        }finally{release.countDown();stop(ex);}
    }
    static Map<String,Object> inactiveProfile()throws Exception {
        Long id=member();var before=snapshot(id);var locked=new CountDownLatch(1);var release=new CountDownLatch(1);var ex=Executors.newFixedThreadPool(2);
        try{var first=ex.submit(()->tx(()->{status(id,"SUSPENDED");locked.countDown();await(release);}));await(locked);var second=ex.submit(()->capture(()->userService.updateMyInfo(id,grade("4학년"))));try{blocked(second);}finally{release.countDown();}first.get(20,TimeUnit.SECONDS);business(second.get(20,TimeUnit.SECONDS),ErrorCode.UNAUTHORIZED);check(before.equals(snapshot(id)),"Inactive profile changed");return Map.of("inactiveRejected",true,"profileUnchanged",true);}finally{release.countDown();stop(ex);}
    }

    record Pair(Long a,Long b){}
    static Pair matchingFixture(){
        Long a=member(),b=member();tx(()->{for(Long id:List.of(a,b)){
            User u=users.findById(id).orElseThrow();
            ctx.getBean(ProfileSurveyRepository.class).saveAndFlush(new ProfileSurvey(u,"22시 이전","07시 이전","없음","없음","없음","보통","가능","비흡연","소등","가능","이어폰","아침","10분","매일","같이 청소","따로","적당히 대화","보통","안함","안함","각자 사용",true));
            var p=ctx.getBean(RoommatePreferenceRepository.class).saveAndFlush(new RoommatePreference(u,"상관없음","상관없음","상관없음","상관없음","상관없음","상관없음","상관없음","상관없음","상관없음","상관없음","상관없음","상관없음","상관없음",true));
            for(var c:PreferenceCriterionCode.values()){
                ctx.getBean(PreferenceCriterionRepository.class).save(new PreferenceCriterion(p,c,1));
                if(c.isMultiSelect())ctx.getBean(RoommatePreferenceOptionRepository.class).save(new RoommatePreferenceOption(p,c,c==PreferenceCriterionCode.GRADE?"3학년":"상관없음"));
            }
        }});refresh.refreshTargets(a,List.of(b));refresh.refreshTargets(b,List.of(a));check(score(a,b)==100&&score(b,a)==100,"Matching fixture must score 100");return new Pair(a,b);
    }
    static int score(Long a,Long b){return jdbc.queryForObject("SELECT match_score FROM roommate_match WHERE requester_user_id=? AND target_user_id=?",Integer.class,a,b);}
    static Map<String,Object> matchingRace(boolean profileFirst)throws Exception {
        Pair p=matchingFixture();var locked=new CountDownLatch(1);var release=new CountDownLatch(1);var ex=Executors.newFixedThreadPool(2);
        try{
            Future<?> first=ex.submit(()->{if(profileFirst)tx(()->{userService.updateMyInfo(p.b,grade("4학년"));locked.countDown();await(release);});else{refreshPause.set(new RefreshPause(locked,release,new AtomicBoolean()));try{refresh.refreshTargets(p.a,List.of(p.b));}finally{refreshPause.remove();}}});await(locked);
            Future<?> second=ex.submit(()->{if(profileFirst)refresh.refreshTargets(p.a,List.of(p.b));else userService.updateMyInfo(p.b,grade("4학년"));});
            try{blocked(second);}finally{release.countDown();}first.get(20,TimeUnit.SECONDS);second.get(20,TimeUnit.SECONDS);drainEvents();
            check(score(p.a,p.b)==0,"Matching retained old target grade after commit");check(score(p.b,p.a)==100,"Unrelated outgoing score was changed");return Map.of("waitingObserved",true,"finalIncomingScore",0,"outgoingScore",100,"afterCommitListenerRan",true);
        }finally{release.countDown();stop(ex);}
    }
    static Map<String,Object> matchingSorted()throws Exception{Pair p=matchingFixture();int start=dbErrors.size();var r=concurrent(16,i->{if(i%2==0)refresh.refreshTargets(p.a,List.of(p.b));else refresh.refreshTargets(p.b,List.of(p.a));});check(ok(r)==16,"Opposite refresh failed");check(dbErrors.subList(start,dbErrors.size()).stream().noneMatch(e->e.get("vendorCode").equals(1213)),"Sorted production path deadlocked");check(score(p.a,p.b)==100&&score(p.b,p.a)==100,"Score changed under duplicate refresh");return outcome(r);}
    static Map<String,Object> matchingRollback()throws Exception{Pair p=matchingFixture();long tasks=executor().getThreadPoolExecutor().getCompletedTaskCount();Throwable e=capture(()->tx(()->{userService.updateMyInfo(p.b,grade("4학년"));throw new IllegalStateException("synthetic rollback");}));check(e instanceof IllegalStateException,"Expected rollback");drainEvents();check(score(p.a,p.b)==100&&snapshot(p.b).get("grade").equals("3학년"),"Rollback left mismatched matching score/input");check(tasks==executor().getThreadPoolExecutor().getCompletedTaskCount(),"Rollback dispatched event");return Map.of("score",100,"grade","3학년","matchingTasksDispatched",0);}
    static org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor executor(){return ctx.getBean("matchingRefreshExecutor",org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor.class);}
    static void drainEvents()throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);while(executor().getActiveCount()!=0||!executor().getThreadPoolExecutor().getQueue().isEmpty()){check(System.nanoTime()<end,"Matching executor did not drain");Thread.sleep(20);}idle(pool);}

    static Map<String,Object> lockWait()throws Exception {
        Long a=member(),b=member();var ex=Executors.newSingleThreadExecutor();try(Connection c=open()){guard(c);c.setAutoCommit(false);try(var ps=c.prepareStatement("SELECT user_id FROM user WHERE user_id=? FOR UPDATE")){ps.setLong(1,a);ps.executeQuery().close();}
            long start=System.nanoTime();var waiting=ex.submit(()->userService.updateMyInfo(a,grade("4학년")));try{blocked(waiting);userService.updateMyInfo(b,grade("5학년"));}finally{c.rollback();}waiting.get(10,TimeUnit.SECONDS);check(snapshot(b).get("grade").equals("5학년"),"Unrelated user was blocked");return Map.of("waitMs",(System.nanoTime()-start)/1_000_000,"unrelatedUserSucceeded",true);
        }finally{stop(ex);}
    }
    static Map<String,Object> lockTimeout(boolean reset)throws Exception {
        Long id=member();Token t=token(id);var before=snapshot(id);
        try(Connection c=open()){guard(c);c.setAutoCommit(false);String sql=reset?"SELECT password_reset_token_id FROM password_reset_token WHERE password_reset_token_id=? FOR UPDATE":"SELECT user_profile_id FROM user_profile WHERE user_id=? FOR UPDATE";
            try(var ps=c.prepareStatement(sql)){ps.setLong(1,reset?t.id:id);ps.executeQuery().close();}
            long start=System.nanoTime();Throwable e=capture(()->{if(reset)password(id,t,true);else userService.updateMyInfo(id,request(nick(),"5학년",RUN+"B",RUN+"B"));});long ms=(System.nanoTime()-start)/1_000_000;
            check(sqlCode(e)==1205,"Expected actual MySQL 1205, actual="+describe(e));check(e instanceof org.springframework.dao.ConcurrencyFailureException||e instanceof PessimisticLockException||e instanceof LockTimeoutException,"Error not caught by concurrency handler");
            var response=new GlobalExceptionHandler().handleConcurrentUpdate((Exception)e);check(response.getStatusCode().value()==409&&response.getBody().errorCode().equals("ACCOUNT_UPDATE_CONFLICT"),"Wrong concurrency API mapping");
            c.rollback();stored(id,"ACTIVE",OLD);check(!used(t)&&before.equals(snapshot(id)),"Timeout partially committed token/password/profile");
            if(reset){password(id,t,true);check(used(t),"Reset recovery failed");}else userService.updateMyInfo(id,grade("4학년"));
            return Map.of("mysqlCode",1205,"elapsedMs",ms,"sessionLockWaitSeconds",2,"jpaHintMillis",5000,"handlerStatus",409,"handlerCode","ACCOUNT_UPDATE_CONFLICT","fullRollback",true,"recovered",true);
        }
    }
    static Map<String,Object> deadlock()throws Exception {
        Long a=member(),b=member();var barrier=new CyclicBarrier(2);var ex=Executors.newFixedThreadPool(2);
        try{List<Future<Throwable>> futures=new ArrayList<>();for(int i=0;i<2;i++){Long first=i==0?a:b,second=i==0?b:a;futures.add(ex.submit(()->capture(()->tx(()->{
            users.findByIdForAccountUpdate(first).orElseThrow();userService.updateMyInfo(first,grade("4학년"));try{barrier.await(10,TimeUnit.SECONDS);}catch(Exception e){throw new RuntimeException(e);}users.findByIdForAccountUpdate(second).orElseThrow();
        }))));}
            List<Throwable> r=new ArrayList<>();for(var f:futures)r.add(f.get(20,TimeUnit.SECONDS));check(ok(r)==1&&r.stream().filter(e->sqlCode(e)==1213).count()==1,"Forced cycle must select one 1213 victim");
            Throwable victim=r.stream().filter(Objects::nonNull).findFirst().orElseThrow();check(victim instanceof org.springframework.dao.ConcurrencyFailureException,"Deadlock exception must route to 409");check(new GlobalExceptionHandler().handleConcurrentUpdate((Exception)victim).getStatusCode().value()==409,"Deadlock HTTP mapping wrong");
            for(int i=0;i<2;i++)check(snapshot(i==0?a:b).get("grade").equals(r.get(i)==null?"4학년":"3학년"),"Deadlock victim partially committed");return Map.of("winner",1,"victim",1,"mysqlCode",1213,"handlerStatus",409,"victimFullRollback",true,"forcedInHarness",true);
        }finally{stop(ex);}
    }
    static Map<String,Object> poolExhaustion()throws Exception {
        Long id=member();Map<String,Object> before=snapshot(id);List<Connection> held=new ArrayList<>();var ex=Executors.newFixedThreadPool(6);
        try(HikariDataSource small=newPool(4,500,2)) {
            var d=new Guarded(small);var f=emf(d,"validate");f.afterPropertiesSet();try{var manager=new JpaTransactionManager(f.getObject());manager.setDataSource(d);
                for(int i=0;i<4;i++)held.add(d.getConnection());var futures=new ArrayList<Future<Throwable>>();for(int i=0;i<6;i++)futures.add(ex.submit(()->capture(()->new TransactionTemplate(manager).executeWithoutResult(s->{}))));
                int failures=0;for(var future:futures){Throwable e=future.get(10,TimeUnit.SECONDS);check(e instanceof CannotCreateTransactionException,"Exhaustion must reject transaction start");failures++;}
                for(var c:held)c.close();held.clear();new TransactionTemplate(manager).executeWithoutResult(s->check(new JdbcTemplate(d).queryForObject("SELECT 1",Integer.class)==1,"Pool did not recover"));idle(small);check(before.equals(snapshot(id)),"Failed transaction mutated profile");return Map.of("heldConnections",4,"transactionStartFailures",failures,"borrowTimeoutMs",500,"activeAfterRecovery",small.getHikariPoolMXBean().getActiveConnections(),"waitingAfterRecovery",small.getHikariPoolMXBean().getThreadsAwaitingConnection());
            }finally{for(var c:held)c.close();held.clear();f.destroy();}
        }finally{stop(ex);}
    }
    static Map<String,Object> stress()throws Exception {
        List<Long> ids=new ArrayList<>();for(int i=0;i<24;i++)ids.add(member());int success=0;
        for(int round=0;round<3;round++){String value=(round%2==0)?"4학년":"5학년";var r=concurrent(24,i->{Long id=ids.get(i);userService.updateMyInfo(id,grade(value));business(capture(()->userService.changePassword(id,new PasswordChangeRequest(NEW,OLD,OLD))),ErrorCode.INVALID_REQUEST);});check(ok(r)==24,"Mixed service stress failed");success+=ok(r);drainEvents();idle(pool);}
        for(Long id:ids)check(snapshot(id).get("grade").equals("4학년"),"Stress lost committed update");return Map.of("successfulProfileUpdates",success,"expectedPasswordRejections",72,"rounds",3,"active",pool.getHikariPoolMXBean().getActiveConnections(),"waiting",pool.getHikariPoolMXBean().getThreadsAwaitingConnection());
    }
    static List<Throwable> concurrent(int n,IntConsumer action)throws Exception {var ex=Executors.newFixedThreadPool(n);var ready=new CountDownLatch(n);var start=new CountDownLatch(1);try{List<Future<Throwable>> fs=new ArrayList<>();for(int i=0;i<n;i++){int index=i;fs.add(ex.submit(()->{ready.countDown();await(start);return capture(()->action.accept(index));}));}await(ready);start.countDown();List<Throwable> r=new ArrayList<>();for(var f:fs)r.add(f.get(30,TimeUnit.SECONDS));return r;}finally{start.countDown();stop(ex);}}
    static long ok(List<Throwable> r){return r.stream().filter(Objects::isNull).count();}
    static long count(List<Throwable> r,ErrorCode c){return r.stream().filter(e->e instanceof CustomException x&&x.getErrorCode()==c).count();}
    static Map<String,Object> outcome(List<Throwable> r){var errors=new TreeMap<String,Integer>();for(var e:r)if(e!=null)errors.merge(describe(e),1,Integer::sum);return Map.of("requests",r.size(),"success",ok(r),"errors",errors);}
    static void cleanup(){
        for(Long id:ownedUsers){
            for(String t:List.of("roommate_match"))jdbc.update("DELETE FROM "+t+" WHERE requester_user_id=? OR target_user_id=?",id,id);
            for(String t:List.of("preference_criterion","roommate_preference_option"))jdbc.update("DELETE c FROM "+t+" c JOIN roommate_preference p ON p.preference_id=c.preference_id WHERE p.user_id=?",id);
            for(String t:List.of("roommate_preference","profile_survey","password_reset_token","user_profile"))jdbc.update("DELETE FROM "+t+" WHERE user_id=?",id);
            jdbc.update("DELETE FROM user WHERE user_id=? AND school_email LIKE ?",id,RUN+"%@hknu.ac.kr");
        }
        for(Long id:List.of(m1,m2))jdbc.update("DELETE FROM major WHERE major_id=? AND major_name LIKE ?",id,RUN+"%");
        for(Long id:List.of(f1,f2))jdbc.update("DELETE FROM faculty WHERE faculty_id=? AND faculty_name LIKE ?",id,RUN+"%");
        jdbc.update("DELETE FROM dormitory WHERE dormitory_id=? AND dormitory_code=?",dormId,RUN);
    }
    interface Work{Map<String,Object> run()throws Exception;}
    static void test(String name,Work work)throws Exception {check(!unsafe.get(),"Unsafe target");currentCase=name;observations.clear();long start=System.nanoTime();Map<String,Object> detail=new LinkedHashMap<>();String status="PASS";try{drainEvents();detail.putAll(work.run());drainEvents();}catch(Throwable e){status="FAIL";detail.put("error",describe(e));if(e instanceof AssertionError)detail.put("assertion",e.getMessage());}detail.putAll(observations);try{idle(pool);}catch(Throwable e){status="FAIL";detail.put("resourceError",describe(e));}detail.put("elapsedMs",(System.nanoTime()-start)/1_000_000);results.add(row(name,status,detail));write("cases.json",results);System.out.println(name+" "+status+" "+JSON.writeValueAsString(detail));}
    static Map<String,Object> row(String n,String s,Map<String,Object> d){return Map.of("name",n,"status",s,"detail",d);}
    static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
    static int sqlCode(Throwable e){for(Throwable t=e;t!=null;t=t.getCause())if(t instanceof SQLException s&&s.getErrorCode()!=0)return s.getErrorCode();return 0;}
    static String describe(Throwable e){if(e==null)return "SUCCESS";if(e instanceof CustomException c)return c.getErrorCode().name();StringBuilder b=new StringBuilder();for(Throwable t=e;t!=null;t=t.getCause()){if(!b.isEmpty())b.append(" -> ");b.append(t.getClass().getSimpleName());if(t instanceof SQLException s)b.append("[code=").append(s.getErrorCode()).append(",state=").append(s.getSQLState()).append("]");}for(StackTraceElement s:e.getStackTrace())if(s.getClassName().equals("UserMysqlConcurrency")){b.append(" at ").append(s.getMethodName()).append(':').append(s.getLineNumber());break;}return b.toString();}
    static void idle(HikariDataSource p)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(12);while(p.getHikariPoolMXBean().getActiveConnections()!=0||p.getHikariPoolMXBean().getThreadsAwaitingConnection()!=0){check(System.nanoTime()<end,"Connection pool did not return resources");Thread.sleep(20);}}
    static long number(Connection c,String sql)throws SQLException{try(var s=c.createStatement();var r=s.executeQuery(sql)){r.next();return r.getLong(1);}}
    static List<String> jdbcStrings(Connection c,String sql)throws SQLException{try(var s=c.createStatement();var r=s.executeQuery(sql)){List<String> values=new ArrayList<>();while(r.next())values.add(r.getString(1));return values;}}
    static Map<String,Object> queryRow(Connection c,String sql)throws SQLException{try(var s=c.createStatement();var r=s.executeQuery(sql)){r.next();var v=new LinkedHashMap<String,Object>();for(int i=1;i<=r.getMetaData().getColumnCount();i++)v.put(r.getMetaData().getColumnLabel(i),r.getString(i));return v;}}
    static Map<String,String> ddl(Connection c)throws SQLException{var map=new TreeMap<String,String>();for(String t:tables)try(var s=c.createStatement();var r=s.executeQuery("SHOW CREATE TABLE `"+t+"`")){r.next();map.put(t,r.getString(2));}return map;}
    static Map<String,String> normalize(Map<String,String> d){var m=new TreeMap<String,String>();d.forEach((k,v)->m.put(k,v.replaceAll(" AUTO_INCREMENT=\\d+","")));return m;}
    static String sha(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    static Map<String,String> protectedFiles(Path root)throws Exception{var map=new TreeMap<String,String>();for(String part:List.of("backend/src/main","backend/tools/auth-mysql-concurrency","backend/tools/auth-mysql-multi-instance","backend/migrations")){Path p=root.resolve(part);try(var walk=Files.walk(p)){for(Path f:walk.filter(Files::isRegularFile).toList())map.put(root.relativize(f).toString().replace('\\','/'),HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(f))));}}return map;}
    static void write(String name,Object value){try{Files.writeString(out.resolve(name),JSON.writerWithDefaultPrettyPrinter().writeValueAsString(value),StandardCharsets.UTF_8);}catch(Exception e){throw new IllegalStateException("Evidence write failed",e);}}
}
