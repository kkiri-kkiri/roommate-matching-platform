package kmi;

import static kmi.MatchingMultiSupport.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import com.kkirikkiri.user.entity.*;
import com.kkirikkiri.global.security.JwtTokenProvider;
import com.kkirikkiri.report.entity.*;
import com.kkirikkiri.report.repository.AdminReportQueryRepository;

/** Only new CCRA cases; reuses existing TCP HTTP, process, JWT and evidence helpers. */
public class CcraMysqlValidation extends MatchingMultiInstance {
    public static class NoAi extends com.kkirikkiri.matching.service.AiGatewayClient {
        public NoAi(tools.jackson.databind.ObjectMapper mapper){super(mapper,"","","");}
        @Override public String analyze(com.kkirikkiri.matching.dto.AiProfileAnalysisInput input){return "CCRA fixed AI response";}
    }
    public static class NoCrawl extends com.kkirikkiri.calendar.crawler.HknuAcademicCalendarCrawler {
        @Override public List<com.kkirikkiri.calendar.crawler.CrawledAcademicEvent> crawl(){throw new IllegalStateException("Crawling forbidden in CCRA validation");}
    }
    static Path repo;
    static Member adminA,adminB;
    static final int PORT=18081;
    static int memberNumber;
    static String mysqlVersion(){String v=System.getenv("CCRA_MYSQL_VERSION");check(Set.of("8.0.44","8.0.46").contains(v),"Use an explicit supported version path");return v;}
    static void strictGuard(Connection c)throws Exception {
        var identity=rows(c,"SELECT CURRENT_USER() account,CURRENT_ROLE() role,@@port port").getFirst();
        check(identity.get("account").equals(user()+"@%")&&identity.get("role").equals("NONE")&&((Number)identity.get("port")).intValue()==3306,"Account/role/port mismatch");
        var grants=new HashSet<String>();for(var row:rows(c,"SHOW GRANTS"))grants.add(row.values().iterator().next().toString());
        check(grants.equals(Set.of("GRANT USAGE ON *.* TO `"+user()+"`@`%`",
            "GRANT ALL PRIVILEGES ON `"+db()+"`.* TO `"+user()+"`@`%`",
            "GRANT SELECT ON `performance_schema`.`data_lock_waits` TO `"+user()+"`@`%`")),"Unexpected grants");
    }
    public static void main(String[] args)throws Exception {
        if(args.length==3){provisionCcra(args);return;}
        repo=Path.of(args[1]);output=Path.of(args[2]);
        ((ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).setLevel(ch.qos.logback.classic.Level.OFF);
        boolean started=false;
        try {
            try(var socket=new ServerSocket()){socket.bind(new InetSocketAddress("127.0.0.1",PORT));}
            initialize();
            test("isolation-and-physical-schema","new empty MySQL "+mysqlVersion()+"/RR schema, exact schema-only grants, all InnoDB",()->{
                try(var c=open()) {
                    strictGuard(c);
                    check(number(c,"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND engine<>'InnoDB'")==0,"Non-InnoDB");
                    check("REPEATABLE-READ".equals(rows(c,"SELECT @@transaction_isolation i").getFirst().get("i")),"Isolation mismatch");
                    return rows(c,"SELECT VERSION() version,DATABASE() db,CURRENT_USER() account,@@server_uuid uuid,@@hostname hostname,@@port port,@@lower_case_table_names lowerCase,@@sql_mode sqlMode,@@session.time_zone zone");
                }
            });
            test("community-manual-migrations","apply anonymous authors and image cleanup scripts to empty legacy tables; validate actual physical names and indexes",()->{migrations();return Map.of("scripts",2,"physicalTableIdentifiersAdapted",true);});
            a=start(PORT,1);started=true;
            me=member(UserRole.USER,true);target=member(UserRole.USER,true);other=member(UserRole.USER,true);
            adminA=member(UserRole.ADMIN,true);adminB=member(UserRole.ADMIN,true);
            for(int i=0;i<8;i++)members.add(member(UserRole.USER,true));
            community();reports();administration();chatHttp();physicalConstraints();
        } catch(Throwable failure){cases.add(Map.of("name","harness","result","INCOMPLETE","error",error(failure)));}
        finally {
            try{if(started)a.stop();}catch(Throwable failure){cases.add(Map.of("name","server-stop","result","FAIL","error",error(failure)));}
            try{write(output.resolve("final-state.json"),finalState());}catch(Throwable failure){cases.add(Map.of("name","final-state","result","FAIL","error",error(failure)));}
            if(emf!=null)emf.close();HTTP.close();
            write(output.resolve("cases.json"),cases);write(output.resolve("requests.json"),requests);write(output.resolve("servers.json"),events);
            long pass=cases.stream().filter(c->c.get("result").equals("PASS")).count();
            var summary=Map.of("executed",cases.size(),"passed",pass,"failed",cases.stream().filter(c->c.get("result").equals("FAIL")).count(),"incomplete",cases.stream().filter(c->c.get("result").equals("INCOMPLETE")).count(),"skipped",0,"httpRequests",requests.size(),"singleJvm",true,"serverStopped",servers.stream().noneMatch(s->s.process.isAlive()),"finishedUtc",Instant.now().toString());
            write(output.resolve("summary.json"),summary);System.out.println(JSON.writeValueAsString(summary));
            if(pass!=cases.size())System.exit(1);
        }
    }
    static void provisionCcra(String[] args)throws Exception {
        Path root=Path.of(args[0]);Path work=Path.of(args[1]);Path run=work.resolve("run-"+args[2]);Files.createDirectory(run);
        String suffix=args[2].substring(0,12),database="kkiriccra"+suffix,account="ccra_"+suffix,password=UUID.randomUUID().toString();
        check(database.matches("kkiriccra[a-f0-9]{12}")&&account.matches("ccra_[a-f0-9]{12}"),"Invalid target names");
        int exit=1;
        try(var c=DriverManager.getConnection(url(""),"root",System.getenv("KM_ROOT_PASSWORD"))){
            identity(c);check("kkiri-ccra".equals(System.getenv("KM_HOSTNAME")),"Wrong container hostname");
            check(number(c,"SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name=?",database)==0,"Existing DB collision");
            check(number(c,"SELECT COUNT(*) FROM mysql.user WHERE user=?",account)==0,"Existing account collision");
            exec(c,"CREATE DATABASE `"+database+"` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
            exec(c,"CREATE USER '"+account+"'@'%' IDENTIFIED BY '"+password+"'");
            exec(c,"GRANT ALL PRIVILEGES ON `"+database+"`.* TO '"+account+"'@'%'");
            exec(c,"GRANT SELECT ON performance_schema.data_lock_waits TO '"+account+"'@'%'");
            var sourceHashes=new TreeMap<String,String>();
            for(String dir:List.of("backend/src/main/java/com/kkirikkiri/community","backend/src/main/java/com/kkirikkiri/chat","backend/src/main/java/com/kkirikkiri/report","backend/src/main/java/com/kkirikkiri/admin","backend/migrations","backend/tools/matching-mysql-multi-instance"))
                try(var files=Files.walk(root.resolve(dir))){for(var file:files.filter(Files::isRegularFile).toList())sourceHashes.put(root.relativize(file).toString().replace('\\','/'),HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))));}
            write(run.resolve("source-hashes.json"),sourceHashes);
            var git=new ProcessBuilder("git","-C",root.toString(),"rev-parse","HEAD").start();String sha=new String(git.getInputStream().readAllBytes(),StandardCharsets.UTF_8).trim();check(git.waitFor()==0,"Git SHA unavailable");
            write(run.resolve("artifact.json"),Map.of("gitCommit",sha,"database",database,"containerOwner",args[2],"mysqlVersion",mysqlVersion(),"java",System.getProperty("java.version"),"bootJarSha256",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(root.resolve("backend/build/libs/backend-0.0.1-SNAPSHOT.jar"))))));
            var pb=new ProcessBuilder(java(),"-Duser.timezone=Asia/Seoul","-Xmx512m","-cp",System.getProperty("java.class.path"),CcraMysqlValidation.class.getName(),"VERIFY",root.toString(),run.toString(),"child");
            var env=pb.environment();
            env.keySet().removeIf(k->k.startsWith("SPRING_")||k.startsWith("DB_")||k.startsWith("SMTP_")||k.startsWith("AI_")||k.startsWith("JWT_")||Set.of("JAVA_TOOL_OPTIONS","JDK_JAVA_OPTIONS","_JAVA_OPTIONS","KM_ROOT_PASSWORD","MYSQL_ROOT_PASSWORD").contains(k));
            env.put("KM_DATABASE",database);env.put("KM_USER",account);env.put("KM_PASSWORD",password);env.put("KM_JWT",Base64.getEncoder().encodeToString((UUID.randomUUID().toString()+UUID.randomUUID()).getBytes(StandardCharsets.UTF_8)));
            var child=pb.inheritIO().start();try{exit=child.waitFor();}finally{if(child.isAlive())child.destroyForcibly().waitFor();}
        } finally {System.out.println("CCRA evidence: "+run);}
        if(exit!=0)System.exit(exit);
    }
    static Member member(UserRole role,boolean profile)throws Exception {
        var em=emf.createEntityManager();try{
            em.getTransaction().begin();int i=++memberNumber;
            var user=new User(HASH,"ccra"+i+"@hknu.ac.kr",role);user.verifyEmail();em.persist(user);
            if(profile){var d=new Dormitory("ccra"+i,"CCRA"+i,true);var f=new Faculty("CCRA"+i,true);em.persist(d);em.persist(f);em.persist(new UserProfile(user,"ccra"+i,"MALE",d,f,null,(short)2003,"22","3학년","INTJ"));}
            em.getTransaction().commit();return new Member(user.getUserId(),new JwtTokenProvider(System.getenv("KM_JWT"),3600).createAccessToken(user.getUserId(),role),user.getSchoolEmail());
        }finally{em.close();}
    }
    static Reply api(String method,String path,Object body,Member who)throws Exception{return api(PORT,method,path,body,who);}
    static long createPost(Member who,boolean anonymous)throws Exception {
        var r=multipart("POST","/api/posts",Map.of("category","자유게시판","title","CCRA 😀","content","본문","anonymous",anonymous),who);expect(r,200,"");return r.data().asLong();
    }
    static Reply multipart(String method,String path,Object body,Member who)throws Exception {
        String boundary="ccra"+UUID.randomUUID();String payload="--"+boundary+"\r\nContent-Disposition: form-data; name=\"request\"\r\nContent-Type: application/json\r\n\r\n"+JSON.writeValueAsString(body)+"\r\n--"+boundary+"--\r\n";
        var response=HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+PORT+path)).timeout(Duration.ofSeconds(35)).header("Content-Type","multipart/form-data; boundary="+boundary).header("Authorization","Bearer "+who.token()).method(method,HttpRequest.BodyPublishers.ofString(payload)).build(),HttpResponse.BodyHandlers.ofString());
        var r=new Reply(response.statusCode(),JSON.readTree(response.body()));requests.add(Map.of("case",current,"port",PORT,"method",method,"path",path,"status",r.status(),"errorCode",r.code()));return r;
    }
    static long comment(long post,Member who,String content,boolean anonymous)throws Exception {var r=api("POST","/api/posts/"+post+"/comments",Map.of("content",content,"anonymous",anonymous),who);expect(r,200,"");return r.data().asLong();}
    static long reply(long parent,Member who)throws Exception {var r=api("POST","/api/comments/"+parent+"/replies",Map.of("content","reply"),who);expect(r,200,"");return r.data().asLong();}
    static long report(String type,long id,Member who,Object description)throws Exception {var body=new LinkedHashMap<String,Object>();body.put("targetType",type);body.put("targetId",id);body.put("reason","욕설·비방");body.put("description",description);var r=api("POST","/api/reports",body,who);expect(r,200,"");return r.data().asLong();}
    static Reply process(long id,String action,Member admin)throws Exception{return api("PATCH","/api/admin/reports/"+id,Map.of("action",action),admin);}
    static String value(String sql,Object...args)throws Exception{return query(sql,args).getFirst().values().iterator().next().toString();}
    static Map<String,Object> state(long report)throws Exception{return query("SELECT * FROM report WHERE report_id=?",report).getFirst();}
    static void community(){
        test("community-post-http-crud","multipart create/update/read and soft delete; author Principal and MySQL final state",()->{
            long post=createPost(me,false);check(n("SELECT author_id FROM community_post WHERE post_id=?",post)==me.id(),"Wrong post author");
            expect(multipart("PATCH","/api/posts/"+post,Map.of("title","updated","content","수정 😀","category","자유게시판"),me),200,"");
            var r=api("GET","/api/posts/"+post,null,null);expect(r,200,"");check(r.data().path("title").asString().equals("updated"),"Update missing");
            expect(api("DELETE","/api/posts/"+post,null,other),403,"FORBIDDEN");check(value("SELECT content FROM community_post WHERE post_id=?",post).equals("수정 😀"),"Unauthorized mutation");
            expect(api("DELETE","/api/posts/"+post,null,me),200,"");check(value("SELECT status FROM community_post WHERE post_id=?",post).equals("DELETED"),"Delete not committed");return Map.of("post",post);
        });
        test("community-comment-reply-http-and-rollback","1000 UTF16 units; replies, Principal, ownership and invalid writes preserve committed thread",()->{
            long post=createPost(me,false),c=comment(post,other,"가".repeat(1000),false),r=reply(c,me);
            expect(api("PATCH","/api/comments/"+r,Map.of("content","edited"),me),200,"");
            var before=query("SELECT * FROM community_comment WHERE post_id=? ORDER BY comment_id",post);
            for(String text:List.of(" ","가".repeat(1001)))expect(api("POST","/api/posts/"+post+"/comments",Map.of("content",text),other),400,"VALIDATION_ERROR");
            expect(api("PATCH","/api/comments/"+c,Map.of("content","forged","userId",other.id()),me),403,"FORBIDDEN");
            expect(api("POST","/api/comments/"+c+"/replies",Map.of("content","blocked"),null),401,"UNAUTHORIZED");
            check(before.equals(query("SELECT * FROM community_comment WHERE post_id=? ORDER BY comment_id",post)),"Rejected writes changed thread");
            expect(api("DELETE","/api/comments/"+c,null,other),200,"");check(n("SELECT COUNT(*) FROM community_comment WHERE comment_id IN (?,?) AND status='DELETED'",c,r)==2,"Reply delete not atomic");return Map.of("post",post,"comment",c,"reply",r);
        });
        test("community-anonymous-concurrent-numbering","8 simultaneous distinct writers on one post have one mapping each and distinct positive numbers",()->{
            long post=createPost(me,true);var results=concurrent(8,i->call(PORT,"POST","/api/posts/"+post+"/comments",Map.of("content","anon "+i,"anonymous",true),members.get(i)));for(var r:results)expect(r,200,"");
            check(n("SELECT COUNT(*) FROM community_anonymous_author WHERE post_id=?",post)==8,"Mapping count");check(n("SELECT COUNT(DISTINCT anonymous_number) FROM community_anonymous_author WHERE post_id=?",post)==8,"Number collision");
            var view=api("GET","/api/posts/"+post,null,null);expect(view,200,"");
            var comments=view.data().path("comments");check(comments.size()==8,"Detail lost concurrent comments");
            var numbers=new HashSet<Long>();for(var entry:comments){check(entry.path("author").path("id").isNull(),"Anonymous author ID leaked");check(entry.path("anonymousNumber").asLong()>0,"Missing anonymous number");numbers.add(entry.path("anonymousNumber").asLong());}check(numbers.size()==8,"Response anonymous number collision");
            return Map.of("writers",8,"mappings",query("SELECT author_id,anonymous_number FROM community_anonymous_author WHERE post_id=? ORDER BY anonymous_number",post));
        });
    }
    static void reports(){
        for(String type:List.of("POST","COMMENT","USER"))test("report-"+type.toLowerCase()+"-target-principal-and-duplicates","actual target author FK, nullable references, independent reporter and duplicates after every status",()->{
            var author=member(UserRole.USER,true);long post=createPost(author,false),c=comment(post,author,"target",false),id=type.equals("POST")?post:type.equals("COMMENT")?c:author.id();
            long report=report(type,id,me,null);var row=state(report);check(((Number)row.get("reporter_id")).longValue()==me.id()&&((Number)row.get("target_user_id")).longValue()==author.id(),"Wrong target/reporter");
            check(type.equals("POST")?Objects.equals(row.get("post_id"),post)&&row.get("comment_id")==null:type.equals("COMMENT")?Objects.equals(row.get("comment_id"),c)&&row.get("post_id")==null:row.get("post_id")==null&&row.get("comment_id")==null,"Nullable target mismatch");
            var body=Map.of("targetType",type,"targetId",id,"reason","욕설·비방","userId",other.id());
            expect(api("POST","/api/reports",body,me),400,"INVALID_REQUEST");expect(process(report,"REJECT",adminA),200,"");expect(api("POST","/api/reports",body,me),400,"INVALID_REQUEST");
            long independent=report(type,id,other,null);expect(process(independent,"COMPLETE",adminB),200,"");expect(api("POST","/api/reports",body,other),400,"INVALID_REQUEST");
            check(n("SELECT COUNT(*) FROM report WHERE report_id IN (?,?)",report,independent)==2,"History lost");return Map.of("report",report,"independent",independent,"final",state(report));
        });
        test("report-description-utf16-and-datetime","499/500 ASCII, Korean and emoji persist exactly; 501/70000 reject; new/processed times have seconds precision",()->{
            int accepted=0,rejected=0;
            for(String unit:List.of("a","한","😀"))for(int length:List.of(499,500,501,70000)){
                var victim=member(UserRole.USER,false);String text=unit.repeat(length/unit.length())+(length%unit.length()==1?"x":"");
                var response=api("POST","/api/reports",Map.of("targetType","USER","targetId",victim.id(),"reason","욕설·비방","description",text),me);
                if(length<=500){expect(response,200,"");long id=response.data().asLong();check(value("SELECT description FROM report WHERE report_id=?",id).equals(text),"Description altered");expect(process(id,"COMPLETE",adminA),200,"");check(n("SELECT COUNT(*) FROM report WHERE report_id=? AND MICROSECOND(created_at)=0 AND MICROSECOND(processed_at)=0 AND processed_at>=created_at",id)==1,"Time roundtrip mismatch");accepted++;}
                else{expect(response,400,"VALIDATION_ERROR");check(n("SELECT COUNT(*) FROM report WHERE target_user_id=?",victim.id())==0,"Invalid description committed");rejected++;}
            }
            return Map.of("accepted",accepted,"rejected",rejected,"column",query("SELECT column_name,column_type,datetime_precision FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='report' AND column_name IN ('description','created_at','processed_at')"));
        });
        test("report-invalid-target-and-enums","invalid and nonexistent USER/POST/COMMENT, reason and unauthenticated request return contract errors with no rows",()->{
            long before=n("SELECT COUNT(*) FROM report");for(String type:List.of("USER","POST","COMMENT"))expect(api("POST","/api/reports",Map.of("targetType",type,"targetId",Long.MAX_VALUE,"reason","욕설·비방"),me),400,"INVALID_REQUEST");
            expect(api("POST","/api/reports",Map.of("targetType","BOGUS","targetId",target.id(),"reason","욕설·비방"),me),400,"VALIDATION_ERROR");
            expect(api("POST","/api/reports",Map.of("targetType","USER","targetId",target.id(),"reason","BOGUS"),me),400,"VALIDATION_ERROR");
            expect(api("POST","/api/reports",Map.of("targetType","USER","targetId",target.id(),"reason","욕설·비방"),null),401,"UNAUTHORIZED");check(n("SELECT COUNT(*) FROM report")==before,"Invalid report inserted");return Map.of("unchanged",before);
        });
    }
    static void administration(){
        test("admin-auth-and-invalid-request","guest/user/DB-suspended admin denied; invalid status/action rejected without history mutation",()->{
            expect(api("GET","/api/admin/reports",null,null),401,"UNAUTHORIZED");expect(api("GET","/api/admin/reports",null,me),403,"FORBIDDEN");
            expect(api("GET","/api/admin/members?status=BOGUS",null,adminA),400,"VALIDATION_ERROR");expect(process(Long.MAX_VALUE,"COMPLETE",adminA),400,"INVALID_REQUEST");
            long r=report("USER",member(UserRole.USER,false).id(),me,null);var before=state(r);expect(process(r,"BOGUS",adminA),400,"VALIDATION_ERROR");check(before.equals(state(r)),"Invalid action mutated");
            update("UPDATE user SET status='SUSPENDED' WHERE user_id=?",adminB.id());try{expect(process(r,"COMPLETE",adminB),401,"UNAUTHORIZED");}finally{update("UPDATE user SET status='ACTIVE' WHERE user_id=?",adminB.id());}
            return Map.of("historyPreserved",true);
        });
        test("admin-comment-sanction-audit-and-dashboard","DELETE_TARGET cascades reply status, preserves sibling, audit and content; dashboard active counts and report filters agree",()->{
            long post=createPost(target,false),c=comment(post,target,"reported target",false),r=reply(c,other),s=comment(post,other,"sibling",false),report=report("COMMENT",c,me,null);
            var before=api("GET","/api/admin/dashboard",null,adminA);expect(before,200,"");expect(process(report,"DELETE_TARGET",adminA),200,"");
            check(n("SELECT COUNT(*) FROM community_comment WHERE comment_id IN (?,?) AND status='DELETED'",c,r)==2&&value("SELECT status FROM community_comment WHERE comment_id=?",s).equals("ACTIVE"),"Comment cascade/sibling mismatch");
            var audit=state(report);check(audit.get("status").equals("COMPLETED")&&audit.get("resolution_action").equals("DELETE_TARGET")&&Objects.equals(audit.get("processed_by"),adminA.id()),"Audit mismatch");
            var after=api("GET","/api/admin/dashboard",null,adminA);expect(after,200,"");var beforeStats=before.data().path("stats");var afterStats=after.data().path("stats");check(afterStats.path("totalComments").asLong()==beforeStats.path("totalComments").asLong()-2,"Dashboard comments");check(afterStats.path("pendingReports").asLong()==beforeStats.path("pendingReports").asLong()-1,"Dashboard reports");check(afterStats.path("totalReports").asLong()==beforeStats.path("totalReports").asLong(),"Audit count lost");
            var detail=api("GET","/api/admin/reports/"+report,null,adminA);expect(detail,200,"");check(detail.data().path("targetContent").asString().equals("reported target"),"Deleted target audit lost");
            var filter=api("GET","/api/admin/reports?type=COMMENT&status=COMPLETED&q=reported",null,adminA);expect(filter,200,"");check(filter.data().path("totalElements").asLong()==1,"Report filter");return audit;
        });
        for(String action:List.of("COMPLETE","SUSPEND_USER","DELETE_POST","DELETE_COMMENT"))test("admin-parallel-"+action.toLowerCase(),"two real HTTP handlers wait on same MySQL REPORT lock; exactly one decision commits, loser INVALID_REQUEST",()->{
            var victim=member(UserRole.USER,true);long post=createPost(victim,false),c=comment(post,victim,"race",false);String type=action.equals("DELETE_POST")?"POST":action.equals("DELETE_COMMENT")?"COMMENT":"USER";
            long report=report(type,type.equals("POST")?post:type.equals("COMMENT")?c:victim.id(),me,null);String first=action.startsWith("DELETE_")?"DELETE_TARGET":action;
            try(var holder=open();var executor=Executors.newVirtualThreadPerTaskExecutor()){
                holder.setAutoCommit(false);rows(holder,"SELECT report_id FROM report WHERE report_id=? FOR UPDATE",report);
                var one=executor.submit(()->process(report,first,adminA));var two=executor.submit(()->process(report,"REJECT",adminB));
                try{awaitWaiters(2);}finally{holder.rollback();}
                var x=one.get(15,TimeUnit.SECONDS);var y=two.get(15,TimeUnit.SECONDS);check((x.status()==200)^(y.status()==200),"Exactly one winner");expect(x.status()==200?y:x,400,"INVALID_REQUEST");expect(x.status()==200?x:y,200,"");
                var state=state(report);check(state.get("processed_by").equals(x.status()==200?adminA.id():adminB.id()),"Winning admin lost");
                String expected=x.status()==200?first.equals("REJECT")?"REJECTED":"COMPLETED":"REJECTED";check(state.get("status").equals(expected),"Winning decision lost");
                check(value("SELECT status FROM user WHERE user_id=?",victim.id()).equals(x.status()==200&&first.equals("SUSPEND_USER")?"SUSPENDED":"ACTIVE"),"Wrong sanction");
                if(type.equals("POST"))check(value("SELECT status FROM community_post WHERE post_id=?",post).equals(x.status()==200?"DELETED":"ACTIVE"),"Post sanction mismatch");
                if(type.equals("COMMENT"))check(value("SELECT status FROM community_comment WHERE comment_id=?",c).equals(x.status()==200?"DELETED":"ACTIVE"),"Comment sanction mismatch");
                return Map.of("observedWaiters",2,"firstStatus",x.status(),"secondStatus",y.status(),"final",state);
            }
        });
        test("admin-predecessor-rollback-and-stale-refresh","rollback wakes HTTP waiter; repository refresh replaces a previously loaded PENDING under RR; reentrant decision preserved",()->{
            long id=report("USER",member(UserRole.USER,false).id(),me,null);
            try(var c=open();var executor=Executors.newVirtualThreadPerTaskExecutor()){
                c.setAutoCommit(false);exec(c,"UPDATE report SET status='COMPLETED',resolution_action='COMPLETE',processed_by=?,processed_at=created_at WHERE report_id=?",adminA.id(),id);
                var pending=executor.submit(()->process(id,"REJECT",adminB));try{awaitWaiters(1);}finally{c.rollback();}expect(pending.get(15,TimeUnit.SECONDS),200,"");check(state(id).get("status").equals("REJECTED"),"Rollback waiter failed");
            }
            long stale=report("USER",member(UserRole.USER,false).id(),me,null);
            var em=emf.createEntityManager();try{em.getTransaction().begin();var loaded=em.find(Report.class,stale);check(loaded.getStatus()==ReportStatus.PENDING,"Initial state");expect(process(stale,"COMPLETE",adminA),200,"");var repository=new AdminReportQueryRepository(em);check(repository.findReportByIdForUpdate(stale).orElseThrow().getStatus()==ReportStatus.COMPLETED,"Stale refresh did not read committed value");em.getTransaction().rollback();}finally{em.close();}
            long reentrant=report("USER",member(UserRole.USER,false).id(),me,null);
            var em2=emf.createEntityManager();try{em2.getTransaction().begin();var repository=new AdminReportQueryRepository(em2);var locked=repository.findReportByIdForUpdate(reentrant).orElseThrow();locked.process(ReportStatus.REJECTED,"NONE",em2.getReference(User.class,adminA.id()),LocalDateTime.now());check(repository.findReportByIdForUpdate(reentrant).orElseThrow().getStatus()==ReportStatus.REJECTED,"Reentrant refresh discarded decision");em2.getTransaction().rollback();check(state(reentrant).get("status").equals("PENDING"),"Explicit rollback failed");}finally{em2.close();}
            return Map.of("rollbackWaiter",id,"staleRefresh",stale,"reentrantRollback",reentrant);
        });
        test("admin-business-failure-rolls-back-sanctions","future creation time raises IllegalArgumentException/HTTP500 after user/post/comment sanction; audit and targets all roll back",()->{
            for(String type:List.of("USER","POST","COMMENT")){
                var victim=member(UserRole.USER,true);long post=createPost(victim,false),c=comment(post,victim,"rollback",false),reply=reply(c,other);long id=report(type,type.equals("USER")?victim.id():type.equals("POST")?post:c,me,null);
                update("UPDATE report SET created_at=DATE_ADD(created_at,INTERVAL 1 DAY) WHERE report_id=?",id);var before=state(id);
                expect(process(id,type.equals("USER")?"SUSPEND_USER":"DELETE_TARGET",adminA),500,"INTERNAL_SERVER_ERROR");check(before.equals(state(id)),"Failed history mutated");check(value("SELECT status FROM user WHERE user_id=?",victim.id()).equals("ACTIVE"),"User sanction leaked");check(value("SELECT status FROM community_post WHERE post_id=?",post).equals("ACTIVE"),"Post sanction leaked");check(n("SELECT COUNT(*) FROM community_comment WHERE comment_id IN (?,?) AND status='ACTIVE'",c,reply)==2,"Comment sanction leaked");
                update("UPDATE report SET created_at=DATE_SUB(created_at,INTERVAL 1 DAY) WHERE report_id=?",id);
            }return Map.of("rollbackTargets",3);
        });
        test("admin-lock-timeout-http409-and-recovery","session 3s InnoDB wait produces 1205/HTTP409, audit unchanged, retry succeeds after release; JPA 5s hint not claimed",()->{
            long id=report("USER",member(UserRole.USER,false).id(),me,null);var before=state(id);long start=System.nanoTime();
            try(var c=open()){c.setAutoCommit(false);rows(c,"SELECT report_id FROM report WHERE report_id=? FOR UPDATE",id);try{expect(process(id,"COMPLETE",adminA),409,"ACCOUNT_UPDATE_CONFLICT");}finally{c.rollback();}}
            long elapsed=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start);check(elapsed>=2500&&elapsed<10000,"Unexpected lock timeout duration");check(before.equals(state(id)),"Timeout mutated report");expect(process(id,"COMPLETE",adminA),200,"");return Map.of("sessionLockWaitSeconds",3,"elapsedMillis",elapsed,"recovered",true);
        });
        test("admin-member-status-invalidates-existing-token","SUSPENDED denies old JWT writes; ACTIVE restores; WITHDRAWN cannot restore",()->{
            var victim=member(UserRole.USER,true);String path="/api/admin/members/"+victim.id()+"/status";
            expect(api("PATCH",path,Map.of("status","SUSPENDED"),adminA),200,"");expect(api("POST","/api/reports",Map.of("targetType","USER","targetId",me.id(),"reason","욕설·비방"),victim),401,"UNAUTHORIZED");
            expect(api("PATCH",path,Map.of("status","ACTIVE"),adminA),200,"");expect(api("GET","/api/auth/me",null,victim),200,"");expect(api("PATCH",path,Map.of("status","WITHDRAWN"),adminA),200,"");expect(api("PATCH",path,Map.of("status","ACTIVE"),adminA),400,"INVALID_REQUEST");check(value("SELECT status FROM user WHERE user_id=?",victim.id()).equals("WITHDRAWN"),"Withdrawal restored");return Map.of("user",victim.id(),"finalStatus","WITHDRAWN");
        });
    }
    static void awaitWaiters(int count)throws Exception {long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);while(n("SELECT COUNT(*) FROM performance_schema.data_lock_waits")<count){check(System.nanoTime()<deadline,"MySQL row lock wait not observed");Thread.sleep(10);}}
    static void chatHttp(){
        test("chat-http-creation-commit-rollback-settings","real creation/reuse commits one room/two members; absent profile rolls all back; settings null/empty preserve",()->{
            Member reader=member(UserRole.USER,true),writer=member(UserRole.USER,true);var body=Map.of("category","ROOMMATE","targetUserId",writer.id());var made=api("POST","/api/chat/rooms",body,reader);expect(made,200,"");long id=made.data().path("id").asLong();var again=api("POST","/api/chat/rooms",body,reader);expect(again,200,"");check(again.data().path("id").asLong()==id&&n("SELECT COUNT(*) FROM chat_room_member WHERE chat_room_id=?",id)==2,"Room reuse/commit");
            expect(api("PATCH","/api/chat/rooms/"+id+"/settings",Map.of("muted",true),reader),200,"");var before=query("SELECT * FROM chat_room_member WHERE chat_room_id=? ORDER BY chat_room_member_id",id);expect(api("PATCH","/api/chat/rooms/"+id+"/settings",Map.of(),reader),200,"");var nulls=new HashMap<String,Object>();nulls.put("muted",null);nulls.put("pinned",null);expect(api("PATCH","/api/chat/rooms/"+id+"/settings",nulls,reader),200,"");check(before.equals(query("SELECT * FROM chat_room_member WHERE chat_room_id=? ORDER BY chat_room_member_id",id)),"Empty/null settings mutation");
            var missing=member(UserRole.USER,false);long rooms=n("SELECT COUNT(*) FROM chat_room"),members=n("SELECT COUNT(*) FROM chat_room_member");expect(api("POST","/api/chat/rooms",Map.of("category","ROOMMATE","targetUserId",missing.id()),reader),400,"INVALID_REQUEST");check(n("SELECT COUNT(*) FROM chat_room")==rooms&&n("SELECT COUNT(*) FROM chat_room_member")==members,"Failed creation committed");return Map.of("room",id,"members",2,"failedCreationRolledBack",true);
        });
        test("chat-single-jvm-bidirectional-socket-http-receipts-and-leave","real two WebSockets deliver both ways; polling read-only, exact-ID receipt commit; outsider and departed reader rejected",()->{
            Member reader=member(UserRole.USER,true),writer=member(UserRole.USER,true);var made=api("POST","/api/chat/rooms",Map.of("category","ROOMMATE","targetUserId",writer.id()),reader);expect(made,200,"");long room=made.data().path("id").asLong();
            try(var left=new MatchingExtraChecks.SocketClient(PORT,reader.token());var right=new MatchingExtraChecks.SocketClient(PORT,writer.token())){
                left.subscribe(room);right.subscribe(room);MatchingExtraChecks.probe(left,right,room);left.messages.clear();right.messages.clear();
                long before=n("SELECT COUNT(*) FROM chat_message WHERE chat_room_id=?",room);left.send(room,"reverse-direction-😀");MatchingExtraChecks.awaitStored(room,before+1);boolean reverse=false;long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);while(System.nanoTime()<deadline){String msg=right.messages.poll(100,TimeUnit.MILLISECONDS);if(msg!=null&&msg.contains("reverse-direction")){reverse=true;break;}}check(reverse,"Reverse delivery missing");
                var receiptBefore=query("SELECT message_id,is_read FROM chat_message WHERE chat_room_id=? ORDER BY message_id",room);expect(api("GET","/api/chat/rooms/"+room+"/messages",null,reader),200,"");check(receiptBefore.equals(query("SELECT message_id,is_read FROM chat_message WHERE chat_room_id=? ORDER BY message_id",room)),"Polling marked read");
                long incoming=n("SELECT MIN(message_id) FROM chat_message WHERE chat_room_id=? AND sender_id=?",room,writer.id());expect(api("POST","/api/chat/rooms/"+room+"/read",Map.of("messageIds",List.of(incoming,incoming)),reader),200,"");check(n("SELECT COUNT(*) FROM chat_message WHERE message_id=? AND is_read=true",incoming)==1,"Receipt did not commit");
                expect(api("POST","/api/chat/rooms/"+room+"/read",Map.of("messageIds",List.of(incoming,Long.MAX_VALUE)),reader),400,"INVALID_REQUEST");
                expect(api("GET","/api/chat/rooms/"+room+"/messages",null,me),403,"FORBIDDEN");
                left.messages.clear();expect(api("DELETE","/api/chat/rooms/"+room+"/members/me",null,reader),200,"");before=n("SELECT COUNT(*) FROM chat_message WHERE chat_room_id=?",room);right.send(room,"after-leave");MatchingExtraChecks.awaitStored(room,before+1);check(left.messages.poll(700,TimeUnit.MILLISECONDS)==null,"Departed subscriber received");
                expect(api("GET","/api/chat/rooms/"+room+"/messages",null,reader),403,"FORBIDDEN");expect(api("POST","/api/chat/rooms/"+room+"/read",Map.of("messageIds",List.of(incoming)),reader),403,"FORBIDDEN");check(n("SELECT COUNT(*) FROM chat_message WHERE chat_room_id=?",room)==before+1,"History lost");return Map.of("room",room,"bidirectional",true,"departedDeliveryBlocked",true);
            }
        });
        test("chat-community-anonymous-source-http","anonymous POST/COMMENT rooms derive target, separate sources, hide counterpart IDs and preserve exact source FK",()->{
            long post=createPost(target,true),comment=comment(post,target,"anonymous source",true);var postBody=Map.of("category","COMMUNITY","sourceFrom","POST","sourcePostId",post,"targetUserId",other.id());
            var rp=api("POST","/api/chat/rooms",postBody,me);expect(rp,200,"");long id=rp.data().path("id").asLong();var rc=api("POST","/api/chat/rooms",Map.of("category","COMMUNITY","sourceFrom","COMMENT","sourcePostId",post,"sourceCommentId",comment,"targetUserId",other.id()),me);expect(rc,200,"");long cid=rc.data().path("id").asLong();check(id!=cid,"Anonymous sources merged");check(n("SELECT COUNT(*) FROM chat_room_member WHERE chat_room_id IN (?,?) AND user_id=?",id,cid,target.id())==2,"Client-controlled target");
            check(rp.data().path("counterpart").path("id").isMissingNode()||rp.data().path("counterpart").path("id").isNull(),"Anonymous counterpart leaked");
            return Map.of("postRoom",id,"commentRoom",cid,"sources",query("SELECT room_key,source_post_id,source_comment_id FROM chat_room WHERE chat_room_id IN (?,?) ORDER BY chat_room_id",id,cid));
        });
    }
    interface SqlWork {void run(Connection c)throws Exception;}
    static void rejected(Set<Integer> codes,SqlWork work)throws Exception {try(var c=open()){c.setAutoCommit(false);try{work.run(c);throw new AssertionError("Expected physical constraint");}catch(SQLException expected){check(codes.contains(expected.getErrorCode()),"Unexpected SQL constraint code "+expected.getErrorCode());events.add(Map.of("kind","expectedConstraint","vendorCode",expected.getErrorCode(),"sqlState",expected.getSQLState()));}finally{c.rollback();}}}
    static void physicalConstraints(){
        test("community-report-chat-physical-constraints-and-rollback","actual PK/FK/UNIQUE/NOT NULL reject SQL; preceding updates rolled back; no API-only invariant misreported as UNIQUE",()->{
            long post=createPost(me,true),comment=comment(post,other,"physical",true),r=report("COMMENT",comment,target,null);var before=state(r);
            rejected(Set.of(1062),c->exec(c,"INSERT INTO report SELECT * FROM report WHERE report_id=?",r));
            rejected(Set.of(1048),c->exec(c,"UPDATE report SET reporter_id=NULL WHERE report_id=?",r));
            rejected(Set.of(1452,1216),c->{exec(c,"UPDATE report SET description='must rollback' WHERE report_id=?",r);exec(c,"UPDATE report SET comment_id=? WHERE report_id=?",Long.MAX_VALUE,r);});
            rejected(Set.of(1451,1217),c->exec(c,"DELETE FROM community_comment WHERE comment_id=?",comment));
            rejected(Set.of(1062),c->exec(c,"INSERT INTO community_anonymous_author(post_id,author_id,anonymous_number) SELECT post_id,author_id,anonymous_number FROM community_anonymous_author WHERE post_id=?",post));
            check(before.equals(state(r)),"Constraint failure leaked prior update");
            long room=n("SELECT MIN(chat_room_id) FROM chat_room"),member=n("SELECT MIN(chat_room_member_id) FROM chat_room_member WHERE chat_room_id=?",room);
            rejected(Set.of(1062),c->exec(c,"INSERT INTO chat_room_member(chat_room_id,user_id,is_left,is_muted,is_pinned,joined_at) SELECT chat_room_id,user_id,is_left,is_muted,is_pinned,joined_at FROM chat_room_member WHERE chat_room_member_id=?",member));
            rejected(Set.of(1452,1216),c->exec(c,"INSERT INTO chat_message(chat_room_id,sender_id,content,sent_at,is_read) VALUES(?,?,?,NOW(),false)",Long.MAX_VALUE,me.id(),"orphan"));
            rejected(Set.of(1048),c->exec(c,"INSERT INTO chat_message(chat_room_id,sender_id,content,sent_at,is_read) VALUES(?,?,NULL,NOW(),false)",room,me.id()));
            var constraints=query("SELECT table_name,constraint_name,constraint_type FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name IN ('community_post','community_comment','community_anonymous_author','report','chat_room','chat_room_member','chat_message') ORDER BY table_name,constraint_name");write(output.resolve("constraints.json"),constraints);return Map.of("rejectedStatements",8,"historyPreserved",true,"constraints",constraints);
        });
    }
    static void migrations()throws Exception {
        // All tables are newly created and empty in this run, before server/fixtures start.
        for(String table:List.of("community_anonymous_author","post_image_cleanup"))check(n("SELECT COUNT(*) FROM "+table)==0,"Legacy preparation refuses populated table");
        update("DROP TABLE community_anonymous_author");update("DROP TABLE post_image_cleanup");update("DROP INDEX idx_post_image_url ON post_image");
        var proof=new ArrayList<Object>();
        for(String name:List.of("20261013_community_anonymous_authors.sql","20261019_post_image_cleanup.sql")){
            String original=Files.readString(repo.resolve("backend/migrations/"+name));String sql=original;
            for(String table:List.of("COMMUNITY_ANONYMOUS_AUTHOR","COMMUNITY_POST","POST_IMAGE_CLEANUP","POST_IMAGE","USER"))sql=sql.replaceAll("\\b"+table+"\\b",table.toLowerCase(Locale.ROOT));
            String executable=sql.replaceAll("(?m)^--.*$","");try(var c=open()){for(String statement:executable.split(";"))if(!statement.isBlank())exec(c,statement);}
            proof.add(Map.of("source",name,"sourceSha256",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(original.getBytes(StandardCharsets.UTF_8))),"identifierAdaptation","uppercase table tokens to observed lowercase physical names; DDL/data logic unchanged","executedSql",executable));
        }
        check(n("SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='community_anonymous_author' AND non_unique=0")==3,"Anonymous PK/two UNIQUE");
        check(n("SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='post_image' AND index_name='idx_post_image_url'")==1,"Image URL index");
        check(n("SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='post_image_cleanup' AND index_name='idx_image_cleanup_due'")==2,"Cleanup index order");
        write(output.resolve("migrations.json"),proof);
    }
    static Map<String,Object> finalState()throws Exception {
        var counts=new TreeMap<String,Long>();for(String table:List.of("user","community_post","community_comment","community_anonymous_author","post_image_cleanup","report","chat_room","chat_room_member","chat_message"))counts.put(table,n("SELECT COUNT(*) FROM "+table));
        check(n("SELECT COUNT(*) FROM report r LEFT JOIN user u ON u.user_id=r.reporter_id LEFT JOIN user t ON t.user_id=r.target_user_id WHERE u.user_id IS NULL OR t.user_id IS NULL")==0,"Report orphan");
        check(n("SELECT COUNT(*) FROM report WHERE (status='PENDING' AND (processed_by IS NOT NULL OR processed_at IS NOT NULL)) OR (status<>'PENDING' AND (processed_by IS NULL OR processed_at IS NULL))")==0,"Audit invariant");
        return Map.of("rowCounts",counts,"reports",query("SELECT report_id,report_type,status,resolution_action,processed_by,created_at,processed_at FROM report ORDER BY report_id"),"reportOrphans",0,"auditInvariantViolations",0,"containerRemovalOwnsAllRows",true);
    }
}
