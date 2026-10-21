package com.kkirikkiri.calendar.crawler;

import com.kkirikkiri.calendar.entity.*;
import com.kkirikkiri.calendar.dto.*;
import com.kkirikkiri.calendar.repository.*;
import com.kkirikkiri.calendar.service.*;
import com.kkirikkiri.user.entity.*;
import com.kkirikkiri.user.repository.*;
import com.kkirikkiri.admin.service.AdminService;
import com.kkirikkiri.global.exception.*;
import com.zaxxer.hikari.*;
import jakarta.persistence.*;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import javax.sql.DataSource;
import java.lang.reflect.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import org.jsoup.Jsoup;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactoryBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.*;
import tools.jackson.databind.json.JsonMapper;

/** Opt-in real JDBC verifier. No Boot application, config import, web server, SMTP, AI or scheduler. */
public class CalendarMysqlValidation {
    static final String DB=System.getenv("KKIRI_CAL_DB"), ACCOUNT=System.getenv("KKIRI_CAL_USER");
    static final LocalDate DAY=LocalDate.of(2026,10,16);
    static final List<String> TABLES=List.of("calendar_category","calendar_category_setting","calendar_event","schedule_crawl_run","user");
    static final List<Class<?>> ENTITIES=List.of(User.class,CalendarCategory.class,CalendarCategorySetting.class,CalendarEvent.class,ScheduleCrawlRun.class);
    static final JsonMapper JSON=JsonMapper.builder().build();
    static final List<Map<String,Object>> results=new ArrayList<>(), errors=new CopyOnWriteArrayList<>();
    static final Set<String> sqlTemplates=ConcurrentHashMap.newKeySet();
    static final ThreadLocal<Consumer<Execution>> observer=new ThreadLocal<>();
    record Execution(String sql, List<Object> parameters) {}
    static Path root,out; static HikariDataSource pool; static DataSource guarded; static JdbcTemplate jdbc;
    static AnnotationConfigApplicationContext ctx; static PlatformTransactionManager tm;
    static CalendarService calendar; static CalendarCrawlingService crawling; static AdminService admins;
    static CalendarEventRepository events; static CalendarCategoryRepository categories; static CalendarCategorySettingRepository settings;
    static ScheduleCrawlRunRepository runs; static UserRepository users; static ControlledCrawler crawler;
    static final List<Long> ownedUsers=new ArrayList<>(); static Long user,other,admin,school,personal;
    static List<CrawledAcademicEvent> fixture; static String caseName="setup";
    static final AtomicInteger sequence=new AtomicInteger();

    public static void main(String[] args) throws Exception {
        ((ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).setLevel(ch.qos.logback.classic.Level.OFF);
        check(args.length==3&&DB!=null&&DB.matches("calendarverify[0-9a-f]{12}")&&ACCOUNT!=null&&ACCOUNT.matches("calverify[0-9a-f]{12}")
                &&DB.substring(14).equals(ACCOUNT.substring(9))&&System.getenv("KKIRI_CAL_PASSWORD")!=null,"Dedicated runner environment required");
        root=Path.of(args[0]).toRealPath();out=Path.of(args[1]).toRealPath();
        check(out.startsWith(root.resolve("backend/build/calendar-mysql-concurrency")),"Unsafe output path");
        var baseline=protectedFiles();
        boolean cleaned=false,unchanged=false; Map<String,String> ddlBefore=null;
        try {
            pool=newPool(); guarded=new Guarded(pool); jdbc=new JdbcTemplate(guarded);
            try(Connection c=guarded.getConnection()) {
                grants(c,args[2].equals("Initialize"));
                check(number(c,"SELECT GET_LOCK('"+DB+".calendar-validation',0)")==1,"Another Calendar run is active");
                if(args[2].equals("Initialize")) {
                    check(number(c,"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()")==0,"Initialization requires a fresh empty schema");
                    var emf=emf(guarded,"create-only");emf.afterPropertiesSet();emf.destroy();
                    check(ddl(c).keySet().equals(new TreeSet<>(TABLES)),"Unexpected initialized tables");
                    write("schema-initialized.json",ddl(c));System.out.println("INITIALIZED "+DB+" tables=5");return;
                }
                check(Set.of("Verify","FormerTimezones","Matrix","Plans").contains(args[2]),"Unknown mode"); ddlBefore=ddl(c);
                check(ddlBefore.keySet().equals(new TreeSet<>(TABLES)),"Expected only five Calendar/User dependency tables");
                assertEmpty(c);write("schema-before.json",ddlBefore);
                write("environment.json",rows(c,"SELECT DATABASE() db,CURRENT_USER() account,VERSION() version,@@server_uuid uuid,@@hostname hostname,@@session.time_zone sessionTimeZone,@@transaction_isolation isolationLevel,@@collation_database collation,@@session.innodb_lock_wait_timeout lockWaitSeconds"));
            }
            ctx=new AnnotationConfigApplicationContext(Config.class);tm=ctx.getBean(PlatformTransactionManager.class);
            calendar=ctx.getBean(CalendarService.class);crawling=ctx.getBean(CalendarCrawlingService.class);admins=ctx.getBean(AdminService.class);
            events=ctx.getBean(CalendarEventRepository.class);categories=ctx.getBean(CalendarCategoryRepository.class);settings=ctx.getBean(CalendarCategorySettingRepository.class);
            runs=ctx.getBean(ScheduleCrawlRunRepository.class);users=ctx.getBean(UserRepository.class);crawler=ctx.getBean(ControlledCrawler.class);
            fixture=new HknuAcademicCalendarCrawler().parse(Jsoup.parse(root.resolve("backend/src/test/resources/calendar/hknu-2026.html").toFile(),"UTF-8"));
            check(fixture.size()==62,"Fixed fixture changed");
            if(args[2].equals("FormerTimezones")) {
                test("integrity","utc-session-seoul-local-datetime-roundtrip",CalendarMysqlValidation::timezones);
                test("integrity","mixed-utc-seoul-session-datetime-consistency",CalendarMysqlValidation::mixedTimezones);
            } else if(args[2].equals("Matrix")) {
                test("matrix","logical-store-raw-and-admin-roundtrip",CalendarMysqlValidation::matrixRoundtrip);
                test("matrix","same-physical-datetime-across-sessions",CalendarMysqlValidation::mixedTimezones);
                test("matrix","date-range-and-nullable-times",CalendarMysqlValidation::nullableTimes);
            } else if(args[2].equals("Plans")) {
                test("plans","actual-list-and-dedup-query-explain",CalendarMysqlValidation::plans);
            } else {
            test("integrity","physical-ddl-and-constraints",CalendarMysqlValidation::constraints);
            test("history","running-success-count-and-admin-status",CalendarMysqlValidation::historySuccess);
            test("history","atomic-visibility-before-commit",CalendarMysqlValidation::atomicCommit);
            test("history","actual-mysql-storage-failure-full-rollback",CalendarMysqlValidation::storageFailure);
            test("history","external-failure-and-recovery",CalendarMysqlValidation::externalFailure);
            test("history","independent-commit-survives-caller-rollback",CalendarMysqlValidation::callerRollback);
            test("crawl","eight-concurrent-fixture-crawls-no-duplicates",CalendarMysqlValidation::concurrentCrawls);
            test("crawl","school-lock-timeout-failure-history-and-retry",CalendarMysqlValidation::crawlTimeout);
            test("crawl","older-failure-cannot-overwrite-newer-success",CalendarMysqlValidation::outOfOrder);
            test("category","same-user-eight-identical-creates",CalendarMysqlValidation::sameCategory);
            test("category","concurrent-first-settings-save",CalendarMysqlValidation::firstSettings);
            test("category","rename-then-create-rechecks-after-lock",()->nameRace(false));
            test("category","batch-settings-then-create-rechecks-after-lock",()->nameRace(true));
            test("category","four-different-users-overlap",CalendarMysqlValidation::differentUsers);
            test("category","event-create-before-category-delete",()->deletionRace(false));
            test("category","category-delete-before-event-create",()->deletionRace(true));
            test("category","collation-and-java-name-policy",CalendarMysqlValidation::collation);
            test("category","user-lock-timeout-and-recovery",CalendarMysqlValidation::userTimeout);
            test("category","forced-reverse-user-lock-deadlock-rollback",CalendarMysqlValidation::deadlock);
            test("integrity","crud-ownership-system-and-batch-rollback",CalendarMysqlValidation::crud);
            test("integrity","date-range-and-nullable-times",CalendarMysqlValidation::nullableTimes);
            test("integrity","utc-session-seoul-local-datetime-roundtrip",CalendarMysqlValidation::timezones);
            test("integrity","mixed-utc-seoul-session-datetime-consistency",CalendarMysqlValidation::mixedTimezones);
            test("plans","actual-list-and-dedup-query-explain",CalendarMysqlValidation::plans);
            }
        } catch(Throwable failure) {
            results.add(result("harness","setup","INCOMPLETE",describe(failure)));
        } finally {
            if(!args[2].equals("Initialize")) {
                try {
                    if(ctx!=null)cleanup();
                    if(pool!=null&&ddlBefore!=null)try(Connection c=guarded.getConnection()) {
                        assertEmpty(c);var after=ddl(c);write("schema-after.json",after);
                        check(normalize(ddlBefore).equals(normalize(after)),"DDL changed during scenarios");cleaned=true;
                    }
                }catch(Throwable e){results.add(result("cleanup","cleanup","INCOMPLETE",describe(e)));}
            }
            int active=-1,waiting=-1;
            if(pool!=null){var mx=pool.getHikariPoolMXBean();active=mx.getActiveConnections();waiting=mx.getThreadsAwaitingConnection();}
            if(ctx!=null)ctx.close();if(pool!=null)pool.close();
            unchanged=baseline.equals(protectedFiles());
            if(!unchanged)results.add(result("protected-files","safety","INCOMPLETE",Map.of("unchanged",false)));
            if(!args[2].equals("Initialize")) {
                if(active!=0||waiting!=0)results.add(result("pool-return","cleanup","FAIL",Map.of("active",active,"waiting",waiting)));
                write("database-errors.json",errors);write("sql-templates.json",new TreeSet<>(sqlTemplates));write("cases.json",results);
                String verdict=results.stream().anyMatch(r->r.get("status").equals("INCOMPLETE"))?"INSUFFICIENT":results.stream().anyMatch(r->r.get("status").equals("FAIL"))?"FAIL":"PASS";
                write("summary.json",Map.of("verdict",verdict,"cases",results,"allFiveTablesEmptyAndDdlUnchanged",cleaned,"protectedFilesUnchanged",unchanged,"poolActive",active,"poolWaiting",waiting,"poolClosed",pool!=null&&pool.isClosed(),"externalHttpCalls",0));
                System.out.println("RESULT "+verdict+" db="+DB+" cases="+results.size()+" evidence="+out);
                if(!verdict.equals("PASS"))System.exit(1);
            } else if(!results.isEmpty()) {
                System.out.println("INITIALIZATION INCOMPLETE "+results);System.exit(1);
            }
        }
    }

    @Configuration @EnableTransactionManagement(proxyTargetClass=true)
    @EnableJpaRepositories(basePackages="com.kkirikkiri.calendar.repository")
    static class Config {
        @Bean DataSource dataSource(){return guarded;}
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds){return emf(ds,"validate");}
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory){var m=new JpaTransactionManager(factory);m.setDataSource(guarded);return m;}
        @Bean JpaRepositoryFactoryBean<UserRepository,User,Long> userRepository(EntityManagerFactory factory){
            var em=SharedEntityManagerCreator.createSharedEntityManager(factory);
            var bean=new JpaRepositoryFactoryBean<UserRepository,User,Long>(UserRepository.class);
            bean.setEntityManager(em);bean.setCustomImplementation(new UserAccountLockRepositoryImpl(em));return bean;
        }
        @Bean(destroyMethod="close") ValidatorFactory validatorFactory(){return Validation.buildDefaultValidatorFactory();}
        @Bean Validator validator(ValidatorFactory f){return f.getValidator();}
        @Bean ControlledCrawler crawler(){return new ControlledCrawler();}
        @Bean CalendarService calendar(CalendarEventRepository e,CalendarCategoryRepository c,CalendarCategorySettingRepository s,UserRepository u,Validator v){return new CalendarService(e,c,s,u,v);}
        @Bean CalendarCrawlingService crawling(ControlledCrawler c,CalendarCategoryRepository cats,CalendarEventRepository e,ScheduleCrawlRunRepository r,PlatformTransactionManager m){return new CalendarCrawlingService(c,cats,e,r,m);}
        @Bean AdminCrawledScheduleQueryRepository scheduleQuery(EntityManagerFactory f){return new AdminCrawledScheduleQueryRepository(SharedEntityManagerCreator.createSharedEntityManager(f));}
        // Only the Calendar admin read methods are exposed by this standalone verifier.
        @Bean AdminService admin(UserRepository u,AdminCrawledScheduleQueryRepository q,ScheduleCrawlRunRepository r,Validator v){return new AdminService(u,null,null,null,null,null,null,null,null,q,r,null,null,null,v);}
    }
    static LocalContainerEntityManagerFactoryBean emf(DataSource ds,String ddl){
        var f=new LocalContainerEntityManagerFactoryBean();f.setDataSource(ds);f.setManagedTypes(PersistenceManagedTypes.of(ENTITIES.stream().map(Class::getName).toArray(String[]::new)));
        f.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        f.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto",ddl,"hibernate.hbm2ddl.halt_on_error","true","hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl","hibernate.show_sql","false","hibernate.session_factory.statement_inspector",(StatementInspector)sql->{sqlTemplates.add(sql);return sql;}));return f;
    }
    static class ControlledCrawler extends HknuAcademicCalendarCrawler {
        volatile Runnable before=()->{};volatile Supplier<List<CrawledAcademicEvent>> response=()->fixture;
        @Override public List<CrawledAcademicEvent> crawl(){before.run();return response.get();}
        void reset(){before=()->{};response=()->fixture;}
    }
    static String jdbcZone(){return "Asia/Seoul".equals(System.getenv("KKIRI_CAL_JDBC_ZONE"))?"Asia/Seoul":"UTC";}
    static Set<String> sessionZones(){return jdbcZone().equals("UTC")?Set.of("+00:00","UTC"):Set.of("+09:00","Asia/Seoul");}
    static String url(){return "jdbc:mysql://127.0.0.1:3307/"+DB+"?connectionTimeZone="+jdbcZone()+"&forceConnectionTimeZoneToSession=true&autoReconnect=false&allowMultiQueries=false&allowLoadLocalInfile=false&connectTimeout=5000&socketTimeout=20000";}
    static HikariDataSource newPool(){var p=new HikariConfig();p.setJdbcUrl(url());p.setUsername(ACCOUNT);p.setPassword(System.getenv("KKIRI_CAL_PASSWORD"));p.setMaximumPoolSize(16);p.setMinimumIdle(1);p.setConnectionTimeout(10000);p.setTransactionIsolation("TRANSACTION_REPEATABLE_READ");p.setConnectionInitSql("SET SESSION innodb_lock_wait_timeout=2");p.setPoolName("CalendarVerification");return new HikariDataSource(p);}
    static class Guarded extends AbstractDataSource {
        final DataSource source;Guarded(DataSource d){source=d;}
        public Connection getConnection() throws SQLException {
            Connection c=source.getConnection();try{guard(c);return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(p,m,a)->{
                try {Object r=m.invoke(c,a);if(r instanceof PreparedStatement ps&&a!=null&&a[0] instanceof String sql)return statement(ps,sql);return r;}catch(InvocationTargetException e){throw e.getCause();}
            });}catch(Throwable e){c.close();throw e;}
        }
        public Connection getConnection(String u,String p)throws SQLException{throw new SQLException("Alternate credentials forbidden");}
    }
    static Object statement(PreparedStatement ps,String sql){
        Map<Integer,Object> values=new HashMap<>();
        return Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),new Class<?>[]{PreparedStatement.class},(p,m,a)->{
            if(m.getName().startsWith("set")&&a!=null&&a.length>=2&&a[0] instanceof Integer i)values.put(i,m.getName().equals("setNull")?null:a[1]);
            try{Object value=m.invoke(ps,a);if(m.getName().startsWith("execute")&&observer.get()!=null){var params=new ArrayList<Object>();for(int i=1;i<=values.size();i++)params.add(values.get(i));observer.get().accept(new Execution(sql,params));}return value;}
            catch(InvocationTargetException e){if(e.getCause() instanceof SQLException s)errors.add(Map.of("case",caseName,"code",s.getErrorCode(),"state",Objects.toString(s.getSQLState(),"")));throw e.getCause();}
        });
    }
    static void guard(Connection c)throws SQLException{guard(c,url(),sessionZones());}
    static void guard(Connection c,String expectedUrl,Set<String> timeZones)throws SQLException{
        try(var s=c.createStatement();var r=s.executeQuery("SELECT DATABASE(),CURRENT_USER(),VERSION(),@@server_uuid,@@hostname,@@port,@@session.time_zone,@@global.read_only,@@global.super_read_only")){
            check(r.next()&&DB.equals(r.getString(1))&&(ACCOUNT+"@%").equals(r.getString(2))&&"8.0.46".equals(r.getString(3))
                    &&System.getenv("KKIRI_CAL_UUID").equals(r.getString(4))&&System.getenv("KKIRI_CAL_HOSTNAME").equals(r.getString(5))&&r.getInt(6)==3306
                    &&timeZones.contains(r.getString(7))&&r.getInt(8)==0&&r.getInt(9)==0,"Database identity mismatch");
        }check(c.getMetaData().getURL().equals(expectedUrl),"Unexpected JDBC URL");
    }
    static void grants(Connection c,boolean ddl)throws SQLException{
        var grants=rows(c,"SHOW GRANTS");check(grants.size()==2,"Unexpected grants/roles");
        Set<String> expected=new TreeSet<>(List.of("SELECT","INSERT","UPDATE","DELETE"));if(ddl)expected.addAll(List.of("CREATE","ALTER","INDEX","REFERENCES"));boolean found=false;
        for(var g:grants){String text=g.values().iterator().next().toString();if(text.equals("GRANT USAGE ON *.* TO `"+ACCOUNT+"`@`%`"))continue;
            String suffix=" ON `"+DB+"`.* TO `"+ACCOUNT+"`@`%`";check(text.startsWith("GRANT ")&&text.endsWith(suffix),"Cross-schema privileges forbidden");
            Set<String> actual=new TreeSet<>(Arrays.asList(text.substring(6,text.length()-suffix.length()).split(", ")));check(actual.equals(expected),"Privileges exceed/omit required scope");found=true;
        }check(found,"Missing Calendar grants");writeUnchecked("grants-"+(ddl?"bootstrap":"runtime")+".json",grants);
    }

    interface Case {Map<String,Object> run()throws Exception;}
    static void test(String group,String name,Case work)throws Exception{
        caseName=name;fixtureSetup();try{var detail=work.run();results.add(result(name,group,"PASS",detail));System.out.println("PASS "+group+"/"+name);}
        catch(Throwable e){results.add(result(name,group,"FAIL",describe(e)));System.out.println("FAIL "+group+"/"+name+" "+describe(e));}
        finally{observer.remove();crawler.reset();cleanup();try(Connection c=guarded.getConnection()){assertEmpty(c);}check(pool.getHikariPoolMXBean().getActiveConnections()==0,"Connection leaked after case");}
    }
    static void fixtureSetup(){
        tx(()->{user=newUser(UserRole.USER);other=newUser(UserRole.USER);admin=newUser(UserRole.ADMIN);
            school=categories.saveAndFlush(new CalendarCategory("SCHOOL","학교",null,true,true)).getCategoryId();categories.saveAndFlush(new CalendarCategory("DORMITORY","기숙사",null,true,true));});
        personal=calendar.createCategory(user,category("personal")).id();crawler.reset();
    }
    static Long newUser(UserRole role){var u=users.saveAndFlush(new User("synthetic-test-hash",DB+"-"+sequence.incrementAndGet()+"@hknu.ac.kr",role));ownedUsers.add(u.getUserId());return u.getUserId();}
    static CalendarCategoryCreateRequest category(String label){return new CalendarCategoryCreateRequest(label,"#123456");}
    static CalendarCategorySettingRequest schoolSetting(String color){return new CalendarCategorySettingRequest(school,"학교",color,true);}
    static CalendarEventCreateRequest event(Long cat,String start,String end){return new CalendarEventCreateRequest("test event",DAY,DAY,start,end,cat,"test description");}
    static ScheduleCrawlRun latest(){return runs.findFirstByCategoryOrderByStartedAtDescCrawlRunIdDesc(ScheduleCrawlCategory.SCHOOL).orElseThrow();}
    static com.kkirikkiri.admin.dto.AdminScheduleCrawlStatusResponse status(){return admins.getScheduleCrawlStatuses(admin).stream().filter(s->s.category()==ScheduleCrawlCategory.SCHOOL).findFirst().orElseThrow();}
    static long count(String table){check(TABLES.contains(table),"Unsafe table");return jdbc.queryForObject("SELECT COUNT(*) FROM `"+table+"`",Long.class);}
    static void tx(Runnable r){new TransactionTemplate(tm).executeWithoutResult(s->r.run());}
    static <T>T inTx(Supplier<T> r){return new TransactionTemplate(tm).execute(s->r.get());}
    static Throwable capture(Runnable r){try{r.run();return null;}catch(Throwable t){return t;}}
    static void business(Throwable e,ErrorCode code){check(e instanceof CustomException c&&c.getErrorCode()==code,"Expected business "+code+", actual "+describe(e));}
    static void await(CountDownLatch l){try{check(l.await(10,TimeUnit.SECONDS),"Worker coordination timeout");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError("Interrupted worker");}}
    static void barrier(CyclicBarrier b){try{b.await(10,TimeUnit.SECONDS);}catch(Exception e){throw new AssertionError("Worker barrier timeout");}}
    static void blocked(Future<?> f)throws Exception{try{f.get(180,TimeUnit.MILLISECONDS);throw new AssertionError("Competitor did not wait");}catch(TimeoutException expected){}}
    static List<Throwable> concurrent(int n,java.util.function.IntConsumer action)throws Exception{
        var ex=Executors.newFixedThreadPool(n);var ready=new CountDownLatch(n);var release=new CountDownLatch(1);var futures=new ArrayList<Future<Throwable>>();
        try{for(int i=0;i<n;i++){final int index=i;futures.add(ex.submit(()->{ready.countDown();await(release);return capture(()->action.accept(index));}));}await(ready);release.countDown();var list=new ArrayList<Throwable>();for(var f:futures)list.add(f.get(20,TimeUnit.SECONDS));return list;}
        finally{release.countDown();stop(ex);}
    }
    static Throwable ordered(Runnable firstAction,Runnable secondAction)throws Exception{
        var ex=Executors.newFixedThreadPool(2);var locked=new CountDownLatch(1);var release=new CountDownLatch(1);var started=new CountDownLatch(1);
        try{var first=ex.submit(()->capture(()->tx(()->{firstAction.run();locked.countDown();await(release);})));await(locked);
            var second=ex.submit(()->{started.countDown();return capture(secondAction);});await(started);
            try{blocked(second);}finally{release.countDown();}check(first.get(15,TimeUnit.SECONDS)==null,"First transaction failed");return second.get(15,TimeUnit.SECONDS);
        }finally{release.countDown();stop(ex);}
    }
    static void stop(ExecutorService ex)throws Exception{ex.shutdownNow();check(ex.awaitTermination(15,TimeUnit.SECONDS),"Worker did not terminate");}
    static int code(Throwable e){for(Throwable t=e;t!=null;t=t.getCause())if(t instanceof SQLException s)return s.getErrorCode();return 0;}
    static Map<String,Object> describe(Throwable e){if(e==null)return Map.of("result","OK");var m=new LinkedHashMap<String,Object>();m.put("type",e.getClass().getSimpleName());m.put("mysqlCode",code(e));if(e instanceof CustomException c)m.put("business",c.getErrorCode().name());if(e instanceof AssertionError)m.put("assertion",e.getMessage());return m;}
    static Map<String,Object> result(String name,String group,String status,Map<String,Object> detail){return Map.of("name",name,"group",group,"status",status,"detail",detail);}
    static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
    static void write(String name,Object value)throws Exception{Files.writeString(out.resolve(name),JSON.writeValueAsString(value),StandardCharsets.UTF_8);}
    static void writeUnchecked(String name,Object value){try{write(name,value);}catch(Exception e){throw new IllegalStateException("Evidence write failed");}}
    static List<Map<String,Object>> rows(Connection c,String sql,Object...args)throws SQLException{
        var list=new ArrayList<Map<String,Object>>();try(var p=c.prepareStatement(sql)){for(int i=0;i<args.length;i++)p.setObject(i+1,args[i]);try(var r=p.executeQuery()){while(r.next()){var row=new LinkedHashMap<String,Object>();for(int i=1;i<=r.getMetaData().getColumnCount();i++)row.put(r.getMetaData().getColumnLabel(i),r.getObject(i));list.add(row);}}}return list;
    }
    static long number(Connection c,String sql,Object...args)throws SQLException{return ((Number)rows(c,sql,args).getFirst().values().iterator().next()).longValue();}
    static Map<String,String> ddl(Connection c)throws SQLException{var map=new TreeMap<String,String>();for(var row:rows(c,"SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE()")){String t=row.values().iterator().next().toString();check(TABLES.contains(t),"Unexpected table");try(var s=c.createStatement();var r=s.executeQuery("SHOW CREATE TABLE `"+t+"`")){r.next();map.put(t,r.getString(2));}}return map;}
    // Only the table option's next counter is volatile; the column AUTO_INCREMENT declaration stays compared.
    static Map<String,String> normalize(Map<String,String> ddl){var n=new TreeMap<String,String>();ddl.forEach((k,v)->n.put(k,v.replaceAll(" AUTO_INCREMENT=\\d+","")));return n;}
    static void assertEmpty(Connection c)throws SQLException{for(String t:TABLES)check(number(c,"SELECT COUNT(*) FROM `"+t+"`")==0,"Residual fixture rows in "+t);}
    static void cleanup(){
        if(jdbc==null||ownedUsers.isEmpty())return;
        String ids=ownedUsers.stream().map(Object::toString).collect(java.util.stream.Collectors.joining(","));
        jdbc.update("DELETE FROM calendar_event WHERE owner_user_id IN ("+ids+") OR (owner_user_id IS NULL AND source_type='CRAWLED' AND source_name=? AND source_url=?)",HknuAcademicCalendarCrawler.SOURCE_NAME,HknuAcademicCalendarCrawler.SOURCE_URL);
        jdbc.update("DELETE FROM calendar_category_setting WHERE user_id IN ("+ids+")");
        jdbc.update("DELETE FROM calendar_category WHERE owner_user_id IN ("+ids+") OR (owner_user_id IS NULL AND category_code IN ('SCHOOL','DORMITORY'))");
        for(Long id:jdbc.queryForList("SELECT crawl_run_id FROM schedule_crawl_run WHERE category='SCHOOL'",Long.class))jdbc.update("DELETE FROM schedule_crawl_run WHERE crawl_run_id=? AND category='SCHOOL'",id);
        jdbc.update("DELETE FROM user WHERE user_id IN ("+ids+") AND school_email LIKE ?",DB+"-%@hknu.ac.kr");ownedUsers.clear();
    }
    static Map<String,String> protectedFiles()throws Exception{
        var files=new TreeMap<String,String>();for(String part:List.of("backend/src","frontend/src","backend/tools/auth-mysql-concurrency","backend/tools/auth-mysql-multi-instance","backend/tools/user-mysql-concurrency","backend/tools/user-mysql-multi-instance","backend/migrations"))try(var walk=Files.walk(root.resolve(part))){for(Path p:walk.filter(Files::isRegularFile).toList())files.put(root.relativize(p).toString(),HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p))));}return files;
    }

    // Scenario implementations follow; every method uses the real service and independent MySQL transactions.
    static Map<String,Object> constraints(){
        var u=users.findById(user).orElseThrow();var cat=categories.findById(personal).orElseThrow();
        long id=calendar.createEvent(user,event(personal,"10:00","11:00")).id();
        var unique=capture(()->settings.saveAndFlush(new CalendarCategorySetting(u,cat,"dup","#123456",true)));
        check(code(unique)==1062,"Expected settings UNIQUE 1062");
        var notNull=capture(()->jdbc.update("UPDATE calendar_event SET title=NULL WHERE event_id=?",id));check(code(notNull)==1048,"Expected NOT NULL 1048");
        // The runtime DML-only account receives MySQL's generic FK errors, not privileged detail variants.
        var orphan=capture(()->jdbc.update("UPDATE calendar_event SET category_id=9223372036854775807 WHERE event_id=?",id));check(code(orphan)==1216,"Expected DML-only account FK orphan 1216; actual="+describe(orphan));
        check(jdbc.queryForObject("SELECT category_id FROM calendar_event WHERE event_id=?",Long.class,id).equals(personal),"Rejected orphan UPDATE changed the row");
        var parent=capture(()->jdbc.update("DELETE FROM calendar_category WHERE category_id=?",personal));check(code(parent)==1217,"Expected DML-only account FK parent delete 1217; actual="+describe(parent));
        check(categories.existsById(personal),"Rejected parent DELETE removed the category");
        var pk=capture(()->jdbc.update("INSERT INTO schedule_crawl_run(crawl_run_id,category,status,collected_count,started_at) VALUES (1,'SCHOOL','RUNNING',0,NOW()),(1,'SCHOOL','RUNNING',0,NOW())"));check(code(pk)==1062,"Expected PK 1062");
        long foreignKeys=jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.referential_constraints WHERE constraint_schema=DATABASE()",Long.class);
        check(foreignKeys==5,"Expected five Calendar/User foreign keys");
        return Map.of("tables",5,"foreignKeys",foreignKeys,"unique",1062,"pk",1062,"notNull",1048,"orphan",1216,"parentDelete",1217);
    }
    static Map<String,Object> historySuccess(){
        crawler.before=()->{check(!TransactionSynchronizationManager.isActualTransactionActive(),"HTTP fixture inside a transaction");check(latest().getStatus()==ScheduleCrawlStatus.RUNNING&&status().status()==ScheduleCrawlStatus.RUNNING,"RUNNING not committed before fixture");};
        check(crawling.crawlAcademicCalendar()==62,"Wrong new count");var run=latest();check(run.getStatus()==ScheduleCrawlStatus.COMPLETED&&run.getCollectedCount()==62&&run.getCompletedAt()!=null&&!run.getCompletedAt().isBefore(run.getStartedAt()),"Invalid success history");
        check(status().recentCollectedCount()==62&&status().totalCollectedCount()==62&&status().lastCompletedAt()!=null,"Wrong admin state");
        check(crawling.crawlAcademicCalendar()==0&&latest().getCollectedCount()==0&&count("calendar_event")==62,"Idempotence/count failed");
        return Map.of("fixture",62,"firstInserted",62,"repeatInserted",0,"runs",2,"state","COMPLETED");
    }
    static Map<String,Object> atomicCommit()throws Exception{
        var ready=new CountDownLatch(1);var release=new CountDownLatch(1);var ex=Executors.newSingleThreadExecutor();
        try{var f=ex.submit(()->{var once=new AtomicBoolean();observer.set(e->{if(e.sql.toLowerCase(Locale.ROOT).startsWith("update schedule_crawl_run")&&once.compareAndSet(false,true)){ready.countDown();await(release);}});try{return crawling.crawlAcademicCalendar();}finally{observer.remove();}});
            try{await(ready);check(count("calendar_event")==0&&latest().getStatus()==ScheduleCrawlStatus.RUNNING,"Uncommitted success/events visible");}finally{release.countDown();}
            check(f.get(15,TimeUnit.SECONDS)==62&&count("calendar_event")==62&&latest().getStatus()==ScheduleCrawlStatus.COMPLETED,"Atomic commit failed");return Map.of("beforeCommitEvents",0,"beforeCommitState","RUNNING","afterCommitEvents",62);
        }finally{release.countDown();stop(ex);}
    }
    static Map<String,Object> storageFailure(){
        crawler.response=()->{var list=new ArrayList<>(fixture);list.add(new CrawledAcademicEvent("CALENDAR_DB_FAIL",DAY,DAY));return list;};
        Throwable failure=capture(crawling::crawlAcademicCalendar);check(code(failure)==1644,"Actual MySQL trigger failure not observed");check(count("calendar_event")==0&&latest().getStatus()==ScheduleCrawlStatus.FAILED&&latest().getCollectedCount()==0,"Failed inserts/history not atomic");
        check(latest().getErrorMessage().equals("ACADEMIC_CALENDAR_STORE_FAILED")&&status().lastFailure()!=null,"Unsafe/missing failure status");
        crawler.reset();check(crawling.crawlAcademicCalendar()==62,"Storage failure recovery failed");return Map.of("mysqlCode",1644,"rolledBackEarlierInserts",62,"failurePreserved",true,"retryInserted",62);
    }
    static Map<String,Object> externalFailure(){
        crawler.response=()->{throw new IllegalStateException("controlled private response");};check(capture(crawling::crawlAcademicCalendar)!=null,"Expected external failure");
        check(latest().getStatus()==ScheduleCrawlStatus.FAILED&&latest().getErrorMessage().equals("ACADEMIC_CALENDAR_FETCH_FAILED")&&count("calendar_event")==0,"External failure not saved safely");
        crawler.reset();crawling.crawlAcademicCalendar();check(status().status()==ScheduleCrawlStatus.COMPLETED&&status().lastFailure()!=null,"Admin lost failure or recovery");return Map.of("failurePreserved",true,"retryState","COMPLETED");
    }
    static Map<String,Object> callerRollback(){new TransactionTemplate(tm).executeWithoutResult(s->{check(crawling.crawlAcademicCalendar()==62,"Crawl failed");s.setRollbackOnly();});check(count("calendar_event")==62&&latest().getStatus()==ScheduleCrawlStatus.COMPLETED,"Caller rollback erased independent commit");return Map.of("events",62,"independentCommit",true);}
    static Map<String,Object> concurrentCrawls()throws Exception{
        var b=new CyclicBarrier(8);crawler.before=()->barrier(b);var inserted=new AtomicInteger();var r=concurrent(8,i->inserted.addAndGet(crawling.crawlAcademicCalendar()));
        write("concurrent-crawl-observation.json",Map.of("errors",r.stream().filter(Objects::nonNull).map(CalendarMysqlValidation::describe).toList(),
                "returnedInserted",inserted.get(),"storedEvents",count("calendar_event"),"histories",runs.findAll().stream().map(x->Map.of("id",x.getCrawlRunId(),"status",x.getStatus().name(),"count",x.getCollectedCount())).toList()));
        check(r.stream().allMatch(Objects::isNull)&&inserted.get()==62&&count("calendar_event")==62&&count("schedule_crawl_run")==8,"Concurrent crawl outcomes invalid");
        check(runs.findAll().stream().allMatch(x->x.getStatus()==ScheduleCrawlStatus.COMPLETED)&&runs.findAll().stream().mapToInt(ScheduleCrawlRun::getCollectedCount).sum()==62,"Inconsistent parallel histories");return Map.of("success",8,"newEvents",62,"duplicates",0);
    }
    static Map<String,Object> crawlTimeout()throws Exception{
        try(Connection c=guarded.getConnection()){c.setAutoCommit(false);rows(c,"SELECT category_id FROM calendar_category WHERE category_id=? FOR UPDATE",school);
            var ex=Executors.newSingleThreadExecutor();try{var f=ex.submit(()->capture(crawling::crawlAcademicCalendar));Throwable e=f.get(10,TimeUnit.SECONDS);check(code(e)==1205,"Expected SCHOOL timeout 1205");check(latest().getStatus()==ScheduleCrawlStatus.FAILED&&count("calendar_event")==0,"Timeout history/rollback wrong");}finally{c.rollback();stop(ex);}}
        check(crawling.crawlAcademicCalendar()==62,"Retry after lock release failed");return Map.of("mysqlCode",1205,"historySurvivedSchoolLock",true,"recovered",true);
    }
    static Map<String,Object> outOfOrder()throws Exception{
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var ex=Executors.newSingleThreadExecutor();
        crawler.before=()->{if(Thread.currentThread().getName().equals("older-crawl")){entered.countDown();await(release);throw new IllegalStateException("older failure");}};
        try{var older=ex.submit(()->{Thread.currentThread().setName("older-crawl");return capture(crawling::crawlAcademicCalendar);});await(entered);
            check(crawling.crawlAcademicCalendar()==62,"Newer run failed");release.countDown();check(older.get(10,TimeUnit.SECONDS)!=null,"Older run should fail");
            check(status().status()==ScheduleCrawlStatus.COMPLETED&&status().lastFailure()!=null&&count("calendar_event")==62,"Older failure overwrote newer success");return Map.of("latestState","COMPLETED","oldFailurePreserved",true);
        }finally{release.countDown();stop(ex);}
    }
    static Map<String,Object> sameCategory()throws Exception{var r=concurrent(8,i->calendar.createCategory(user,category("same")));check(r.stream().filter(Objects::isNull).count()==1,"Expected one category winner");for(var e:r)if(e!=null)business(e,ErrorCode.VALIDATION_ERROR);check(calendar.getCategories(user).stream().filter(c->c.label().equals("same")).count()==1,"Duplicate persisted");return Map.of("success",1,"businessRejected",7);}
    static Map<String,Object> firstSettings()throws Exception{var r=concurrent(8,i->calendar.saveCategorySettings(user,List.of(schoolSetting(i%2==0?"#123456":"#654321"))));check(r.stream().allMatch(Objects::isNull),"First settings exposed DB exception");check(settings.findAllByUser_UserId(user).size()==2,"Duplicate settings rows");return Map.of("success",8,"settingsRows",2);}
    static Map<String,Object> nameRace(boolean batch)throws Exception{
        Throwable e=ordered(()->{if(batch)calendar.saveCategorySettings(user,List.of(new CalendarCategorySettingRequest(personal,"target","#123456",true)));else calendar.updateCategory(user,personal,new CalendarCategoryUpdateRequest("target",null,null));},()->calendar.createCategory(user,category("target")));
        business(e,ErrorCode.VALIDATION_ERROR);check(calendar.getCategories(user).stream().filter(c->c.label().equals("target")).count()==1,"Name recheck failed");return Map.of("waited",true,"loser","VALIDATION_ERROR");
    }
    static Map<String,Object> differentUsers()throws Exception{
        var ids=new ArrayList<Long>();ids.add(user);ids.add(other);tx(()->{ids.add(newUser(UserRole.USER));ids.add(newUser(UserRole.USER));});var b=new CyclicBarrier(4);
        var r=concurrent(4,i->tx(()->{users.findByIdForAccountUpdate(ids.get(i)).orElseThrow();barrier(b);calendar.createCategory(ids.get(i),category("shared"));}));check(r.stream().allMatch(Objects::isNull),"Different-user parallel operations failed");return Map.of("simultaneousUserLocks",4,"success",4);
    }
    static Map<String,Object> deletionRace(boolean deleteFirst)throws Exception{
        Throwable e=deleteFirst?ordered(()->calendar.deleteCategory(user,personal),()->calendar.createEvent(user,event(personal,"10:00","11:00"))):ordered(()->calendar.createEvent(user,event(personal,"10:00","11:00")),()->calendar.deleteCategory(user,personal));
        business(e,ErrorCode.INVALID_REQUEST);check(categories.existsById(personal)!=deleteFirst&&count("calendar_event")== (deleteFirst?0:1),"Delete/create integrity failed");return Map.of("deleteFirst",deleteFirst,"loser","INVALID_REQUEST","orphanRows",0);
    }
    static Map<String,Object> collation()throws Exception{
        calendar.createCategory(user,category("Alpha"));calendar.createCategory(user,category("alpha"));
        try(Connection c=guarded.getConnection()){check(number(c,"SELECT CONVERT(? USING utf8mb4) COLLATE utf8mb4_0900_ai_ci = CONVERT(? USING utf8mb4) COLLATE utf8mb4_0900_ai_ci","Alpha","alpha")==1,"Unexpected collation");}
        check(calendar.getCategories(user).stream().filter(x->Set.of("Alpha","alpha").contains(x.label())).count()==2,"Existing Java case-sensitive policy changed");
        return Map.of("collation","utf8mb4_0900_ai_ci","sqlNamesEqual",true,"javaNamesDistinct",true,"consistentWithDocumentedPolicy",true);
    }
    static Map<String,Object> userTimeout()throws Exception{
        try(Connection c=guarded.getConnection()){c.setAutoCommit(false);rows(c,"SELECT user_id FROM user WHERE user_id=? FOR UPDATE",user);var ex=Executors.newSingleThreadExecutor();
            try{var f=ex.submit(()->capture(()->calendar.createCategory(user,category("timeout"))));check(code(f.get(10,TimeUnit.SECONDS))==1205,"Expected user lock timeout");}finally{c.rollback();stop(ex);}}
        check(calendar.getCategories(user).stream().noneMatch(x->x.label().equals("timeout")),"Timeout partially committed");calendar.createCategory(user,category("timeout"));return Map.of("mysqlCode",1205,"recovered",true);
    }
    static Map<String,Object> deadlock()throws Exception{
        var b=new CyclicBarrier(2);var r=concurrent(2,i->tx(()->{Long first=i==0?user:other,second=i==0?other:user;jdbc.queryForObject("SELECT user_id FROM user WHERE user_id=? FOR UPDATE",Long.class,first);barrier(b);calendar.createCategory(second,category("forced-cycle"));}));
        check(r.stream().filter(Objects::isNull).count()==1&&r.stream().filter(e->code(e)==1213).count()==1,"Expected one real InnoDB deadlock victim");
        check(count("calendar_category")==4&&count("calendar_category_setting")==2,"Deadlock victim did not fully roll back");return Map.of("inducedReverseOrder",true,"success",1,"mysql1213Victims",1);
    }
    static Map<String,Object> crud(){
        var mine=calendar.createEvent(user,event(personal,"10:00","11:00"));var otherCat=calendar.createCategory(other,category("other"));calendar.createEvent(other,event(otherCat.id(),null,null));
        check(calendar.getEvents(null,new CalendarEventListRequest(DAY,DAY,null)).isEmpty(),"Private event leaked");check(calendar.getEvents(user,new CalendarEventListRequest(DAY,DAY,null)).size()==1,"Wrong owner list");
        business(capture(()->calendar.deleteEvent(other,mine.id())),ErrorCode.FORBIDDEN);business(capture(()->calendar.createEvent(user,event(otherCat.id(),null,null))),ErrorCode.FORBIDDEN);
        business(capture(()->calendar.createEvent(user,event(school,null,null))),ErrorCode.FORBIDDEN);business(capture(()->calendar.deleteCategory(user,school)),ErrorCode.FORBIDDEN);
        business(capture(()->calendar.updateCategory(user,school,new CalendarCategoryUpdateRequest("bad",null,null))),ErrorCode.FORBIDDEN);
        business(capture(()->calendar.saveCategorySettings(user,Arrays.asList(new CalendarCategorySettingRequest(personal,"changed","#654321",true),null))),ErrorCode.VALIDATION_ERROR);
        check(calendar.getCategories(user).stream().anyMatch(x->x.id().equals(personal)&&x.label().equals("personal")),"Invalid batch partially committed");
        calendar.updateEvent(user,mine.id(),new CalendarEventUpdateRequest("updated",null,DAY.plusDays(1),null,null,null,null));calendar.deleteEvent(user,mine.id());check(!events.existsById(mine.id()),"CRUD delete failed");
        business(capture(()->admins.getScheduleCrawlStatuses(user)),ErrorCode.FORBIDDEN);return Map.of("ownership",true,"systemProtection",true,"batchRollback",true,"crud",true);
    }
    static Map<String,Object> nullableTimes(){
        for(String[] time:new String[][]{{"10:00",null},{null,"11:00"},{null,null},{"23:45","00:00"}}){var e=calendar.createEvent(user,new CalendarEventCreateRequest("range",DAY,DAY.plusDays(2),time[0],time[1],personal,null));check(e.startDate().equals(DAY)&&e.endDate().equals(DAY.plusDays(2)),"Date range changed");}
        var stored=calendar.getEvents(user,new CalendarEventListRequest(DAY.plusDays(1),DAY.plusDays(1),null));check(stored.size()==4&&stored.stream().filter(e->e.startTime()==null).count()==2&&stored.stream().filter(e->e.endTime()==null).count()==2,"Nullable times or overlap query changed");return Map.of("variants",4,"overlappingEvents",4);
    }
    static Map<String,Object> timezones()throws Exception{
        LocalDateTime before=LocalDateTime.now(ZoneId.of("Asia/Seoul"));crawling.crawlAcademicCalendar();LocalDateTime after=LocalDateTime.now(ZoneId.of("Asia/Seoul"));var run=latest();
        check(!run.getStartedAt().isBefore(before.minusSeconds(1))&&!run.getCompletedAt().isAfter(after.plusSeconds(1)),"Seoul crawl wall time shifted");
        try(Connection c=guarded.getConnection()){String zone=rows(c,"SELECT @@session.time_zone").getFirst().values().iterator().next().toString();check(Set.of("UTC","+00:00").contains(zone),"Session not UTC");
            try(var p=c.prepareStatement("SELECT started_at,DATE_FORMAT(started_at,'%Y-%m-%d %H:%i:%s') FROM schedule_crawl_run WHERE crawl_run_id=?")){p.setLong(1,run.getCrawlRunId());try(var r=p.executeQuery()){r.next();
                LocalDateTime raw=r.getObject(1,LocalDateTime.class);
                write("timezone-utc-observation.json",Map.of("ormSeoulTime",run.getStartedAt().toString(),"rawDatetime",raw.toString(),"sqlDatetimeLiteral",r.getString(2),"wallClockDifferenceHours",java.time.temporal.ChronoUnit.HOURS.between(raw,run.getStartedAt()),"sessionZone",zone));
                check(raw.equals(run.getStartedAt()),"DATETIME local wall time was timezone converted");}}
        }return Map.of("jvmZone",ZoneId.systemDefault().toString(),"jdbcSessionZone","UTC","storedDatetimeSemantics","Asia/Seoul local wall time","dateTimeRoundtrip",true);
    }
    static Map<String,Object> mixedTimezones()throws Exception{
        String offsetUrl=url().replace("connectionTimeZone="+jdbcZone(),"connectionTimeZone=%2B09:00");
        DataSource seoul=new AbstractDataSource(){
            public Connection getConnection()throws SQLException{var c=DriverManager.getConnection(offsetUrl,ACCOUNT,System.getenv("KKIRI_CAL_PASSWORD"));try{guard(c,offsetUrl,Set.of("+09:00"));return c;}catch(Throwable e){c.close();throw e;}}
            public Connection getConnection(String u,String p)throws SQLException{throw new SQLException("Alternate credentials forbidden");}
        };
        var f=emf(seoul,"validate");f.afterPropertiesSet();EntityManager em=f.getObject().createEntityManager();
        LocalDateTime expected=LocalDateTime.of(2026,10,16,12,34,56);Long id=null;
        try{
            em.getTransaction().begin();var run=new ScheduleCrawlRun(ScheduleCrawlCategory.SCHOOL,expected);em.persist(run);em.getTransaction().commit();id=run.getCrawlRunId();em.clear();
            LocalDateTime seoulRead=em.find(ScheduleCrawlRun.class,id).getStartedAt();
            LocalDateTime utcRead=runs.findById(id).orElseThrow().getStartedAt();
            write("timezone-mixed-observation.json",Map.of("writtenSeoulTime",expected.toString(),"seoulJdbcRead",seoulRead.toString(),"primaryJdbcRead",utcRead.toString(),"primaryJdbcZone",jdbcZone(),"readDifferenceHours",java.time.temporal.ChronoUnit.HOURS.between(seoulRead,utcRead)));
            check(seoulRead.equals(expected)&&utcRead.equals(expected),"Same DATETIME has different values under UTC and Seoul JDBC sessions");
            // When the primary matrix connection is Seoul too, use an actual independent
            // UTC JDBC session, rather than comparing two Seoul sessions under a UTC label.
            if(!jdbcZone().equals("UTC")) {
                String utcUrl=url().replace("connectionTimeZone="+jdbcZone(),"connectionTimeZone=UTC");
                DataSource utc=new AbstractDataSource(){
                    public Connection getConnection()throws SQLException{var c=DriverManager.getConnection(utcUrl,ACCOUNT,System.getenv("KKIRI_CAL_PASSWORD"));try{guard(c,utcUrl,Set.of("UTC","+00:00"));return c;}catch(Throwable e){c.close();throw e;}}
                    public Connection getConnection(String u,String p)throws SQLException{throw new SQLException("Alternate credentials forbidden");}
                };
                var uf=emf(utc,"validate");uf.afterPropertiesSet();EntityManager ue=uf.getObject().createEntityManager();
                try {
                    LocalDateTime independentUtc=ue.find(ScheduleCrawlRun.class,id).getStartedAt();
                    check(independentUtc.equals(expected),"Same physical Seoul DATETIME shifted in independent UTC JDBC session");
                    write("timezone-independent-utc-observation.json",Map.of("writtenSeoulTime",expected.toString(),"utcJdbcRead",independentUtc.toString(),"primaryJdbcZone",jdbcZone()));
                } finally {ue.close();uf.destroy();}
            }
            return Map.of("mixedSessionReadsEqual",true);
        }finally{if(em.getTransaction().isActive())em.getTransaction().rollback();em.close();f.destroy();}
    }
    static Map<String,Object> matrixRoundtrip()throws Exception{
        LocalDateTime start=LocalDateTime.of(2026,10,16,23,59,59), end=start.plusSeconds(1);
        Long id=inTx(()->{var r=new ScheduleCrawlRun(ScheduleCrawlCategory.SCHOOL,start);r.complete(0,end);return runs.saveAndFlush(r).getCrawlRunId();});
        var loaded=runs.findById(id).orElseThrow();
        check(loaded.getStartedAt().equals(start)&&loaded.getCompletedAt().equals(end),"Logical history roundtrip shifted");
        check(status().lastRunAt().equals(start)&&status().lastCompletedAt().equals(end),"Admin history time shifted");
        try(Connection c=guarded.getConnection();var p=c.prepareStatement("SELECT started_at,completed_at FROM schedule_crawl_run WHERE crawl_run_id=?")){
            p.setLong(1,id);try(var rs=p.executeQuery()){rs.next();check(rs.getObject(1,LocalDateTime.class).equals(start)&&rs.getObject(2,LocalDateTime.class).equals(end),"Raw history DATETIME shifted");}
        }
        var event=calendar.createEvent(user,new CalendarEventCreateRequest("midnight",DAY,DAY.plusDays(1),"23:59","00:00",personal,null));
        var read=calendar.getEvents(user,new CalendarEventListRequest(DAY,DAY.plusDays(1),null)).getFirst();
        check(read.startDate().equals(DAY)&&read.endDate().equals(DAY.plusDays(1))&&read.startTime().equals(LocalTime.of(23,59))&&read.endTime().equals(LocalTime.MIDNIGHT),"Local schedule shifted");
        try(Connection c=guarded.getConnection();var p=c.prepareStatement("SELECT start_date,end_date,start_time,end_time FROM calendar_event WHERE event_id=?")){
            p.setLong(1,event.id());try(var rs=p.executeQuery()){rs.next();check(rs.getObject(1,LocalDate.class).equals(DAY)&&rs.getObject(2,LocalDate.class).equals(DAY.plusDays(1))&&rs.getObject(3,LocalTime.class).equals(LocalTime.of(23,59))&&rs.getObject(4,LocalTime.class).equals(LocalTime.MIDNIGHT),"Raw local date/time shifted");}
        }
        crawling.crawlAcademicCalendar();
        // The fixed midnight run may be in the future. Inspect the new execution by ID,
        // rather than accidentally selecting that fixed run through the latest-by-time API.
        var actual=runs.findAll().stream().filter(r->!r.getCrawlRunId().equals(id))
                .max(Comparator.comparing(ScheduleCrawlRun::getCrawlRunId)).orElseThrow();
        check(java.time.Duration.between(actual.getStartedAt(),LocalDateTime.now(ZoneId.of("Asia/Seoul"))).abs().toSeconds()<30,"Crawler does not use Seoul wall time");
        var cat=categories.findById(personal).orElseThrow();
        check(java.time.Duration.between(cat.getCreatedAt(),LocalDateTime.now(ZoneId.of("Asia/Seoul"))).abs().toSeconds()<30,"Category does not use Seoul wall time");
        try(Connection c=guarded.getConnection()) {
            try(var p=c.prepareStatement("SELECT created_at FROM calendar_category WHERE category_id=?")) {
                p.setLong(1,personal);try(var rs=p.executeQuery()){rs.next();check(rs.getObject(1,LocalDateTime.class).equals(cat.getCreatedAt()),"Raw category DATETIME shifted");}
            }
            var collected=events.findAll().stream().filter(e->e.getSourceType()==CalendarEventSourceType.CRAWLED).findFirst().orElseThrow();
            try(var p=c.prepareStatement("SELECT collected_at FROM calendar_event WHERE event_id=?")) {
                p.setLong(1,collected.getEventId());try(var rs=p.executeQuery()){rs.next();check(rs.getObject(1,LocalDateTime.class).equals(collected.getCollectedAt()),"Raw collected DATETIME shifted");}
            }
        }
        return Map.of("jvmZone",ZoneId.systemDefault().toString(),"jdbcZone",jdbcZone(),"rawAndLogicalEqual",true,"midnightBoundary",true,"adminEqual",true);
    }
    static Map<String,Object> plans()throws Exception{
        long insertStart=System.nanoTime();
        try(Connection c=guarded.getConnection();var p=c.prepareStatement("INSERT INTO calendar_event(owner_user_id,category_id,title,start_date,end_date,is_all_day,source_type) VALUES (?,?,'plan-history',?, ?,true,'USER')")){
            c.setAutoCommit(false);
            try {for(int i=0;i<5000;i++){p.setLong(1,user);p.setLong(2,personal);p.setObject(3,LocalDate.of(2000,1,1).plusDays(i%365));p.setObject(4,LocalDate.of(2000,1,1).plusDays(i%365));p.addBatch();}p.executeBatch();c.commit();}
            catch(Exception e){c.rollback();throw e;}
        }
        long insertMillis=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-insertStart);
        calendar.createEvent(user,event(personal,"10:00","11:00"));var captured=new AtomicReference<Execution>();
        observer.set(e->{if(e.sql.toLowerCase(Locale.ROOT).contains(" from calendar_event ")&&!e.sql.toLowerCase(Locale.ROOT).contains("count("))captured.compareAndSet(null,e);});
        try{check(calendar.getEvents(user,new CalendarEventListRequest(DAY,DAY,null)).size()==1,"Wrong large-table list");}finally{observer.remove();}
        check(captured.get()!=null,"Actual list SQL not observed");comparePlans("list",captured.get(),"idx_calendar_period");
        captured.set(null);observer.set(e->{if(e.sql.toLowerCase(Locale.ROOT).contains(" from calendar_event "))captured.compareAndSet(null,e);});
        try{check(!events.existsBySourceTypeAndSourceNameAndTitleAndStartDateAndEndDate(CalendarEventSourceType.CRAWLED,HknuAcademicCalendarCrawler.SOURCE_NAME,"missing",DAY,DAY),"Unexpected public fixture");}finally{observer.remove();}
        check(captured.get()!=null,"Dedup SQL not observed");comparePlans("dedup",captured.get(),"idx_calendar_crawl_identity");
        crawling.crawlAcademicCalendar();
        var another=calendar.createCategory(other,category("plan-other"));calendar.createEvent(other,event(another.id(),null,null));
        var publicEvents=calendar.getEvents(null,new CalendarEventListRequest(DAY,DAY,null));
        var visible=calendar.getEvents(user,new CalendarEventListRequest(DAY,DAY,null));
        check(visible.size()==publicEvents.size()+1,"Large-table visibility changed");
        check(calendar.getEvents(user,new CalendarEventListRequest(DAY,DAY,List.of(categories.findById(personal).orElseThrow().getCategoryCode()))).size()==1,"Category-filtered period changed");
        check(publicEvents.stream().allMatch(e->!e.editable()&&HknuAcademicCalendarCrawler.SOURCE_NAME.equals(e.source())),"Large-table private event leaked");
        return Map.of("rows",5001,"listMatches",1,"bulkInsertMillis",insertMillis,"explainUsesActualPreparedSql",true,"indexes",jdbc.queryForList("SELECT table_name,index_name,column_name,seq_in_index FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name IN ('calendar_event','schedule_crawl_run') ORDER BY table_name,index_name,seq_in_index"));
    }
    static void comparePlans(String label,Execution actual,String expectedIndex)throws Exception {
        // Same data/SQL/binds; the baseline hides only the two new indexes. Never force an index in the application query.
        String ignored=actual.sql.replaceFirst("(?i)from calendar_event (\\w+)","from calendar_event $1 IGNORE INDEX (idx_calendar_crawl_identity,idx_calendar_period)");
        check(!ignored.equals(actual.sql),"Calendar table alias not recognized");
        Execution baseline=new Execution(ignored,actual.parameters);
        try(Connection c=guarded.getConnection()){check(rows(c,actual.sql,actual.parameters.toArray()).equals(rows(c,baseline.sql,baseline.parameters.toArray())),"Indexes changed query results");}
        var before=explain(baseline);var after=explain(actual);
        write("explain-"+label+"-before.json",before);write("explain-"+label+".json",after);
        check(after.toString().contains(expectedIndex),"Optimizer did not use "+expectedIndex);
    }
    static List<Map<String,Object>> explain(Execution e)throws Exception{check(e.sql.stripLeading().toLowerCase(Locale.ROOT).startsWith("select"),"Only SELECT may be explained");try(Connection c=guarded.getConnection()){return rows(c,"EXPLAIN ANALYZE "+e.sql,e.parameters.toArray());}}
}
