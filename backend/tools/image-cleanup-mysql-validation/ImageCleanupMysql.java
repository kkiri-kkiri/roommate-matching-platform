package kmi;

import static kmi.MatchingMultiSupport.*;
import static kmi.ImageCleanupRuntime.*;
import com.kkirikkiri.community.post.entity.*;
import com.kkirikkiri.community.post.service.PostImageStorage;
import com.kkirikkiri.user.entity.User;
import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.support.TransactionTemplate;

/** Opt-in MySQL-only boundaries. PREPARE cannot open JDBC or construct a Spring context. */
public final class ImageCleanupMysql {
    static final List<String> CASES=List.of("atomic-unlink-enqueue-commit","outer-rollback",
        "requires-new-read-committed","filesystem-failure-retry","innodb-timeout-recovery",
        "delete-before-commit-rollback","same-job-concurrent-existing-file","duplicate-url-existing-file",
        "active-deleted-reference-protection","deferred-deadline-after-lock","retry-boundary","batch-100",
        "real-jvm-restart-scheduler");
    static Path output, root;
    static ImageCleanupRuntime runtime;
    static final List<Map<String,Object>> results=new ArrayList<>();
    static final List<Map<String,Object>> checkpoints=new ArrayList<>();
    static final List<Process> processes=new ArrayList<>();
    static final List<Future<?>> activeTasks=new ArrayList<>();
    static final Set<String> knownFiles=new TreeSet<>();
    static ExecutorService executor;
    static boolean approved() { return "CREATE_NEW_ISOLATED_ONLY".equals(System.getenv("ICMV_APPROVED")); }
    static void requireApproved() {
        check(approved(),"Use the explicitly approved runner; no JDBC permitted otherwise");
        check(System.getenv("ICMV_OWNER")!=null && System.getenv("ICMV_OWNER").matches("[a-f0-9]{32}"),"Invalid owner");
        check("kkiri-image-cleanup-mysql-20261021".equals(System.getenv("ICMV_CONTAINER")),"Wrong container");
        String suffix=System.getenv("ICMV_OWNER").substring(0,12);
        if(System.getenv("KM_DATABASE")!=null) check(("kkiriimgcleanup"+suffix).equals(System.getenv("KM_DATABASE")),"Schema is not owned by this run");
        if(System.getenv("KM_USER")!=null) check(("imgcleanup_"+suffix).equals(System.getenv("KM_USER")),"Account is not owned by this run");
    }
    public static void strictGuard(Connection c) throws Exception {
        requireApproved();
        check(Set.of("init","runtime").contains(Objects.toString(System.getenv("ICMV_PHASE"),"")),"Invalid grant phase");
        var row=rows(c,"SELECT CURRENT_USER() account,CURRENT_ROLE() role,@@port port").getFirst();
        check((user()+"@%").equals(row.get("account")) && "NONE".equals(row.get("role"))
            && ((Number)row.get("port")).intValue()==3306,"Wrong account/role/port");
        var privileges=new HashSet<String>(); boolean scope=false, usage=false, observer=false;
        var grants=rows(c,"SHOW GRANTS"); check(grants.size()==3,"Unexpected grant count");
        for(var grant:grants) {
            String text=grant.values().iterator().next().toString();
            check(!text.contains("GRANT OPTION")&&!text.contains("ALL PRIVILEGES"),"Excess privilege");
            String suffix=" ON `"+db()+"`.* TO `"+user()+"`@`%`";
            if(text.endsWith(suffix)) { scope=true; privileges.addAll(Arrays.asList(text.substring(6,text.length()-suffix.length()).split(", "))); }
            else if(text.equals("GRANT USAGE ON *.* TO `"+user()+"`@`%`")) usage=true;
            else if(text.equals("GRANT SELECT ON `performance_schema`.`data_lock_waits` TO `"+user()+"`@`%`")) observer=true;
            else throw new AssertionError("Unexpected grant scope");
        }
        var expected=new HashSet<>(Set.of("SELECT","INSERT","UPDATE","DELETE"));
        if("init".equals(System.getenv("ICMV_PHASE"))) expected.addAll(Set.of("CREATE","ALTER","INDEX","REFERENCES"));
        check(scope&&usage&&observer&&privileges.equals(expected),"Nonminimal grants");
    }
    static void quiet() {
        ((ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME))
            .setLevel(ch.qos.logback.classic.Level.OFF);
    }
    public static void main(String[] args) {
        try { execute(args); }
        catch(Throwable failure) {
            // Never print a JDBC exception message or credentials embedded in a driver message.
            System.err.println(JSON.writeValueAsString(Map.of("status","INCOMPLETE","error",safeError(failure))));
            System.exit(2);
        }
    }
    static Map<String,Object> safeError(Throwable failure) {
        var causes=new ArrayList<Object>();
        for(Throwable t=failure;t!=null;t=t.getCause()) {
            var entry=new LinkedHashMap<String,Object>(); entry.put("class",t.getClass().getName());
            if(t instanceof AssertionError && t.getCause()==null) entry.put("check",Objects.toString(t.getMessage(),""));
            if(t instanceof SQLException sql) { entry.put("vendorCode",sql.getErrorCode()); entry.put("sqlState",Objects.toString(sql.getSQLState(),"")); }
            entry.put("frames",Arrays.stream(t.getStackTrace()).limit(6).map(Object::toString).toList()); causes.add(entry);
        }
        return Map.of("causes",causes);
    }
    static void execute(String[] args) throws Exception {
        check(args.length>=1,"Mode required"); quiet();
        if(args[0].equals("PREPARE")) {
            // Class linkage and policy/catalog validation only; deliberately no DataSource/context.
            check(CASES.size()==new HashSet<>(CASES).size(),"Duplicate cases");
            check(PostImageStorage.filenameFromUrl("/api/post-images/safe.png").isPresent(),"Storage contract changed");
            check(PostImageStorage.filenameFromUrl("/api/post-images/../unsafe.png").isEmpty(),"Unsafe filename accepted");
            Class.forName("com.kkirikkiri.community.post.service.PostImageCleanupWorker",false,ImageCleanupMysql.class.getClassLoader());
            System.out.println(JSON.writeValueAsString(Map.of("status","PREPARED_NOT_EXECUTED","scenarioCount",CASES.size(),
                "scenarios",CASES,"mysqlExecuted",0,"jdbcConnections",0,"mockMysqlPasses",0,
                "springMetadata",List.of(prepareMetadata(false),prepareMetadata(true)))));
            return;
        }
        requireApproved();
        check(args.length==2,"Expected mode and owned run directory");
        output=Path.of(args[1]).toAbsolutePath().normalize();
        Path expected=Path.of(System.getenv("ICMV_RUN")).toAbsolutePath().normalize();
        Path compiled=Path.of(ImageCleanupMysql.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath().normalize();
        check(output.getParent().equals(compiled.getParent()),"Run must be under this compiled tool's build directory");
        check(output.equals(expected)&&output.getFileName().toString().equals("run-"+System.getenv("ICMV_OWNER")),"Wrong run directory");
        check(output.toRealPath().equals(output),"Run directory must not redirect outside owned path");
        check(Files.readString(output.resolve("owner.txt")).trim().equals(System.getenv("ICMV_OWNER")),"Owner marker missing");
        root=output.resolve("images"); Files.createDirectories(root);
        check(root.toRealPath().equals(root)&&!Files.isSymbolicLink(root),"Storage root must not redirect");
        switch(args[0]) {
            case "PROVISION" -> provision();
            case "INIT" -> initialize();
            case "VERIFY" -> verify();
            case "PENDING" -> pending();
            case "SCHEDULE" -> schedule();
            default -> throw new AssertionError("Unknown mode");
        }
    }
    static void provision() throws Exception {
        String suffix=System.getenv("ICMV_OWNER").substring(0,12);
        String database="kkiriimgcleanup"+suffix, account="imgcleanup_"+suffix;
        String password=UUID.randomUUID().toString().replace("-","");
        // Root has no existing-volume mount and is only used for the brand new schema/account/grants.
        try(var c=DriverManager.getConnection(url(""),"root",System.getenv("ICMV_ROOT_PASSWORD"))) {
            identity(c);
            check(number(c,"SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name=?",database)==0,"Schema collision");
            check(number(c,"SELECT COUNT(*) FROM mysql.user WHERE user=?",account)==0,"Account collision");
            exec(c,"CREATE DATABASE `"+database+"` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
            exec(c,"CREATE USER '"+account+"'@'%' IDENTIFIED BY '"+password+"'");
            exec(c,"GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES ON `"+database+"`.* TO '"+account+"'@'%'");
            exec(c,"GRANT SELECT ON performance_schema.data_lock_waits TO '"+account+"'@'%'");
        }
        write(output.resolve("provision.json"),Map.of("database",database,"account",account,"newEmptySchema",true,
            "runtimePrivileges",List.of("SELECT","INSERT","UPDATE","DELETE"),"observerPrivilege","performance_schema.data_lock_waits SELECT"));
        check(child("INIT",database,account,password,"init",45).exitValue()==0,"Schema initialization failed");
        try(var c=DriverManager.getConnection(url(""),"root",System.getenv("ICMV_ROOT_PASSWORD"))) {
            identity(c); exec(c,"REVOKE CREATE, ALTER, INDEX, REFERENCES ON `"+database+"`.* FROM '"+account+"'@'%'");
        }
        int exit=child("VERIFY",database,account,password,"runtime",240).exitValue();
        check(exit==0,"Validation has failure, finding or incomplete evidence; inspect summary.json");
    }
    static String javaBinary() { return Path.of(System.getProperty("java.home"),"bin","java.exe").toString(); }
    static Process child(String mode,String database,String account,String password,String phase,int seconds) throws Exception {
        var builder=new ProcessBuilder(javaBinary(),"-Duser.timezone=Asia/Seoul","-Xmx512m","-cp",
            System.getProperty("java.class.path"),ImageCleanupMysql.class.getName(),mode,output.toString());
        var environment=builder.environment();
        environment.keySet().removeIf(k->k.startsWith("SPRING_")||k.startsWith("SMTP_")||k.startsWith("AI_")||k.startsWith("JWT_")
            ||k.startsWith("DB_")||Set.of("ICMV_ROOT_PASSWORD","MYSQL_ROOT_PASSWORD","JAVA_TOOL_OPTIONS","JDK_JAVA_OPTIONS","_JAVA_OPTIONS").contains(k));
        environment.put("KM_DATABASE",database); environment.put("KM_USER",account); environment.put("KM_PASSWORD",password);
        environment.put("ICMV_PHASE",phase);
        builder.redirectErrorStream(true).redirectOutput(output.resolve("child-"+mode.toLowerCase(Locale.ROOT)+".log").toFile());
        var process=builder.start(); processes.add(process);
        if(!process.waitFor(seconds,TimeUnit.SECONDS)) { stopOwned(process); throw new AssertionError("Child JVM timeout: "+mode); }
        write(output.resolve("process-"+mode.toLowerCase(Locale.ROOT)+".json"),Map.of("mode",mode,"pid",process.pid(),"exitCode",process.exitValue(),"stopped",!process.isAlive()));
        return process;
    }
    static void stopOwned(Process process) throws Exception {
        var descendants=process.descendants().toList();
        for(var handle:descendants.reversed()) if(handle.isAlive()) handle.destroyForcibly();
        if(process.isAlive()) process.destroyForcibly();
        check(process.waitFor(10,TimeUnit.SECONDS),"Owned child did not terminate");
        for(var handle:descendants) if(handle.isAlive()) handle.onExit().get(10,TimeUnit.SECONDS);
    }
    static Process child(String mode,int seconds) throws Exception {
        return child(mode,db(),user(),System.getenv("KM_PASSWORD"),"runtime",seconds);
    }
    static void initialize() throws Exception {
        try(var c=open()) { check(number(c,"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()")==0,"DDL requires empty owned schema"); }
        ddl="create-only";
        try(var rt=new ImageCleanupRuntime(root,false);var c=open()) {
            check(number(c,"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND engine<>'InnoDB'")==0,"Non-InnoDB table");
            var indexes=rows(c,"SELECT table_name,index_name,seq_in_index,column_name FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name IN ('post_image','post_image_cleanup') ORDER BY table_name,index_name,seq_in_index");
            check(indexes.stream().anyMatch(r->"idx_image_cleanup_due".equals(r.get("INDEX_NAME"))||"idx_image_cleanup_due".equals(r.get("index_name"))),"Missing due index");
            write(output.resolve("schema.json"),Map.of("ddlSource","current Entity metadata on new empty schema only",
                "manualMigrationRetested",false,"indexes",indexes,"tableCount",number(c,"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()")));
        }
    }
    static void verify() throws Exception {
        ddl="validate"; executor=Executors.newFixedThreadPool(3);
        try {
            try(var c=open()) { write(output.resolve("environment.json"),Map.of("identity",rows(c,"SELECT VERSION() version,DATABASE() db,CURRENT_USER() account,@@server_uuid uuid,@@hostname hostname,@@session.time_zone zone,@@transaction_isolation isolation"),"grants",rows(c,"SHOW GRANTS"),"jvmZone",ZoneId.systemDefault().toString(),"httpServers",0,"smtpClients",0,"aiClients",0)); }
            runtime=new ImageCleanupRuntime(root,false);
            run(CASES.get(0),ImageCleanupMysql::atomicCommit);
            run(CASES.get(1),ImageCleanupMysql::outerRollback);
            run(CASES.get(2),ImageCleanupMysql::independent);
            run(CASES.get(3),ImageCleanupMysql::fileRetry);
            run(CASES.get(4),ImageCleanupMysql::timeout);
            run(CASES.get(5),ImageCleanupMysql::crashWindow);
            run(CASES.get(6),ImageCleanupMysql::sameJob);
            run(CASES.get(7),ImageCleanupMysql::duplicateUrl);
            run(CASES.get(8),ImageCleanupMysql::references);
            run(CASES.get(9),ImageCleanupMysql::deadlineGap);
            run(CASES.get(10),ImageCleanupMysql::boundary);
            run(CASES.get(11),ImageCleanupMysql::batch);
            runtime.close(); runtime=null;
            run(CASES.get(12),ImageCleanupMysql::restart);
        } catch(Throwable failure) { results.add(Map.of("name","harness","result","INCOMPLETE","error",safeError(failure))); }
        finally {
            executor.shutdownNow();
            boolean stopped=executor.awaitTermination(25,TimeUnit.SECONDS);
            if(!stopped) results.add(Map.of("name","worker-shutdown","result","INCOMPLETE"));
            for(var process:processes) if(process.isAlive()) stopOwned(process);
            if(runtime!=null && stopped) runtime.close();
            write(output.resolve("cases.json"),results);
            boolean finalStateAvailable=true;
            try { write(output.resolve("final-state.json"),snapshot()); }
            catch(Throwable failure) { finalStateAvailable=false; write(output.resolve("final-state.json"),Map.of("status","INCOMPLETE","error",safeError(failure))); }
            var scenarios=results.stream().filter(r->CASES.contains(r.get("name"))).toList();
            var summary=new LinkedHashMap<String,Object>(); summary.put("planned",CASES.size()); summary.put("executed",scenarios.size());
            summary.put("passed",scenarios.stream().filter(r->r.get("result").equals("PASS")).count());
            summary.put("failed",scenarios.stream().filter(r->r.get("result").equals("FAIL")).count());
            summary.put("findings",scenarios.stream().filter(r->r.get("result").equals("FINDING")).count());
            summary.put("incomplete",scenarios.stream().filter(r->r.get("result").equals("INCOMPLETE")).count());
            summary.put("notExecuted",CASES.size()-scenarios.size()); summary.put("harnessErrors",results.size()-scenarios.size());
            summary.put("skipped",0); summary.put("h2Executed",0); summary.put("realMysql",true);
            summary.put("finalStateAvailable",finalStateAvailable); summary.put("workerExecutorStopped",stopped);
            summary.put("allChildProcessesStopped",processes.stream().noneMatch(Process::isAlive));
            write(output.resolve("summary.json"),summary);
            if(!finalStateAvailable || !stopped) System.exit(2);
        }
        if(results.size()!=CASES.size()||results.stream().anyMatch(r->!r.get("result").equals("PASS"))) System.exit(2);
    }
    interface Work { void run() throws Exception; }
    static final class Finding extends AssertionError { Finding(String message) { super(message); } }
    static void run(String name,Work action) throws Exception {
        clean(); checkpoints.clear(); observations.clear(); activeTasks.clear();
        Map<String,Object> record=new LinkedHashMap<>(); record.put("name",name); record.put("started",Instant.now().toString());
        try { action.run(); check(!checkpoints.isEmpty(),"Missing pre-action state"); record.put("result","PASS"); }
        catch(Finding finding) { record.put("result","FINDING"); record.put("error",safeError(finding)); }
        catch(Throwable failure) { record.put("result","FAIL"); record.put("error",safeError(failure)); }
        finally {
            var gate=storageGate; if(gate!=null) gate.release.countDown();
            boolean quiescent=true;
            var taskErrors=new ArrayList<Object>();
            for(var task:activeTasks) {
                try { task.get(25,TimeUnit.SECONDS); }
                catch(TimeoutException failure) { quiescent=false; taskErrors.add(safeError(failure)); }
                catch(ExecutionException failure) { taskErrors.add(safeError(failure)); }
            }
            if(!taskErrors.isEmpty()) { record.put("result",quiescent?"FAIL":"INCOMPLETE"); record.put("asyncErrors",taskErrors); }
            var unexpectedSql=observations.stream().filter(o->"sql-error".equals(o.get("kind")))
                .filter(o->!(name.equals("innodb-timeout-recovery")&&"timeout".equals(o.get("actor"))&&Integer.valueOf(1205).equals(o.get("vendorCode")))).toList();
            if(!unexpectedSql.isEmpty()) { record.put("result","FAIL"); record.put("unexpectedSqlErrors",unexpectedSql); }
            storageGate=null; beforeJobLock=null; timeoutActor=false;
            actor.remove(); record.put("checkpoints",List.copyOf(checkpoints)); record.put("observations",List.copyOf(observations));
            try { record.put("finalSeparateJdbcState",snapshot()); }
            catch(Throwable failure) { record.put("result","INCOMPLETE"); record.put("finalStateError",safeError(failure)); }
            results.add(record);
            write(output.resolve(name+".json"),record);
            check(quiescent,"Worker still active; refuse next scenario or cleanup");
        }
    }
    static Map<String,Object> snapshot() throws Exception {
        check(root.toRealPath().equals(root)&&!Files.isSymbolicLink(root),"Storage root must not redirect");
        try(var c=open()) {
            var files=new ArrayList<Object>();
            try(var paths=Files.walk(root)) { for(var p:paths.filter(p->!p.equals(root)).sorted().toList()) {
                check(!Files.isSymbolicLink(p),"Unexpected storage link");
                if(Files.isRegularFile(p)) knownFiles.add(root.relativize(p).toString());
                var info=new LinkedHashMap<String,Object>(); info.put("path",root.relativize(p).toString());
                info.put("directory",Files.isDirectory(p)); if(Files.isRegularFile(p)) info.put("bytes",Files.size(p)); files.add(info);
            } }
            return Map.of("jobs",rows(c,"SELECT * FROM post_image_cleanup ORDER BY cleanup_id"),
                "images",rows(c,"SELECT post_image_id,post_id,image_url FROM post_image ORDER BY post_image_id"),
                "posts",rows(c,"SELECT post_id,status FROM community_post ORDER BY post_id"),
                "users",number(c,"SELECT COUNT(*) FROM `user`"),"files",files,
                "knownFileExistence",knownFiles.stream().map(name->Map.of("path",name,
                    "exists",Files.exists(root.resolve(name),LinkOption.NOFOLLOW_LINKS))).toList(),
                "connectionId",number(c,"SELECT CONNECTION_ID()"));
        }
    }
    static void before() throws Exception { checkpoints.add(Map.of("stage","before-worker-or-commit","state",snapshot())); }
    static void checkpoint(String stage) throws Exception { checkpoints.add(Map.of("stage",stage,"state",snapshot())); }
    static long n(String sql,Object...args) throws Exception { try(var c=open()) { return number(c,sql,args); } }
    static void update(String sql,Object...args) throws Exception { try(var c=open()) { exec(c,sql,args); } }
    static void setDue(long id,LocalDateTime due) {
        // Bind through the same mapped LocalDateTime attribute as production findDueIds.
        // Do not substitute a JDBC wall-clock literal for Hibernate's timestamp binding policy.
        runtime.tx(()->{runtime.em.createQuery("update PostImageCleanup j set j.nextAttemptAt=:due where j.cleanupId=:id")
            .setParameter("due",due).setParameter("id",id).executeUpdate();return null;});
    }
    static LocalDateTime retryDeadline(long id) {
        return runtime.tx(()->runtime.jobs.findById(id).orElseThrow().getNextAttemptAt());
    }
    static void clean() throws Exception {
        Path expected=output.resolve("images").toAbsolutePath().normalize();
        check(root.equals(expected)&&root.startsWith(output)&&root.toRealPath().equals(root)
            &&Files.readString(output.resolve("owner.txt")).trim().equals(System.getenv("ICMV_OWNER")),"Unsafe cleanup root");
        try(var c=open()) {
            // This database was created by this run; never accept an existing database option.
            for(String table:List.of("post_image_cleanup","post_image","community_post","user")) exec(c,"DELETE FROM `"+table+"`");
        }
        try(var paths=Files.walk(root)) { var owned=paths.filter(p->!p.equals(root)).sorted(Comparator.reverseOrder()).toList();
            for(var p:owned) { check(p.toAbsolutePath().normalize().startsWith(root)&&!Files.isSymbolicLink(p),"Unsafe cleanup path"); Files.delete(p); }
        }
        knownFiles.clear();
    }
    record Fixture(long post,long image,String url,Path path) { }
    static Fixture fixture() throws Exception {
        String filename=UUID.randomUUID()+".png"; Path path=root.resolve(filename); Files.write(path,new byte[]{1,2,3},StandardOpenOption.CREATE_NEW);
        knownFiles.add(filename);
        return runtime.tx(()->{
            var user=new User("validation-not-a-real-credential",UUID.randomUUID()+"@example.invalid"); runtime.em.persist(user);
            var post=new CommunityPost(user,null,"자유게시판","worker fixture","fixture",false); runtime.em.persist(post);
            var image=new PostImage(post,PostImageStorage.URL_PREFIX+filename,filename,0); runtime.em.persist(image); runtime.em.flush();
            return new Fixture(post.getPostId(),image.getPostImageId(),image.getImageUrl(),path);
        });
    }
    static long pending(Fixture f) {
        // Fixture deliberately models a committed queue before callback delivery, not a mocked worker.
        return runtime.tx(()->{runtime.em.remove(runtime.em.find(PostImage.class,f.image()));
            var job=new PostImageCleanup(f.url()); runtime.em.persist(job); runtime.em.flush(); return job.getCleanupId();});
    }
    static void actor(String name,Runnable action) { actor.set(name); try { action.run(); } finally { actor.remove(); } }
    static Future<?> async(String name,Runnable action) {
        var task=executor.submit(()->actor(name,action)); activeTasks.add(task); return task;
    }
    static void await(BooleanSupplier ready,String message,int seconds) throws Exception {
        long limit=System.nanoTime()+TimeUnit.SECONDS.toNanos(seconds);
        while(!ready.getAsBoolean()&&System.nanoTime()<limit) Thread.sleep(25);
        check(ready.getAsBoolean(),message);
    }
    static int waits() {
        try(var c=open()) {
            var evidence=rows(c,"SELECT REQUESTING_ENGINE_TRANSACTION_ID,BLOCKING_ENGINE_TRANSACTION_ID FROM performance_schema.data_lock_waits");
            observations.add(Map.of("kind","actual-innodb-lock-waits","waits",evidence)); return evidence.size();
        } catch(Exception e) { throw new AssertionError("Lock observation unavailable"); }
    }
    static void atomicCommit() throws Exception {
        var f=fixture(); before();
        runtime.tx(()->{runtime.em.remove(runtime.em.find(PostImage.class,f.image())); runtime.cleanup.enqueue(f.url()); runtime.em.flush();
            check(runtime.jobs.count()==1,"Queue not in caller transaction");
            try { check(n("SELECT COUNT(*) FROM post_image WHERE post_image_id=?",f.image())==1,"Uncommitted unlink leaked");
                check(n("SELECT COUNT(*) FROM post_image_cleanup")==0,"Uncommitted queue leaked"); } catch(Exception e) { throw new AssertionError(e); }
            check(Files.exists(f.path()),"File removed before commit"); return null; });
        check(n("SELECT COUNT(*) FROM post_image")==0&&n("SELECT COUNT(*) FROM post_image_cleanup")==0&&!Files.exists(f.path()),"Commit/callback cleanup failed");
    }
    static void outerRollback() throws Exception {
        var f=fixture(); before();
        new TransactionTemplate(runtime.manager).executeWithoutResult(status->{
            runtime.em.remove(runtime.em.find(PostImage.class,f.image())); runtime.cleanup.enqueue(f.url()); runtime.em.flush();
            check(runtime.jobs.count()==1&&Files.exists(f.path()),"Pending rollback state wrong"); status.setRollbackOnly(); });
        check(n("SELECT COUNT(*) FROM post_image")==1&&n("SELECT COUNT(*) FROM post_image_cleanup")==0&&Files.exists(f.path()),"Outer rollback lost association/file");
    }
    static void independent() throws Exception {
        var f=fixture(); long id=pending(f); before(); var outer=new long[2];
        new TransactionTemplate(runtime.manager).executeWithoutResult(status->{
            runtime.em.unwrap(org.hibernate.Session.class).doWork(c->{outer[0]=number(c,"SELECT CONNECTION_ID()"); outer[1]=c.getTransactionIsolation();});
            runtime.em.createQuery("select count(i) from PostImage i",Long.class).getSingleResult();
            var transientUser=new User("fixture",UUID.randomUUID()+"@example.invalid"); runtime.em.persist(transientUser); runtime.em.flush();
            try { update("INSERT INTO post_image(post_id,image_url,original_name,sort_order,created_at) VALUES(?,?,?,0,?)",
                f.post(),f.url(),"shared.png",LocalDateTime.now()); } catch(Exception e) { throw new AssertionError(e); }
            actor("independent",()->runtime.worker.process(id)); status.setRollbackOnly(); });
        check(n("SELECT COUNT(*) FROM post_image_cleanup")==0&&Files.exists(f.path())&&n("SELECT COUNT(*) FROM `user`")==1,"REQUIRES_NEW independent commit/reference visibility failed");
        check(outer[1]==Connection.TRANSACTION_REPEATABLE_READ,"Outer baseline not RR");
        check(observations.stream().anyMatch(o->"independent".equals(o.get("actor"))&&Integer.valueOf(Connection.TRANSACTION_READ_COMMITTED).equals(o.get("isolation"))
            &&"READ-COMMITTED".equals(o.get("sessionIsolation"))
            &&((Number)o.get("connectionId")).longValue()!=outer[0]),"No actual RC/new connection evidence");
        checkpoints.add(Map.of("stage","outer-transaction","connectionId",outer[0],"isolation",outer[1]));
    }
    static void blockedFile(Fixture f) throws Exception { Files.delete(f.path()); Files.createDirectory(f.path()); Files.write(f.path().resolve("blocker"),new byte[]{9}); }
    static void repair(Fixture f) throws Exception { Files.delete(f.path().resolve("blocker")); Files.delete(f.path()); Files.write(f.path(),new byte[]{1,2,3}); }
    static void fileRetry() throws Exception {
        var f=fixture(); blockedFile(f); before();
        runtime.tx(()->{runtime.em.remove(runtime.em.find(PostImage.class,f.image()));runtime.cleanup.enqueue(f.url());return null;});
        check(n("SELECT attempts FROM post_image_cleanup")==1,"Failure retry info not committed");
        check(n("SELECT COUNT(*) FROM post_image_cleanup WHERE next_attempt_at>created_at")==1,"Retry deadline missing");
        checkpoint("real-file-delete-failed-and-edit-committed"); repair(f);
        long id=n("SELECT cleanup_id FROM post_image_cleanup");
        check(retryDeadline(id).isAfter(LocalDateTime.now()),"Retry deadline is not in future under production mapping");
        setDue(id,LocalDateTime.now().minusMinutes(2));
        runtime.cleanup.retryPending(); check(n("SELECT COUNT(*) FROM post_image_cleanup")==0&&!Files.exists(f.path()),"Due retry failed");
    }
    static void timeout() throws Exception {
        var f=fixture(); long id=pending(f); before();
        try(var c=open()) { c.setAutoCommit(false); rows(c,"SELECT * FROM post_image_cleanup WHERE cleanup_id=? FOR UPDATE",id);
            timeoutActor=true;
            try { async("timeout",()->runtime.worker.process(id)).get(12,TimeUnit.SECONDS); }
            finally { timeoutActor=false; c.rollback(); }
        }
        check(observations.stream().anyMatch(o->Integer.valueOf(1205).equals(o.get("vendorCode"))),"No actual InnoDB 1205: cannot claim timeout pass");
        check(n("SELECT COUNT(*) FROM post_image_cleanup WHERE cleanup_id=?",id)==1&&Files.exists(f.path()),"Timeout lost work/file");
        checkpoint("after-observed-1205"); runtime.worker.process(id);
        check(n("SELECT COUNT(*) FROM post_image_cleanup")==0&&!Files.exists(f.path()),"Timeout recovery failed");
    }
    static void crashWindow() throws Exception {
        var f=fixture(); long id=pending(f); before();
        var gate=new StorageGate("crash",true,true); gate.release.countDown(); storageGate=gate;
        boolean threw=false;
        try { actor("crash",()->runtime.worker.process(id)); } catch(SimulatedExit expected) { threw=true; }
        finally { storageGate=null; }
        check(threw&&!Files.exists(f.path())&&n("SELECT COUNT(*) FROM post_image_cleanup WHERE cleanup_id=?",id)==1,"Delete/commit failure window wrong");
        checkpoint("simulated-Error-not-real-process-kill"); runtime.worker.process(id); runtime.worker.process(id);
        check(n("SELECT COUNT(*) FROM post_image_cleanup")==0,"Missing file retry not idempotent");
    }
    static void sameJob() throws Exception {
        var f=fixture(); long id=pending(f); before(); var gate=new StorageGate("first",false,false); storageGate=gate;
        var first=async("first",()->runtime.worker.process(id));
        try { check(gate.entered.await(5,TimeUnit.SECONDS),"First worker not at physical file");
            check(Files.exists(f.path()),"Concurrent test requires existing file");
            var second=async("second",()->runtime.worker.process(id));
            try { await(()->waits()>=1,"No observed InnoDB same-job wait",4); checkpoint("same-job-wait-file-still-present"); }
            finally { gate.release.countDown(); }
            first.get(12,TimeUnit.SECONDS); second.get(12,TimeUnit.SECONDS);
        } finally { gate.release.countDown(); }
        check(n("SELECT COUNT(*) FROM post_image_cleanup")==0&&!Files.exists(f.path()),"Same-job concurrent cleanup failed");
    }
    static void duplicateUrl() throws Exception {
        var f=fixture(); long a=pending(f); long b=runtime.tx(()->runtime.jobs.save(new PostImageCleanup(f.url())).getCleanupId()); before();
        var gate=new StorageGate("duplicate-a",false,false); storageGate=gate;
        var one=async("duplicate-a",()->runtime.worker.process(a));
        try {
            check(gate.entered.await(5,TimeUnit.SECONDS)&&Files.exists(f.path()),"First duplicate must hold job with real file present");
            async("duplicate-b",()->runtime.worker.process(b)).get(12,TimeUnit.SECONDS);
            check(!Files.exists(f.path())&&n("SELECT COUNT(*) FROM post_image_cleanup WHERE cleanup_id=?",a)==1,
                "Second duplicate did not commit while first was paused");
            checkpoint("different-job-commit-during-first-worker-transaction");
        } finally { gate.release.countDown(); }
        one.get(12,TimeUnit.SECONDS);
        check(n("SELECT COUNT(*) FROM post_image_cleanup")==0&&!Files.exists(f.path()),"Duplicate URL not idempotent");
    }
    static void references() throws Exception {
        for(boolean deleted:List.of(false,true)) {
            var f=fixture(); long id=pending(f); var shared=fixture();
            runtime.tx(()->{
                var post=runtime.em.find(CommunityPost.class,shared.post());
                runtime.em.persist(new PostImage(post,f.url(),"legacy-shared.png",1));
                if(deleted)post.delete(); return null; });
            before(); runtime.worker.process(id);
            check(Files.exists(f.path())&&Files.exists(shared.path())&&n("SELECT COUNT(*) FROM post_image WHERE image_url=?",f.url())==1
                &&n("SELECT COUNT(*) FROM post_image_cleanup WHERE cleanup_id=?",id)==0,"Referenced image deleted");
            checkpoint(deleted?"DELETED-reference-protected":"ACTIVE-reference-protected");
        }
    }
    static void deadlineGap() throws Exception {
        var f=fixture(); long id=pending(f); blockedFile(f);
        setDue(id,LocalDateTime.now().minusMinutes(2)); before();
        beforeJobLock=new CountDownLatch(2);
        Future<?> a=null,b=null;
        try(var c=open()) { c.setAutoCommit(false); rows(c,"SELECT * FROM post_image_cleanup WHERE cleanup_id=? FOR UPDATE",id);
            try { a=async("due-a",runtime.cleanup::retryPending); b=async("due-b",runtime.cleanup::retryPending);
                check(beforeJobLock.await(4,TimeUnit.SECONDS),"Both batches did not select same due job");
                await(()->waits()>=2,"Two actual lock waits not observed",3); checkpoint("both-batches-selected-before-lock");
            } finally { c.rollback(); }
        }
        a.get(12,TimeUnit.SECONDS); b.get(12,TimeUnit.SECONDS); beforeJobLock=null;
        long attempts=n("SELECT attempts FROM post_image_cleanup WHERE cleanup_id=?",id);
        check(retryDeadline(id).isAfter(LocalDateTime.now()),"Missing future retry deadline under production mapping");
        checkpoint("after-competing-deferred-attempts");
        if(attempts>1) throw new Finding("Deferred deadline was not rechecked after row-lock wait; early repeat attempt observed. Not counted as PASS.");
        check(attempts==1,"Ambiguous deferred attempt count");
    }
    static void boundary() throws Exception {
        var cutoff=LocalDateTime.of(2030,1,2,3,4,5);
        var ids=new ArrayList<Long>();
        for(int delta:List.of(-1,0,1)) { var f=fixture();long id=pending(f);ids.add(id);setDue(id,cutoff.plusSeconds(delta)); }
        before(); var due=runtime.jobs.findDueIds(cutoff,PageRequest.of(0,100));
        check(due.equals(ids.subList(0,2)),"DATETIME <= boundary/order differs");
        checkpoints.add(Map.of("stage","explicit-cutoff-no-clock-sleep","cutoff",cutoff.toString(),"selectedIds",due,"futureId",ids.getLast()));
    }
    static void batch() throws Exception {
        for(int i=0;i<101;i++) { var f=fixture();long id=pending(f);setDue(id,LocalDateTime.now().minusMinutes(2)); }
        before(); runtime.cleanup.retryPending();
        check(n("SELECT COUNT(*) FROM post_image_cleanup")==1,"Batch not limited to 100");
        try(var files=Files.list(root)) { check(files.count()==1,"Batch deleted wrong number of files"); }
        checkpoint("first-batch-exactly-100"); runtime.cleanup.retryPending();
        check(n("SELECT COUNT(*) FROM post_image_cleanup")==0,"Second batch did not drain");
        try(var files=Files.list(root)) { check(files.findAny().isEmpty(),"Second batch left physical files"); }
    }
    static void restart() throws Exception {
        var first=child("PENDING",45); check(first.exitValue()==0,"Pending child failed");
        before(); check(n("SELECT COUNT(*) FROM post_image_cleanup")==1,"Committed pending job missing after JVM halt");
        var second=child("SCHEDULE",45); check(second.exitValue()==0,"Scheduler child failed");
        check(first.pid()!=second.pid()&&!first.isAlive()&&!second.isAlive(),"Not a real sequential JVM restart");
        check(n("SELECT COUNT(*) FROM post_image_cleanup")==0,"Scheduler did not drain persisted work");
        try(var files=Files.list(root)) { check(files.findAny().isEmpty(),"Restart left target file"); }
        checkpoints.add(Map.of("stage","actual-process-restart","pendingPid",first.pid(),"schedulerPid",second.pid(),"schedulerDelayMs",500));
    }
    static void pending() throws Exception {
        runtime=new ImageCleanupRuntime(root,false); var f=fixture(); long id=pending(f);
        write(output.resolve("pending-jvm.json"),Map.of("pid",ProcessHandle.current().pid(),"job",id,"state",snapshot(),
            "termination","Runtime.halt(0) after committed queue fixture; no afterCommit callback registered by fixture"));
        Runtime.getRuntime().halt(0); // Real process death. Does not change production callback behavior.
    }
    static void schedule() throws Exception {
        var initial=snapshot();
        try(var rt=new ImageCleanupRuntime(root,true)) {
            await(()->{try{return n("SELECT COUNT(*) FROM post_image_cleanup")==0;}catch(Exception e){throw new AssertionError(e);}},"Real scheduler timeout",20);
            write(output.resolve("scheduler-jvm.json"),Map.of("pid",ProcessHandle.current().pid(),"beforeSeparateJdbcState",initial,
                "afterSeparateJdbcState",snapshot(),"actualProductionScheduler",true));
        }
    }
}
