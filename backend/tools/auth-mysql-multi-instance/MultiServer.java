import com.kkirikkiri.BackendApplication;
import com.zaxxer.hikari.*;
import java.io.*;
import java.net.*;
import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationTargetException;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import org.springframework.boot.*;
import org.springframework.context.*;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;
import jakarta.servlet.http.*;

/** Full production Spring Boot application; only isolated infrastructure is supplied here. */
public class MultiServer {
    static final AtomicInteger peakActive=new AtomicInteger(),peakWaiting=new AtomicInteger();
    static final AtomicLong leases=new AtomicLong();
    static Map<String,Object> stats(HikariDataSource pool){var m=pool.getHikariPoolMXBean();return Map.of("active",m.getActiveConnections(),"idle",m.getIdleConnections(),"total",m.getTotalConnections(),"waiting",m.getThreadsAwaitingConnection(),"peakActive",peakActive.get(),"peakWaiting",peakWaiting.get());}
    static void event(String kind,Object data){System.out.println("MI_EVENT "+AuthMysqlConcurrency.json(Map.of("kind",kind,"data",data)));System.out.flush();}
    public static void main(String[] args)throws Exception {
        int port=Integer.parseInt(args[0]);MultiSupport.check(port==8081||port==8082,"Unsafe HTTP port");
        try(var c=MultiSupport.open()){MultiSupport.grants(c);}
        var settings=new HashMap<String,String>();
        settings.put("spring.profiles.active","auth-multi-validation");settings.put("spring.config.import","");
        settings.put("spring.config.location","classpath:/application.properties");settings.put("spring.profiles.include","");
        settings.put("server.address","127.0.0.1");settings.put("server.port",Integer.toString(port));
        settings.put("spring.datasource.url",MultiSupport.url());settings.put("spring.datasource.username",MultiSupport.user());settings.put("spring.datasource.password",System.getenv("KKIRI_MI_PASSWORD"));
        settings.put("spring.jpa.hibernate.ddl-auto","validate");settings.put("spring.sql.init.mode","never");settings.put("spring.jpa.show-sql","false");settings.put("spring.jpa.properties.hibernate.format_sql","false");
        settings.put("spring.jpa.open-in-view","false");
        settings.put("spring.jpa.properties.hibernate.session_factory.statement_inspector","MultiServer$AuthSqlInspector");
        settings.put("app.seed.mode","NONE");settings.put("app.calendar.crawl-on-startup","false");
        settings.put("jwt.secret",System.getenv("KKIRI_MI_JWT"));settings.put("ai.api-key","unused-test-key");settings.put("ai.base-url","http://127.0.0.1:1");settings.put("ai.model","unused");
        settings.put("spring.mail.host","127.0.0.1");settings.put("spring.mail.port","1");settings.put("spring.mail.username","");settings.put("spring.mail.password","");settings.put("spring.mail.test-connection","false");settings.put("auth.email-verification.mail-from","test@example.invalid");
        settings.put("auth.protection.trusted-proxies","127.0.0.1/32");settings.put("server.forward-headers-strategy","NONE");
        settings.put("logging.level.root","OFF");settings.put("spring.main.banner-mode","off");
        // Pin every protection setting: inherited shell variables cannot change this validation.
        settings.putAll(AuthMultiInstance.protectionSettings());
        settings.forEach(System::setProperty);
        ((ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).setLevel(ch.qos.logback.classic.Level.OFF);
        ConfigurableApplicationContext context=null;HikariDataSource pool=null;boolean failed=false;
        ScheduledExecutorService monitor=Executors.newSingleThreadScheduledExecutor();var held=new ArrayList<Connection>();
        try {
            var app=new SpringApplication(BackendApplication.class,Infrastructure.class);app.setLogStartupInfo(false);
            context=app.run();pool=context.getBean("multiPool",HikariDataSource.class);
            final HikariDataSource watched=pool;monitor.scheduleAtFixedRate(()->{var m=watched.getHikariPoolMXBean();peakActive.accumulateAndGet(m.getActiveConnections(),Math::max);peakWaiting.accumulateAndGet(m.getThreadsAwaitingConnection(),Math::max);},0,10,TimeUnit.MILLISECONDS);
            event("infrastructure",Map.of("scheduledProcessors",context.getBeansOfType(org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor.class).keySet(),"calendarSchedulers",context.getBeansOfType(com.kkirikkiri.calendar.scheduler.CalendarCrawlingScheduler.class).keySet()));
            MultiSupport.check(context.getBeansOfType(org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor.class).isEmpty(),"Scheduling was not disabled");
            MultiSupport.check(context.getBeansOfType(org.springframework.boot.ApplicationRunner.class).isEmpty(),"Unexpected application runners/seeds");
            MultiSupport.check(Proxy.isProxyClass(context.getBean(JavaMailSender.class).getClass()),"Real mail sender present");
            event("ready",Map.of("pid",ProcessHandle.current().pid(),"port",port,"database",MultiSupport.db(),"account",MultiSupport.user(),"profile",List.of(context.getEnvironment().getActiveProfiles()),"hibernate","validate","scheduledProcessors",0,"mailTransport","memory-only authenticated loopback IPC","protection",AuthMultiInstance.protectionSettings()));
            try(var reader=new BufferedReader(new InputStreamReader(System.in))){String line;while((line=reader.readLine())!=null){if(line.equals("STOP"))break;if(line.equals("STATS"))event("pool",stats(pool));if(line.equals("HOLD_POOL")){for(int i=0;i<12;i++)held.add(pool.getConnection());event("heldPool",stats(pool));}if(line.equals("RELEASE_POOL")){for(var c:held)c.close();held.clear();event("releasedPool",stats(pool));}}}
        } catch(Throwable t){failed=true;event("error",MultiSupport.error(t));}
        finally {monitor.shutdownNow();for(var c:held)c.close();if(context!=null)context.close();event("closed",Map.of("poolClosed",pool!=null&&pool.isClosed(),"peakActive",peakActive.get(),"peakWaiting",peakWaiting.get()));}
        if(failed)System.exit(1);
    }
    @Configuration(proxyBeanMethods=false)
    public static class Infrastructure {
        @Bean HandlerExceptionResolver observeSqlFailure(){return new SqlFailureObserver();}
        @Bean static BeanFactoryPostProcessor preventScheduledWork(){return factory->{var registry=(DefaultListableBeanFactory)factory;
            var names=new ArrayList<String>();names.addAll(List.of(registry.getBeanNamesForType(org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor.class,false,false)));names.addAll(List.of(registry.getBeanNamesForType(com.kkirikkiri.calendar.scheduler.CalendarCrawlingScheduler.class,false,false)));
            for(String name:names){if(registry.containsSingleton(name))registry.destroySingleton(name);if(registry.containsBeanDefinition(name))registry.removeBeanDefinition(name);}event("removedSchedulers",names);
        };}
        @Bean(destroyMethod="close") HikariDataSource multiPool(){var p=new HikariDataSource();p.setJdbcUrl(MultiSupport.url());p.setUsername(MultiSupport.user());p.setPassword(System.getenv("KKIRI_MI_PASSWORD"));p.setMaximumPoolSize(12);p.setMinimumIdle(0);p.setConnectionTimeout(5000);p.setConnectionInitSql("SET SESSION innodb_lock_wait_timeout=2");return p;}
        @Bean @Primary DataSource dataSource(HikariDataSource multiPool){return new AbstractDataSource(){public Connection getConnection()throws SQLException {var c=multiPool.getConnection();try{MultiSupport.guard(c);return observe(c,leases.incrementAndGet());}catch(Throwable t){c.close();throw t;}}public Connection getConnection(String u,String p)throws SQLException{throw new SQLException("Explicit credentials forbidden");}};}
        @Bean JavaMailSender noNetworkMailSender(){return (JavaMailSender)Proxy.newProxyInstance(JavaMailSender.class.getClassLoader(),new Class<?>[]{JavaMailSender.class},(proxy,method,args)->{
            if(method.getName().equals("toString"))return "NoNetworkMailSender";
            if(method.getName().equals("send")) {for(Object arg:args){if(arg instanceof SimpleMailMessage m)capture(m);else if(arg instanceof SimpleMailMessage[] ms)for(var m:ms)capture(m);else throw new MailSendException("Unsupported test message");}return null;}
            if(method.getName().equals("equals"))return proxy==args[0];if(method.getName().equals("hashCode"))return System.identityHashCode(proxy);
            throw new MailSendException("No SMTP transport available");});}
        static void capture(SimpleMailMessage message)throws Exception {
            // Message content and tokens stay in parent memory; never in stdout or result files.
            try(var socket=new Socket()){socket.connect(new InetSocketAddress("127.0.0.1",Integer.parseInt(System.getenv("KKIRI_MI_MAIL_PORT"))),2000);socket.setSoTimeout(3000);
                var out=new DataOutputStream(socket.getOutputStream());out.writeUTF(System.getenv("KKIRI_MI_IPC"));out.writeUTF(System.getProperty("server.port"));out.writeUTF(message.getTo()[0]);out.writeUTF(message.getText());out.flush();
                if(!new DataInputStream(socket.getInputStream()).readBoolean())throw new MailSendException("Mock delivery rejected");}
        }
    }
    static Connection observe(Connection connection,long lease){String trace=Objects.toString(org.slf4j.MDC.get("authRequestId"),"");if(!trace.isEmpty())event("jdbcLease",Map.of("lease",lease,"requestId",trace));return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args)->{
        try{Object result=method.invoke(connection,args);if(result instanceof PreparedStatement statement&&args!=null&&args.length>0&&args[0] instanceof String sql){String lower=sql.toLowerCase(Locale.ROOT);if((lower.contains("from auth_limit_bucket")||lower.contains("into auth_limit_bucket")||lower.contains("update auth_limit_bucket")||lower.contains("from auth_email_identity")||lower.contains("into auth_email_identity"))&&AuthSqlInspector.seen.add("jdbc:"+sql))event("jdbcAuthSql",sql);return Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),new Class<?>[]{PreparedStatement.class},(p,m,a)->{try{return m.invoke(statement,a);}catch(InvocationTargetException e){Throwable cause=e.getCause();if(cause instanceof SQLException error)event("jdbcFailure",Map.of("lease",lease,"requestId",trace,"sql",sql,"error",MultiSupport.error(error)));throw cause;}});}return result;}catch(InvocationTargetException e){throw e.getCause();}
    });}
    public static class AuthSqlInspector implements org.hibernate.resource.jdbc.spi.StatementInspector {
        static final Set<String> seen=ConcurrentHashMap.newKeySet();
        public String inspect(String sql){String lower=sql.toLowerCase(Locale.ROOT);if((lower.contains("email_verification")||lower.contains("auth_email_identity"))&&seen.add(sql))event("authSql",sql);return sql;}
    }
    static class SqlFailureObserver implements HandlerExceptionResolver,Ordered {
        public int getOrder(){return Ordered.HIGHEST_PRECEDENCE;}
        public ModelAndView resolveException(HttpServletRequest request,HttpServletResponse response,Object handler,Exception error){
            // Observe and continue to the unchanged production exception handler.
            if(!(error instanceof com.kkirikkiri.global.exception.CustomException))event("databaseFailure",Map.of("path",request.getRequestURI(),"requestId",Objects.toString(org.slf4j.MDC.get("authRequestId"),""),"error",MultiSupport.error(error)));
            return null;
        }
    }
}
