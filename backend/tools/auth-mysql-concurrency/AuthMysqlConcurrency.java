import com.kkirikkiri.auth.dto.*;
import com.kkirikkiri.auth.entity.*;
import com.kkirikkiri.auth.repository.*;
import com.kkirikkiri.auth.service.*;
import com.kkirikkiri.global.exception.*;
import com.kkirikkiri.global.security.JwtTokenProvider;
import com.kkirikkiri.user.entity.User;
import com.kkirikkiri.user.repository.*;
import com.zaxxer.hikari.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.persistence.EntityManagerFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import javax.sql.DataSource;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/** Opt-in real MySQL tests; no Spring Boot server, H2, SMTP transport or production changes. */
public class AuthMysqlConcurrency {
    static final String URL="jdbc:mysql://127.0.0.1:3307/kkiri_auth_test?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&autoReconnect=false&allowMultiQueries=false&allowLoadLocalInfile=false&connectTimeout=5000&socketTimeout=15000";
    static final String SERVER_UUID="53ab727e-c847-11f1-9aaf-bad504fada53";
    static final List<String> TABLES=List.of("auth_email_identity","auth_limit_bucket","dormitory","email_verification","faculty","major","password_reset_token","user","user_profile");
    static final Set<String> keys=ConcurrentHashMap.newKeySet(), emails=ConcurrentHashMap.newKeySet();
    static final List<SimpleMailMessage> messages=new CopyOnWriteArrayList<>();
    static final AtomicBoolean failMail=new AtomicBoolean(), unsafe=new AtomicBoolean();
    static final AtomicInteger peakActive=new AtomicInteger(), peakWaiting=new AtomicInteger();
    static final String RUN="am"+UUID.randomUUID().toString().replace("-", "").substring(0,12);
    static HikariDataSource pool;
    static DataSource guarded;
    static TrackingJdbc jdbc;
    static AnnotationConfigApplicationContext context;
    static PlatformTransactionManager tm;
    static AuthLimitStore store;
    static AuthProtectionService protection;
    static AuthService auth;
    static EmailVerificationService verification;
    static PasswordResetService resets;
    static PasswordEncoder encoder;
    static List<Map<String,Object>> results=new ArrayList<>();
    static Path out;
    static int sequence;
    static String password(){ return UUID.randomUUID()+"!aA1"; }

    public static void main(String[] args) throws Exception {
        // No application log may contain SQL parameters, credentials, recipient/code/token or exception text.
        ((ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).setLevel(ch.qos.logback.classic.Level.OFF);
        if((args.length!=1 && args.length!=2) || System.getenv("KKIRI_AUTH_TEST_DB_PASSWORD")==null) throw new IllegalArgumentException("Repository path and password environment are required");
        Path root=Path.of(args[0]).toRealPath();
        out=root.resolve("backend/tools/auth-mysql-concurrency/results/"+LocalDate.now(ZoneId.of("Asia/Seoul"))+"-"+RUN);
        Files.createDirectories(out);
        boolean claimed=false, cleaned=false;
        Map<String,String> ddlBefore=new TreeMap<>();
        ScheduledExecutorService monitor=Executors.newSingleThreadScheduledExecutor();
        try(Connection claim=open()) {
            guard(claim);
            grants(claim);
            check(scalar(claim,"SELECT GET_LOCK('kkiri_auth_test.auth_concurrency_validation',0)")==1,"Another validation is active");
            check(scalar(claim,"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()")==9,"Expected exactly 9 tables");
            for(String t:TABLES) check(scalar(claim,"SELECT COUNT(*) FROM `"+t+"`")==0,"Expected empty isolated database");
            for(String t:TABLES) ddlBefore.put(t,text(claim,"SHOW CREATE TABLE `"+t+"`",2));
            claimed=true;
            Files.writeString(out.resolve("environment.json"),json(Map.of("endpoint","127.0.0.1:3307/kkiri_auth_test","account","kkiri_test","version","8.0.46","uuid",SERVER_UUID,"initialTables",9,"initialRows",0,"run",RUN,"poolMaximum",16,"sessionLockWaitSeconds",2,"mode","one JVM, multiple threads, real Spring services and transactions")));
            pool=newPool(16,10000,2);
            guarded=new Guarded(pool);
            context=new AnnotationConfigApplicationContext(Config.class);
            tm=context.getBean(PlatformTransactionManager.class);
            jdbc=context.getBean(TrackingJdbc.class); store=context.getBean(AuthLimitStore.class);
            protection=context.getBean(AuthProtectionService.class); auth=context.getBean(AuthService.class);
            verification=context.getBean(EmailVerificationService.class); resets=context.getBean(PasswordResetService.class);
            encoder=context.getBean(PasswordEncoder.class);
            monitor.scheduleAtFixedRate(()->{var m=pool.getHikariPoolMXBean(); if(m!=null){peakActive.accumulateAndGet(m.getActiveConnections(),Math::max);peakWaiting.accumulateAndGet(m.getThreadsAwaitingConnection(),Math::max);}},0,10,TimeUnit.MILLISECONDS);
            if(args.length==2 && args[1].equals("FormerFailuresOnly")) {
                test("collation-alias-account-login-limit",()->collationLogin());
                test("collation-alias-reset-cooldown",()->collationReset());
                test("unrelated-verification-row-lock-isolation",()->unrelatedLock());
            } else if(args.length==2 && args[1].equals("TokenLockOnly")) {
                test("unrelated-token-consumption-lock-isolation",()->consumeUnrelated());
            } else {
            test("valid-code-concurrent-service",()->validCode());
            test("invalid-code-concurrent-service",()->invalidCode());
            test("login-failure-concurrent-service",()->loginFailures());
            test("reset-request-and-token-use-service",()->resetRequests());
            test("first-bucket-insert-race",()->firstBucket());
            test("sorted-multiple-row-locks",()->sortedLocks());
            test("email-case-request-quota",()->emailQuota());
            test("shared-ip-failure-and-denied-row-rollback",()->ipFailures());
            test("shared-ip-request-quota",()->ipRequests());
            test("reset-shared-ip-quota",()->resetIp());
            test("untrusted-forwarded-ip-and-ipv6",()->proxyIp());
            test("business-rollback-independent-failure",()->independentRollback());
            test("smtp-mock-failure-rollback",()->smtpRollback());
            test("row-lock-wait-release",()->lockWait());
            test("row-lock-timeout-rollback-and-http-mapping",()->lockTimeout());
            test("forced-deadlock-victim-rollback-and-http-mapping",()->deadlock());
            test("callback-exception-atomic-rollback",()->callbackRollback());
            test("pool-exhaustion-timeout-recovery",()->poolExhaustion());
            test("mixed-transaction-stress-no-pool-leak",()->stress());
            test("collation-alias-account-login-limit",()->collationLogin());
            test("collation-alias-reset-cooldown",()->collationReset());
            test("unrelated-verification-row-lock-isolation",()->unrelatedLock());
            test("preregistration-send-alias-cooldown",()->preregistrationSend());
            test("preregistration-verify-unicode-alias-limit",()->preregistrationVerify());
            test("different-account-quota-independence",()->accountIndependence());
            test("unknown-account-alias-privacy-and-cooldown",()->unknownAliases());
            test("alias-window-recovery",()->aliasRecovery());
            test("same-verification-token-consumption",()->consumeOnce());
            test("unrelated-token-consumption-lock-isolation",()->consumeUnrelated());
            test("different-email-simultaneous-verification",()->differentEmailsVerify());
            }
        } catch(Throwable e) {
            results.add(row("harness", "INCOMPLETE", Map.of("error",describe(e))));
        } finally {
            monitor.shutdownNow();
            if(claimed && !unsafe.get()) try {
                cleanup();
                try(Connection c=open()) {
                    guard(c);
                    for(String t:TABLES){require(scalar(c,"SELECT COUNT(*) FROM `"+t+"`")==0,"Residual rows after scoped cleanup",Map.of("table",t));require(Objects.equals(normalizeDdl(ddlBefore.get(t)),normalizeDdl(text(c,"SHOW CREATE TABLE `"+t+"`",2))),"Schema changed",Map.of("table",t));}
                    cleaned=true;
                }
            } catch(Throwable e){results.add(row("cleanup","INCOMPLETE",Map.of("error",describe(e))));}
            if(context!=null)context.close();
            if(pool!=null)pool.close();
            var summary=new LinkedHashMap<String,Object>();
            summary.put("run",RUN);summary.put("results",results);summary.put("cleanupAllTablesEmptyAndDdlUnchanged",cleaned);
            summary.put("peakActive",peakActive.get());summary.put("peakWaiting",peakWaiting.get());
            summary.put("poolClosed",pool!=null && pool.isClosed());summary.put("realSmtpConnections",0);
            String verdict=results.stream().anyMatch(x->x.get("status").equals("INCOMPLETE"))?"INSUFFICIENT":results.stream().anyMatch(x->x.get("status").equals("FAIL"))?"FAIL":"PASS";
            summary.put("verdict",verdict);
            Files.writeString(out.resolve("summary.json"),json(summary),StandardCharsets.UTF_8);
            System.out.println("RESULT "+verdict+" evidence="+out);
            if(!verdict.equals("PASS"))System.exit(1);
        }
    }

    @Configuration
    @EnableTransactionManagement(proxyTargetClass=true)
    @EnableJpaRepositories(basePackages={"com.kkirikkiri.auth.repository","com.kkirikkiri.user.repository"},
        includeFilters=@ComponentScan.Filter(type=FilterType.ASSIGNABLE_TYPE,classes={EmailVerificationRepository.class,PasswordResetTokenRepository.class,UserRepository.class,UserProfileRepository.class}),
        excludeFilters=@ComponentScan.Filter(type=FilterType.REGEX,pattern={".*AdminMemberQueryRepository",".*DormitoryRepository",".*FacultyRepository",".*MajorRepository"}))
    static class Config {
        @Bean DataSource dataSource(){return guarded;}
        @Bean TrackingJdbc jdbcTemplate(DataSource d){return new TrackingJdbc(d);}
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource d){
            var f=new LocalContainerEntityManagerFactoryBean(); f.setDataSource(d);
            f.setPackagesToScan("com.kkirikkiri.auth.entity","com.kkirikkiri.user.entity");
            f.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            f.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","validate","hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl","hibernate.show_sql","false","hibernate.session_factory.statement_inspector",new VerificationSqlInspector()));return f;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory f,DataSource d){var t=new JpaTransactionManager(f);t.setDataSource(d);return t;}
        @Bean AuthLimitStore store(TrackingJdbc j,PlatformTransactionManager t){return new AuthLimitStore(j,t);}
        @Bean AuthProtectionProperties properties(){return new AuthProtectionProperties();}
        @Bean AuthProtectionService protection(AuthLimitStore s,AuthProtectionProperties p){return new AuthProtectionService(s,p);}
        @Bean AuthTransactions transactions(PlatformTransactionManager t){return new AuthTransactions(t);}
        @Bean PasswordEncoder encoder(){return new BCryptPasswordEncoder();}
        @Bean(destroyMethod="close") ValidatorFactory validatorFactory(){return Validation.buildDefaultValidatorFactory();}
        @Bean Validator validator(ValidatorFactory f){return f.getValidator();}
        @Bean JavaMailSender smtp(){return (JavaMailSender)Proxy.newProxyInstance(JavaMailSender.class.getClassLoader(),new Class<?>[]{JavaMailSender.class},(p,m,a)->{
            if(m.getName().equals("send") && a.length==1 && a[0] instanceof SimpleMailMessage mail){if(failMail.get())throw new MailSendException("mock transport failure");messages.add(new SimpleMailMessage(mail));return null;}
            if(m.getName().equals("toString"))return "NoNetworkSmtpMock";
            if(m.getName().equals("hashCode"))return System.identityHashCode(p);
            if(m.getName().equals("equals"))return p==a[0];
            throw new UnsupportedOperationException("Only mock SimpleMailMessage send is supported");});}
        @Bean AuthMailTelemetry telemetry(){return new AuthMailTelemetry(new SimpleMeterRegistry());}
        @Bean EmailVerificationMailSender codeMail(JavaMailSender s,AuthMailTelemetry t){return new EmailVerificationMailSender(s,t,"auth-test@example.invalid");}
        @Bean PasswordResetMailSender resetMail(JavaMailSender s,AuthMailTelemetry t){return new PasswordResetMailSender(s,t,"auth-test@example.invalid");}
        @Bean EmailVerificationService verification(EmailVerificationRepository r,PasswordEncoder p,Validator v,EmailVerificationMailSender m,UserRepository u,AuthProtectionService a,AuthTransactions t){return new EmailVerificationService(r,p,v,m,u,a,t,300,300,60);}
        @Bean PasswordResetService resets(UserRepository u,PasswordResetTokenRepository r,PasswordResetMailSender m,PasswordEncoder p,Validator v,AuthProtectionService a,AuthTransactions t){return new PasswordResetService(u,r,m,p,v,a,t,300);}
        @Bean JwtTokenProvider jwt(){byte[] b=new byte[32];new SecureRandom().nextBytes(b);return new JwtTokenProvider(Base64.getEncoder().encodeToString(b),3600);}
        @Bean AuthService auth(UserRepository u,UserProfileRepository p,PasswordEncoder e,JwtTokenProvider j,Validator v,AuthProtectionService a,AuthTransactions t){return new AuthService(u,p,e,j,v,a,t);}
    }

    static Connection open() throws SQLException {var p=new Properties();p.setProperty("user","kkiri_test");p.setProperty("password",System.getenv("KKIRI_AUTH_TEST_DB_PASSWORD"));return DriverManager.getConnection(URL,p);}
    static void guard(Connection c) throws SQLException {
        try(var s=c.createStatement();var r=s.executeQuery("SELECT DATABASE(),CURRENT_USER(),VERSION(),@@server_uuid,@@hostname,@@port,@@session.time_zone,@@global.read_only,@@global.super_read_only")){
            if(!r.next() || !"kkiri_auth_test".equals(r.getString(1)) || !r.getString(2).startsWith("kkiri_test@") || !"8.0.46".equals(r.getString(3)) || !SERVER_UUID.equals(r.getString(4)) || !"e15a46faca0b".equals(r.getString(5)) || r.getInt(6)!=3306 || !Set.of("+00:00","UTC").contains(r.getString(7)) || r.getInt(8)!=0 || r.getInt(9)!=0){unsafe.set(true);throw new SQLException("Unsafe target identity");}
        }
    }
    static void grants(Connection c) throws SQLException {
        try(var s=c.createStatement();var r=s.executeQuery("SHOW GRANTS FOR CURRENT_USER")){while(r.next()){
            String g=r.getString(1);check((g.startsWith("GRANT USAGE ON *.* TO ") || g.contains(" ON `kkiri\\_auth\\_test`.* TO ")) && !g.contains("WITH GRANT OPTION"),"Non-test account privileges");}}
    }
    static HikariDataSource newPool(int max,long timeout,int lockSeconds){var p=new HikariConfig();p.setJdbcUrl(URL);p.setUsername("kkiri_test");p.setPassword(System.getenv("KKIRI_AUTH_TEST_DB_PASSWORD"));p.setMaximumPoolSize(max);p.setMinimumIdle(1);p.setConnectionTimeout(timeout);p.setValidationTimeout(500);p.setInitializationFailTimeout(5000);p.setConnectionInitSql("SET SESSION innodb_lock_wait_timeout="+lockSeconds);p.setPoolName("AuthMysqlValidation-"+max+"-"+System.nanoTime());return new HikariDataSource(p);}
    static class Guarded extends AbstractDataSource {
        final DataSource delegate;Guarded(DataSource d){delegate=d;}
        public Connection getConnection() throws SQLException{Connection c=delegate.getConnection();try{guard(c);return c;}catch(Throwable e){c.close();throw e;}}
        public Connection getConnection(String u,String p) throws SQLException{throw new SQLException("Arbitrary credentials are forbidden");}
    }
    static class TrackingJdbc extends JdbcTemplate {
        TrackingJdbc(DataSource d){super(d);}
        @Override public int update(String sql,Object... args){if(sql.stripLeading().startsWith("INSERT INTO auth_limit_bucket"))for(Object arg:args)if(arg instanceof String key)keys.add(key);return super.update(sql,args);}
    }
    interface Work { Map<String,Object> run() throws Exception; }
    static void test(String name,Work work) throws Exception {
        if(unsafe.get())throw new IllegalStateException("Unsafe target; no further tests");
        long start=System.nanoTime();Map<String,Object> detail=new LinkedHashMap<>();String status="PASS";
        try{detail.putAll(work.run());}catch(CheckFailure e){status="FAIL";detail.putAll(e.detail);detail.put("assertion",e.getMessage());}catch(Throwable e){status="FAIL";detail.put("error",describe(e));}
        try{awaitIdle(pool);}catch(Throwable e){status="FAIL";detail.put("poolIdleCheck",describe(e));}
        detail.put("elapsedMs",(System.nanoTime()-start)/1_000_000);results.add(row(name,status,detail));
        Files.writeString(out.resolve("cases.json"),json(results));System.out.println(name+" "+status+" "+json(detail));
    }
    static Map<String,Object> row(String name,String status,Map<String,Object> d){var r=new LinkedHashMap<String,Object>();r.put("name",name);r.put("status",status);r.put("detail",d);return r;}
    static class CheckFailure extends AssertionError{final Map<String,Object> detail;CheckFailure(String m,Map<String,Object>d){super(m);detail=d;}}
    static void check(boolean b,String m){if(!b)throw new CheckFailure(m,Map.of());}
    static void require(boolean b,String m,Map<String,Object>d){if(!b)throw new CheckFailure(m,d);}
    static String email(){String e=RUN+(sequence++)+"@hknu.ac.kr";emails.add(e);return e;}
    static String ip(int n){return "198.18."+(sequence%250)+"."+(n+1);}
    static String key(String scope,String subject){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((scope+":"+subject).getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    static String ownKey(String purpose){String k=key("validation",RUN+":"+purpose);keys.add(k);return k;}
    static String emailSubject(String email,boolean verification){
        if(!verification){var users=jdbc.queryForList("SELECT user_id FROM `user` WHERE school_email=?",Long.class,email);if(!users.isEmpty())return "user:"+users.getFirst();}
        String column=verification?"verification_email":"account_email";
        var identities=jdbc.queryForList("SELECT identity_id FROM auth_email_identity WHERE "+column+"=?",Long.class,email);
        return identities.isEmpty()?"missing-identity":(verification?"verification-email:":"account-email:")+identities.getFirst();
    }
    static String bucketKey(String scope,String subject){return key(scope,scope.endsWith("-ip") || scope.endsWith("-ip-fail") || scope.equals("mail-global")?subject:emailSubject(subject,scope.startsWith("verify") || scope.startsWith("send")));}
    static long count(String field,String scope,String subject){return jdbc.queryForObject("SELECT COALESCE(SUM("+field+"),0) FROM auth_limit_bucket WHERE bucket_key=?",Long.class,bucketKey(scope,subject));}
    static long allBuckets(){return jdbc.queryForObject("SELECT COUNT(*) FROM auth_limit_bucket",Long.class);}
    static long rows(String k){return jdbc.queryForObject("SELECT COUNT(*) FROM auth_limit_bucket WHERE bucket_key=?",Long.class,k);}
    static User user(String e){return context.getBean(UserRepository.class).saveAndFlush(new User(encoder.encode(password()),e));}
    static String sentValue(String e,String prefix){return messages.stream().filter(m->Arrays.asList(m.getTo()).contains(e)).map(SimpleMailMessage::getText).filter(t->t.contains(prefix)).map(t->t.substring(t.indexOf(prefix)+prefix.length()).split("\n")[0]).findFirst().orElseThrow();}
    static List<Throwable> concurrent(int n,IntConsumer action) throws Exception {
        var executor=Executors.newFixedThreadPool(n);var ready=new CountDownLatch(n);var start=new CountDownLatch(1);var futures=new ArrayList<Future<Throwable>>();
        try{for(int i=0;i<n;i++){int x=i;futures.add(executor.submit(()->{ready.countDown();start.await();try{action.accept(x);return null;}catch(Throwable e){return e;}}));}
            check(ready.await(10,TimeUnit.SECONDS),"Worker readiness timeout");start.countDown();var out=new ArrayList<Throwable>();for(var f:futures)out.add(f.get(30,TimeUnit.SECONDS));return out;
        }finally{start.countDown();executor.shutdownNow();check(executor.awaitTermination(20,TimeUnit.SECONDS),"Workers did not terminate");}
    }
    static long code(List<Throwable> x,ErrorCode c){return x.stream().filter(t->t instanceof CustomException e && e.getErrorCode()==c).count();}
    static long ok(List<Throwable>x){return x.stream().filter(Objects::isNull).count();}
    static Map<String,Object> outcome(List<Throwable>x){var m=new LinkedHashMap<String,Object>();m.put("requests",x.size());m.put("success",ok(x));var codes=new TreeMap<String,Integer>();for(Throwable t:x)if(t!=null)codes.merge(t instanceof CustomException e?e.getErrorCode().name():describe(t),1,Integer::sum);m.put("errors",codes);return m;}

    static Map<String,Object> validCode() throws Exception {
        String e=email();verification.send(new EmailVerificationSendRequest(e,"SIGNUP"),ip(0));String code=sentValue(e,"인증번호: ");var winners=new CopyOnWriteArrayList<String>();
        var r=concurrent(16,i->winners.add(verification.verify(new EmailVerificationVerifyRequest(e,code,"SIGNUP"),ip(i)).verificationToken()));var d=outcome(r);
        check(ok(r)==1,"Valid code must have one winner");check(code(r,ErrorCode.EMAIL_VERIFICATION_ALREADY_USED)+code(r,ErrorCode.AUTH_REQUEST_LIMITED)==15,"Unexpected verification result");
        check(jdbc.queryForObject("SELECT COUNT(*) FROM email_verification WHERE email=? AND verification_token_hash=? AND verified_at IS NOT NULL",Long.class,e,keyRaw(winners.getFirst()))==1,"Winner token does not match committed hash");
        check(count("in_flight","verify-email-fail",e)==0,"Pending verification leaked");return d;
    }
    static String keyRaw(String v){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    static Map<String,Object> invalidCode() throws Exception {
        String e=email();verification.send(new EmailVerificationSendRequest(e,"SIGNUP"),ip(0));String actual=sentValue(e,"인증번호: ");String invalid=actual.equals("000000")?"000001":"000000";
        var r=concurrent(24,i->verification.verify(new EmailVerificationVerifyRequest(e,invalid,"SIGNUP"),ip(i)));var d=outcome(r);
        d.put("storedFailures",count("failure_count","verify-email-fail",e));d.put("storedRequests",count("request_count","verify-email",e));
        require(code(r,ErrorCode.EMAIL_VERIFICATION_INVALID)==5 && code(r,ErrorCode.AUTH_REQUEST_LIMITED)==19,"Verification failure quota mismatch",d);
        check(count("failure_count","verify-email-fail",e)==5 && count("in_flight","verify-email-fail",e)==0,"Verification reservation mismatch");return d;
    }
    static Map<String,Object> loginFailures() throws Exception {
        String e=email();user(e);String wrong=password();var r=concurrent(24,i->auth.login(new LoginRequest(e,wrong),ip(i)));var d=outcome(r);
        d.put("storedFailures",count("failure_count","login-email-fail",e));d.put("storedRequests",count("request_count","login-email",e));
        require(code(r,ErrorCode.UNAUTHORIZED)==10 && code(r,ErrorCode.AUTH_REQUEST_LIMITED)==14,"Login failure quota mismatch",d);
        check(count("failure_count","login-email-fail",e)==10 && count("in_flight","login-email-fail",e)==0,"Login reservations leaked");return d;
    }
    static Map<String,Object> resetRequests() throws Exception {
        String e=email();User u=user(e);int sent=messages.size();var r=concurrent(24,i->resets.requestPasswordReset(new PasswordResetRequest(e),ip(i)));var d=outcome(r);
        check(ok(r)==1 && code(r,ErrorCode.AUTH_REQUEST_LIMITED)==23,"Reset cooldown mismatch");check(messages.size()-sent==1,"Multiple reset mails");
        check(jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_token WHERE user_id=?",Long.class,u.getUserId())==1,"Reset tokens mismatch");
        String token=sentValue(e,"비밀번호 재설정 토큰: ");String changed=password();var uses=concurrent(2,i->resets.resetPassword(new PasswordResetConfirmRequest(token,changed,changed)));
        check(ok(uses)==1 && code(uses,ErrorCode.PASSWORD_RESET_TOKEN_ALREADY_USED)==1,"Reset token reused");
        check(encoder.matches(changed,context.getBean(UserRepository.class).findById(u.getUserId()).orElseThrow().getPassword()),"Password commit missing");
        d.put("tokenUse",outcome(uses));d.put("storedCooldown",count("request_count","reset-cooldown",e));return d;
    }
    static Map<String,Object> firstBucket() throws Exception {String k=ownKey("first");var r=concurrent(64,i->store.locked(List.of(k),v->{v.getFirst().requests++;return null;}));var d=outcome(r);long n=jdbc.queryForObject("SELECT request_count FROM auth_limit_bucket WHERE bucket_key=?",Long.class,k);d.put("storedRequests",n);require(ok(r)==64 && rows(k)==1 && n==64,"First bucket race lost updates",d);return d;}
    static Map<String,Object> sortedLocks() throws Exception {String a=ownKey("sort-a"),b=ownKey("sort-b");var r=concurrent(32,i->store.locked(i%2==0?List.of(a,b):List.of(b,a),v->{v.forEach(x->x.requests++);return null;}));check(ok(r)==32,"Sorted store locks failed");check(jdbc.queryForObject("SELECT SUM(request_count) FROM auth_limit_bucket WHERE bucket_key IN (?,?)",Long.class,a,b)==64,"Sorted counters mismatch");return outcome(r);}
    static Map<String,Object> emailQuota() throws Exception {String e=email();var config=new AuthProtectionProperties();config.getLoginFailures().setEmailMax(100);var isolated=new AuthProtectionService(store,config);var r=concurrent(24,i->{String variant=i%2==0?e:e.substring(0,e.indexOf('@')).toUpperCase(Locale.ROOT)+"@hknu.ac.kr";var p=isolated.login(variant,ip(i));isolated.complete(p,true);});var d=outcome(r);d.put("failureCeilingForRequestQuotaIsolation",100);d.put("storedRequests",count("request_count","login-email",e));require(ok(r)==20 && code(r,ErrorCode.AUTH_REQUEST_LIMITED)==4 && count("request_count","login-email",e)==20,"Case variant bypass",d);return d;}
    static Map<String,Object> ipFailures() throws Exception {String p=ip(200);var subjects=new ArrayList<String>();for(int i=0;i<40;i++)subjects.add(email());long before=allBuckets();var r=concurrent(40,i->{var permit=protection.login(subjects.get(i),p);protection.complete(permit,false);});var d=outcome(r);d.put("storedIpFailures",count("failure_count","login-ip-fail",p));d.put("newBuckets",allBuckets()-before);require(ok(r)==30 && code(r,ErrorCode.AUTH_REQUEST_LIMITED)==10 && count("failure_count","login-ip-fail",p)==30 && allBuckets()-before==62,"IP failure/denied bucket rollback mismatch",d);return d;}
    static Map<String,Object> ipRequests() throws Exception {String p=ip(201);var config=new AuthProtectionProperties();config.getVerifyFailures().setIpMax(1000);var isolated=new AuthProtectionService(store,config);var subjects=new ArrayList<String>();for(int i=0;i<72;i++)subjects.add(email());var r=concurrent(72,i->{var permit=isolated.verify(subjects.get(i),p);isolated.complete(permit,true);});var d=outcome(r);d.put("failureCeilingForRequestQuotaIsolation",1000);d.put("storedIpRequests",count("request_count","verify-ip",p));require(ok(r)==60 && code(r,ErrorCode.AUTH_REQUEST_LIMITED)==12 && count("request_count","verify-ip",p)==60,"IP request quota mismatch",d);return d;}
    static Map<String,Object> resetIp() throws Exception {String p=ip(202);var subjects=new ArrayList<String>();for(int i=0;i<24;i++)subjects.add(email());var r=concurrent(24,i->protection.reset(subjects.get(i),p));var d=outcome(r);d.put("storedIpRequests",count("request_count","reset-ip",p));require(ok(r)==20 && code(r,ErrorCode.AUTH_REQUEST_LIMITED)==4,"Reset IP quota mismatch",d);return d;}
    static HttpServletRequest request(String remote,String forwarded){return(HttpServletRequest)Proxy.newProxyInstance(HttpServletRequest.class.getClassLoader(),new Class<?>[]{HttpServletRequest.class},(p,m,a)->switch(m.getName()){case"getRemoteAddr"->remote;case"getHeader"->forwarded;default->throw new UnsupportedOperationException();});}
    static Map<String,Object> proxyIp(){var resolver=new ClientIpResolver("");check(resolver.resolve(request("198.51.100.10","1.2.3.4")).equals("198.51.100.10"),"Untrusted XFF accepted");check(resolver.resolve(request("2001:db8::1",null)).equals(resolver.resolve(request("2001:0db8:0:0:0:0:0:1",null))),"IPv6 alias not normalized");return Map.of("untrustedXffIgnored",true,"ipv6Canonicalized",true);}
    static Map<String,Object> independentRollback(){String e=email();var permit=protection.verify(e,ip(0));new TransactionTemplate(tm).executeWithoutResult(tx->{context.getBean(UserRepository.class).saveAndFlush(new User(encoder.encode(password()),e));protection.complete(permit,false);tx.setRollbackOnly();});check(jdbc.queryForObject("SELECT COUNT(*) FROM `user` WHERE school_email=?",Long.class,e)==0,"Outer user committed");check(count("failure_count","verify-email-fail",e)==1 && count("request_count","verify-email",e)==1 && count("in_flight","verify-email-fail",e)==0,"Independent failure rolled back");return Map.of("businessRows",0,"durableFailures",1,"durableRequests",1);}
    static Map<String,Object> smtpRollback(){String e=email();User u=user(e);long mailBefore=count("request_count","mail-global","all");failMail.set(true);Throwable error=null;try{resets.requestPasswordReset(new PasswordResetRequest(e),ip(0));}catch(Throwable t){error=t;}finally{failMail.set(false);}check(error instanceof CustomException c && c.getErrorCode()==ErrorCode.INTERNAL_SERVER_ERROR,"Expected mock SMTP failure");check(jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_token WHERE user_id=?",Long.class,u.getUserId())==0,"Failed SMTP left token");check(count("request_count","reset-cooldown",e)==1 && count("request_count","mail-global","all")==mailBefore+1,"SMTP failure refunded quota");return Map.of("tokens",0,"cooldown",1,"globalQuotaDelta",1,"smtp","mock failure only");}

    static Connection hold(String k) throws Exception {store.locked(List.of(k),v->null);Connection c=open();guard(c);c.setAutoCommit(false);try(var s=c.prepareStatement("SELECT bucket_key FROM auth_limit_bucket WHERE bucket_key=? FOR UPDATE")){s.setString(1,k);try(var r=s.executeQuery()){check(r.next(),"Missing lock row");}}return c;}
    static Map<String,Object> lockWait() throws Exception {String k=ownKey("wait");var ex=Executors.newSingleThreadExecutor();try(Connection blocker=hold(k)){var start=new CountDownLatch(1);long time=System.nanoTime();var f=ex.submit(()->{start.countDown();store.locked(List.of(k),v->{v.getFirst().requests++;return null;});});check(start.await(2,TimeUnit.SECONDS),"Waiter did not start");Thread.sleep(300);check(!f.isDone(),"Row was not locked");blocker.commit();f.get(10,TimeUnit.SECONDS);return Map.of("waitMs",(System.nanoTime()-time)/1_000_000,"counter",jdbc.queryForObject("SELECT request_count FROM auth_limit_bucket WHERE bucket_key=?",Integer.class,k));}finally{ex.shutdownNow();}}
    static Map<String,Object> lockTimeout() throws Exception {
        String locked="f"+ownKey("timeout").substring(1),fresh="0"+ownKey("timeout-new").substring(1);keys.add(locked);keys.add(fresh);
        Throwable error=null;long time=System.nanoTime();try(Connection blocker=hold(locked)){try{store.locked(List.of(fresh,locked),v->{v.forEach(x->x.requests++);return null;});}catch(Throwable t){error=t;}blocker.rollback();}
        long elapsed=(System.nanoTime()-time)/1_000_000;check(sqlCode(error)==1205,"Expected MySQL lock wait timeout 1205");check(error instanceof org.springframework.dao.ConcurrencyFailureException,"Timeout not translated as concurrency failure");check(new GlobalExceptionHandler().handleConcurrentUpdate((Exception)error).getStatusCode().value()==409,"Concurrency handler status");check(rows(fresh)==0,"Fresh row survived timeout rollback");check(jdbc.queryForObject("SELECT request_count FROM auth_limit_bucket WHERE bucket_key=?",Integer.class,locked)==0,"Timeout changed row");return Map.of("mysqlCode",1205,"exception",error.getClass().getSimpleName(),"elapsedMs",elapsed,"httpMapping",409,"insertRolledBack",true);
    }
    static Map<String,Object> deadlock() throws Exception {
        String a=ownKey("dead-a"),b=ownKey("dead-b");store.locked(List.of(a,b),v->null);var barrier=new CyclicBarrier(2);
        // Preserve the original single-victim/rollback assertion by testing retry exhaustion at one attempt.
        // The new two-process HTTP cases independently verify successful full-transaction retries.
        var retry=new AuthDatabaseRetryProperties();retry.setMaxAttempts(1);
        var oneAttempt=new AuthLimitStore(jdbc,tm,retry);
        var r=concurrent(2,i->oneAttempt.locked(List.of(i==0?a:b),v->{try{barrier.await(5,TimeUnit.SECONDS);}catch(Exception e){throw new IllegalStateException("Deadlock barrier",e);}jdbc.update("UPDATE auth_limit_bucket SET request_count=request_count+1 WHERE bucket_key=?",i==0?b:a);v.getFirst().requests++;return null;}));
        var victim=r.stream().filter(Objects::nonNull).findFirst().orElseThrow();var d=outcome(r);d.put("mysqlVictimCode",sqlCode(victim));
        require(ok(r)==1 && sqlCode(victim)==1213,"Expected one real InnoDB deadlock victim",d);check(victim instanceof org.springframework.dao.ConcurrencyFailureException,"Deadlock translation mismatch");check(new GlobalExceptionHandler().handleConcurrentUpdate((Exception)victim).getStatusCode().value()==409,"Deadlock HTTP mapping mismatch");
        check(jdbc.queryForObject("SELECT SUM(request_count) FROM auth_limit_bucket WHERE bucket_key IN (?,?)",Long.class,a,b)==2,"Deadlock victim partial commit");d.put("httpMapping",409);d.put("committedCounterSum",2);return d;
    }
    static Map<String,Object> callbackRollback(){String k=ownKey("rollback");try{store.locked(List.of(k),v->{jdbc.update("UPDATE auth_limit_bucket SET failure_count=9 WHERE bucket_key=?",k);throw new IllegalStateException("intentional test rollback");});throw new CheckFailure("Callback did not throw",Map.of());}catch(IllegalStateException expected){}check(rows(k)==0,"Callback failure partially committed");return Map.of("remainingRows",0);}
    static Map<String,Object> poolExhaustion() throws Exception {try(var small=newPool(4,500,2)){var safe=new Guarded(small);var smallStore=new AuthLimitStore(new TrackingJdbc(safe),new JdbcTransactionManager(safe));String k=ownKey("small-pool");var leases=new ArrayList<Connection>();try{for(int i=0;i<4;i++)leases.add(safe.getConnection());var r=concurrent(8,i->{try(var c=safe.getConnection()){throw new CheckFailure("Exhausted pool unexpectedly acquired",Map.of());}catch(SQLException e){throw new PoolExpected(e);}});check(r.stream().allMatch(t->t instanceof PoolExpected && sqlCode(t)==0),"Pool exhaustion did not time out");var tx=concurrent(4,i->smallStore.locked(List.of(k),v->null));check(tx.stream().allMatch(t->t instanceof org.springframework.transaction.CannotCreateTransactionException),"Exhausted store bypassed transaction failure");}finally{for(var c:leases)c.close();}awaitIdle(small);for(int i=0;i<8;i++)try(var c=safe.getConnection()){check(scalar(c,"SELECT 1")==1,"Pool recovery failed");}smallStore.locked(List.of(k),v->{v.getFirst().requests++;return null;});awaitIdle(small);return Map.of("poolSize",4,"heldConnections",4,"waitingRequests",8,"boundedAcquisitionTimeouts",8,"storeTransactionFailures",4,"recoveredQueries",8,"recoveredStoreTransactions",1,"activeAfter",small.getHikariPoolMXBean().getActiveConnections());}}
    static class PoolExpected extends RuntimeException{PoolExpected(Throwable e){super(e);}}
    static Map<String,Object> stress() throws Exception {String k=ownKey("stress");for(int round=0;round<3;round++){var r=concurrent(48,i->store.locked(List.of(k),v->{v.getFirst().requests++;return null;}));check(ok(r)==48,"Stress transaction failed");awaitIdle(pool);}check(jdbc.queryForObject("SELECT request_count FROM auth_limit_bucket WHERE bucket_key=?",Long.class,k)==144,"Stress lost updates");return Map.of("rounds",3,"requestsPerRound",48,"storedRequests",144,"activeAfter",pool.getHikariPoolMXBean().getActiveConnections());}
    static Map<String,Object> collationLogin() throws Exception {
        String e=email();User u=user(e);String alias="á"+e.substring(1);emails.add(alias);
        check(context.getBean(Validator.class).validate(new LoginRequest(alias,password())).isEmpty(),"Accent alias not accepted by DTO; scenario not applicable");
        check(context.getBean(UserRepository.class).findBySchoolEmail(alias).orElseThrow().getUserId().equals(u.getUserId()),"Alias resolves another account");
        String wrong=password();var r=concurrent(24,i->auth.login(new LoginRequest(i%2==0?e:alias,wrong),ip(i)));var d=outcome(r);
        d.put("sameDatabaseAccount",true);d.put("canonicalFailures",count("failure_count","login-email-fail",e));d.put("aliasFailures",count("failure_count","login-email-fail",alias));
        require(code(r,ErrorCode.UNAUTHORIZED)<=10,"One database account exceeded email failure quota through collation aliases",d);
        require(code(r,ErrorCode.UNAUTHORIZED)==10 && code(r,ErrorCode.AUTH_REQUEST_LIMITED)==14,"Alias results must all be valid failures or quota rejections",d);
        check(bucketKey("login-email-fail",e).equals(bucketKey("login-email-fail",alias)),"Known account aliases use different keys");return d;
    }
    static Map<String,Object> collationReset() throws Exception {
        String e=email();User u=user(e);String alias="á"+e.substring(1);emails.add(alias);
        check(context.getBean(UserRepository.class).findBySchoolEmail(alias).orElseThrow().getUserId().equals(u.getUserId()),"Alias resolves another account");
        int sent=messages.size();var r=concurrent(2,i->resets.requestPasswordReset(new PasswordResetRequest(i==0?e:alias),ip(i)));var d=outcome(r);
        d.put("sameDatabaseAccount",true);d.put("mockMails",messages.size()-sent);long tokens=jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_token WHERE user_id=?",Long.class,u.getUserId());d.put("storedTokens",tokens);
        require(ok(r)==1,"One account bypassed reset cooldown with collation alias",d);
        require(code(r,ErrorCode.AUTH_REQUEST_LIMITED)==1 && tokens==1 && messages.size()-sent==1,"Reset alias result mismatch",d);return d;
    }
    static Map<String,Object> unrelatedLock() throws Exception {
        String a=email(),b=email();verification.send(new EmailVerificationSendRequest(a,"SIGNUP"),ip(0));verification.send(new EmailVerificationSendRequest(b,"SIGNUP"),ip(1));String code=sentValue(b,"인증번호: ");
        Long id=jdbc.queryForObject("SELECT verification_id FROM email_verification WHERE email=?",Long.class,a);Throwable error=null;long time=System.nanoTime();
        try(Connection c=open()){guard(c);c.setAutoCommit(false);try(var s=c.prepareStatement("SELECT verification_id FROM email_verification WHERE verification_id=? FOR UPDATE")){s.setLong(1,id);s.executeQuery().close();}
            try{verification.verify(new EmailVerificationVerifyRequest(b,code,"SIGNUP"),ip(2));}catch(Throwable t){error=t;}c.rollback();}
        long elapsed=(System.nanoTime()-time)/1_000_000;var d=new LinkedHashMap<String,Object>();d.put("waitMs",elapsed);d.put("mysqlCode",sqlCode(error));d.put("error",error==null?"none":describe(error));
        d.put("equivalentQueryPlan",jdbc.queryForList("EXPLAIN SELECT verification_id FROM email_verification WHERE email=? AND purpose='SIGNUP' ORDER BY created_at DESC,verification_id DESC LIMIT 1 FOR UPDATE",b));
        require(error==null,"Unrelated verification row blocked valid verification",d);
        String actual=VerificationSqlInspector.latest.get();check(actual!=null,"Missing real Hibernate locking query");
        var plan=jdbc.queryForList("EXPLAIN "+actual,b,"SIGNUP",1);d.put("actualHibernateQuery",actual);d.put("actualHibernateQueryPlan",plan);
        check(plan.stream().anyMatch(x->"idx_email_verification_latest".equals(x.get("key"))),"Actual locking query did not use latest index");return d;
    }
    static class VerificationSqlInspector implements org.hibernate.resource.jdbc.spi.StatementInspector {
        static final ThreadLocal<String> latest=new ThreadLocal<>();
        public String inspect(String sql){String normalized=sql.toLowerCase(Locale.ROOT);if(normalized.contains("select ") && normalized.contains("email_verification") && normalized.contains("email") && normalized.contains("purpose"))latest.set(sql);return sql;}
    }

    static List<String> aliases(String e) {
        String accent="á"+e.substring(1);
        var variants=List.of(e,accent,java.text.Normalizer.normalize(accent,java.text.Normalizer.Form.NFD),e.substring(0,e.indexOf('@')).toUpperCase(Locale.ROOT)+"@hknu.ac.kr");
        emails.addAll(variants);return variants;
    }
    static Map<String,Object> preregistrationSend() throws Exception {
        String e=email();var variants=aliases(e);int sent=messages.size();var r=concurrent(24,i->verification.send(new EmailVerificationSendRequest(variants.get(i%4),"SIGNUP"),ip(i)));var d=outcome(r);
        long codes=jdbc.queryForObject("SELECT COUNT(*) FROM email_verification WHERE email=?",Long.class,e);
        d.put("storedCodes",codes);d.put("mockMails",messages.size()-sent);
        require(ok(r)==1 && code(r,ErrorCode.EMAIL_VERIFICATION_COOLDOWN)==23 && codes==1 && messages.size()-sent==1,"Pre-registration send alias bypass",d);
        for(String alias:variants)check(count("request_count","send-cooldown",alias)==1,"Send aliases not shared");
        check(jdbc.queryForObject("SELECT COUNT(*) FROM auth_limit_bucket WHERE bucket_key NOT REGEXP '^[0-9a-f]{64}$'",Long.class)==0,"Bucket key contains unprotected identity");return d;
    }
    static Map<String,Object> preregistrationVerify() throws Exception {
        String e=email();var variants=aliases(e);verification.send(new EmailVerificationSendRequest(e,"SIGNUP"),ip(0));String correct=sentValue(e,"인증번호: "),wrong=correct.equals("000000")?"000001":"000000";
        var r=concurrent(24,i->verification.verify(new EmailVerificationVerifyRequest(variants.get(i%4),wrong,"SIGNUP"),ip(i)));var d=outcome(r);
        require(code(r,ErrorCode.EMAIL_VERIFICATION_INVALID)==5 && code(r,ErrorCode.AUTH_REQUEST_LIMITED)==19,"Pre-registration verification alias bypass",d);
        for(String alias:variants)check(count("failure_count","verify-email-fail",alias)==5 && count("in_flight","verify-email-fail",alias)==0,"Verification aliases not shared");return d;
    }
    static Map<String,Object> accountIndependence() throws Exception {
        String a=email(),b=email();user(a);user(b);String wrong=password();var r=concurrent(24,i->auth.login(new LoginRequest(i%2==0?a:b,wrong),ip(i)));var d=outcome(r);
        require(code(r,ErrorCode.UNAUTHORIZED)==20 && code(r,ErrorCode.AUTH_REQUEST_LIMITED)==4,"Different user quotas interfere",d);
        check(count("failure_count","login-email-fail",a)==10 && count("failure_count","login-email-fail",b)==10,"Independent account failure counts");
        check(!bucketKey("login-email-fail",a).equals(bucketKey("login-email-fail",b)),"Different users share key");return d;
    }
    static Map<String,Object> unknownAliases() throws Exception {
        String e=email();var variants=aliases(e);int before=messages.size();var success=new AtomicInteger();var r=concurrent(4,i->{if(resets.requestPasswordReset(new PasswordResetRequest(variants.get(i)),ip(i)).sent())success.incrementAndGet();});var d=outcome(r);
        require(ok(r)==1 && code(r,ErrorCode.AUTH_REQUEST_LIMITED)==3 && success.get()==1 && before==messages.size(),"Unknown-account alias contract mismatch",d);
        String other=email();check(resets.requestPasswordReset(new PasswordResetRequest(other),ip(50)).sent(),"Unknown account response changed");check(before==messages.size(),"Unknown account sent mail");return d;
    }
    static Map<String,Object> aliasRecovery() throws Exception {
        String e=email();var variants=aliases(e);String secret=password();context.getBean(UserRepository.class).saveAndFlush(new User(encoder.encode(secret),e));
        check(auth.login(new LoginRequest(e,secret),ip(0)).accessToken()!=null,"Normal login failed");
        var cfg=new AuthProtectionProperties();cfg.getLoginRequests().setEmailMax(100);cfg.getLoginFailures().setEmailMax(2);cfg.getLoginFailures().setBlockSeconds(1);var shortBlock=new AuthProtectionService(store,cfg);
        for(int i=0;i<2;i++)shortBlock.complete(shortBlock.login(variants.get(i),ip(i+1)),false);
        try{shortBlock.login(variants.get(2),ip(5));throw new CheckFailure("Alias did not share block",Map.of());}catch(AuthRateLimitException expected){}
        Thread.sleep(1100);check(auth.login(new LoginRequest(variants.get(2),secret),ip(6)).accessToken()!=null,"Login did not recover after block");
        check(count("failure_count","login-email-fail",e)==0 && count("in_flight","login-email-fail",e)==0,"Recovered successful login did not reset failures");return Map.of("blockedAcrossVariants",true,"recoveredWithRealDbTime",true,"successfulLogin",true);
    }
    static String verifiedToken(String e,int n){verification.send(new EmailVerificationSendRequest(e,"SIGNUP"),ip(n));return verification.verify(new EmailVerificationVerifyRequest(e,sentValue(e,"인증번호: "),"SIGNUP"),ip(n)).verificationToken();}
    static Map<String,Object> consumeOnce() throws Exception {
        String e=email(),token=verifiedToken(e,0);var r=concurrent(2,i->new TransactionTemplate(tm).executeWithoutResult(tx->verification.consumeToken(e,EmailVerificationPurpose.SIGNUP,token)));var d=outcome(r);
        require(ok(r)==1 && code(r,ErrorCode.EMAIL_VERIFICATION_TOKEN_INVALID)==1,"Verification token consumed twice",d);return d;
    }
    static Map<String,Object> consumeUnrelated() throws Exception {
        String a=email(),b=email();verifiedToken(a,0);String token=verifiedToken(b,1);Long id=jdbc.queryForObject("SELECT verification_id FROM email_verification WHERE email=?",Long.class,a);
        // With only one row per email both indexes estimate one row. Keep the exact token-index
        // assertion and add same-email history so the third predicate has meaningful selectivity.
        LocalDateTime now=LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        for(int i=0;i<32;i++)jdbc.update("""
            INSERT INTO email_verification
            (email,purpose,verification_code,expires_at,verified_at,verification_token_hash,verification_token_expires_at,created_at)
            VALUES (?,'SIGNUP','unused-history-fixture',?,?,?,?,?)
            """,b,now.plusMinutes(5),now,keyRaw(UUID.randomUUID().toString()),now.plusMinutes(5),now.minusMinutes(i+1));
        try(Connection c=open()){guard(c);c.setAutoCommit(false);try(var s=c.prepareStatement("SELECT verification_id FROM email_verification WHERE verification_id=? FOR UPDATE")){s.setLong(1,id);s.executeQuery().close();}
            new TransactionTemplate(tm).executeWithoutResult(tx->verification.consumeToken(b,EmailVerificationPurpose.SIGNUP,token));c.rollback();}
        String actual=VerificationSqlInspector.latest.get();check(actual!=null,"Missing token query");var plan=jdbc.queryForList("EXPLAIN "+actual,b,"SIGNUP",keyRaw(token));
        require(plan.stream().anyMatch(x->"idx_email_verification_token".equals(x.get("key"))),"Token query did not use token index",Map.of("actualHibernateQuery",actual,"actualHibernateQueryPlan",plan));
        check(jdbc.queryForObject("SELECT COUNT(*) FROM email_verification WHERE email=? AND consumed_at IS NOT NULL",Long.class,b)==1,"Unrelated token not consumed");return Map.of("consumed",1,"sameEmailHistoryRows",32,"actualHibernateQuery",actual,"actualHibernateQueryPlan",plan);
    }
    static Map<String,Object> differentEmailsVerify() throws Exception {
        var subjects=new ArrayList<String>();var codes=new ArrayList<String>();for(int i=0;i<8;i++){String e=email();subjects.add(e);verification.send(new EmailVerificationSendRequest(e,"SIGNUP"),ip(i));codes.add(sentValue(e,"인증번호: "));}
        var r=concurrent(8,i->verification.verify(new EmailVerificationVerifyRequest(subjects.get(i),codes.get(i),"SIGNUP"),ip(i)));var d=outcome(r);require(ok(r)==8,"Different email verifications interfered",d);return d;
    }

    static void cleanup() throws Exception {try(Connection c=open()){guard(c);grants(c);c.setAutoCommit(false);try{
        for(String e:emails){try(var s=c.prepareStatement("DELETE FROM email_verification WHERE email=?")){s.setString(1,e);s.executeUpdate();}try(var s=c.prepareStatement("DELETE FROM password_reset_token WHERE user_id IN (SELECT user_id FROM `user` WHERE school_email=?)")){s.setString(1,e);s.executeUpdate();}try(var s=c.prepareStatement("DELETE FROM `user` WHERE school_email=?")){s.setString(1,e);s.executeUpdate();}}
        for(String k:keys)try(var s=c.prepareStatement("DELETE FROM auth_limit_bucket WHERE bucket_key=?")){s.setString(1,k);s.executeUpdate();}
        for(String e:emails)try(var s=c.prepareStatement("DELETE FROM auth_email_identity WHERE account_email=? OR verification_email=?")){s.setString(1,e);s.setString(2,e);s.executeUpdate();}c.commit();
        }catch(Throwable e){c.rollback();throw e;}}}
    static void awaitIdle(HikariDataSource p) throws Exception {long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);do{var m=p.getHikariPoolMXBean();if(m.getActiveConnections()==0 && m.getThreadsAwaitingConnection()==0)return;Thread.sleep(20);}while(System.nanoTime()<end);throw new CheckFailure("Pool connections not returned",Map.of("active",p.getHikariPoolMXBean().getActiveConnections(),"waiting",p.getHikariPoolMXBean().getThreadsAwaitingConnection()));}
    static long scalar(Connection c,String q) throws SQLException {try(var s=c.createStatement();var r=s.executeQuery(q)){r.next();return r.getLong(1);}}
    static String text(Connection c,String q,int column)throws SQLException{try(var s=c.createStatement();var r=s.executeQuery(q)){r.next();return r.getString(column);}}
    static String normalizeDdl(String ddl){return ddl.replaceAll(" AUTO_INCREMENT=\\d+","");}
    static int sqlCode(Throwable t){Set<Throwable>seen=Collections.newSetFromMap(new IdentityHashMap<>());for(Throwable x=t;x!=null && seen.add(x);x=x.getCause())if(x instanceof SQLException s && s.getErrorCode()!=0)return s.getErrorCode();return 0;}
    static String describe(Throwable t){var chain=new ArrayList<String>();Set<Throwable>seen=Collections.newSetFromMap(new IdentityHashMap<>());for(Throwable x=t;x!=null && seen.add(x);x=x.getCause()){String part=x.getClass().getSimpleName();if(x instanceof org.springframework.beans.factory.BeanCreationException b)part+="(bean="+b.getBeanName()+")";if(x.getStackTrace().length>0)part+="@"+x.getStackTrace()[0].getClassName()+":"+x.getStackTrace()[0].getLineNumber();if(x instanceof org.hibernate.tool.schema.spi.SchemaManagementException || x instanceof ClassNotFoundException || x instanceof NoClassDefFoundError)part+="["+x.getMessage()+"]";chain.add(part);}return String.join(" -> ",chain)+"/mysql="+sqlCode(t);}
    static String json(Object o){if(o==null)return"null";if(o instanceof Number || o instanceof Boolean)return o.toString();if(o instanceof Map<?,?>m){var a=new ArrayList<String>();m.forEach((k,v)->a.add(json(k.toString())+":"+json(v)));return"{"+String.join(",",a)+"}";}if(o instanceof Collection<?>c)return"["+String.join(",",c.stream().map(AuthMysqlConcurrency::json).toList())+"]";return"\""+o.toString().replace("\\","\\\\").replace("\"","\\\"").replace("\r","\\r").replace("\n","\\n").replace("\t","\\t")+"\"";}
}
