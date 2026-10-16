package com.kkirikkiri.calendar.crawler;

import com.kkirikkiri.BackendApplication;
import com.kkirikkiri.calendar.scheduler.CalendarCrawlingScheduler;
import com.zaxxer.hikari.*;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.lang.reflect.*;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.sql.DataSource;
import org.jsoup.Jsoup;
import org.springframework.boot.*;
import org.springframework.context.*;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.util.ContentCachingResponseWrapper;
import jakarta.servlet.http.*;
import static com.kkirikkiri.calendar.crawler.CalendarMultiSupport.*;

/** Full Boot app, original MVC/security/services/repositories. Only test infrastructure is supplied. */
public class CalendarMultiServer {
    record Gate(String kind,String path,CountDownLatch release,AtomicBoolean entered){}
    static final AtomicReference<Gate> gate=new AtomicReference<>();
    static final ThreadLocal<String> path=new ThreadLocal<>();static final ThreadLocal<Long> connectionId=new ThreadLocal<>();
    static final AtomicInteger peakActive=new AtomicInteger(),peakWaiting=new AtomicInteger();
    static volatile String fixtureMode="NORMAL";static HttpServer fixtureServer;static ExecutorService fixtureWorkers;
    static byte[] html;static int fixturePort;static long socketTimeouts;
    static void event(String kind,Object data){System.out.println("CM_EVENT "+JSON.writeValueAsString(Map.of("kind",kind,"data",data)));System.out.flush();}
    static void gated(String kind)throws Exception{Gate g=gate.get();if(g!=null&&g.kind.equals(kind)&&(g.path.isEmpty()||Objects.equals(g.path,path.get()))&&g.entered.compareAndSet(false,true)){
        event("gated",Map.of("kind",kind,"connection",Objects.requireNonNullElse(connectionId.get(),0L)));
        try{check(g.release.await(15,TimeUnit.SECONDS),"Test coordination gate expired");}finally{gate.compareAndSet(g,null);}
    }}
    static Map<String,Object> stats(HikariDataSource pool){var m=pool.getHikariPoolMXBean();return Map.of("active",m.getActiveConnections(),"waiting",m.getThreadsAwaitingConnection(),"total",m.getTotalConnections(),"peakActive",peakActive.get(),"peakWaiting",peakWaiting.get());}
    public static void main(String[] args)throws Exception{
        int port=Integer.parseInt(args[0]);check(port==8081||port==8082,"Unsafe HTTP port");
        try(var c=CalendarMultiSupport.open()){grants(c);}
        html=Files.readAllBytes(Path.of(args[1]).resolve("backend/src/test/resources/calendar/hknu-2026.html"));
        fixtureWorkers=Executors.newVirtualThreadPerTaskExecutor();fixtureServer=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);fixtureServer.setExecutor(fixtureWorkers);
        fixtureServer.createContext("/fixture",exchange->{try{String mode=fixtureMode;event("fixtureRequest",Map.of("mode",mode));if(mode.equals("TIMEOUT"))Thread.sleep(32000);byte[] body=mode.equals("DB_FAIL")?("<div id='timeTableList'><ul><li><div class='box-year'>2026</div><div class='list-inner'><div class='list-box'><span class='list-content'>fresh-before-failure</span><span class='list-date'>10.16</span></div><div class='list-box'><span class='list-content'>CALENDAR_DB_FAIL</span><span class='list-date'>10.16</span></div></div></li></ul></div>").getBytes(java.nio.charset.StandardCharsets.UTF_8):html;
            int status=mode.equals("FETCH_FAIL")?503:200;exchange.getResponseHeaders().set("Content-Type","text/html;charset=UTF-8");exchange.sendResponseHeaders(status,body.length);exchange.getResponseBody().write(body);
        }catch(Exception ignored){}finally{exchange.close();}});fixtureServer.start();fixturePort=fixtureServer.getAddress().getPort();
        var p=new HashMap<String,String>();
        p.put("spring.profiles.active","calendar-multi-validation");p.put("spring.config.location","classpath:/application.properties");p.put("spring.config.import","");p.put("spring.profiles.include","");
        p.put("server.address","127.0.0.1");p.put("server.port",""+port);p.put("server.forward-headers-strategy","NONE");
        p.put("spring.datasource.url",url());p.put("spring.datasource.username",account());p.put("spring.datasource.password",System.getenv("CM_PASSWORD"));
        p.put("spring.jpa.hibernate.ddl-auto","validate");p.put("spring.sql.init.mode","never");p.put("spring.jpa.show-sql","false");p.put("spring.jpa.properties.hibernate.format_sql","false");p.put("spring.jpa.open-in-view","false");
        p.put("app.seed.mode","NONE");p.put("app.calendar.crawl-on-startup","false");p.put("jwt.secret",System.getenv("CM_JWT"));p.put("jwt.access-token-expiration-seconds","3600");
        p.put("ai.api-key","unused");p.put("ai.base-url","http://127.0.0.1:1");p.put("ai.model","unused");p.put("spring.mail.host","127.0.0.1");p.put("spring.mail.port","1");p.put("spring.mail.test-connection","false");p.put("auth.email-verification.mail-from","calendar@example.invalid");
        p.put("logging.level.root","OFF");p.put("logging.level.org.springframework","OFF");p.put("logging.level.org.hibernate","OFF");p.put("logging.level.com.zaxxer.hikari","OFF");p.put("spring.main.banner-mode","off");p.forEach(System::setProperty);
        ((ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).setLevel(ch.qos.logback.classic.Level.OFF);
        ConfigurableApplicationContext context=null;HikariDataSource pool=null;var held=new ArrayList<Connection>();var monitor=Executors.newSingleThreadScheduledExecutor();boolean failed=false;
        try{
            var app=new SpringApplication(BackendApplication.class,Infrastructure.class);app.setLogStartupInfo(false);context=app.run();pool=context.getBean("calendarValidationPool",HikariDataSource.class);
            check(context.getBeansOfType(org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor.class).isEmpty(),"Automatic cron processor is active");
            check(context.getBeansOfType(ApplicationRunner.class).isEmpty(),"Unexpected seed runner");check(context.getBean(HknuAcademicCalendarCrawler.class) instanceof FixtureCrawler,"Real crawler selected");
            HikariDataSource watched=pool;monitor.scheduleAtFixedRate(()->{peakActive.accumulateAndGet(watched.getHikariPoolMXBean().getActiveConnections(),Math::max);peakWaiting.accumulateAndGet(watched.getHikariPoolMXBean().getThreadsAwaitingConnection(),Math::max);},0,10,TimeUnit.MILLISECONDS);
            try(var c=CalendarMultiSupport.open()){event("ready",Map.of("pid",ProcessHandle.current().pid(),"port",port,"db",db(),"account",account(),"jvmZone",java.time.ZoneId.systemDefault().toString(),"jdbcZone",zone(),"sessionZone",rows(c,"SELECT @@session.time_zone z").getFirst().get("z"),"ddl","validate","schedulerBean",context.getBeansOfType(CalendarCrawlingScheduler.class).size(),"automaticScheduling",false));}
            try(var reader=new BufferedReader(new InputStreamReader(System.in))){String line;while((line=reader.readLine())!=null){
                if(line.equals("STOP"))break;
                if(line.equals("STATS"))event("pool",stats(pool));
                if(line.startsWith("MODE ")){fixtureMode=line.substring(5);event("mode",Map.of("mode",fixtureMode));}
                if(line.startsWith("ARM ")){var parts=line.split(" ",3);check(gate.compareAndSet(null,new Gate(parts[1],parts.length==3?parts[2]:"",new CountDownLatch(1),new AtomicBoolean())),"Gate already armed");event("armed",Map.of());}
                if(line.equals("RELEASE")){Gate g=gate.get();if(g!=null)g.release.countDown();event("released",Map.of());}
                if(line.equals("HOLD_POOL")){for(int i=0;i<6;i++)held.add(pool.getConnection());event("held",stats(pool));}
                if(line.equals("RELEASE_POOL")){for(var c:held)c.close();held.clear();event("releasedPool",stats(pool));}
                if(line.equals("CRAWL")){var scheduler=context.getBean(CalendarCrawlingScheduler.class);context.getBean(ThreadPoolTaskScheduler.class).schedule(()->{try{scheduler.collectAcademicCalendar();}finally{event("crawlDone",Map.of());}},Instant.now());event("crawlScheduled",Map.of());}
            }}
        }catch(Throwable t){failed=true;event("error",error(t));}
        finally{Gate g=gate.get();if(g!=null)g.release.countDown();monitor.shutdownNow();for(var c:held)try{c.close();}catch(Exception ignored){}if(context!=null)context.close();fixtureServer.stop(0);fixtureWorkers.shutdownNow();fixtureWorkers.awaitTermination(5,TimeUnit.SECONDS);event("closed",Map.of("poolClosed",pool!=null&&pool.isClosed(),"fixtureStopped",true,"peakActive",peakActive.get(),"peakWaiting",peakWaiting.get()));}
        if(failed)System.exit(1);
    }
    public static class FixtureCrawler extends HknuAcademicCalendarCrawler {
        @Override public List<CrawledAcademicEvent> crawl(){try{gated("CRAWL_FETCH");return parse(Jsoup.connect("http://127.0.0.1:"+fixturePort+"/fixture").userAgent("RoommateCalendar/1.0").timeout(30000).maxBodySize(5000000).get());}catch(Exception e){throw new IllegalStateException("Controlled local fixture failure",e);}}
    }
    @Configuration(proxyBeanMethods=false)
    public static class Infrastructure {
        @Bean static BeanFactoryPostProcessor controlCron(){return f->{var r=(DefaultListableBeanFactory)f;for(String n:r.getBeanNamesForType(org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor.class,false,false))r.removeBeanDefinition(n);};}
        @Bean @Primary ThreadPoolTaskScheduler calendarValidationScheduler(){var s=new ThreadPoolTaskScheduler();s.setPoolSize(2);s.setThreadNamePrefix("calendar-controlled-scheduler-");s.setWaitForTasksToCompleteOnShutdown(false);return s;}
        @Bean @Primary HknuAcademicCalendarCrawler fixtureCrawler(){return new FixtureCrawler();}
        @Bean(destroyMethod="close") HikariDataSource calendarValidationPool(){var p=new HikariDataSource();p.setJdbcUrl(url());p.setUsername(account());p.setPassword(System.getenv("CM_PASSWORD"));p.setMaximumPoolSize(6);p.setMinimumIdle(0);p.setConnectionTimeout(5000);p.setConnectionInitSql("SET SESSION innodb_lock_wait_timeout=2");return p;}
        @Bean @Primary DataSource calendarValidationSource(HikariDataSource pool){return new AbstractDataSource(){public Connection getConnection()throws SQLException{var c=pool.getConnection();try{guard(c);long id=number(c,"SELECT CONNECTION_ID()");connectionId.set(id);return observe(c,id);}catch(Throwable e){c.close();throw e;}}public Connection getConnection(String u,String p)throws SQLException{throw new SQLException("Alternate credentials forbidden");}};}
        @Bean JavaMailSender noNetworkMailSender(){return (JavaMailSender)Proxy.newProxyInstance(JavaMailSender.class.getClassLoader(),new Class<?>[]{JavaMailSender.class},(p,m,a)->{if(m.getName().equals("toString"))return "CalendarNoSMTP";if(m.getName().equals("hashCode"))return System.identityHashCode(p);if(m.getName().equals("equals"))return p==a[0];event("forbiddenExternalCall",Map.of("transport","SMTP"));throw new IllegalStateException("SMTP prohibited");});}
        @Bean org.springframework.boot.web.servlet.FilterRegistrationBean<org.springframework.web.filter.OncePerRequestFilter> calendarValidationTrace(){
            var f=new org.springframework.web.filter.OncePerRequestFilter(){protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,jakarta.servlet.FilterChain chain)throws IOException,jakarta.servlet.ServletException{
                path.set(req.getRequestURI());var wrap=new ContentCachingResponseWrapper(res);
                try{chain.doFilter(req,wrap);gated("AFTER_RESPONSE");wrap.copyBodyToResponse();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new jakarta.servlet.ServletException(e);}catch(IOException|jakarta.servlet.ServletException e){throw e;}catch(Exception e){throw new jakarta.servlet.ServletException(e);}finally{path.remove();connectionId.remove();}
            }};var reg=new org.springframework.boot.web.servlet.FilterRegistrationBean<org.springframework.web.filter.OncePerRequestFilter>(f);reg.setOrder(Integer.MIN_VALUE);return reg;
        }
    }
    static Connection observe(Connection c,long id){return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(p,m,a)->{try{Object result=m.invoke(c,a);if(result instanceof PreparedStatement statement&&a!=null&&a.length>0&&a[0] instanceof String sql)return Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),new Class<?>[]{PreparedStatement.class},(sp,sm,sa)->{try{Object value=sm.invoke(statement,sa);if(sm.getName().startsWith("execute")){
            String lower=sql.toLowerCase(Locale.ROOT);if(lower.contains(" from user ")&&lower.contains("for update"))gated("ACCOUNT");if(lower.startsWith("insert into calendar_event"))gated("EVENT_INSERT");if(lower.startsWith("update schedule_crawl_run"))gated("CRAWL_COMPLETE");
        }return value;}catch(InvocationTargetException e){Throwable cause=e.getCause();if(cause instanceof SQLException s)event("jdbcFailure",Map.of("connection",id,"path",Objects.toString(path.get(),"scheduler"),"code",s.getErrorCode(),"state",Objects.toString(s.getSQLState(),"")));throw cause;}});return result;}catch(InvocationTargetException e){throw e.getCause();}});}
}
