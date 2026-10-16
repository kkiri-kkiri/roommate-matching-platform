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
public class UserMultiServer {
    static final ThreadLocal<String> requestPath=new ThreadLocal<>();
    static class Gate {
        final String kind,path;final CountDownLatch release=new CountDownLatch(1);final java.util.concurrent.atomic.AtomicBoolean triggered=new java.util.concurrent.atomic.AtomicBoolean();
        Gate(String k,String p){kind=k;path=p;}
    }
    static final java.util.concurrent.atomic.AtomicReference<Gate> gate=new java.util.concurrent.atomic.AtomicReference<>();
    static void afterStatement(String sql)throws Exception {
        String lower=sql.toLowerCase(Locale.ROOT);boolean matching=Thread.currentThread().getName().startsWith("matching-refresh-");
        if(matching&&lower.contains(" from user ")&&lower.contains("for update"))event("matchingBatch",Map.of("users",sql.chars().filter(x->x=='?').count(),"thread",Thread.currentThread().getName()));
        Gate g=gate.get();if(g==null)return;
        boolean match=g.kind.equals("ACCOUNT")&&!matching&&Objects.equals(g.path,requestPath.get())&&lower.contains(" from user ")&&lower.contains("for update")&&!lower.contains(" in (")
            ||g.kind.equals("PROFILE")&&!matching&&Objects.equals(g.path,requestPath.get())&&lower.startsWith("update user_profile ")
            ||g.kind.equals("MATCHING")&&matching&&lower.contains(" from user ")&&lower.contains("for update");
        if(match&&g.triggered.compareAndSet(false,true)){event("gated",Map.of("kind",g.kind,"path",Objects.toString(requestPath.get(),"async")));try{UserMultiSupport.check(g.release.await(15,TimeUnit.SECONDS),"Validation gate timed out");}finally{gate.compareAndSet(g,null);}}
    }
    static Map<String,Object> stats(ConfigurableApplicationContext context,HikariDataSource pool){var m=new LinkedHashMap<String,Object>(stats(pool));var ex=context.getBean("matchingRefreshExecutor",org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor.class);m.put("matchingActive",ex.getActiveCount());m.put("matchingQueue",ex.getThreadPoolExecutor().getQueue().size());m.put("matchingCompleted",ex.getThreadPoolExecutor().getCompletedTaskCount());return m;}
    static final AtomicInteger peakActive=new AtomicInteger(),peakWaiting=new AtomicInteger();
    static final AtomicLong leases=new AtomicLong();
    static Map<String,Object> stats(HikariDataSource pool){var m=pool.getHikariPoolMXBean();return Map.of("active",m.getActiveConnections(),"idle",m.getIdleConnections(),"total",m.getTotalConnections(),"waiting",m.getThreadsAwaitingConnection(),"peakActive",peakActive.get(),"peakWaiting",peakWaiting.get());}
    static void event(String kind,Object data){System.out.println("MI_EVENT "+AuthMysqlConcurrency.json(Map.of("kind",kind,"data",data)));System.out.flush();}
    public static void main(String[] args)throws Exception {
        int port=Integer.parseInt(args[0]);UserMultiSupport.check(port==8081||port==8082,"Unsafe HTTP port");
        try(var c=UserMultiSupport.open()){UserMultiSupport.grants(c);}
        var settings=new HashMap<String,String>();
        settings.put("spring.profiles.active","user-multi-validation");settings.put("spring.config.import","");
        settings.put("spring.config.location","classpath:/application.properties");settings.put("spring.profiles.include","");
        settings.put("server.address","127.0.0.1");settings.put("server.port",Integer.toString(port));
        settings.put("spring.datasource.url",UserMultiSupport.url());settings.put("spring.datasource.username",UserMultiSupport.user());settings.put("spring.datasource.password",System.getenv("KKIRI_MI_PASSWORD"));
        settings.put("spring.jpa.hibernate.ddl-auto","validate");settings.put("spring.sql.init.mode","never");settings.put("spring.jpa.show-sql","false");settings.put("spring.jpa.properties.hibernate.format_sql","false");
        settings.put("spring.jpa.open-in-view","false");
        settings.put("spring.jpa.properties.hibernate.session_factory.statement_inspector","UserMultiServer$AuthSqlInspector");
        settings.put("app.seed.mode","NONE");settings.put("app.calendar.crawl-on-startup","false");
        settings.put("jwt.secret",System.getenv("KKIRI_MI_JWT"));settings.put("ai.api-key","unused-test-key");settings.put("ai.base-url","http://127.0.0.1:1");settings.put("ai.model","unused");
        settings.put("spring.mail.host","127.0.0.1");settings.put("spring.mail.port","1");settings.put("spring.mail.username","");settings.put("spring.mail.password","");settings.put("spring.mail.test-connection","false");settings.put("auth.email-verification.mail-from","test@example.invalid");
        settings.put("auth.protection.trusted-proxies","127.0.0.1/32");settings.put("server.forward-headers-strategy","NONE");
        settings.put("logging.level.root","OFF");settings.put("spring.main.banner-mode","off");
        // Pin every protection setting: inherited shell variables cannot change this validation.
        settings.putAll(UserMultiInstance.protectionSettings());
        settings.forEach(System::setProperty);
        ((ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).setLevel(ch.qos.logback.classic.Level.OFF);
        ConfigurableApplicationContext context=null;HikariDataSource pool=null;boolean failed=false;
        ScheduledExecutorService monitor=Executors.newSingleThreadScheduledExecutor();var held=new ArrayList<Connection>();
        try {
            var app=new SpringApplication(BackendApplication.class,Infrastructure.class);app.setLogStartupInfo(false);
            context=app.run();pool=context.getBean("multiPool",HikariDataSource.class);
            // Capture only the production filter's sanitized diagnostic, never exception messages.
            var authLogger=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(com.kkirikkiri.global.security.JwtAuthenticationFilter.class);
            authLogger.setLevel(ch.qos.logback.classic.Level.ERROR);authLogger.setAdditive(false);
            var authAppender=new ch.qos.logback.core.AppenderBase<ch.qos.logback.classic.spi.ILoggingEvent>(){
                protected void append(ch.qos.logback.classic.spi.ILoggingEvent e){event("authenticationInfrastructureLog",Map.of("message",e.getFormattedMessage(),"throwableAttached",e.getThrowableProxy()!=null));}
            };authAppender.setContext(authLogger.getLoggerContext());authAppender.start();authLogger.addAppender(authAppender);
            final HikariDataSource watched=pool;monitor.scheduleAtFixedRate(()->{var m=watched.getHikariPoolMXBean();peakActive.accumulateAndGet(m.getActiveConnections(),Math::max);peakWaiting.accumulateAndGet(m.getThreadsAwaitingConnection(),Math::max);},0,10,TimeUnit.MILLISECONDS);
            event("infrastructure",Map.of("scheduledProcessors",context.getBeansOfType(org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor.class).keySet(),"calendarSchedulers",context.getBeansOfType(com.kkirikkiri.calendar.scheduler.CalendarCrawlingScheduler.class).keySet()));
            UserMultiSupport.check(context.getBeansOfType(org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor.class).isEmpty(),"Scheduling was not disabled");
            UserMultiSupport.check(context.getBeansOfType(org.springframework.boot.ApplicationRunner.class).isEmpty(),"Unexpected application runners/seeds");
            UserMultiSupport.check(Proxy.isProxyClass(context.getBean(JavaMailSender.class).getClass()),"Real mail sender present");
            event("ready",Map.of("pid",ProcessHandle.current().pid(),"port",port,"database",UserMultiSupport.db(),"account",UserMultiSupport.user(),"profile",List.of(context.getEnvironment().getActiveProfiles()),"hibernate","validate","scheduledProcessors",0,"mailTransport","memory-only authenticated loopback IPC","protection",UserMultiInstance.protectionSettings()));
            try(var reader=new BufferedReader(new InputStreamReader(System.in))){String line;while((line=reader.readLine())!=null){if(line.equals("STOP"))break;if(line.equals("STATS"))event("pool",stats(context,pool));if(line.startsWith("ARM_")){String[] parts=line.split(" ",2);UserMultiSupport.check(gate.compareAndSet(null,new Gate(parts[0].substring(4),parts.length==2?parts[1]:"")),"Gate already armed");event("armed",Map.of("kind",parts[0].substring(4)));}if(line.equals("RELEASE_GATE")){Gate g=gate.get();if(g!=null)g.release.countDown();event("gateReleased",Map.of());}if(line.equals("HOLD_POOL")){for(int i=0;i<12;i++)held.add(pool.getConnection());event("heldPool",stats(pool));}if(line.equals("RELEASE_POOL")){for(var c:held)c.close();held.clear();event("releasedPool",stats(pool));}}}
        } catch(Throwable t){failed=true;event("error",UserMultiSupport.error(t));}
        finally {Gate g=gate.get();if(g!=null)g.release.countDown();monitor.shutdownNow();for(var c:held)c.close();if(context!=null)context.close();event("closed",Map.of("poolClosed",pool!=null&&pool.isClosed(),"peakActive",peakActive.get(),"peakWaiting",peakWaiting.get()));}
        if(failed)System.exit(1);
    }
    @Configuration(proxyBeanMethods=false)
    public static class Infrastructure {
        @Bean org.springframework.boot.web.servlet.FilterRegistrationBean<org.springframework.web.filter.OncePerRequestFilter> validationTrace(){
            org.springframework.web.filter.OncePerRequestFilter f=new org.springframework.web.filter.OncePerRequestFilter(){protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,jakarta.servlet.FilterChain chain)throws java.io.IOException,jakarta.servlet.ServletException{
                if(req.getDispatcherType()==jakarta.servlet.DispatcherType.REQUEST&&Objects.equals(System.getenv("KKIRI_MI_IPC"),req.getHeader("X-Validation-Container-Error"))){res.sendError(500);return;}
                if(req.getDispatcherType()==jakarta.servlet.DispatcherType.ERROR)event("errorDispatch",Map.of("path",req.getRequestURI(),"originalPath",Objects.toString(req.getAttribute("jakarta.servlet.error.request_uri"),""),"originalStatus",Objects.toString(req.getAttribute("jakarta.servlet.error.status_code"),""),"error",req.getAttribute("jakarta.servlet.error.exception") instanceof Throwable failure?UserMultiSupport.error(failure):Map.of()));
                String trace=req.getHeader("X-User-Validation");if(trace==null||!trace.matches("UQ[0-9]+"))trace="untracked";
                requestPath.set(req.getRequestURI());org.slf4j.MDC.put("userValidationRequest",trace);
                try{chain.doFilter(req,res);}catch(java.io.IOException|jakarta.servlet.ServletException|RuntimeException failure){event("filterFailure",Map.of("requestId",trace,"path",req.getRequestURI(),"error",UserMultiSupport.error(failure)));throw failure;}finally{event("httpFinished",Map.of("requestId",trace,"path",req.getRequestURI(),"status",res.getStatus()));requestPath.remove();org.slf4j.MDC.remove("userValidationRequest");}}
                protected boolean shouldNotFilterErrorDispatch(){return false;}
                protected void doFilterNestedErrorDispatch(HttpServletRequest req,HttpServletResponse res,jakarta.servlet.FilterChain chain)throws java.io.IOException,jakarta.servlet.ServletException{event("errorDispatch",Map.of("path",req.getRequestURI(),"originalPath",Objects.toString(req.getAttribute("jakarta.servlet.error.request_uri"),""),"originalStatus",Objects.toString(req.getAttribute("jakarta.servlet.error.status_code"),""),"error",req.getAttribute("jakarta.servlet.error.exception") instanceof Throwable failure?UserMultiSupport.error(failure):Map.of()));chain.doFilter(req,res);}
            };var registration=new org.springframework.boot.web.servlet.FilterRegistrationBean<>(f);registration.setOrder(Ordered.HIGHEST_PRECEDENCE);return registration;
        }
        @Bean HandlerExceptionResolver observeSqlFailure(){return new SqlFailureObserver();}
        @Bean static BeanFactoryPostProcessor preventScheduledWork(){return factory->{var registry=(DefaultListableBeanFactory)factory;
            var names=new ArrayList<String>();names.addAll(List.of(registry.getBeanNamesForType(org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor.class,false,false)));names.addAll(List.of(registry.getBeanNamesForType(com.kkirikkiri.calendar.scheduler.CalendarCrawlingScheduler.class,false,false)));
            for(String name:names){if(registry.containsSingleton(name))registry.destroySingleton(name);if(registry.containsBeanDefinition(name))registry.removeBeanDefinition(name);}event("removedSchedulers",names);
        };}
        @Bean(destroyMethod="close") HikariDataSource multiPool(){var p=new HikariDataSource();p.setJdbcUrl(UserMultiSupport.url());p.setUsername(UserMultiSupport.user());p.setPassword(System.getenv("KKIRI_MI_PASSWORD"));p.setMaximumPoolSize(12);p.setMinimumIdle(0);p.setConnectionTimeout(5000);p.setConnectionInitSql("SET SESSION innodb_lock_wait_timeout=2");return p;}
        @Bean @Primary DataSource dataSource(HikariDataSource multiPool){return new AbstractDataSource(){public Connection getConnection()throws SQLException {Connection c;try{c=multiPool.getConnection();}catch(SQLException failure){event("poolBorrowFailure",Map.of("path",Objects.toString(requestPath.get(),""),"requestId",Objects.toString(org.slf4j.MDC.get("userValidationRequest"),""),"error",UserMultiSupport.error(failure)));throw failure;}try{UserMultiSupport.guard(c);return observe(c,leases.incrementAndGet());}catch(Throwable t){c.close();throw t;}}public Connection getConnection(String u,String p)throws SQLException{throw new SQLException("Explicit credentials forbidden");}};}
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
    static Connection observe(Connection connection,long lease){String trace=Objects.toString(org.slf4j.MDC.get("userValidationRequest"),"async");event("jdbcLease",Map.of("lease",lease,"requestId",trace));return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args)->{
        try{Object result=method.invoke(connection,args);if(result instanceof PreparedStatement statement&&args!=null&&args.length>0&&args[0] instanceof String sql){return Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),new Class<?>[]{PreparedStatement.class},(p,m,a)->{try{Object value=m.invoke(statement,a);if(m.getName().startsWith("execute"))afterStatement(sql);return value;}catch(InvocationTargetException e){Throwable cause=e.getCause();if(cause instanceof SQLException error)event("jdbcFailure",Map.of("lease",lease,"requestId",trace,"sql",sql,"thread",Thread.currentThread().getName(),"error",UserMultiSupport.error(error)));throw cause;}});}return result;}catch(InvocationTargetException e){throw e.getCause();}
    });}
    public static class AuthSqlInspector implements org.hibernate.resource.jdbc.spi.StatementInspector {
        static final Set<String> seen=ConcurrentHashMap.newKeySet();
        public String inspect(String sql){String lower=sql.toLowerCase(Locale.ROOT);if((lower.contains(" from user ")||lower.startsWith("update user ")||lower.contains("user_profile")||lower.contains("roommate_match")||lower.contains("password_reset_token"))&&seen.add(sql))event("userSql",sql);return sql;}
    }
    static class SqlFailureObserver implements HandlerExceptionResolver,Ordered {
        public int getOrder(){return Ordered.HIGHEST_PRECEDENCE;}
        public ModelAndView resolveException(HttpServletRequest request,HttpServletResponse response,Object handler,Exception error){
            // Observe and continue to the unchanged production exception handler.
            if(!(error instanceof com.kkirikkiri.global.exception.CustomException))event("databaseFailure",Map.of("path",request.getRequestURI(),"requestId",Objects.toString(org.slf4j.MDC.get("authRequestId"),""),"error",UserMultiSupport.error(error)));
            return null;
        }
    }
}
