package kmi;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import com.sun.net.httpserver.HttpServer;
import com.kkirikkiri.global.security.JwtTokenProvider;
import com.kkirikkiri.user.entity.*;
import jakarta.persistence.*;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import tools.jackson.databind.JsonNode;
import static kmi.MatchingMultiSupport.*;
import static com.kkirikkiri.survey.SurveyTestData.*;

/** Opt-in two OS processes, actual HTTP/MySQL, explicit assertions and redacted evidence. */
public class MatchingMultiInstance {
    static Path output;
    static final List<Map<String,Object>> cases=new CopyOnWriteArrayList<>(),requests=new CopyOnWriteArrayList<>(),events=new CopyOnWriteArrayList<>();
    static final List<Server> servers=new ArrayList<>();
    static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    static String current="setup";
    static EntityManagerFactory emf;
    static final List<Member> members=new ArrayList<>();
    static Member me,target,other;
    static Server a,b;
    static final String SURVEY="/api/users/me/profile-survey",PREFERENCE="/api/users/me/roommate-preferences";
    static final String PASSWORD=UUID.randomUUID()+"!Aa1",HASH=new BCryptPasswordEncoder().encode(PASSWORD);
    static final AtomicInteger stubActive=new AtomicInteger(),stubPeak=new AtomicInteger(),stubCalls=new AtomicInteger();
    static volatile String stubMode="normal";
    static volatile CountDownLatch stubGate;
    record Member(long id,String token,String email){}
    record Reply(int status,JsonNode body){JsonNode data(){return body.path("data");}String code(){return body.path("errorCode").asString("");}}
    interface Work{Object run()throws Exception;}
    public static void main(String[] args)throws Exception{
        if(args.length==2&&!args[0].equals("VERIFY")){provision(args);return;}
        output=Path.of(args[1]).toRealPath();boolean passed=false,cleaned=false,stopped=false;
        var stub=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var stubWorkers=Executors.newVirtualThreadPerTaskExecutor();stub.setExecutor(stubWorkers);
        stub.createContext("/chat/completions/",exchange->{stubCalls.incrementAndGet();int active=stubActive.incrementAndGet();stubPeak.accumulateAndGet(active,Math::max);try{
            check(exchange.getRequestMethod().equals("POST"),"Stub method");JSON.readTree(exchange.getRequestBody());
            var gate=stubGate;if(gate!=null)check(gate.await(20,TimeUnit.SECONDS),"Stub gate timeout");
            byte[] bytes=(stubMode.equals("invalid")?"{\"choices\":[]}":"{\"choices\":[{\"message\":{\"content\":\"격리 검증 응답\"}}]}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);
        }catch(Exception e){events.add(Map.of("kind","stubError","error",error(e)));}finally{stubActive.decrementAndGet();exchange.close();}});
        try{
            for(int port:List.of(18081,18082))try(var socket=new ServerSocket()){socket.bind(new InetSocketAddress("127.0.0.1",port));}
            initialize();stub.start();a=start(18081,stub.getAddress().getPort());b=start(18082,stub.getAddress().getPort());
            check(a.process.pid()!=b.process.pid()&&a.process.isAlive()&&b.process.isAlive(),"Distinct live OS processes required");
            fixtures();runCases();MatchingExtraChecks.run();passed=cases.stream().allMatch(c->c.get("result").equals("PASS"));
        }catch(Throwable failure){cases.add(Map.of("name","harness","result","INCOMPLETE","error",error(failure)));}
        finally{
            if(stubGate!=null)stubGate.countDown();for(var s:servers)try{s.stop();}catch(Throwable failure){events.add(Map.of("kind","processCleanupFailure","error",error(failure)));}
            stopped=servers.stream().noneMatch(s->s.process.isAlive());stub.stop(0);stubWorkers.shutdownNow();HTTP.close();if(emf!=null)emf.close();
            try{cleanup();cleaned=true;}catch(Throwable failure){cases.add(Map.of("name","cleanup","result","FAIL","error",error(failure)));}
            write(output.resolve("cases.json"),cases);write(output.resolve("requests.json"),requests);write(output.resolve("servers.json"),events);
            var summary=Map.of("verdict",passed&&stopped&&cleaned?"PASS":"FAIL_OR_INCOMPLETE","cases",cases.size(),"passed",cases.stream().filter(c->c.get("result").equals("PASS")).count(),"requests",requests.size(),"distinctPids",servers.stream().map(s->s.process.pid()).toList(),"processesStopped",stopped,"allTablesEmpty",cleaned,"stubCalls",stubCalls.get(),"peakStubConcurrency",stubPeak.get(),"database",db());write(output.resolve("summary.json"),summary);System.out.println(JSON.writeValueAsString(summary));
        }
        if(!passed||!cleaned||!stopped)System.exit(1);
    }
    static void provision(String[] args)throws Exception{
        var work=Path.of(args[0]).toRealPath();output=work.resolve("run-"+UUID.randomUUID());Files.createDirectory(output);
        String suffix=UUID.randomUUID().toString().replace("-","").substring(0,12),database="kkiri_matching_mi_"+suffix,account="kkiri_kmi_"+suffix,password=UUID.randomUUID().toString().replace("-","");
        String jwt=Base64.getEncoder().encodeToString((UUID.randomUUID().toString()+UUID.randomUUID()).getBytes(StandardCharsets.UTF_8));
        boolean createdDb=false,createdAccount=false;int exit=1;
        try(var root=DriverManager.getConnection(url(""),"root",System.getenv("KM_ROOT_PASSWORD"))){
            identity(root);check(database.matches("kkiri_matching_mi_[a-f0-9]{12}")&&account.matches("kkiri_kmi_[a-f0-9]{12}"),"Unsafe generated names");
            try{
                exec(root,"CREATE DATABASE `"+database+"` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");createdDb=true;
                exec(root,"CREATE USER '"+account+"'@'%' IDENTIFIED BY '"+password+"'");createdAccount=true;
                exec(root,"GRANT ALL PRIVILEGES ON `"+database.replace("_","\\_")+"`.* TO '"+account+"'@'%'");
                var pb=new ProcessBuilder(java(),"-Duser.timezone=Asia/Seoul","-cp",System.getProperty("java.class.path"),MatchingMultiInstance.class.getName(),"VERIFY",output.toString());
                var env=pb.environment();env.remove("KM_ROOT_PASSWORD");env.put("KM_DATABASE",database);env.put("KM_USER",account);env.put("KM_PASSWORD",password);env.put("KM_JWT",jwt);
                write(output.resolve("artifact.json"),Map.of("bootJarSha256",args[1],"java",System.getProperty("java.version"),"startedUtc",Instant.now().toString(),"database",database));
                var child=pb.inheritIO().start();try{exit=child.waitFor();}finally{if(child.isAlive())child.destroyForcibly().waitFor();}
            }finally{
                // Only this invocation's successfully created, regex-checked schema/account can be removed.
                identity(root);if(createdDb)exec(root,"DROP DATABASE `"+database+"`");if(createdAccount)exec(root,"DROP USER '"+account+"'@'%'");
                write(output.resolve("isolation-cleanup.json"),Map.of("databaseRemoved",createdDb,"accountRemoved",createdAccount,"protectedDatabasesAccessed",false));
            }
        }
        System.out.println("Matching multi-JVM evidence: "+output);if(exit!=0)System.exit(exit);
    }
    static String java(){return Path.of(System.getProperty("java.home"),"bin","java.exe").toString();}
    static void initialize()throws Exception{
        try(var c=open()){check(number(c,"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()")==0,"Expected new empty schema");write(output.resolve("environment.json"),rows(c,"SELECT VERSION() version,DATABASE() db,@@server_uuid uuid,@@hostname hostname,@@transaction_isolation isolationLevel,@@session.time_zone zone"));write(output.resolve("grants.json"),rows(c,"SHOW GRANTS"));}
        var f=new LocalContainerEntityManagerFactoryBean();f.setDataSource(source());f.setPackagesToScan("com.kkirikkiri");f.setJpaVendorAdapter(new HibernateJpaVendorAdapter());f.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","create-only","hibernate.hbm2ddl.halt_on_error","true","hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl"));f.afterPropertiesSet();emf=f.getObject();
        try(var c=open()){exec(c,"ALTER TABLE auth_limit_bucket MODIFY bucket_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL");write(output.resolve("schema.json"),rows(c,"SELECT table_name,engine FROM information_schema.tables WHERE table_schema=DATABASE()"));}
    }
    static class Server{
        final Process process;final int port;final BufferedWriter input;final BlockingQueue<Map<String,Object>> queue=new LinkedBlockingQueue<>();final CountDownLatch drained=new CountDownLatch(1);
        Server(int port,int stubPort)throws Exception{this.port=port;var pb=new ProcessBuilder(java(),"-Duser.timezone=Asia/Seoul","-cp",System.getProperty("java.class.path"),MatchingMultiServer.class.getName(),""+port);pb.environment().put("KM_STUB_PORT",""+stubPort);pb.redirectErrorStream(true);process=pb.start();servers.add(this);input=process.outputWriter(StandardCharsets.UTF_8);
            Thread.ofVirtual().start(()->{try(var reader=process.inputReader(StandardCharsets.UTF_8)){String line;while((line=reader.readLine())!=null){if(line.startsWith("KM_EVENT ")){var e=JSON.readValue(line.substring(9),Map.class);var copy=new LinkedHashMap<String,Object>(e);copy.put("pid",process.pid());copy.put("port",port);events.add(copy);queue.add(e);}/* No unstructured app output: never persist possible credentials. */}}catch(Exception e){events.add(Map.of("kind","readerError","error",error(e)));}finally{drained.countDown();}});
        }
        Map<String,Object> await(String kind,int seconds)throws Exception{long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(seconds);while(System.nanoTime()<deadline){var e=queue.poll(100,TimeUnit.MILLISECONDS);if(e!=null){if(e.get("kind").equals("error"))throw new AssertionError("Server error "+JSON.writeValueAsString(e.get("data")));if(e.get("kind").equals(kind))return (Map<String,Object>)e.get("data");}if(!process.isAlive()){drained.await(1,TimeUnit.SECONDS);if(queue.isEmpty())throw new AssertionError("Server exited while awaiting "+kind);}}throw new AssertionError("Timed out awaiting "+kind);}
        void command(String v)throws Exception{input.write(v);input.newLine();input.flush();}
        void stop()throws Exception{if(process.isAlive()){command("RELEASE");command("STOP");if(!process.waitFor(30,TimeUnit.SECONDS))process.destroyForcibly().waitFor(5,TimeUnit.SECONDS);}drained.await(2,TimeUnit.SECONDS);check(!process.isAlive(),"JVM still alive");}
    }
    static Server start(int port,int stub)throws Exception{var s=new Server(port,stub);var ready=s.await("ready",100);check(((Number)ready.get("pid")).longValue()==s.process.pid()&&db().equals(ready.get("database")),"PID/database mismatch");System.out.println("READY "+JSON.writeValueAsString(ready));return s;}
    static Reply api(int port,String method,String path,Object body,Member who)throws Exception{
        var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(35)).header("Content-Type","application/json");if(who!=null)builder.header("Authorization","Bearer "+who.token);
        builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));long start=System.nanoTime();var response=HTTP.send(builder.build(),HttpResponse.BodyHandlers.ofString());var reply=new Reply(response.statusCode(),JSON.readTree(response.body()));
        requests.add(Map.of("case",current,"port",port,"method",method,"path",path,"status",reply.status,"errorCode",reply.code(),"elapsedMillis",TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)));return reply;
    }
    static void expect(Reply reply,int status,String code){check(reply.status==status&&reply.code().equals(code),"Expected "+status+"/"+code+", received "+reply.status+"/"+reply.code());check(reply.body.path("success").asBoolean()==(status==200),"ApiResponse success mismatch");}
    static long n(String sql,Object...args)throws Exception{try(var c=open()){return number(c,sql,args);}}
    static void update(String sql,Object...args)throws Exception{try(var c=open()){exec(c,sql,args);}}
    static List<Map<String,Object>> query(String sql,Object...args)throws Exception{try(var c=open()){return rows(c,sql,args);}}
    static void fixtures()throws Exception{
        var em=emf.createEntityManager();try{em.getTransaction().begin();var d=new Dormitory("matching-mi","Matching MI",true);var f=new Faculty("Matching MI",true);em.persist(d);em.persist(f);
            var tokens=new JwtTokenProvider(System.getenv("KM_JWT"),3600);for(int i=0;i<15;i++){var u=new User(HASH,"kmi"+i+"@hknu.ac.kr");u.verifyEmail();em.persist(u);em.persist(new UserProfile(u,"kmi"+i,"MALE",d,f,null,(short)2003,"22","3학년","INTJ"));members.add(new Member(u.getUserId(),tokens.createAccessToken(u.getUserId(),UserRole.USER),u.getSchoolEmail()));}em.getTransaction().commit();
        }finally{em.close();}
        me=members.get(0);target=members.get(1);other=members.get(2);
        for(var u:members){expect(api(18081,"PUT",SURVEY,profile(false),u),200,"");}
        expect(api(18081,"PUT",PREFERENCE,preference(false),me),200,"");
    }
    static void idle()throws Exception{for(var server:servers){long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);while(true){server.command("STATS");var stats=server.await("stats",5);if(((Number)stats.get("matchingActive")).intValue()==0&&((Number)stats.get("matchingQueue")).intValue()==0)break;check(System.nanoTime()<deadline,"Async matching did not settle");Thread.sleep(20);}}}
    static void test(String name,String expected,Work work){current=name;long start=System.nanoTime();var result=new LinkedHashMap<String,Object>();result.put("name",name);result.put("expected",expected);try{idle();result.put("actual",work.run());result.put("result","PASS");System.out.println("PASS "+name);}catch(Throwable failure){result.put("result","FAIL");result.put("error",error(failure));System.out.println("FAIL "+name+" "+JSON.writeValueAsString(error(failure)));}result.put("elapsedMillis",TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start));cases.add(result);try{write(output.resolve("cases.json"),cases);}catch(Exception e){throw new RuntimeException(e);}}
    static List<Reply> concurrent(int count,java.util.function.IntFunction<Reply> work)throws Exception{var latch=new CountDownLatch(1);try(var executor=Executors.newVirtualThreadPerTaskExecutor()){var jobs=new ArrayList<Future<Reply>>();for(int i=0;i<count;i++){final int j=i;jobs.add(executor.submit(()->{latch.await();return work.apply(j);}));}latch.countDown();var result=new ArrayList<Reply>();for(var job:jobs)result.add(job.get(40,TimeUnit.SECONDS));return result;}}
    static Reply call(int port,String method,String path,Object body,Member who){try{return api(port,method,path,body,who);}catch(Exception e){throw new RuntimeException(e);}}
    static int port(int i){return i%2==0?18081:18082;}
    static String likes(){return "/api/roommates/"+target.id+"/likes";}
    static String detail(){return "/api/roommates/"+target.id;}
    static String ai(){return detail()+"/ai-analysis";}
    static String quotaKey(long id)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(("ai-analysis:user:"+id).getBytes(StandardCharsets.UTF_8)));}
    static void resetQuota()throws Exception{update("DELETE FROM auth_limit_bucket WHERE bucket_key=?",quotaKey(me.id));}
    static void runCases(){
        test("authentication-principal-and-cross-jvm-login","401 unauthenticated; A login token works on B; own survey only",()->{expect(api(18082,"PUT",SURVEY,profile(true),null),401,"UNAUTHORIZED");var login=api(18081,"POST","/api/auth/login",Map.of("email",me.email,"password",PASSWORD),null);expect(login,200,"");var actual=new Member(me.id,login.data().path("accessToken").asString(),me.email);expect(api(18082,"GET",SURVEY,null,actual),200,"");expect(api(18082,"GET","/api/users/"+target.id+"/profile-survey",null,actual),403,"FORBIDDEN");return Map.of("tokenAcceptedAcrossProcesses",true);});
        test("invalid-missing-and-self-target","400; no mutation for invalid survey, missing target or self-like",()->{long before=n("SELECT COUNT(*) FROM profile_like");expect(api(18081,"PUT",SURVEY,Map.of("sleepTime","bad"),me),400,"VALIDATION_ERROR");expect(api(18082,"GET","/api/roommates/9223372036854775807",null,me),400,"INVALID_REQUEST");expect(api(18082,"POST","/api/roommates/"+me.id+"/likes",null,me),400,"INVALID_REQUEST");check(n("SELECT COUNT(*) FROM profile_like")==before,"Invalid request mutated likes");return Map.of("rowsUnchanged",true);});
        test("same-user-survey-concurrent-updates","2/4/8 parallel PUTs all 200; one parent, complete request values, no mixed options",()->{for(int count:List.of(2,4,8)){for(var r:concurrent(count,i->call(port(i),"PUT",SURVEY,profile(i%2==0),target)))expect(r,200,"");var r=api(18082,"GET",SURVEY,null,target);expect(r,200,"");boolean second=r.data().path("introduction").asString().equals(profile(true).introduction());check(r.data().path("answers").equals(profileAnswers(second)),"Mixed survey request");check(n("SELECT COUNT(*) FROM profile_survey WHERE user_id=?",target.id)==1,"Duplicate parent");check(n("SELECT COUNT(*) FROM profile_survey_option o JOIN profile_survey s ON s.profile_survey_id=o.profile_survey_id WHERE s.user_id=?",target.id)==(second?3:4),"Duplicate options");}return Map.of("parallelLevels",List.of(2,4,8));});
        test("profile-like-duplicate-registration","2/8/32 parallel POSTs all 200 and exactly one like",()->{update("DELETE FROM profile_like WHERE user_id=? AND target_user_id=?",me.id,target.id);for(int count:List.of(2,8,32)){for(var r:concurrent(count,i->call(port(i),"POST",likes(),null,me)))expect(r,200,"");check(n("SELECT COUNT(*) FROM profile_like WHERE user_id=? AND target_user_id=?",me.id,target.id)==1,"Duplicate like");}return Map.of("parallelLevels",List.of(2,8,32),"rows",1);});
        test("profile-like-register-cancel-race","32 mixed requests: POST 200, DELETE 200 or absent 400; no 5xx; final state agrees across JVMs",()->{var replies=concurrent(32,i->call(port(i),i%3==0?"POST":"DELETE",likes(),null,me));for(int i=0;i<replies.size();i++){var r=replies.get(i);if(i%3==0)expect(r,200,"");else check(r.status==200||r.status==400&&r.code().equals("INVALID_REQUEST"),"Unexpected cancel response "+r.status+"/"+r.code());}long count=n("SELECT COUNT(*) FROM profile_like WHERE user_id=? AND target_user_id=?",me.id,target.id);check(count<=1,"Duplicate like");for(int p:List.of(18081,18082)){var r=api(p,"GET",detail(),null,me);expect(r,200,"");check(r.data().path("liked").asBoolean()==(count==1),"Like read mismatch");}return Map.of("requests",32,"rows",count);});
        test("matching-simultaneous-two-jvm-reads","16 parallel detail reads return same committed score; one pair in DB",()->{expect(api(18081,"PUT",SURVEY,profile(false),target),200,"");var replies=concurrent(16,i->call(port(i),"GET",detail(),null,me));var scores=new HashSet<Integer>();for(var r:replies){expect(r,200,"");scores.add(r.data().path("matchScore").asInt());}check(scores.size()==1,"Scores disagree");check(n("SELECT COUNT(*) FROM roommate_match WHERE requester_user_id=? AND target_user_id=?",me.id,target.id)==1,"Duplicate score");check(n("SELECT match_score FROM roommate_match WHERE requester_user_id=? AND target_user_id=?",me.id,target.id)==scores.iterator().next(),"Stored score differs");return Map.of("parallel",16,"score",scores.iterator().next());});
        test("survey-commit-overlapping-matching-read","B waits for A survey transaction; after commit score changes and answers/introduction match",()->{expect(api(18081,"PUT",SURVEY,profile(false),target),200,"");int before=api(18081,"GET",detail(),null,me).data().path("matchScore").asInt();a.command("ARM");a.await("armed",5);try(var executor=Executors.newVirtualThreadPerTaskExecutor()){var save=executor.submit(()->api(18081,"PUT",SURVEY,profile(true),target));a.await("gated",10);var read=executor.submit(()->api(18082,"GET",detail(),null,me));try{read.get(200,TimeUnit.MILLISECONDS);throw new AssertionError("Matching read bypassed survey lock");}catch(TimeoutException expected){}finally{a.command("RELEASE");a.await("released",5);}expect(save.get(20,TimeUnit.SECONDS),200,"");var r=read.get(20,TimeUnit.SECONDS);expect(r,200,"");int after=r.data().path("matchScore").asInt();check(after!=before&&r.data().path("answers").path("meal").asString().equals(profile(true).meal())&&r.data().path("introduction").asString().equals(profile(true).introduction()),"Stale or mixed read");return Map.of("before",before,"after",after,"lockWaitObserved",true);}finally{a.command("RELEASE");}});
        test("profile-update-related-reads","PATCH on B reflected in A detail and liked list",()->{expect(api(18082,"PATCH","/api/users/me",Map.of("nickname","updated"),target),200,"");expect(api(18081,"POST",likes(),null,me),200,"");var r=api(18081,"GET",detail(),null,me);expect(r,200,"");check(r.data().path("nickname").asString().equals("updated"),"Detail stale");var liked=api(18082,"GET","/api/users/me/liked-roommates",null,me);expect(liked,200,"");boolean found=false;for(var item:liked.data().path("content"))if(item.path("id").asLong()==target.id){found=true;check(item.path("nickname").asString().equals("updated"),"Liked profile stale");}check(found,"Missing liked target");return Map.of("updatedNicknameVisible",true);});
        test("pagination-inactive-and-withdrawn-filter","guest and authenticated pages/count exclude SUSPENDED/WITHDRAWN; no duplicates",()->{update("UPDATE user SET status='SUSPENDED' WHERE user_id=?",members.get(13).id);update("UPDATE user SET status='WITHDRAWN' WHERE user_id=?",members.get(14).id);try{for(Member viewer:Arrays.asList(null,me)){var ids=new HashSet<Long>();long total=-1;for(int page=0;page<5;page++){var r=api(port(page),"GET","/api/roommates?page="+page+"&size=4",null,viewer);expect(r,200,"");total=r.data().path("totalElements").asLong();for(var item:r.data().path("content"))check(ids.add(item.path("id").asLong()),"Duplicate page entry");if(r.data().path("last").asBoolean())break;}check(ids.size()==total&&total==(viewer==null?13:12),"Pagination count mismatch");check(!ids.contains(members.get(13).id)&&!ids.contains(members.get(14).id),"Inactive leaked");}return Map.of("guestCount",13,"memberCount",12);}finally{update("UPDATE user SET status='ACTIVE' WHERE user_id IN (?,?)",members.get(13).id,members.get(14).id);}});
        test("ai-shared-quota-parallel-decrement","32 requests across JVMs: exactly 12 quota admissions (200/503), 20 HTTP429; DB requests=12",()->{resetQuota();var replies=concurrent(32,i->call(port(i),"POST",ai(),null,me));int admitted=0,denied=0,ok=0,busy=0;for(var r:replies){if(r.status==429){expect(r,429,"AI_ANALYSIS_RATE_LIMITED");denied++;}else if(r.status==503){expect(r,503,"AI_ANALYSIS_BUSY");admitted++;busy++;}else{expect(r,200,"");admitted++;ok++;}}check(admitted==12&&denied==20,"Quota admission mismatch");long stored=n("SELECT request_count FROM auth_limit_bucket WHERE bucket_key=?",quotaKey(me.id));check(stored==12,"Quota exceeded");return Map.of("requests",32,"admitted",admitted,"denied",denied,"success",ok,"busy",busy,"stored",stored);});
        test("ai-same-user-instance-capacity","2 AI in flight per JVM, aggregate 4; fifth/sixth HTTP503; shared quota=6",()->{resetQuota();stubGate=new CountDownLatch(1);try(var executor=Executors.newVirtualThreadPerTaskExecutor()){var pending=new ArrayList<Future<Reply>>();for(int i=0;i<4;i++){int p=port(i);pending.add(executor.submit(()->api(p,"POST",ai(),null,me)));}long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);while(stubActive.get()<4&&System.nanoTime()<deadline)Thread.sleep(10);check(stubActive.get()==4,"Expected four upstream calls across two JVMs");expect(api(18081,"POST",ai(),null,me),503,"AI_ANALYSIS_BUSY");expect(api(18082,"POST",ai(),null,me),503,"AI_ANALYSIS_BUSY");stubGate.countDown();for(var pendingReply:pending)expect(pendingReply.get(15,TimeUnit.SECONDS),200,"");check(n("SELECT request_count FROM auth_limit_bucket WHERE bucket_key=?",quotaKey(me.id))==6,"Busy admission quota mismatch");return Map.of("perJvmLimit",2,"aggregateObserved",4,"quota",6);}finally{stubGate.countDown();stubGate=null;}});
        test("ai-failure-quota-and-permit-recovery","invalid stub response gives 502; quota retained; next request works on each JVM",()->{resetQuota();int calls=stubCalls.get();stubMode="invalid";for(int p:List.of(18081,18082))expect(api(p,"POST",ai(),null,me),502,"AI_GATEWAY_INVALID_RESPONSE");stubMode="normal";for(int p:List.of(18081,18082))expect(api(p,"POST",ai(),null,me),200,"");check(n("SELECT request_count FROM auth_limit_bucket WHERE bucket_key=?",quotaKey(me.id))==4,"Failure quota lost");check(stubCalls.get()-calls==4,"Unexpected upstream retries");return Map.of("quotaRetained",4,"recovered",2);});
        test("mysql-lock-conflict-rollback-and-recovery","held user lock produces HTTP409; survey/profile/options unchanged; next request succeeds",()->{var before=snapshot(target.id);try(var c=open()){c.setAutoCommit(false);rows(c,"SELECT user_id FROM user WHERE user_id=? FOR UPDATE",target.id);try{expect(api(18082,"PUT",SURVEY,profile(false),target),409,"ACCOUNT_UPDATE_CONFLICT");check(snapshot(target.id).equals(before),"Failed request changed rows");}finally{c.rollback();}}expect(api(18081,"PUT",SURVEY,profile(false),target),200,"");return Map.of("unchangedAfterConflict",true,"recovered",true);});
        test("mysql-physical-unique-fk-and-rollback","duplicate pair/FK rejected by engine; transaction rollback preserves existing score",()->{long before=n("SELECT match_score FROM roommate_match WHERE requester_user_id=? AND target_user_id=?",me.id,target.id);int rejected=0;try(var c=open()){for(long id:List.of(target.id,Long.MAX_VALUE)){c.setAutoCommit(false);try{exec(c,"UPDATE roommate_match SET match_score=1 WHERE requester_user_id=? AND target_user_id=?",me.id,target.id);exec(c,"INSERT INTO roommate_match(requester_user_id,target_user_id,match_score) VALUES(?,?,50)",me.id,id);throw new AssertionError("Expected physical constraint");}catch(SQLException expected){check(Set.of(1062,1452).contains(expected.getErrorCode()),"Unexpected SQL error");rejected++;c.rollback();}}}check(rejected==2&&n("SELECT match_score FROM roommate_match WHERE requester_user_id=? AND target_user_id=?",me.id,target.id)==before,"Rollback lost existing score");return Map.of("constraintsRejected",rejected,"scorePreserved",before);});
    }
    static JsonNode profileAnswers(boolean second){var fields=new LinkedHashMap<String,Object>(JSON.convertValue(profile(second),Map.class));fields.remove("introduction");return JSON.valueToTree(fields);}
    static Object snapshot(long id)throws Exception{return List.of(query("SELECT * FROM user_profile WHERE user_id=?",id),query("SELECT * FROM profile_survey WHERE user_id=?",id),query("SELECT o.* FROM profile_survey_option o JOIN profile_survey s ON s.profile_survey_id=o.profile_survey_id WHERE s.user_id=? ORDER BY o.profile_survey_option_id",id));}
    static void cleanup()throws Exception{
        try(var c=open()){var tables=rows(c,"SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE()").stream().map(row->row.values().iterator().next().toString()).toList();for(String t:tables)check(t.matches("[a-z_]+"),"Unexpected table");
            // Only this per-run database/account exists here. Delete child tables before their referenced parents.
            var remaining=new HashSet<>(tables);while(!remaining.isEmpty()){boolean progress=false;for(String table:List.copyOf(remaining)){long refs=number(c,"SELECT COUNT(*) FROM information_schema.key_column_usage WHERE referenced_table_schema=DATABASE() AND referenced_table_name=? AND table_name<>?",table,table);boolean hasLiveChild=false;if(refs>0){for(var row:rows(c,"SELECT DISTINCT table_name FROM information_schema.key_column_usage WHERE referenced_table_schema=DATABASE() AND referenced_table_name=?",table))if(!table.equals(row.values().iterator().next().toString())&&remaining.contains(row.values().iterator().next().toString()))hasLiveChild=true;}if(!hasLiveChild){exec(c,"DELETE FROM `"+table+"`");remaining.remove(table);progress=true;}}check(progress,"Foreign-key cleanup cycle");}
            var counts=new LinkedHashMap<String,Long>();for(String t:tables){long count=number(c,"SELECT COUNT(*) FROM `"+t+"`");counts.put(t,count);check(count==0,"Cleanup left rows");}write(output.resolve("cleanup.json"),counts);
        }
    }
}
