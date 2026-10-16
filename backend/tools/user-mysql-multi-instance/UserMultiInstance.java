import com.zaxxer.hikari.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import tools.jackson.databind.*;

/** Opt-in two-process, actual HTTP validation. No production changes or real mail transport. */
public class UserMultiInstance {
    static final String RUN=java.util.UUID.randomUUID().toString().replace("-","").substring(0,12);
    static final JsonMapperHolder JSON=new JsonMapperHolder();
    static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    static final List<Map<String,Object>> CASES=new ArrayList<>(), REQUESTS=new CopyOnWriteArrayList<>(), EVENTS=new CopyOnWriteArrayList<>();
    static final List<Mail> MAIL=new CopyOnWriteArrayList<>();
    static final Set<String> FAIL_MAIL=ConcurrentHashMap.newKeySet();
    static final List<Server> SERVERS=new CopyOnWriteArrayList<>();
    static final AtomicInteger REQUEST_SEQUENCE=new AtomicInteger();static volatile String activeCase="startup";
    static Path root,out;static String jwt,ipc;static ServerSocket mailSocket;static ExecutorService mailExecutor=Executors.newVirtualThreadPerTaskExecutor();
    static Server a,b;static int seq,entityCount;static boolean provisioned,cleaned;static List<String> tables;
    static Set<String> selected=Set.of();
    static Map<String,Object> originalBefore,initialSchema;
    static final String PASSWORD=java.util.UUID.randomUUID()+"!Aa1",NEW_PASSWORD=java.util.UUID.randomUUID()+"!Aa1";
    record Mail(String server,String email,String text){}
    record Reply(int port,int status,JsonNode json,long millis,String retry){String code(){return json.path("errorCode").asString("");}boolean ok(){return status==200&&json.path("success").asBoolean();}}
    static class JsonMapperHolder {final tools.jackson.databind.json.JsonMapper mapper=tools.jackson.databind.json.JsonMapper.builder().build();}
    static Map<String,String> protectionSettings(){var m=new LinkedHashMap<String,String>();
        for(String scope:List.of("login","verify","reset","send")){int em=scope.equals("login")?20:scope.equals("verify")?10:5;int ip=scope.equals("login")?120:scope.equals("verify")?60:20;int win=scope.equals("login")||scope.equals("verify")?60:3600;
            m.put("auth.protection."+scope+"-requests.email-max",""+em);m.put("auth.protection."+scope+"-requests.ip-max",""+ip);m.put("auth.protection."+scope+"-requests.window-seconds",""+win);}
        for(String s:List.of("login","verify")){boolean login=s.equals("login");String p="auth.protection."+s+"-failures.";m.put(p+"email-max",login?"10":"5");m.put(p+"ip-max",login?"30":"20");m.put(p+"window-seconds",login?"600":"300");m.put(p+"block-seconds",login?"60":"300");}
        m.put("auth.protection.reset-cooldown-seconds","60");m.put("auth.protection.mail-max","100");m.put("auth.protection.mail-window-seconds","3600");m.put("auth.email-verification.resend-interval-seconds","60");return m;
    }
    public static void main(String[] args)throws Exception {
        if(args.length==3)selected=Set.of(args[2].split(","));
        root=Path.of(args[0]).toRealPath();out=root.resolve("backend/tools/user-mysql-multi-instance/results/"+LocalDate.now()+"-"+RUN);Files.createDirectories(out);
        ((ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).setLevel(ch.qos.logback.classic.Level.OFF);
        UserMultiSupport.database="kkiri_user_mi_"+RUN;UserMultiSupport.account="kkiri_umi_"+RUN;UserMultiSupport.password=randomSecret();jwt=randomSecret();ipc=randomSecret();
        var startHashes=sourceHashes();boolean completed=false;
        try {
            portFree(8081);portFree(8082);
            originalBefore=originalSnapshot();UserMultiSupport.write(out.resolve("original-user-before.json"),originalBefore);
            provision();generateSchema();applySchema();startMailCapture();
            a=startServer(8081);b=startServer(8082);
            runUserScenarios();completed=true;
        } catch(Throwable t){CASES.add(Map.of("name","harness","status","INCOMPLETE","error",UserMultiSupport.error(t)));}
        finally {
            for(var s:SERVERS)try{s.stop();}catch(Throwable t){CASES.add(Map.of("name","server-cleanup","status","INCOMPLETE","error",UserMultiSupport.error(t)));}
            if(mailSocket!=null)mailSocket.close();mailExecutor.shutdownNow();
            if(provisioned&&tables!=null){try{cleanup();cleaned=true;}catch(Throwable t){CASES.add(Map.of("name","data-cleanup","status","INCOMPLETE","error",UserMultiSupport.error(t)));}}
            try{var after=originalSnapshot();UserMultiSupport.write(out.resolve("original-user-after.json"),after);UserMultiSupport.check(originalBefore.equals(after),"Original User database changed");}catch(Throwable t){CASES.add(Map.of("name","original-user-preservation","status","INCOMPLETE","error",UserMultiSupport.error(t)));}
            var hashes=sourceHashes();boolean unchanged=startHashes.equals(hashes);UserMultiSupport.write(out.resolve("source-hashes.json"),hashes);
            UserMultiSupport.write(out.resolve("requests.json"),REQUESTS);UserMultiSupport.write(out.resolve("servers.json"),EVENTS);UserMultiSupport.write(out.resolve("cases.json"),CASES);
            boolean stopped=SERVERS.stream().noneMatch(s->s.process.isAlive());
            String verdict=!completed||!cleaned||!stopped||!unchanged||CASES.stream().anyMatch(x->x.get("status").equals("INCOMPLETE"))?"INSUFFICIENT":CASES.stream().anyMatch(x->x.get("status").equals("FAIL"))?"FAIL":"PASS";
            var summary=new LinkedHashMap<String,Object>();summary.put("verdict",verdict);summary.put("cases",CASES.size());summary.put("passed",CASES.stream().filter(x->x.get("status").equals("PASS")).count());summary.put("failed",CASES.stream().filter(x->x.get("status").equals("FAIL")).count());summary.put("httpRequests",REQUESTS.size());summary.put("mockMailMessages",MAIL.size());summary.put("allSpawnedProcessesStopped",stopped);summary.put("allTablesEmpty",cleaned);summary.put("productionAndPriorEvidenceUnchanged",unchanged);summary.put("database",UserMultiSupport.db());summary.put("account",UserMultiSupport.user());summary.put("realSmtpConnections",0);summary.put("bootJarSha256",args[1]);UserMultiSupport.write(out.resolve("summary.json"),summary);
            System.out.println("RESULT "+verdict+" evidence="+out);if(!verdict.equals("PASS"))System.exit(1);
        }
    }
    static String randomSecret(){byte[] bytes=new byte[48];new SecureRandom().nextBytes(bytes);return Base64.getEncoder().encodeToString(bytes);}
    static void portFree(int port)throws Exception {try(var socket=new ServerSocket()){socket.setReuseAddress(false);socket.bind(new InetSocketAddress("127.0.0.1",port));}}
    static String dockerRoot(String sql)throws Exception {
        var pb=new ProcessBuilder("docker","exec","-i","-e","MYSQL_PWD","kkiri-user-mysql-test","mysql","--protocol=SOCKET","-uroot","--batch","--skip-column-names","--raw");pb.environment().put("MYSQL_PWD",System.getenv("KKIRI_MI_ROOT_PASSWORD"));
        var p=pb.start();try(var w=new OutputStreamWriter(p.getOutputStream(),StandardCharsets.UTF_8)){w.write(sql);}
        String result=new String(p.getInputStream().readAllBytes(),StandardCharsets.UTF_8);byte[] errors=p.getErrorStream().readAllBytes();UserMultiSupport.check(p.waitFor(20,TimeUnit.SECONDS)&&p.exitValue()==0,"Container-local provisioning query failed");return result.trim();
    }
    static void provision()throws Exception {
        UserMultiSupport.check(dockerRoot("SELECT CONCAT(@@server_uuid,'|',VERSION(),'|',@@hostname,'|',@@port,'|',CURRENT_USER());").equals(UserMultiSupport.UUID+"|8.0.46|cbede7f4b30d|3306|root@localhost"),"Provisioning server identity mismatch");
        UserMultiSupport.check(dockerRoot("SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='"+UserMultiSupport.db()+"';").equals("0"),"Dedicated schema already exists");
        UserMultiSupport.check(dockerRoot("SELECT COUNT(*) FROM mysql.user WHERE User='"+UserMultiSupport.user()+"';").equals("0"),"Dedicated account already exists");
        // Wildcards in MySQL schema grants are escaped; only this literal per-run schema is authorized.
        dockerRoot("CREATE DATABASE `"+UserMultiSupport.db()+"` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;\nCREATE USER '"+UserMultiSupport.user()+"'@'%' IDENTIFIED BY '"+UserMultiSupport.password+"';\nGRANT ALL PRIVILEGES ON `"+UserMultiSupport.db().replace("_","\\_")+"`.* TO '"+UserMultiSupport.user()+"'@'%';\n");
        provisioned=true;
        try(var c=UserMultiSupport.open()){UserMultiSupport.grants(c);UserMultiSupport.check(UserMultiSupport.number(c,"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()")==0,"New schema not empty");
            UserMultiSupport.write(out.resolve("environment.json"),Map.of("endpoint","127.0.0.1:3308/"+UserMultiSupport.db(),"account",UserMultiSupport.user(),"identity",UserMultiSupport.rows(c,"SELECT DATABASE() db,CURRENT_USER() account,USER() client,VERSION() version,@@server_uuid uuid,@@hostname hostname,@@port port,@@transaction_isolation isolation,@@session.time_zone time_zone"),"grants",UserMultiSupport.rows(c,"SHOW GRANTS"),"existingAuthTestDatabaseAccessed",false,"protectionSettings",protectionSettings()));}
    }
    static void generateSchema()throws Exception {
        var factory=new LocalContainerEntityManagerFactoryBean();factory.setPackagesToScan("com.kkirikkiri");factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        var properties=new HashMap<String,Object>();properties.put("hibernate.dialect","org.hibernate.dialect.MySQLDialect");properties.put("hibernate.boot.allow_jdbc_metadata_access",false);properties.put("hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl");
        properties.put("jakarta.persistence.schema-generation.database.action","none");properties.put("jakarta.persistence.schema-generation.scripts.action","create");properties.put("jakarta.persistence.schema-generation.scripts.create-target",out.resolve("synthetic-full-schema.sql").toString());
        factory.setJpaPropertyMap(properties);factory.afterPropertiesSet();entityCount=factory.getObject().getMetamodel().getEntities().size();factory.destroy();
    }
    static void applySchema()throws Exception {
        String sql=Files.readString(out.resolve("synthetic-full-schema.sql"));var statements=AuthMysqlMigration.split(sql);
        for(String statement:statements)UserMultiSupport.check(statement.matches("(?is)^(create table|create (unique )?index|alter table) .+")&&!statement.matches("(?is).*\\b(drop|truncate|grant|insert|update|delete|use)\\b.*")&&!statement.contains("kkiri_"),"Unexpected generated DDL");
        try(var c=UserMultiSupport.open()){UserMultiSupport.grants(c);for(String statement:statements)try(var s=c.createStatement()){s.execute(statement);}
            // Preserve the explicit physical ASCII contract not expressed by the Entity annotation.
            UserMultiSupport.exec(c,"ALTER TABLE auth_limit_bucket MODIFY bucket_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL");
            tables=UserMultiSupport.rows(c,"SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() ORDER BY table_name").stream().map(x->x.values().iterator().next().toString()).toList();UserMultiSupport.check(tables.size()==entityCount,"Expected a table for every mapped entity");
            initialSchema=schema(c);UserMultiSupport.write(out.resolve("initial-schema.json"),initialSchema);}
    }
    static Map<String,Object> schema(Connection c)throws Exception {var m=new LinkedHashMap<String,Object>();for(String t:tables)m.put(t,UserMultiSupport.rows(c,"SHOW CREATE TABLE `"+t+"`"));return m;}
    static void startMailCapture()throws Exception {
        mailSocket=new ServerSocket();mailSocket.bind(new InetSocketAddress("127.0.0.1",0));mailExecutor.submit(()->{while(!mailSocket.isClosed())try{var s=mailSocket.accept();mailExecutor.submit(()->{try(s){s.setSoTimeout(3000);var input=new DataInputStream(s.getInputStream());boolean trusted=ipc.equals(input.readUTF());String server=input.readUTF(),email=input.readUTF(),text=input.readUTF();boolean accepted=trusted&&!FAIL_MAIL.contains(email);if(accepted)MAIL.add(new Mail(server,email,text));var output=new DataOutputStream(s.getOutputStream());output.writeBoolean(accepted);output.flush();}catch(Exception ignored){}});}catch(IOException ignored){break;}});
    }
    static class Server {
        final Process process;final int port;final BlockingQueue<Map<String,Object>> events=new LinkedBlockingQueue<>();final BufferedWriter input;
        Server(int port)throws Exception {this.port=port;var pb=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java.exe").toString(),"-Duser.timezone=Asia/Seoul","-cp",System.getProperty("java.class.path"),"UserMultiServer",""+port);
            var env=pb.environment();env.remove("KKIRI_MI_ROOT_PASSWORD");env.remove("KKIRI_UM_ORIGINAL_PASSWORD");env.put("KKIRI_MI_DATABASE",UserMultiSupport.db());env.put("KKIRI_MI_USER",UserMultiSupport.user());env.put("KKIRI_MI_PASSWORD",UserMultiSupport.password);env.put("KKIRI_MI_JWT",jwt);env.put("KKIRI_MI_IPC",ipc);env.put("KKIRI_MI_MAIL_PORT",""+mailSocket.getLocalPort());
            pb.redirectErrorStream(true);process=pb.start();SERVERS.add(this);input=process.outputWriter(StandardCharsets.UTF_8);
            Thread.ofVirtual().start(()->{try(var reader=process.inputReader(StandardCharsets.UTF_8)){String line;while((line=reader.readLine())!=null)if(line.startsWith("MI_EVENT ")){Map<String,Object> e=JSON.mapper.readValue(line.substring(9),Map.class);EVENTS.add(Map.of("pid",process.pid(),"port",port,"event",e));events.add(e);}}catch(Exception ignored){}});
        }
        Map<String,Object> await(String kind,int seconds)throws Exception {long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(seconds);while(System.nanoTime()<end){var e=events.poll(1,TimeUnit.SECONDS);if(e!=null){if(e.get("kind").equals("error"))throw new IllegalStateException("Child boot failed; see servers.json");if(e.get("kind").equals(kind))return (Map<String,Object>)e.get("data");}if(!process.isAlive())throw new IllegalStateException("Child exited before event: "+kind);}throw new IllegalStateException("Child event timeout: "+kind);}
        void command(String command)throws Exception {input.write(command);input.newLine();input.flush();}
        Map<String,Object> stats()throws Exception {command("STATS");return await("pool",10);}
        void stop()throws Exception {if(!process.isAlive())return;command("STOP");if(!process.waitFor(20,TimeUnit.SECONDS)){process.destroy();if(!process.waitFor(5,TimeUnit.SECONDS))process.destroyForcibly().waitFor(5,TimeUnit.SECONDS);}UserMultiSupport.check(!process.isAlive(),"Child process still running");long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);while(System.nanoTime()<end&&!poolClosedEvent(process.pid()))Thread.sleep(20);UserMultiSupport.check(poolClosedEvent(process.pid()),"Child pool close was not observed");}
    }
    static Server startServer(int port)throws Exception {var s=new Server(port);s.await("ready",90);System.out.println("READY port="+port+" pid="+s.process.pid()+" database="+UserMultiSupport.db());return s;}
    interface Work {Map<String,Object> run()throws Exception;}
    static void test(String name,Work work)throws Exception {if(!selected.isEmpty()&&!selected.contains(name))return;long start=System.nanoTime();activeCase=name;var before=counterSnapshot();var record=new LinkedHashMap<String,Object>();record.put("name",name);
        try{idle();record.put("result",work.run());idle();record.put("status","PASS");System.out.println("PASS "+name);}catch(Throwable t){record.put("error",UserMultiSupport.error(t));record.put("status","FAIL");System.out.println("FAIL "+name);}
        var after=counterSnapshot();var changes=new TreeMap<String,Object>();for(var e:after.entrySet())if(!Objects.equals(before.get(e.getKey()),e.getValue()))changes.put(e.getKey(),e.getValue());
        record.put("dbBucketChanges",changes);var http=new TreeMap<String,Long>();for(var r:REQUESTS)if(name.equals(r.get("case")))http.merge(r.get("port")+":"+r.get("status")+":"+r.get("errorCode"),1L,Long::sum);record.put("observedHttp",http);record.put("durationMs",TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start));CASES.add(record);UserMultiSupport.write(out.resolve("cases.json"),CASES);
    }
    static Map<String,Object> counterSnapshot()throws Exception {try(var c=UserMultiSupport.open()){var m=new TreeMap<String,Object>();for(var row:UserMultiSupport.rows(c,"SELECT bucket_key,request_count,failure_count,in_flight,window_started_at,generation,blocked_until FROM auth_limit_bucket"))m.put(row.get("bucket_key").toString(),row);return m;}}
    static String email(String tag){return "mi"+RUN+tag+(++seq)+"@hknu.ac.kr";}
    static String ip(){return "198.18."+(seq/200)+"."+(++seq%200+1);}
    static String alias(String e,int i){return switch(i%4){case 0->e;case 1->e.toUpperCase(Locale.ROOT).replace("@HKNU.AC.KR","@hknu.ac.kr");case 2->e.replaceFirst("i","í");default->e.replaceFirst("i","i\u0301");};}
    static String account(String tag)throws Exception {String e=email(tag);try(var c=UserMultiSupport.open()){UserMultiSupport.exec(c,"INSERT INTO user(password,school_email,email_verified,role,status,created_at,updated_at) VALUES(?,?,true,'USER','ACTIVE',NOW(),NOW())",new BCryptPasswordEncoder().encode(PASSWORD),e);}return e;}
    static String subject(String email)throws Exception {try(var c=UserMultiSupport.open()){return "user:"+UserMultiSupport.number(c,"SELECT user_id FROM user WHERE school_email=?",email);}}
    static String verificationSubject(String email)throws Exception {try(var c=UserMultiSupport.open()){return "verification-email:"+UserMultiSupport.number(c,"SELECT identity_id FROM auth_email_identity WHERE verification_email=?",email);}}
    static Map<String,Object> bucket(String scope,String subject)throws Exception {try(var c=UserMultiSupport.open()){return UserMultiSupport.rows(c,"SELECT request_count,failure_count,in_flight,generation,blocked_until FROM auth_limit_bucket WHERE bucket_key=?",UserMultiSupport.key(scope,subject)).getFirst();}}
    static void field(Map<String,Object> row,String key,long expected){UserMultiSupport.check(((Number)row.get(key)).longValue()==expected,"Counter mismatch "+key+" expected "+expected);}
    static Reply post(int port,String path,Object body,String ip)throws Exception {var req=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/auth/"+path)).timeout(Duration.ofSeconds(20)).header("Content-Type","application/json").header("X-Forwarded-For",ip).POST(HttpRequest.BodyPublishers.ofString(JSON.mapper.writeValueAsString(body))).build();return send(port,path,req);}
    static Reply getMe(int port,String token)throws Exception {return send(port,"me",HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/auth/me")).header("Authorization","Bearer "+token).GET().build());}
    static Reply send(int port,String path,HttpRequest request)throws Exception {long start=System.nanoTime();var r=HTTP.send(request,HttpResponse.BodyHandlers.ofString());var node=JSON.mapper.readTree(r.body());var reply=new Reply(port,r.statusCode(),node,TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start),r.headers().firstValue("Retry-After").orElse(""));
        // Redact all response data: JWT, verification token and account data must never persist.
        var audit=new LinkedHashMap<String,Object>();audit.put("sequence",REQUEST_SEQUENCE.incrementAndGet());audit.put("case",activeCase);audit.put("requestId",r.headers().firstValue("X-Request-ID").orElse(""));audit.put("validationId",request.headers().firstValue("X-User-Validation").orElse(""));audit.put("port",port);audit.put("method",request.method());audit.put("path",path);audit.put("status",r.statusCode());audit.put("success",node.path("success").asBoolean());audit.put("errorCode",reply.code());audit.put("message",node.path("message").asString(""));audit.put("retryAfter",reply.retry);audit.put("millis",reply.millis);REQUESTS.add(audit);return reply;}
    static Reply login(int p,String e,String pass,String ip)throws Exception{return post(p,"login",Map.of("email",e,"password",pass),ip);}
    static Reply verify(int p,String e,String code,String ip)throws Exception{return post(p,"email-verifications/verify",Map.of("email",e,"code",code,"purpose","SIGNUP"),ip);}
    static Reply sendCode(int p,String e,String ip)throws Exception{return post(p,"email-verifications/send",Map.of("email",e,"purpose","SIGNUP"),ip);}
    static Reply reset(int p,String e,String ip)throws Exception{return post(p,"password-reset/request",Map.of("email",e),ip);}
    static Reply confirm(int p,String token)throws Exception{return post(p,"password-reset/confirm",Map.of("resetToken",token,"newPassword",NEW_PASSWORD,"newPasswordConfirm",NEW_PASSWORD),ip());}
    static void expect(Reply r,int status,String code){UserMultiSupport.check(r.status==status&&r.code().equals(code),"Unexpected HTTP result "+r.status+"/"+r.code()+" expected "+status+"/"+code);if(status==429)UserMultiSupport.check(!r.retry.isBlank()&&Long.parseLong(r.retry)>0,"Missing Retry-After");}
    interface Request {Reply run(int i,int port)throws Exception;}
    static List<Reply> concurrent(int n,Request f)throws Exception {try(var ex=Executors.newFixedThreadPool(n)){var go=new CountDownLatch(1);var fs=new ArrayList<Future<Reply>>();for(int i=0;i<n;i++){final int j=i;fs.add(ex.submit(()->{go.await();return f.run(j,j%2==0?8081:8082);}));}go.countDown();var rs=new ArrayList<Reply>();for(var future:fs)rs.add(future.get(35,TimeUnit.SECONDS));return rs;}}
    static long count(List<Reply> rs,int status,String code){return rs.stream().filter(r->r.status==status&&r.code().equals(code)).count();}
    static Map<String,Object> distribution(List<Reply> rs){var m=new TreeMap<String,Long>();for(var r:rs)m.merge(r.port+":"+r.status+":"+r.code(),1L,Long::sum);return Map.of("requests",rs.size(),"byServerStatusCode",m);}
    static List<Mail> mails(String email){return MAIL.stream().filter(m->m.email.equals(email)).toList();}
    static String code(String email){return mails(email).getLast().text.split("인증번호: ")[1].split("\n")[0];}
    static String token(String email){return mails(email).getLast().text.split("토큰: ")[1].split("\n")[0];}
    static long query(String sql,Object...args)throws Exception {try(var c=UserMultiSupport.open()){return UserMultiSupport.number(c,sql,args);}}
    static String setupCode(String tag)throws Exception {String e=email(tag);expect(sendCode(8081,e,ip()),200,"");return e;}
    static final AtomicInteger auditSequence=new AtomicInteger();
    static final BCryptPasswordEncoder encoder=new BCryptPasswordEncoder();
    static String encodedPassword;static long dorm,f1,f2,m1,m2;static Member admin;
    record Member(long id,String email,String jwt){}
    static void runUserScenarios()throws Exception {
        basics();
        test("two-real-jvms-shared-db-and-jwt",()->{Member u=member(true);expect(api(8082,"GET","/api/users/me",null,u.jwt),200,"");UserMultiSupport.check(a.process.pid()!=b.process.pid(),"JVMs are not independent");return Map.of("aPid",a.process.pid(),"bPid",b.process.pid(),"sharedJwt",true,"sameIsolatedDb",UserMultiSupport.db());});
        for(boolean reset:new boolean[]{false,true})for(String state:List.of("SUSPENDED","WITHDRAWN"))for(boolean statusFirst:new boolean[]{false,true}){
            final boolean r=reset,s=statusFirst;final String st=state;test((r?"reset":"password")+"-"+st.toLowerCase()+"-"+(s?"status-first":"password-first"),()->accountRace(r,st,s));
        }
        for(String state:List.of("SUSPENDED","WITHDRAWN"))test("inactive-"+state.toLowerCase()+"-jwt-login-reset",()->inactive(state));
        test("principal-ownership-admin-and-invalid-jwt",UserMultiInstance::ownership);
        test("same-user-profile-partial-updates-across-servers",UserMultiInstance::partialProfile);
        test("nickname-unique-conflict-and-full-rollback",UserMultiInstance::nicknameConflict);
        test("faculty-major-null-and-invalid-request-rollback",UserMultiInstance::academic);
        test("physical-unique-fk-not-null",UserMultiInstance::physicalConstraints);
        test("matching-after-profile-commit-and-cross-instance-update",UserMultiInstance::matchingUpdate);
        test("matching-failure-keeps-profile-and-http-read-repairs",UserMultiInstance::matchingFailure);
        test("bulk-matching-batches-lock-wait-latency-and-recovery",UserMultiInstance::bulkMatching);
        test("row-lock-wait-and-independent-user-http",UserMultiInstance::rowWait);
        test("profile-lock-timeout-http409-full-rollback",()->timeout(false));
        test("reset-token-lock-timeout-http409-full-rollback",()->timeout(true));
        test("forced-deadlock-with-real-http-transaction",UserMultiInstance::deadlockHttp);
        test("same-user-password-multiple-http-requests",UserMultiInstance::samePassword);
        test("pool-exhaustion-and-recovery",UserMultiInstance::exhaustion);
        test("server-stop-restart-and-cross-server-jwt",UserMultiInstance::restart);
        test("repeated-profile-http-and-pool-return",UserMultiInstance::stressProfiles);
        if(selected.contains("jwt-infrastructure-security-regression"))test("jwt-infrastructure-security-regression",UserMultiInstance::jwtSecurityRegression);
        UserMultiSupport.check(CASES.size()==(selected.isEmpty()?27:selected.size()),"Scenario selection mismatch");
    }
    static void basics()throws Exception {
        encodedPassword=encoder.encode(PASSWORD);
        try(var c=UserMultiSupport.open()){
            UserMultiSupport.exec(c,"INSERT INTO dormitory(dormitory_code,dormitory_name,is_active) VALUES('UM','UM dormitory',true)");dorm=UserMultiSupport.number(c,"SELECT dormitory_id FROM dormitory");
            UserMultiSupport.exec(c,"INSERT INTO faculty(faculty_name,is_active) VALUES('UM faculty A',true),('UM faculty B',true)");f1=UserMultiSupport.number(c,"SELECT faculty_id FROM faculty WHERE faculty_name='UM faculty A'");f2=UserMultiSupport.number(c,"SELECT faculty_id FROM faculty WHERE faculty_name='UM faculty B'");
            UserMultiSupport.exec(c,"INSERT INTO major(faculty_id,major_name,is_active) VALUES(?,'UM major A',true),(?,'UM major B',true)",f1,f2);m1=UserMultiSupport.number(c,"SELECT major_id FROM major WHERE faculty_id=?",f1);m2=UserMultiSupport.number(c,"SELECT major_id FROM major WHERE faculty_id=?",f2);
        }admin=createMember(true,"ADMIN");
    }
    static Member member(boolean login)throws Exception{return createMember(login,"USER");}
    static Member createMember(boolean authenticate,String role)throws Exception {
        String e=email("user"),nickname=nickname();long id;
        try(var c=UserMultiSupport.open()){
            UserMultiSupport.exec(c,"INSERT INTO user(password,school_email,email_verified,role,status,created_at,updated_at) VALUES(?,?,true,?,'ACTIVE',NOW(),NOW())",encodedPassword,e,role);id=UserMultiSupport.number(c,"SELECT user_id FROM user WHERE school_email=?",e);
            UserMultiSupport.exec(c,"INSERT INTO user_profile(user_id,nickname,gender,dormitory_id,faculty_id,major_id,birth_year,admission_year,grade,mbti,created_at,updated_at) VALUES(?,?,'남자',?,?,?,2002,'24','3학년','INTJ',NOW(),NOW())",id,nickname,dorm,f1,m1);
        }
        String token="";if(authenticate){var r=login(8081,e,PASSWORD,ip());expect(r,200,"");token=r.json.path("data").path("accessToken").asString();UserMultiSupport.check(!token.isBlank(),"JWT absent");}return new Member(id,e,token);
    }
    static String nickname(){return "U"+String.format(Locale.ROOT,"%07d",++seq);}
    static Reply api(int port,String method,String path,Object body,String token)throws Exception {
        var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(25)).header("Content-Type","application/json").header("X-User-Validation","UQ"+auditSequence.incrementAndGet()).header("X-Forwarded-For",ip());
        if(token!=null&&!token.isEmpty())builder.header("Authorization","Bearer "+token);
        builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(JSON.mapper.writeValueAsString(body)));return send(port,path,builder.build());
    }
    static Reply profile(int port,Member u,Object body)throws Exception{return api(port,"PATCH","/api/users/me",body,u.jwt);}
    static Reply password(int port,Member u)throws Exception{return api(port,"PATCH","/api/users/me/password",Map.of("currentPassword",PASSWORD,"newPassword",NEW_PASSWORD,"newPasswordConfirm",NEW_PASSWORD),u.jwt);}
    static Reply status(int port,Member u,String state)throws Exception{return api(port,"PATCH","/api/admin/members/"+u.id+"/status",Map.of("status",state),admin.jwt);}
    static String issueToken(Member u)throws Exception {expect(reset(8081,u.email,ip()),200,"");return token(u.email);}
    static Map<String,Object> profileRow(Member u)throws Exception{try(var c=UserMultiSupport.open()){return UserMultiSupport.rows(c,"SELECT nickname,grade,faculty_id,major_id,mbti FROM user_profile WHERE user_id=?",u.id).getFirst();}}
    static Map<String,Object> accountRow(Member u)throws Exception {try(var c=UserMultiSupport.open()){var row=UserMultiSupport.rows(c,"SELECT status,password FROM user WHERE user_id=?",u.id).getFirst();return Map.of("status",row.get("status"),"passwordOld",encoder.matches(PASSWORD,row.get("password").toString()),"passwordNew",encoder.matches(NEW_PASSWORD,row.get("password").toString()));}}
    static void checkAccount(Member u,String state,boolean changed)throws Exception{var row=accountRow(u);UserMultiSupport.check(row.get("status").equals(state)&&row.get(changed?"passwordNew":"passwordOld").equals(true),"Wrong committed password/account status");}
    static long used(Member u)throws Exception{return query("SELECT COUNT(*) FROM password_reset_token WHERE user_id=? AND used_at IS NOT NULL",u.id);}
    static void arm(Server s,String kind,String path)throws Exception{s.command("ARM_"+kind+" "+path);s.await("armed",10);}
    static void release(Server s)throws Exception{s.command("RELEASE_GATE");s.await("gateReleased",10);}
    static void blocked(Future<?> f)throws Exception{try{f.get(180,TimeUnit.MILLISECONDS);throw new AssertionError("Competing HTTP did not wait");}catch(TimeoutException expected){}}
    static Map<String,Object> accountRace(boolean reset,String state,boolean statusFirst)throws Exception {
        Member u=member(true);String token=reset?issueToken(u):"";Server firstServer=statusFirst?b:a;
        String path=statusFirst?"/api/admin/members/"+u.id+"/status":reset?"/api/auth/password-reset/confirm":"/api/users/me/password";
        arm(firstServer,"ACCOUNT",path);
        try(var ex=Executors.newFixedThreadPool(2)){
            var first=ex.submit(()->statusFirst?status(8082,u,state):reset?confirm(8081,token):password(8081,u));firstServer.await("gated",10);
            var second=ex.submit(()->statusFirst?(reset?confirm(8081,token):password(8081,u)):status(8082,u,state));
            try{blocked(second);}finally{release(firstServer);}
            expect(first.get(20,TimeUnit.SECONDS),200,"");var r=second.get(20,TimeUnit.SECONDS);expect(r,statusFirst?(reset?400:401):200,statusFirst?(reset?"PASSWORD_RESET_ACCOUNT_INACTIVE":"UNAUTHORIZED"):"");
            checkAccount(u,state,!statusFirst);if(reset)UserMultiSupport.check(used(u)==(statusFirst?0:1),"Token use not atomic");
            return Map.of("reset",reset,"statusFirst",statusFirst,"waitingObserved",true,"db",accountRow(u),"usedTokens",used(u));
        }finally{release(firstServer);}
    }
    static Map<String,Object> inactive(String state)throws Exception {
        Member u=member(true);String t=issueToken(u);expect(status(8082,u,state),200,"");
        for(int p:List.of(8081,8082)){expect(api(p,"GET","/api/users/me",null,u.jwt),401,"UNAUTHORIZED");expect(profile(p,u,Map.of("grade","4학년")),401,"UNAUTHORIZED");expect(login(p,u.email,PASSWORD,ip()),401,"UNAUTHORIZED");expect(confirm(p,t),400,"PASSWORD_RESET_ACCOUNT_INACTIVE");}
        checkAccount(u,state,false);UserMultiSupport.check(used(u)==0,"Inactive token consumed");
        Member unknownResponse=member(false);expect(status(8082,unknownResponse,state),200,"");int mailCount=MAIL.size();var r=reset(8081,unknownResponse.email,ip());expect(r,200,"");UserMultiSupport.check(r.json.path("data").path("sent").asBoolean()&&MAIL.size()==mailCount&&query("SELECT COUNT(*) FROM password_reset_token WHERE user_id=?",unknownResponse.id)==0,"Inactive request enumeration/mail contract changed");
        return Map.of("state",state,"oldJwtBlockedOnBothServers",true,"loginBlocked",true,"confirmBlocked",true,"inactiveRequestSentTrueWithoutMailOrToken",true);
    }
    static Map<String,Object> ownership()throws Exception {
        Member u=member(true),v=member(true);var before=profileRow(v);
        var own=api(8082,"GET","/api/users/me",null,u.jwt);expect(own,200,"");UserMultiSupport.check(own.json.path("data").path("id").asLong()==u.id,"JWT Principal not used");
        var forged=profile(8081,u,Map.of("userId",v.id,"id",v.id,"nickname",nickname()));
        UserMultiSupport.check(forged.status==200||forged.status==400,"Unexpected unknown-field policy");
        if(forged.status==200)UserMultiSupport.check(forged.json.path("data").path("id").asLong()==u.id,"Request ID overrode Principal");
        UserMultiSupport.check(before.equals(profileRow(v)),"Other user modified via request ID");
        expect(api(8082,"PATCH","/api/users/"+v.id,Map.of("grade","4학년"),u.jwt),403,"FORBIDDEN");
        expect(api(8082,"PATCH","/api/admin/members/"+v.id+"/status",Map.of("status","SUSPENDED"),u.jwt),403,"FORBIDDEN");
        expect(api(8081,"GET","/api/users/me",null,""),401,"UNAUTHORIZED");expect(api(8082,"GET","/api/users/me",null,u.jwt+"x"),401,"UNAUTHORIZED");checkAccount(v,"ACTIVE",false);
        return Map.of("requestIdIgnoredOrRejected",true,"unknownFieldHttp",forged.status,"otherProfileUnchanged",true,"userAdminForbidden",true,"invalidJwtBlocked",true);
    }
    static Map<String,Object> partialProfile()throws Exception {
        Member u=member(true);arm(a,"PROFILE","/api/users/me");try(var ex=Executors.newFixedThreadPool(2)){
            var first=ex.submit(()->profile(8081,u,Map.of("grade","4학년")));a.await("gated",10);var second=ex.submit(()->profile(8082,u,Map.of("mbtiEnergy","E")));try{blocked(second);}finally{release(a);}expect(first.get(15,TimeUnit.SECONDS),200,"");expect(second.get(15,TimeUnit.SECONDS),200,"");
            var row=profileRow(u);UserMultiSupport.check(row.get("grade").equals("4학년")&&row.get("mbti").equals("ENTJ"),"Lost same-user partial update");return row;
        }finally{release(a);}
    }
    static Map<String,Object> nicknameConflict()throws Exception {
        Member u=member(true),v=member(true);var before=profileRow(v);String n=nickname();arm(a,"PROFILE","/api/users/me");
        try(var ex=Executors.newFixedThreadPool(2)){var first=ex.submit(()->profile(8081,u,Map.of("nickname",n,"grade","4학년")));a.await("gated",10);var second=ex.submit(()->profile(8082,v,Map.of("nickname",n.toLowerCase(Locale.ROOT),"grade","5학년","faculty","UM faculty B","major","UM major B")));try{blocked(second);}finally{release(a);}expect(first.get(15,TimeUnit.SECONDS),200,"");expect(second.get(15,TimeUnit.SECONDS),400,"INVALID_REQUEST");UserMultiSupport.check(before.equals(profileRow(v)),"Nickname loser partially committed");return Map.of("winner",1,"invalidRequest",1,"loserAllFieldsRestored",true,"caseEquivalentUnique",true);}finally{release(a);}
    }
    static Map<String,Object> academic()throws Exception {
        Member u=member(true);var original=profileRow(u);var nulls=new LinkedHashMap<String,Object>();nulls.put("faculty",null);nulls.put("major",null);expect(profile(8081,u,nulls),200,"");UserMultiSupport.check(original.equals(profileRow(u)),"NULL did not preserve fields");
        expect(profile(8082,u,Map.of("nickname",nickname(),"grade","4학년","faculty","UM faculty B")),400,"INVALID_REQUEST");UserMultiSupport.check(original.equals(profileRow(u)),"Invalid affiliation partially changed");
        var clear=profile(8081,u,Map.of("faculty","UM faculty B","major","세부전공 없음"));expect(clear,200,"");UserMultiSupport.check(clear.json.path("data").path("major").isNull()&&profileRow(u).get("major_id")==null,"Explicit major clear not SQL/JSON null");
        expect(profile(8082,u,Map.of("major","UM major B")),200,"");var before=profileRow(u);expect(profile(8081,u,Map.of("nickname",nickname(),"grade","4학년","major","UM major A")),400,"INVALID_REQUEST");UserMultiSupport.check(before.equals(profileRow(u)),"Cross-faculty change committed");expect(profile(8082,u,Map.of("grade","9학년")),400,"VALIDATION_ERROR");return Map.of("nullPreserves",true,"explicitClearSqlAndJsonNull",true,"validAffiliation",true,"invalidAllFieldsRollback",true);
    }
    static Map<String,Object> physicalConstraints()throws Exception {
        Member u=member(false),v=member(false);int duplicate=0,fk=0,notNull=0;
        try(var c=UserMultiSupport.open()){try{UserMultiSupport.exec(c,"UPDATE user_profile SET nickname=(SELECT n FROM (SELECT nickname n FROM user_profile WHERE user_id=?) q) WHERE user_id=?",u.id,v.id);}catch(SQLException e){duplicate=e.getErrorCode();}try{UserMultiSupport.exec(c,"UPDATE user_profile SET major_id=9223372036854775807 WHERE user_id=?",v.id);}catch(SQLException e){fk=e.getErrorCode();}try{UserMultiSupport.exec(c,"UPDATE user_profile SET faculty_id=NULL WHERE user_id=?",v.id);}catch(SQLException e){notNull=e.getErrorCode();}}
        UserMultiSupport.check(duplicate==1062&&fk==1452&&notNull==1048,"Physical constraints not enforced");return Map.of("unique",duplicate,"fk",fk,"notNull",notNull,"method","direct JDBC, HTTP business validation tested separately");
    }
    record Pair(Member requester,Member target){}
    static Pair matchingFixture()throws Exception {Member u=member(true),v=member(true);survey(v);preference(u);var r=detail(8081,u,v);expect(r,200,"");UserMultiSupport.check(r.json.path("data").path("matchScore").asInt(-1)==100,"Matching fixture not 100");return new Pair(u,v);}
    static void survey(Member u)throws Exception {try(var c=UserMultiSupport.open()){
        UserMultiSupport.exec(c,"INSERT INTO profile_survey(user_id,sleep_time,wake_time,sleep_talking,snoring,teeth_grinding,sleep_sensitivity,indoor_eating,smoking,lighting_preference,phone_call,media_sound,shower_time,shower_duration,cleaning_frequency,cleaning_style,meal,roommate_interaction,temperature_preference,scent_product_use,desktop_use,shared_items,completed,created_at,updated_at) VALUES(?,'22시 이전','07시 이전','없음','없음','없음','보통','가능','비흡연','소등','가능','이어폰','아침','10분','매일','같이 청소','따로','적당히 대화','보통','안함','안함','각자 사용',true,NOW(),NOW())",u.id);
    }}
    static void preference(Member u)throws Exception {try(var c=UserMultiSupport.open()){
        UserMultiSupport.exec(c,"INSERT INTO roommate_preference(user_id,department_preference,sleep_talking_allowance,snoring_allowance,teeth_grinding_allowance,smoking_preference,lighting_preference,shower_time_preference,shower_duration_preference,cleaning_style_preference,interaction_preference,meal_preference,shared_items_preference,temperature_preference,completed,created_at,updated_at) VALUES(?,'상관없음','상관없음','상관없음','상관없음','상관없음','상관없음','상관없음','상관없음','상관없음','상관없음','상관없음','상관없음','상관없음',true,NOW(),NOW())",u.id);
        long id=UserMultiSupport.number(c,"SELECT preference_id FROM roommate_preference WHERE user_id=?",u.id);
        for(var code:com.kkirikkiri.survey.entity.PreferenceCriterionCode.values()){UserMultiSupport.exec(c,"INSERT INTO preference_criterion(preference_id,criterion_code,weight) VALUES(?,?,1)",id,code.name());if(code.isMultiSelect())UserMultiSupport.exec(c,"INSERT INTO roommate_preference_option(preference_id,criterion_code,option_value) VALUES(?,?,?)",id,code.name(),code.name().equals("GRADE")?"3학년":"상관없음");}
    }}
    static Reply detail(int p,Member requester,Member target)throws Exception{return api(p,"GET","/api/roommates/"+target.id,null,requester.jwt);}
    static int score(Member requester,Member target)throws Exception{return (int)query("SELECT match_score FROM roommate_match WHERE requester_user_id=? AND target_user_id=?",requester.id,target.id);}
    static Map<String,Object> matchingUpdate()throws Exception {Pair pair=matchingFixture();expect(profile(8081,pair.target,Map.of("grade","4학년")),200,"");idle();UserMultiSupport.check(score(pair.requester,pair.target)==0,"After-commit score stale");expect(detail(8082,pair.requester,pair.target),200,"");expect(profile(8082,pair.target,Map.of("grade","3학년")),200,"");idle();UserMultiSupport.check(score(pair.requester,pair.target)==100,"Cross-instance matching did not recover");return Map.of("scores",List.of(100,0,100),"afterCommitVerifiedBeforeReadRepair",true);}
    static Map<String,Object> matchingFailure()throws Exception {Pair pair=matchingFixture();int begin=EVENTS.size();try(var c=UserMultiSupport.open()){c.setAutoCommit(false);UserMultiSupport.rows(c,"SELECT user_id FROM user WHERE user_id=? FOR UPDATE",pair.requester.id);var r=profile(8081,pair.target,Map.of("grade","4학년"));expect(r,200,"");UserMultiSupport.check(profileRow(pair.target).get("grade").equals("4학년"),"Matching failure rolled back saved input");waitMatchingFailure(begin);UserMultiSupport.check(score(pair.requester,pair.target)==100,"Expected old score until repair");c.rollback();}
        var repaired=detail(8082,pair.requester,pair.target);expect(repaired,200,"");UserMultiSupport.check(repaired.json.path("data").path("matchScore").asInt(-1)==0&&score(pair.requester,pair.target)==0,"HTTP read did not repair failed matching");return Map.of("profileHttp",200,"profileCommitted",true,"actualMysqlTimeout",1205,"readRepairScore",0);}
    static void waitMatchingFailure(int begin)throws Exception {long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(14);while(System.nanoTime()<end){long count=vendorEvents(begin,1205,true);var st=a.stats();if(count>=3&&((Number)st.get("matchingActive")).intValue()==0)return;Thread.sleep(80);}throw new AssertionError("Matching did not exhaust real 1205 retries");}
    static Map<String,Object> bulkMatching()throws Exception {Member target=member(true);survey(target);List<Member> requesters=new ArrayList<>();for(int i=0;i<120;i++){Member u=member(false);preference(u);requesters.add(u);}Member independent=member(true);int eventStart=EVENTS.size();
        arm(a,"MATCHING","");try{var r=profile(8081,target,Map.of("grade","4학년"));expect(r,200,"");a.await("gated",12);var independentResult=profile(8082,independent,Map.of("mbtiEnergy","E"));expect(independentResult,200,"");UserMultiSupport.check(r.millis<5000&&independentResult.millis<5000,"Profile request blocked by async bulk work");}finally{release(a);}idle();
        for(Member requester:requesters)UserMultiSupport.check(score(requester,target)==0,"Bulk target update missing");expect(profile(8082,target,Map.of("grade","3학년")),200,"");idle();for(Member requester:requesters)UserMultiSupport.check(score(requester,target)==100,"Bulk cross-instance recovery missing");
        long max=eventsSince(eventStart).stream().filter(e->((Map<?,?>)e.get("event")).get("kind").equals("matchingBatch")).map(e->(Map<?,?>)((Map<?,?>)e.get("event")).get("data")).mapToLong(d->((Number)d.get("users")).longValue()).max().orElse(0);UserMultiSupport.check(max>1&&max<=101,"Matching batch lock set unbounded");return Map.of("requesters",120,"maxObservedUserLocksPerBatch",max,"allScoresChangedAndRecovered",true,"unrelatedHttpSucceededWhileBatchHeld",true);}
    static Map<String,Object> rowWait()throws Exception {Member u=member(true),other=member(true);try(var c=UserMultiSupport.open();var ex=Executors.newSingleThreadExecutor()){c.setAutoCommit(false);UserMultiSupport.rows(c,"SELECT user_id FROM user WHERE user_id=? FOR UPDATE",u.id);var f=ex.submit(()->profile(8081,u,Map.of("grade","4학년")));try{blocked(f);expect(profile(8082,other,Map.of("grade","5학년")),200,"");}finally{c.rollback();}expect(f.get(15,TimeUnit.SECONDS),200,"");UserMultiSupport.check(profileRow(u).get("grade").equals("4학년"),"Waiting user not saved");return Map.of("waitingObserved",true,"unrelatedCommittedBeforeRelease",true);}}
    static Map<String,Object> timeout(boolean reset)throws Exception {Member u=member(true);String t=reset?issueToken(u):"";var before=profileRow(u);int eventStart=EVENTS.size();try(var c=UserMultiSupport.open()){c.setAutoCommit(false);UserMultiSupport.rows(c,reset?"SELECT password_reset_token_id FROM password_reset_token WHERE user_id=? FOR UPDATE":"SELECT user_profile_id FROM user_profile WHERE user_id=? FOR UPDATE",u.id);var r=reset?confirm(8082,t):profile(8081,u,Map.of("nickname",nickname(),"grade","5학년","faculty","UM faculty B","major","UM major B"));expect(r,409,"ACCOUNT_UPDATE_CONFLICT");UserMultiSupport.check(vendorEvents(eventStart,1205,false)>0,"No real MySQL 1205 evidence");checkAccount(u,"ACTIVE",false);UserMultiSupport.check(before.equals(profileRow(u))&&used(u)==0,"Timeout partial commit");c.rollback();}if(reset)expect(confirm(8081,t),200,"");else expect(profile(8082,u,Map.of("grade","4학년")),200,"");return Map.of("http",409,"code","ACCOUNT_UPDATE_CONFLICT","vendorCode",1205,"fullRollback",true,"recovered",true);}
    static Map<String,Object> deadlockHttp()throws Exception {Member u=member(true);String t=issueToken(u);int eventStart=EVENTS.size();List<Member> weight=new ArrayList<>();for(int i=0;i<20;i++)weight.add(member(false));try(var c=UserMultiSupport.open();var ex=Executors.newSingleThreadExecutor()){c.setAutoCommit(false);for(Member w:weight)UserMultiSupport.exec(c,"UPDATE user_profile SET grade='4학년' WHERE user_id=?",w.id);UserMultiSupport.rows(c,"SELECT password_reset_token_id FROM password_reset_token WHERE user_id=? FOR UPDATE",u.id);var f=ex.submit(()->confirm(8082,t));boolean locked=false;long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);while(System.nanoTime()<end){try(var probe=UserMultiSupport.open()){probe.setAutoCommit(false);try{UserMultiSupport.rows(probe,"SELECT user_id FROM user WHERE user_id=? FOR UPDATE NOWAIT",u.id);}catch(SQLException e){if(e.getErrorCode()==3572)locked=true;else throw e;}finally{probe.rollback();}}if(locked)break;Thread.sleep(10);}UserMultiSupport.check(locked,"HTTP USER lock not observed");int outside=0;try{UserMultiSupport.exec(c,"UPDATE user SET updated_at=NOW() WHERE user_id=?",u.id);}catch(SQLException e){outside=e.getErrorCode();UserMultiSupport.check(outside==1213,"Unexpected cycle error");}finally{c.rollback();}var r=f.get(15,TimeUnit.SECONDS);UserMultiSupport.check(outside==0,"Expected lighter HTTP transaction to be chosen as victim");expect(r,409,"ACCOUNT_UPDATE_CONFLICT");UserMultiSupport.check(vendorEvents(eventStart,1213,false)>0,"HTTP 1213 not observed");checkAccount(u,"ACTIVE",false);UserMultiSupport.check(used(u)==0,"Deadlock victim consumed token");expect(confirm(8081,t),200,"");for(Member w:weight)UserMultiSupport.check(profileRow(w).get("grade").equals("3학년"),"Outside rollback leaked changes");return Map.of("httpVictim",409,"vendorCode",1213,"tokenAndPasswordRolledBack",true,"externalChangesRolledBack",20,"retrySucceeded",true);}}
    static Map<String,Object> samePassword()throws Exception {Member u=member(true);var rs=concurrent(12,(i,p)->password(p,u));UserMultiSupport.check(count(rs,200,"")==1&&count(rs,400,"INVALID_REQUEST")==11,"Same current password had multiple winners/server errors");checkAccount(u,"ACTIVE",true);return distribution(rs);}
    static Map<String,Object> exhaustion()throws Exception {
        Member u=member(true);var before=profileRow(u);var control=api(8081,"GET","/api/users/me",null,u.jwt);expect(control,200,"");
        int eventStart=EVENTS.size();a.command("HOLD_POOL");var held=a.await("heldPool",10);Reply r,other;
        try{r=api(8081,"GET","/api/users/me",null,u.jwt);other=api(8082,"GET","/api/users/me",null,u.jwt);}
        finally{a.command("RELEASE_POOL");a.await("releasedPool",10);}
        var recovered=api(8081,"GET","/api/users/me",null,u.jwt);expect(other,200,"");expect(recovered,200,"");idle();
        UserMultiSupport.check(before.equals(profileRow(u)),"Pool failure changed profile");checkAccount(u,"ACTIVE",false);
        var result=Map.<String,Object>of("beforeHttp",control.status,"heldPool",held,"duringHttp",r.status,"duringErrorCode",r.code(),"otherServerHttp",other.status,"afterSameJwtHttp",recovered.status,"dbUnchanged",true,"aAfter",a.stats(),"bAfter",b.stats());
        UserMultiSupport.write(out.resolve("pool-exhaustion-observations.json"),result);
        // Keep the original server-error assertion after collecting recovery evidence.
        expect(r,500,"INTERNAL_SERVER_ERROR");
        assertSafeFailure(r);
        var logs=EVENTS.subList(eventStart,EVENTS.size()).stream().filter(e->((Map<?,?>)e.get("event")).get("kind").equals("authenticationInfrastructureLog")).toList();
        UserMultiSupport.check(logs.size()==1,"Expected one safe infrastructure log");
        UserMultiSupport.check(!AuthMysqlConcurrency.json(logs).contains(u.jwt),"JWT leaked in infrastructure log");
        UserMultiSupport.write(out.resolve("authentication-infrastructure-logs.json"),logs);
        return result;
    }
    static void assertSafeFailure(Reply r)throws Exception {
        String body=JSON.mapper.writeValueAsString(r.json);
        for(String secret:List.of(jwt,PASSWORD,NEW_PASSWORD,UserMultiSupport.password,"jdbc:mysql:","SQLTransientConnectionException","HikariPool","stackTrace"))UserMultiSupport.check(!body.contains(secret),"Sensitive server error response");
        UserMultiSupport.check(r.json.path("data").isNull()||r.json.path("data").isMissingNode(),"Server failure exposed data");
    }
    static String signedJwt(long id,int seconds)throws Exception {
        var token=new com.nimbusds.jwt.SignedJWT(new com.nimbusds.jose.JWSHeader(com.nimbusds.jose.JWSAlgorithm.HS256),new com.nimbusds.jwt.JWTClaimsSet.Builder().claim("userId",id).claim("role","USER").expirationTime(java.util.Date.from(Instant.now().plusSeconds(seconds))).build());
        token.sign(new com.nimbusds.jose.crypto.MACSigner(Base64.getDecoder().decode(jwt)));return token.serialize();
    }
    static Map<String,Object> jwtSecurityRegression()throws Exception {
        Member u=member(true);long missing=query("SELECT MAX(user_id) FROM user")+100000;
        for(int p:List.of(8081,8082)){
            expect(api(p,"GET","/api/users/me",null,u.jwt),200,"");
            expect(api(p,"GET","/api/users/me",null,signedJwt(u.id,-60)),401,"UNAUTHORIZED");
            expect(api(p,"GET","/api/users/me",null,u.jwt+"x"),401,"UNAUTHORIZED");
            expect(api(p,"GET","/api/users/me",null,signedJwt(missing,3600)),401,"UNAUTHORIZED");
            expect(api(p,"GET","/api/admin/members",null,u.jwt),403,"FORBIDDEN");
            expect(api(p,"GET","/error",null,""),401,"UNAUTHORIZED");
            expect(api(p,"GET","/error",null,u.jwt),403,"FORBIDDEN");
            var errorRequest=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+p+"/api/users/me")).header("X-Validation-Container-Error",ipc).GET().build();
            var errorReply=send(p,"/api/users/me",errorRequest);UserMultiSupport.check(errorReply.status==500,"Container ERROR status overwritten");assertSafeFailure(errorReply);
        }
        for(String state:List.of("SUSPENDED","WITHDRAWN")){Member inactive=member(true);expect(status(8082,inactive,state),200,"");for(int p:List.of(8081,8082))expect(api(p,"GET","/api/users/me",null,inactive.jwt),401,"UNAUTHORIZED");}
        return Map.ofEntries(Map.entry("normal",200),Map.entry("expired",401),Map.entry("tampered",401),Map.entry("missingAccount",401),Map.entry("suspended",401),Map.entry("withdrawn",401),Map.entry("userAdmin",403),Map.entry("ordinaryErrorAnonymous",401),Map.entry("ordinaryErrorAuthenticated",403),Map.entry("actualContainerErrorDispatch",500),Map.entry("bothServersVerified",true));
    }
    static Map<String,Object> restart()throws Exception {Member u=member(true);expect(password(8081,u),200,"");long old=a.process.pid();a.stop();expect(api(8082,"GET","/api/users/me",null,u.jwt),200,"");expect(profile(8082,u,Map.of("grade","4학년")),200,"");a=startServer(8081);expect(api(8081,"GET","/api/users/me",null,u.jwt),200,"");checkAccount(u,"ACTIVE",true);UserMultiSupport.check(a.process.pid()!=old&&profileRow(u).get("grade").equals("4학년"),"Restart lost shared state");return Map.of("oldPid",old,"newPid",a.process.pid(),"bAlive",b.process.isAlive(),"oldJwtWorksOnRestartedServer",true,"dbChangesRetained",true);}
    static Map<String,Object> stressProfiles()throws Exception {List<Member> members=new ArrayList<>();for(int i=0;i<16;i++)members.add(member(true));for(int round=0;round<3;round++){String grade=round%2==0?"4학년":"5학년";var rs=concurrent(16,(i,p)->profile(p,members.get(i),Map.of("grade",grade)));UserMultiSupport.check(count(rs,200,"")==16,"Unexpected stress HTTP errors");idle();}return Map.of("requests",48,"a",a.stats(),"b",b.stats());}
    static void idle()throws Exception {if(a==null||b==null)return;long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(25);while(System.nanoTime()<end){var x=a.stats();var y=b.stats();boolean quiet=true;for(var st:List.of(x,y))for(String key:List.of("active","waiting","matchingActive","matchingQueue"))quiet&=((Number)st.get(key)).intValue()==0;if(quiet)return;Thread.sleep(60);}throw new AssertionError("Pools/matching work did not drain");}
    static List<Map<String,Object>> eventsSince(int start){var snapshot=new ArrayList<>(EVENTS);return snapshot.subList(Math.min(start,snapshot.size()),snapshot.size());}
    static boolean poolClosedEvent(long pid){return EVENTS.stream().anyMatch(e->((Number)e.get("pid")).longValue()==pid&&((Map<?,?>)e.get("event")).get("kind").equals("closed")&&Boolean.TRUE.equals(((Map<?,?>)((Map<?,?>)e.get("event")).get("data")).get("poolClosed")));}
    static long vendorEvents(int start,int code,boolean matchingOnly){return eventsSince(start).stream().filter(e->{var event=(Map<?,?>)e.get("event");if(!event.get("kind").equals("jdbcFailure"))return false;var data=(Map<?,?>)event.get("data");if(matchingOnly&&!Objects.toString(data.get("thread"),"").startsWith("matching-refresh-"))return false;return ((List<?>)((Map<?,?>)data.get("error")).get("causes")).stream().anyMatch(c->{Object n=((Map<?,?>)c).get("vendorCode");return n instanceof Number v&&v.intValue()==code;});}).count();}
    static Map<String,String> sourceHashes()throws Exception {var hashes=new TreeMap<String,String>();for(String directory:List.of("backend/src/main","backend/src/test","backend/tools/auth-mysql-concurrency","backend/tools/auth-mysql-multi-instance","backend/tools/user-mysql-concurrency","backend/migrations")){try(var files=Files.walk(root.resolve(directory))){for(var f:files.filter(Files::isRegularFile).sorted().toList())hashes.put(root.relativize(f).toString(),HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(f))));}}return hashes;}
    static Map<String,Object> originalSnapshot()throws Exception {String url="jdbc:mysql://127.0.0.1:3308/kkiriusertest?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&allowMultiQueries=false";try(var c=DriverManager.getConnection(url,"kkiri_user_test",System.getenv("KKIRI_UM_ORIGINAL_PASSWORD"))){c.setReadOnly(true);var identity=UserMultiSupport.rows(c,"SELECT DATABASE() db,CURRENT_USER() account,VERSION() version,@@server_uuid uuid,@@hostname hostname").getFirst();UserMultiSupport.check(identity.get("db").equals("kkiriusertest")&&identity.get("account").equals("kkiri_user_test@%")&&identity.get("version").equals("8.0.46")&&identity.get("uuid").equals(UserMultiSupport.UUID)&&identity.get("hostname").equals("cbede7f4b30d"),"Original User snapshot identity mismatch");var names=UserMultiSupport.rows(c,"SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() ORDER BY table_name");UserMultiSupport.check(names.size()==30,"Original schema table count changed");var snapshot=new TreeMap<String,Object>();for(var row:names){String t=row.values().iterator().next().toString();snapshot.put(t,Map.of("rows",UserMultiSupport.number(c,"SELECT COUNT(*) FROM `"+t+"`"),"ddl",UserMultiSupport.rows(c,"SHOW CREATE TABLE `"+t+"`")));}return snapshot;}}
    static void cleanup()throws Exception {try(var c=UserMultiSupport.open()){UserMultiSupport.grants(c);var fks=UserMultiSupport.rows(c,"SELECT table_name child,referenced_table_name parent FROM information_schema.key_column_usage WHERE table_schema=DATABASE() AND referenced_table_name IS NOT NULL");var remaining=new HashSet<>(tables);var deletion=new ArrayList<String>();while(!remaining.isEmpty()){var leaves=remaining.stream().filter(t->fks.stream().noneMatch(f->remaining.contains(f.get("child").toString())&&f.get("parent").equals(t)&&!f.get("child").equals(t))).sorted().toList();UserMultiSupport.check(!leaves.isEmpty(),"FK cycle prevents safe cleanup");for(String t:leaves){UserMultiSupport.exec(c,"DELETE FROM `"+t+"`");remaining.remove(t);deletion.add(t);}}
        var counts=new TreeMap<String,Long>();for(String t:tables){long n=UserMultiSupport.number(c,"SELECT COUNT(*) FROM `"+t+"`");UserMultiSupport.check(n==0,"Residual fixture rows");counts.put(t,n);}var after=schema(c);UserMultiSupport.check(JSON.mapper.writeValueAsString(initialSchema).replaceAll(" AUTO_INCREMENT=\\d+","").equals(JSON.mapper.writeValueAsString(after).replaceAll(" AUTO_INCREMENT=\\d+","")),"New schema DDL changed during scenarios");UserMultiSupport.write(out.resolve("cleanup.json"),Map.of("rows",counts,"deletionOrder",deletion,"ddlUnchanged",true,"allProcessesStoppedBeforeCleanup",SERVERS.stream().noneMatch(s->s.process.isAlive())));UserMultiSupport.write(out.resolve("final-schema.json"),after);}
    }
}
