package kmi;

import static kmi.MatchingMultiSupport.*;
import com.kkirikkiri.community.post.repository.*;
import com.kkirikkiri.community.post.service.*;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.io.IOException;
import java.lang.reflect.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.springframework.context.annotation.*;
import org.springframework.core.env.MapPropertySource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/** Actual production repositories/worker/storage. No application scan, Boot server or external clients. */
public final class ImageCleanupRuntime implements AutoCloseable {
    static String ddl = "validate";
    static Path imageRoot;
    static final ThreadLocal<String> actor = ThreadLocal.withInitial(() -> "main");
    static final List<Map<String,Object>> observations = new CopyOnWriteArrayList<>();
    static volatile StorageGate storageGate;
    static volatile CountDownLatch beforeJobLock;
    static volatile boolean timeoutActor;

    static final class SimulatedExit extends Error { }
    static final class StorageGate {
        final String actorName;
        final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        final boolean afterDelete, crash;
        StorageGate(String name, boolean after, boolean crash) { actorName=name; afterDelete=after; this.crash=crash; }
        void await() {
            entered.countDown();
            try { check(release.await(20, TimeUnit.SECONDS), "Storage gate timeout"); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError("Interrupted storage gate"); }
            if (crash) throw new SimulatedExit();
        }
    }
    static final class ObservedStorage extends PostImageStorage {
        ObservedStorage() { super(imageRoot.toString()); }
        @Override public void delete(String filename) throws IOException {
            var gate=storageGate;
            if(gate!=null && gate.actorName.equals(actor.get()) && !gate.afterDelete) gate.await();
            super.delete(filename); // Always real filesystem deletion, never a mocked successful delete.
            if(gate!=null && gate.actorName.equals(actor.get()) && gate.afterDelete) gate.await();
        }
    }

    @Configuration(proxyBeanMethods=false)
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses=PostImageCleanupRepository.class,
        includeFilters=@ComponentScan.Filter(type=FilterType.ASSIGNABLE_TYPE,
            classes={PostImageCleanupRepository.class,PostImageRepository.class}),
        excludeFilters=@ComponentScan.Filter(type=FilterType.REGEX,
            pattern=".*(CommunityPostRepository|PostLikeRepository)"))
    @Import({PostImageCleanupWorker.class,PostImageCleanupService.class,PostImageCleanupScheduler.class})
    static class Config {
        @Bean(destroyMethod="close") HikariDataSource pool() {
            var pool=new HikariDataSource(); pool.setJdbcUrl(url(db())); pool.setUsername(user());
            pool.setPassword(System.getenv("KM_PASSWORD")); pool.setMaximumPoolSize(8); pool.setMinimumIdle(0);
            pool.setConnectionTimeout(5000); pool.setConnectionInitSql("SET SESSION innodb_lock_wait_timeout=8");
            pool.setTransactionIsolation("TRANSACTION_REPEATABLE_READ"); return pool;
        }
        @Bean @Primary DataSource dataSource(HikariDataSource pool) {
            return new AbstractDataSource() {
                public Connection getConnection() throws SQLException {
                    var c=pool.getConnection();
                    try { guard(c); return observe(c); }
                    catch(Throwable e) { c.close(); throw new SQLException("Isolated connection rejected"); }
                }
                public Connection getConnection(String u,String p) throws SQLException { throw new SQLException("Explicit credentials forbidden"); }
            };
        }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            var factory=new LocalContainerEntityManagerFactoryBean(); factory.setDataSource(dataSource);
            factory.setPackagesToScan("com.kkirikkiri"); factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto",ddl,
                "hibernate.hbm2ddl.halt_on_error","true",
                "hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl",
                "hibernate.show_sql","false")); return factory;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory) { return new JpaTransactionManager(factory); }
        @Bean PostImageStorage storage() { return new ObservedStorage(); }
    }

    /** Parse bean definitions without refreshing the context or constructing any application bean. */
    static Map<String,Object> prepareMetadata(boolean scheduler) {
        try(var context=new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("prepare-only",Map.of(
                "app.community.image-cleanup.enabled",Boolean.toString(scheduler))));
            context.register(Config.class);
            var parser=new ConfigurationClassPostProcessor(); parser.setEnvironment(context.getEnvironment());
            parser.postProcessBeanDefinitionRegistry(context);
            check(context.containsBeanDefinition("postImageRepository")&&context.containsBeanDefinition("postImageCleanupRepository"),"Required repository metadata missing");
            check(!context.containsBeanDefinition("communityPostRepository")&&!context.containsBeanDefinition("postLikeRepository"),"Unexpected repository metadata");
            long schedulers=Arrays.stream(context.getBeanDefinitionNames()).filter(name->
                PostImageCleanupScheduler.class.getName().equals(context.getBeanDefinition(name).getBeanClassName())).count();
            check(schedulers==(scheduler?1:0),"Scheduler condition metadata mismatch");
            check(!context.getBeanFactory().containsSingleton("pool")&&!context.getBeanFactory().containsSingleton("entityManagerFactory"),"Preparation instantiated database infrastructure");
            return Map.of("schedulerEnabled",scheduler,"schedulerDefinitions",schedulers,"repositories",2,"jdbcBeansInstantiated",0,"contextRefreshed",false);
        }
    }

    final AnnotationConfigApplicationContext context;
    final EntityManager em;
    final PlatformTransactionManager manager;
    final PostImageCleanupRepository jobs;
    final PostImageRepository images;
    final PostImageCleanupWorker worker;
    final PostImageCleanupService cleanup;
    ImageCleanupRuntime(Path root, boolean scheduler) throws Exception {
        imageRoot=root; Files.createDirectories(root);
        check(!Files.isSymbolicLink(root),"Storage root must not be a link");
        context=new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("isolated-worker",Map.of(
            "app.community.image-cleanup.enabled",Boolean.toString(scheduler),
            "app.community.image-cleanup.delay-ms","500")));
        context.register(Config.class);
        try { context.refresh(); }
        catch(Throwable failure) { context.close(); throw failure; }
        manager=context.getBean(PlatformTransactionManager.class);
        em=SharedEntityManagerCreator.createSharedEntityManager(context.getBean(EntityManagerFactory.class));
        jobs=context.getBean(PostImageCleanupRepository.class); images=context.getBean(PostImageRepository.class);
        worker=context.getBean(PostImageCleanupWorker.class); cleanup=context.getBean(PostImageCleanupService.class);
        check(org.springframework.aop.support.AopUtils.isAopProxy(cleanup),"Expected production service transaction proxy");
        check(context.getBeansOfType(PostImageCleanupScheduler.class).size()==(scheduler?1:0),"Scheduler condition mismatch");
        check(context.getBeansOfType(org.springframework.boot.ApplicationRunner.class).isEmpty(),"Unexpected application runner");
    }
    <T> T tx(Supplier<T> action) { return new TransactionTemplate(manager).execute(status -> action.get()); }
    @Override public void close() { context.close(); }

    static Connection observe(Connection c) {
        return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(p,m,a)->{
            try {
                Object result=m.invoke(c,a);
                if(result instanceof PreparedStatement statement && a!=null && a.length>0 && a[0] instanceof String sql) {
                    return Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),new Class<?>[]{PreparedStatement.class},(q,n,b)->{
                        try {
                            String normalized=sql.toLowerCase(Locale.ROOT);
                            boolean execute=n.getName().startsWith("execute");
                            boolean lock=normalized.contains("post_image_cleanup") && normalized.contains("for update");
                            if(execute && lock) {
                                try(var s=c.createStatement()) { s.execute("SET SESSION innodb_lock_wait_timeout="+(timeoutActor && actor.get().equals("timeout")?1:8)); }
                                observations.add(Map.of("kind","job-lock-attempt","actor",actor.get(),
                                    "isolation",c.getTransactionIsolation(),"connectionId",number(c,"SELECT CONNECTION_ID()"),
                                    "sessionIsolation",rows(c,"SELECT @@session.transaction_isolation isolation").getFirst().get("isolation")));
                                var latch=beforeJobLock; if(latch!=null) latch.countDown();
                            }
                            Object value=n.invoke(statement,b);
                            return value;
                        } catch(InvocationTargetException e) {
                            if(e.getCause() instanceof SQLException s) observations.add(Map.of("kind","sql-error",
                                "actor",actor.get(),"vendorCode",s.getErrorCode(),"sqlState",Objects.toString(s.getSQLState(),"")));
                            throw e.getCause();
                        }
                    });
                }
                return result;
            } catch(InvocationTargetException e) { throw e.getCause(); }
        });
    }
}
