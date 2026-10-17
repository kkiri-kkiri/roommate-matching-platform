import static com.kkirikkiri.survey.SurveyTestData.*;
import com.kkirikkiri.survey.service.*;
import com.kkirikkiri.survey.dto.*;
import com.kkirikkiri.user.entity.*;
import com.kkirikkiri.user.repository.*;
import com.kkirikkiri.global.exception.*;
import com.kkirikkiri.matching.service.MatchingInputsChanged;
import com.zaxxer.hikari.*;
import jakarta.persistence.*;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import javax.sql.DataSource;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.event.*;
import org.springframework.transaction.support.*;
import tools.jackson.databind.json.JsonMapper;

/** Opt-in actual Survey services/JPA verifier. No Boot, .env, HTTP, SMTP, seeds or schedulers. */
public class SurveyMysqlConcurrency {
    static final String URL = "jdbc:mysql://127.0.0.1:3311/kkirisurveytest?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&autoReconnect=false&allowMultiQueries=false&allowLoadLocalInfile=false&connectTimeout=5000&socketTimeout=15000";
    static final JsonMapper JSON = JsonMapper.builder().build();
    static HikariDataSource pool;
    static JdbcTemplate jdbc;
    static AnnotationConfigApplicationContext context;
    static PlatformTransactionManager manager;
    static UserRepository users;
    static UserProfileRepository profiles;
    static ProfileSurveyService profileService;
    static RoommatePreferenceService preferenceService;
    static Recorder recorder;
    static Long dormId, facultyId;
    static final List<Long> ownedUsers = new ArrayList<>();
    static final List<Map<String, Object>> results = new ArrayList<>();
    static final ExecutorService workers = Executors.newFixedThreadPool(2);
    static final Set<Long> sessions = ConcurrentHashMap.newKeySet();
    static final List<String> order = new CopyOnWriteArrayList<>();
    static String ddl = "validate";

    public static void main(String[] args) throws Exception {
        ((ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).setLevel(ch.qos.logback.classic.Level.OFF);
        check(args.length == 1 && System.getenv("KKIRI_SURVEY_TEST_PASSWORD") != null, "Use the dedicated PowerShell runner");
        Path output = Path.of(args[0]).resolve("run-"+UUID.randomUUID()); Files.createDirectories(output);
        boolean claimed = false, passed = false;
        Connection guard = null;
        try {
            guard = DriverManager.getConnection(URL, "kkiri_survey_test", System.getenv("KKIRI_SURVEY_TEST_PASSWORD"));
            var g = new JdbcTemplate(new org.springframework.jdbc.datasource.SingleConnectionDataSource(guard, true));
            check("kkirisurveytest".equals(g.queryForObject("select database()", String.class)), "Wrong schema");
            check(g.queryForObject("select current_user()", String.class).startsWith("kkiri_survey_test@"), "Wrong account");
            check(System.getenv("KKIRI_SURVEY_TEST_UUID").equals(g.queryForObject("select @@server_uuid", String.class)), "Wrong server UUID");
            check(System.getenv("KKIRI_SURVEY_TEST_HOSTNAME").equals(g.queryForObject("select @@hostname", String.class)), "Wrong server hostname");
            check(g.queryForObject("select get_lock('kkirisurveytest.survey_validation',0)", Integer.class) == 1, "Another verifier is active"); claimed = true;
            var tables = g.queryForList("select table_name from information_schema.tables where table_schema=database()", String.class);
            for (String table : tables) {
                check(table.matches("[A-Za-z_]+"), "Unexpected table identifier");
                check(g.queryForObject("select count(*) from `"+table+"`", Long.class) == 0, "Preexisting data: refusing all writes");
            }
            // DDL is created only on this verified, dedicated, completely empty schema.
            ddl = tables.isEmpty() ? "create-only" : "validate";
            var config = new HikariConfig(); config.setJdbcUrl(URL); config.setUsername("kkiri_survey_test");
            config.setPassword(System.getenv("KKIRI_SURVEY_TEST_PASSWORD")); config.setMaximumPoolSize(6);
            config.setConnectionTimeout(5000); config.setTransactionIsolation("TRANSACTION_REPEATABLE_READ");
            config.setConnectionInitSql("set session innodb_lock_wait_timeout=3"); pool = new HikariDataSource(config);
            context = new AnnotationConfigApplicationContext(Config.class);
            jdbc = context.getBean(JdbcTemplate.class); manager = context.getBean(PlatformTransactionManager.class);
            users = context.getBean(UserRepository.class); profiles = context.getBean(UserProfileRepository.class);
            profileService = context.getBean(ProfileSurveyService.class); preferenceService = context.getBean(RoommatePreferenceService.class);
            recorder = context.getBean(Recorder.class);
            Files.writeString(output.resolve("environment.json"), JSON.writeValueAsString(jdbc.queryForMap(
                    "select version() version, database() db, @@server_uuid serverUuid, @@transaction_isolation isolationLevel, @@session.innodb_lock_wait_timeout lockWaitSeconds")));
            fixtureReferences();
            for (boolean preference : List.of(false, true)) {
                test((preference ? "preference" : "profile")+"-concurrent-first-create", () -> concurrentCreate(preference));
                test((preference ? "preference" : "profile")+"-rollback-after-flush", () -> rollback(preference));
                for (UserStatus state : List.of(UserStatus.SUSPENDED, UserStatus.WITHDRAWN)) {
                    test((preference ? "preference" : "profile")+"-"+state+"-status-first-stale-context", () -> statusFirst(preference, state));
                    test((preference ? "preference" : "profile")+"-"+state+"-survey-first", () -> surveyFirst(preference, state));
                }
            }
            passed = true;
        } catch (Throwable failure) {
            results.add(Map.of("case", "harness", "result", "FAIL", "errorType", failure.getClass().getName()));
        } finally {
            workers.shutdownNow();
            if (!workers.awaitTermination(20, TimeUnit.SECONDS)) { passed = false; results.add(Map.of("case", "workers-stopped", "result", "FAIL")); }
            try { if (context != null) cleanup(); }
            catch (Throwable failure) { passed = false; results.add(Map.of("case", "owned-row-cleanup", "result", "FAIL", "errorType", failure.getClass().getName())); }
            if (context != null) context.close(); if (pool != null) pool.close();
            // Retain the advisory claim through worker termination and owned-row cleanup.
            if (guard != null) guard.close();
            Files.writeString(output.resolve("evidence.json"), JSON.writeValueAsString(Map.of("passed", passed, "identityClaimed", claimed, "cases", results)));
            System.out.println("Survey MySQL evidence: "+output);
        }
        if (!passed) System.exit(1);
    }
    interface Case { void run() throws Exception; }
    static void test(String name, Case action) throws Exception {
        sessions.clear(); order.clear(); recorder.events.clear();
        try { action.run(); results.add(Map.of("case", name, "result", "PASS", "connectionIds", List.copyOf(sessions), "commitOrder", List.copyOf(order))); }
        catch (Throwable failure) { results.add(Map.of("case", name, "result", "FAIL", "errorType", failure.getClass().getName(), "connectionIds", List.copyOf(sessions), "commitOrder", List.copyOf(order))); throw failure; }
    }
    static TransactionTemplate tx() { var tx = new TransactionTemplate(manager); tx.setTimeout(12); return tx; }
    static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    static void await(CountDownLatch latch) {
        try { check(latch.await(8, TimeUnit.SECONDS), "Coordination timeout"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
    }
    static void session() { sessions.add(jdbc.queryForObject("select connection_id()", Long.class)); }
    static void expectWaiting(Future<?> task) throws Exception {
        try { task.get(200, TimeUnit.MILLISECONDS); throw new AssertionError("Expected row lock wait"); }
        catch (TimeoutException expected) { order.add("second-transaction-waiting"); }
    }
    static void fixtureReferences() {
        tx().executeWithoutResult(s -> {
            var em = context.getBean(EntityManager.class);
            var d = new Dormitory("survey-validation", "Survey validation", true); em.persist(d); dormId = d.getDormitoryId();
            var f = new Faculty("Survey validation", true); em.persist(f); facultyId = f.getFacultyId();
        });
    }
    static Long user() {
        return tx().execute(s -> {
            var em = context.getBean(EntityManager.class);
            var u = users.saveAndFlush(new User("test-hash", UUID.randomUUID()+"@hknu.ac.kr")); ownedUsers.add(u.getUserId());
            profiles.saveAndFlush(new UserProfile(u, "sv"+u.getUserId(), "MALE", em.getReference(Dormitory.class, dormId),
                    em.getReference(Faculty.class, facultyId), null, (short)2003, "22", "3학년", "INTJ")); return u.getUserId();
        });
    }
    static void save(Long id, boolean preference, boolean second) {
        if (preference) preferenceService.saveOrUpdateMyRoommatePreference(id, preference(second));
        else profileService.saveOrUpdateMyProfileSurvey(id, profile(second));
    }
    static void status(Long id, UserStatus state) { users.findByIdForAccountUpdate(id).orElseThrow().changeStatus(state); }
    static void committed(String label) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { order.add(label); }
        });
    }
    static List<List<Map<String,Object>>> snapshot(Long id) {
        return tx().execute(s -> List.of(
                jdbc.queryForList("select * from USER_PROFILE where user_id=?", id),
                jdbc.queryForList("select * from PROFILE_SURVEY where user_id=?", id),
                jdbc.queryForList("select o.* from PROFILE_SURVEY_OPTION o join PROFILE_SURVEY p on p.profile_survey_id=o.profile_survey_id where p.user_id=? order by o.profile_survey_option_id", id),
                jdbc.queryForList("select * from ROOMMATE_PREFERENCE where user_id=?", id),
                jdbc.queryForList("select o.* from ROOMMATE_PREFERENCE_OPTION o join ROOMMATE_PREFERENCE p on p.preference_id=o.preference_id where p.user_id=? order by o.preference_option_id", id),
                jdbc.queryForList("select c.* from PREFERENCE_CRITERION c join ROOMMATE_PREFERENCE p on p.preference_id=c.preference_id where p.user_id=? order by c.preference_criterion_id", id)));
    }
    static void assertSaved(Long id, boolean preference, boolean second) {
        var rows = snapshot(id); check(rows.get(preference ? 3 : 1).size() == 1, "Duplicate parent");
        if (preference) {
            var response = preferenceService.getMyRoommatePreference(id);
            check(response.completed() && JSON.convertValue(preference(second), Map.class).equals(JSON.convertValue(response.answers(), Map.class)), "Mixed preference request");
            check(rows.get(4).size() == (second ? 6 : 7) && rows.get(5).size() == 18, "Duplicate or missing preference children");
        } else {
            var response = profileService.getMyProfileSurvey(id);
            Map<String,Object> actual = new LinkedHashMap<>(JSON.convertValue(response.answers(), Map.class)); actual.put("introduction", response.introduction());
            check(response.completed() && JSON.convertValue(profile(second), Map.class).equals(actual), "Mixed profile request");
            check(rows.get(2).size() == (second ? 3 : 4), "Duplicate profile options");
        }
    }
    static void concurrentCreate(boolean preference) throws Exception {
        Long id = user(); var ready = new CountDownLatch(2); var start = new CountDownLatch(1); var jobs = new ArrayList<Future<?>>();
        for (boolean second : List.of(false, true)) jobs.add(workers.submit(() -> tx().executeWithoutResult(s -> {
            session(); ready.countDown(); await(start); save(id, preference, second); committed("survey-"+second+"-committed");
        })));
        await(ready); start.countDown(); for (var job : jobs) job.get(15, TimeUnit.SECONDS);
        check(sessions.size() == 2, "Connections must differ");
        boolean second = order.getLast().equals("survey-true-committed"); assertSaved(id, preference, second);
        check(recorder.events.size() == 2, "Missing committed refresh");
    }
    static void rollback(boolean preference) {
        Long id = user(); save(id, preference, false); var before = snapshot(id); recorder.events.clear();
        class Forced extends RuntimeException {}
        try { tx().executeWithoutResult(s -> { save(id, preference, true); context.getBean(EntityManager.class).flush(); throw new Forced(); }); throw new AssertionError("Expected rollback"); }
        catch (Forced expected) { }
        check(before.equals(snapshot(id)), "Rollback changed existing rows"); check(recorder.events.isEmpty(), "Rolled-back refresh delivered");
        assertSaved(id, preference, false);
    }
    static void statusFirst(boolean preference, UserStatus state) throws Exception {
        Long id = user(); save(id, preference, false); var before = snapshot(id); recorder.events.clear();
        var stale = new CountDownLatch(1); var locked = new CountDownLatch(1); var attempting = new CountDownLatch(1); var release = new CountDownLatch(1);
        var survey = workers.submit(() -> {
            try { tx().executeWithoutResult(s -> {
                session(); check(users.findById(id).orElseThrow().getStatus() == UserStatus.ACTIVE, "Initial authentication snapshot");
                stale.countDown(); await(locked); attempting.countDown(); save(id, preference, true);
            }); return false; } catch (CustomException ex) { return ex.getErrorCode() == ErrorCode.UNAUTHORIZED; }
        });
        await(stale);
        var account = workers.submit(() -> tx().executeWithoutResult(s -> {
            session(); status(id, state); context.getBean(EntityManager.class).flush(); committed("status-committed"); locked.countDown(); await(release);
        }));
        await(locked); await(attempting); try { expectWaiting(survey); } finally { release.countDown(); }
        account.get(15, TimeUnit.SECONDS); check(survey.get(15, TimeUnit.SECONDS), "Inactive save must be unauthorized");
        order.add("survey-rejected"); check(sessions.size() == 2, "Connections must differ");
        check(before.equals(snapshot(id)), "Rejected save changed data"); check(recorder.events.isEmpty(), "Rejected refresh delivered");
        check(users.findById(id).orElseThrow().getStatus() == state, "Final status");
    }
    static void surveyFirst(boolean preference, UserStatus state) throws Exception {
        Long id = user(); var locked = new CountDownLatch(1); var attempting = new CountDownLatch(1); var release = new CountDownLatch(1);
        var survey = workers.submit(() -> tx().executeWithoutResult(s -> {
            session(); save(id, preference, true); context.getBean(EntityManager.class).flush(); committed("survey-committed"); locked.countDown(); await(release);
        }));
        await(locked);
        var account = workers.submit(() -> tx().executeWithoutResult(s -> { session(); attempting.countDown(); status(id, state); committed("status-committed"); }));
        await(attempting); try { expectWaiting(account); check(recorder.events.isEmpty(), "Refresh before commit"); } finally { release.countDown(); }
        survey.get(15, TimeUnit.SECONDS); account.get(15, TimeUnit.SECONDS);
        check(order.indexOf("survey-committed") < order.indexOf("status-committed"), "Wrong commit order");
        check(sessions.size() == 2, "Connections must differ"); assertSaved(id, preference, true);
        check(users.findById(id).orElseThrow().getStatus() == state, "Final status"); check(recorder.events.size() == 1, "Committed refresh missing");
    }
    static void cleanup() {
        tx().executeWithoutResult(s -> {
            for (Long id : ownedUsers) {
                jdbc.update("delete o from PROFILE_SURVEY_OPTION o join PROFILE_SURVEY p on p.profile_survey_id=o.profile_survey_id where p.user_id=?", id);
                jdbc.update("delete o from ROOMMATE_PREFERENCE_OPTION o join ROOMMATE_PREFERENCE p on p.preference_id=o.preference_id where p.user_id=?", id);
                jdbc.update("delete c from PREFERENCE_CRITERION c join ROOMMATE_PREFERENCE p on p.preference_id=c.preference_id where p.user_id=?", id);
                jdbc.update("delete from PROFILE_SURVEY where user_id=?", id); jdbc.update("delete from ROOMMATE_PREFERENCE where user_id=?", id);
                jdbc.update("delete from USER_PROFILE where user_id=?", id); jdbc.update("delete from USER where user_id=?", id);
            }
            if (dormId != null) jdbc.update("delete from DORMITORY where dormitory_id=?", dormId);
            if (facultyId != null) jdbc.update("delete from FACULTY where faculty_id=?", facultyId);
        });
        results.add(Map.of("case", "owned-row-cleanup", "result", "PASS", "ownedUsers", ownedUsers.size()));
    }
    public static class Recorder {
        final List<MatchingInputsChanged> events = new CopyOnWriteArrayList<>();
        @TransactionalEventListener public void committed(MatchingInputsChanged event) { events.add(event); }
    }
    @Configuration @EnableTransactionManagement
    @EnableJpaRepositories(basePackages = "com.kkirikkiri")
    @Import({ProfileSurveyService.class, RoommatePreferenceService.class})
    static class Config {
        @Bean DataSource dataSource() { return pool; }
        @Bean JdbcTemplate jdbcTemplate() { return new JdbcTemplate(pool); }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory() {
            var factory = new LocalContainerEntityManagerFactoryBean(); factory.setDataSource(pool);
            factory.setPackagesToScan("com.kkirikkiri"); factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", ddl, "hibernate.jdbc.time_zone", "UTC")); return factory;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory) { return new JpaTransactionManager(factory); }
        @Bean @Primary EntityManager entityManager(EntityManagerFactory factory) { return SharedEntityManagerCreator.createSharedEntityManager(factory); }
        @Bean(destroyMethod = "close") ValidatorFactory validatorFactory() { return Validation.buildDefaultValidatorFactory(); }
        @Bean Validator validator(ValidatorFactory factory) { return factory.getValidator(); }
        @Bean Recorder recorder() { return new Recorder(); }
    }
}
