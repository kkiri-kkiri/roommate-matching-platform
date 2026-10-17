package kmi;
import com.kkirikkiri.BackendApplication;
import com.zaxxer.hikari.*;
import java.io.*;
import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.sql.DataSource;
import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.core.Ordered;
import jakarta.servlet.http.*;
import static kmi.MatchingMultiSupport.*;

/** Real Boot MVC/security/services/JPA. Only external transports and observability are supplied. */
public class MatchingMultiServer {
    static final AtomicReference<CountDownLatch> gate=new AtomicReference<>();
    static final AtomicBoolean triggered=new AtomicBoolean();
    static final ThreadLocal<Boolean> surveyRequest=ThreadLocal.withInitial(()->false);
    static void after(String sql)throws Exception{
        var latch=gate.get();
        if(latch!=null&&surveyRequest.get()&&sql.toLowerCase(Locale.ROOT).startsWith("update user_profile ")&&triggered.compareAndSet(false,true)){
            event("gated",Map.of("pid",ProcessHandle.current().pid()));
            check(latch.await(15,TimeUnit.SECONDS),"Gate timeout");
        }
    }
    public static void main(String[] args)throws Exception{
        int port=Integer.parseInt(args[0]);check(port==18081||port==18082,"Unsafe port");try(var c=open()){guard(c);}
        var p=new HashMap<String,String>();
        p.put("spring.profiles.active","matching-multi-validation");p.put("spring.profiles.include","");p.put("spring.config.import","");p.put("spring.config.location","classpath:/application.properties");
        p.put("server.address","127.0.0.1");p.put("server.port",""+port);p.put("spring.datasource.url",url(db()));p.put("spring.datasource.username",user());p.put("spring.datasource.password",System.getenv("KM_PASSWORD"));
        p.put("spring.jpa.hibernate.ddl-auto","validate");p.put("spring.sql.init.mode","never");p.put("app.seed.mode","NONE");p.put("app.calendar.crawl-on-startup","false");p.put("spring.jpa.show-sql","false");p.put("spring.jpa.properties.hibernate.format_sql","false");
        p.put("jwt.secret",System.getenv("KM_JWT"));p.put("jwt.access-token-expiration-seconds","3600");
        p.put("ai.api-key","test-only");p.put("ai.model","stub");p.put("ai.base-url","http://127.0.0.1:"+System.getenv("KM_STUB_PORT"));p.put("ai.analysis.max-requests","12");p.put("ai.analysis.window-seconds","3600");p.put("ai.analysis.max-concurrent","2");
        p.put("spring.mail.host","127.0.0.1");p.put("spring.mail.port","1");p.put("spring.mail.username","");p.put("spring.mail.password","");p.put("auth.email-verification.mail-from","test@example.invalid");
        p.put("logging.level.root","OFF");p.put("spring.main.banner-mode","off");p.put("server.forward-headers-strategy","NONE");p.forEach(System::setProperty);
        try(var context=new SpringApplication(BackendApplication.class,Infrastructure.class).run();var reader=new BufferedReader(new InputStreamReader(System.in))){
            check(context.getBeansOfType(org.springframework.boot.ApplicationRunner.class).isEmpty(),"Unexpected runners");
            check(context.getBeansOfType(org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor.class).isEmpty(),"Unexpected schedulers");
            try(var c=context.getBean(DataSource.class).getConnection()){event("ready",Map.of("pid",ProcessHandle.current().pid(),"port",port,"database",db(),"identity",rows(c,"SELECT DATABASE() db,@@server_uuid uuid,CONNECTION_ID() connectionId"),"ddl","validate","sqlInit","never","seeds",0));}
            String line;while((line=reader.readLine())!=null){if(line.equals("STOP"))break;if(line.equals("STATS")){var executor=context.getBean("matchingRefreshExecutor",org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor.class);event("stats",Map.of("matchingActive",executor.getActiveCount(),"matchingQueue",executor.getThreadPoolExecutor().getQueue().size()));}if(line.equals("ARM")){triggered.set(false);check(gate.compareAndSet(null,new CountDownLatch(1)),"Gate already armed");event("armed",Map.of());}if(line.equals("RELEASE")){var l=gate.getAndSet(null);if(l!=null)l.countDown();event("released",Map.of());}}
        }catch(Throwable t){event("error",error(t));System.exit(1);}finally{var l=gate.getAndSet(null);if(l!=null)l.countDown();}
    }
    @Configuration(proxyBeanMethods=false)
    public static class Infrastructure {
        @Bean static BeanFactoryPostProcessor noSchedules(){return f->{var r=(DefaultListableBeanFactory)f;for(String n:r.getBeanNamesForType(org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor.class,false,false))r.removeBeanDefinition(n);for(String n:r.getBeanNamesForType(com.kkirikkiri.calendar.scheduler.CalendarCrawlingScheduler.class,false,false))r.removeBeanDefinition(n);};}
        @Bean(destroyMethod="close") HikariDataSource validationPool(){var p=new HikariDataSource();p.setJdbcUrl(url(db()));p.setUsername(user());p.setPassword(System.getenv("KM_PASSWORD"));p.setMaximumPoolSize(16);p.setConnectionTimeout(5000);p.setConnectionInitSql("SET SESSION innodb_lock_wait_timeout=3");return p;}
        @Bean @Primary DataSource dataSource(HikariDataSource validationPool){return new AbstractDataSource(){public Connection getConnection()throws SQLException{var c=validationPool.getConnection();try{guard(c);return observe(c);}catch(Exception e){c.close();throw new SQLException("Validation connection rejected",e);}}public Connection getConnection(String u,String p)throws SQLException{throw new SQLException("Explicit credentials forbidden");}};}
        @Bean JavaMailSender noMail(){return (JavaMailSender)Proxy.newProxyInstance(JavaMailSender.class.getClassLoader(),new Class<?>[]{JavaMailSender.class},(p,m,a)->{if(m.getName().equals("toString"))return "NoNetworkMail";if(m.getName().equals("hashCode"))return System.identityHashCode(p);if(m.getName().equals("equals"))return p==a[0];event("forbiddenMail",Map.of());throw new IllegalStateException("SMTP forbidden in validation");});}
        @Bean org.springframework.boot.web.servlet.FilterRegistrationBean<org.springframework.web.filter.OncePerRequestFilter> trace(){var f=new org.springframework.web.filter.OncePerRequestFilter(){protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,jakarta.servlet.FilterChain chain)throws IOException,jakarta.servlet.ServletException{surveyRequest.set(req.getMethod().equals("PUT")&&req.getRequestURI().endsWith("/profile-survey"));try{chain.doFilter(req,res);}finally{event("http",Map.of("method",req.getMethod(),"path",req.getRequestURI(),"status",res.getStatus()));surveyRequest.remove();}}};var r=new org.springframework.boot.web.servlet.FilterRegistrationBean<org.springframework.web.filter.OncePerRequestFilter>(f);r.setOrder(Ordered.HIGHEST_PRECEDENCE);return r;}
        @Bean HandlerExceptionResolver observeFailures(){return new FailureObserver();}
    }
    static class FailureObserver implements HandlerExceptionResolver,Ordered{public int getOrder(){return Ordered.HIGHEST_PRECEDENCE;}public ModelAndView resolveException(HttpServletRequest req,HttpServletResponse res,Object h,Exception e){if(!(e instanceof com.kkirikkiri.global.exception.CustomException))event("exception",Map.of("path",req.getRequestURI(),"error",error(e)));return null;}}
    static Connection observe(Connection c){return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(p,m,a)->{try{Object result=m.invoke(c,a);if(result instanceof PreparedStatement s&&a!=null&&a.length>0&&a[0] instanceof String sql)return Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),new Class<?>[]{PreparedStatement.class},(q,n,b)->{try{var value=n.invoke(s,b);if(n.getName().startsWith("execute"))after(sql);return value;}catch(InvocationTargetException x){if(x.getCause() instanceof SQLException e)event("sqlFailure",error(e));throw x.getCause();}});return result;}catch(InvocationTargetException e){throw e.getCause();}});}
}
