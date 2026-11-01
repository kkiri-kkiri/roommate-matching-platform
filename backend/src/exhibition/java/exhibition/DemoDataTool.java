package exhibition;

import java.nio.channels.*;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.boot.*;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.json.JsonMapper;
import com.kkirikkiri.global.init.SeedUserManifest;
import com.kkirikkiri.user.repository.UserRepository;

/** Data preparation only. Refuses the normal DB and the original exhibition DB before Spring starts. */
public final class DemoDataTool {
    private DemoDataTool() {}
    public static void validateTarget(Map<String, String> env) {
        String schema = required(env, "DEMO_DB_SCHEMA");
        if (!schema.matches("kkirikkiri_demo50_[a-zA-Z0-9_]{1,40}"))
            throw new IllegalStateException("Only explicitly named kkirikkiri_demo50_* isolated schemas are writable");
        if (!required(env, "DEMO_DB_URL").matches("jdbc:mysql://(localhost|127\\.0\\.0\\.1):[0-9]{1,5}/" + schema
                + "(\\?useSSL=false&allowPublicKeyRetrieval=true)?"))
            throw new IllegalStateException("Target URL and isolated schema must agree; local MySQL only");
        if (!"local".equals(env.get("SPRING_PROFILES_ACTIVE"))
                || !env.getOrDefault("SPRING_PROFILES_INCLUDE", "").isBlank()
                || !env.getOrDefault("SPRING_APPLICATION_JSON", "").isBlank()
                || !Set.of("", "local").contains(System.getProperty("spring.profiles.active", "")))
            throw new IllegalStateException("Explicit local profile without inherited overrides is required");
        required(env, "DEMO_DB_USERNAME"); required(env, "DEMO_DB_PASSWORD");
    }
    static ConfigurableApplicationContext open(Map<String, String> env, boolean bootstrap) {
        var settings = new LinkedHashMap<String, Object>();
        settings.put("spring.config.location", "optional:classpath:/demo-tool.properties");
        settings.put("spring.config.import", ""); settings.put("spring.profiles.active", "local");
        settings.put("spring.profiles.include", ""); settings.put("spring.profiles.default", "none");
        settings.put("spring.datasource.url", required(env, "DEMO_DB_URL"));
        settings.put("spring.datasource.username", required(env, "DEMO_DB_USERNAME"));
        settings.put("spring.datasource.password", required(env, "DEMO_DB_PASSWORD"));
        settings.put("spring.datasource.driver-class-name", "com.mysql.cj.jdbc.Driver");
        settings.put("spring.jpa.hibernate.ddl-auto", bootstrap ? "create" : "validate");
        settings.put("spring.jpa.properties.hibernate.hbm2ddl.auto", bootstrap ? "create" : "validate");
        settings.put("spring.sql.init.mode", bootstrap ? "always" : "never");
        settings.put("spring.jpa.defer-datasource-initialization", bootstrap);
        settings.put("spring.sql.init.data-locations", "classpath:data-common.sql");
        settings.put("spring.sql.init.encoding", "UTF-8");
        settings.put("spring.jpa.open-in-view", false); settings.put("spring.jpa.show-sql", false);
        settings.put("spring.flyway.enabled", false); settings.put("spring.liquibase.enabled", false);
        settings.put("spring.main.banner-mode", "off"); settings.put("app.seed.mode", "NONE");
        settings.put("app.calendar.crawl-on-startup", false); settings.put("logging.level.root", "ERROR");
        settings.put("logging.level.org.hibernate.SQL", "OFF");
        settings.put("logging.level.org.hibernate.orm.jdbc.bind", "OFF");
        var environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addFirst(new MapPropertySource("demo-data-only", settings));
        var app = new SpringApplication(ExhibitionConfiguration.class);
        app.setWebApplicationType(WebApplicationType.NONE); app.setEnvironment(environment);
        var context = app.run();
        ExhibitionTool.assertIsolated(context);
        if (!required(env, "DEMO_DB_SCHEMA").equals(context.getBean(JdbcTemplate.class).queryForObject("SELECT DATABASE()", String.class))) {
            context.close(); throw new IllegalStateException("Actual target schema mismatch");
        }
        return context;
    }
    public static void main(String[] args) throws Exception {
        if (args.length < 2 || !Set.of("bootstrap", "seal-clone", "preview", "apply", "verify", "verify-source", "fresh-preview", "fresh-apply").contains(args[0]))
            throw new IllegalArgumentException("Explicit data operation and --state=directory required");
        String operation = args[0]; Map<String, String> options = new HashMap<>();
        for (int i = 1; i < args.length; i++) {
            String[] pair = args[i].split("=", 2);
            if (pair.length != 2 || !Set.of("--state", "--source-journal", "--anchor", "--expect").contains(pair[0])
                    || pair[1].isBlank() || options.putIfAbsent(pair[0], pair[1]) != null)
                throw new IllegalArgumentException("Unsupported or duplicate option");
        }
        Map<String, String> env = System.getenv(); validateTarget(env);
        Path directory = Path.of(Objects.requireNonNull(options.get("--state"))).toAbsolutePath();
        Files.createDirectories(directory);
        Path receiptFile = directory.resolve("receipt.json");
        boolean writes = Set.of("bootstrap", "seal-clone", "apply", "fresh-apply").contains(operation);
        if (writes && !"CONFIRMED_ISOLATED_OFFLINE".equals(env.get("DEMO_WRITES")))
            throw new IllegalStateException("Explicit isolated/offline write guard required");
        if ((operation.equals("apply") || operation.equals("fresh-apply"))
                && !options.getOrDefault("--expect", "").matches("[a-f0-9]{64}"))
            throw new IllegalArgumentException("Apply requires the exact reviewed token");
        // A file lock and one DB-wide lock cover receipt changes, including independent matching transactions.
        try (var channel = FileChannel.open(directory.resolve("operation.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var fileLock = channel.tryLock();
             var lockConnection = connect(env, "DEMO_DB_")) {
            if (fileLock == null) throw new IllegalStateException("Another data operation is running");
            String lockName = "kkiri-demo50-" + ExhibitionJournal.hash(required(env, "DEMO_DB_SCHEMA")).substring(0, 32);
            try (var statement = lockConnection.prepareStatement("SELECT GET_LOCK(?,0)")) {
                statement.setString(1, lockName);
                try (var rs = statement.executeQuery()) { if (!rs.next() || rs.getInt(1) != 1) throw new IllegalStateException("DB is busy"); }
            }
            try {
                if (operation.equals("bootstrap")) {
                    try (var statement = lockConnection.createStatement(); var rs = statement.executeQuery("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()")) {
                        rs.next(); if (rs.getLong(1) != 0) throw new IllegalStateException("Bootstrap requires a completely empty isolated schema");
                    }
                    if (Files.exists(receiptFile) || Files.exists(directory.resolve("fresh.json"))) throw new IllegalStateException("Unused state directory required");
                }
                try (var context = open(env, operation.equals("bootstrap"))) {
                    if (operation.equals("bootstrap")) { System.out.println("Empty isolated schema initialized; no users created."); return; }
                    var jdbc = context.getBean(JdbcTemplate.class);
                    String database = DemoDataExpansion.identity(jdbc);
                    var expansion = new DemoDataExpansion(context);
                    if (operation.equals("seal-clone")) {
                        if (Files.exists(receiptFile)) throw new IllegalStateException("Receipt already exists; never reseal modified data");
                        String sourceUrl = required(env, "DEMO_SOURCE_URL");
                        if (!sourceUrl.matches("jdbc:mysql://(localhost|127\\.0\\.0\\.1):[0-9]{1,5}/kkirikkiri_exhibition_20261031(\\?useSSL=false&allowPublicKeyRetrieval=true)?"))
                            throw new IllegalStateException("Only the verified original exhibition schema is a clone source");
                        try (var source = connect(env, "DEMO_SOURCE_")) {
                            source.setReadOnly(true); source.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ); source.setAutoCommit(false);
                            var sourceJdbc = new JdbcTemplate(new SingleConnectionDataSource(source, true));
                            var state = JsonMapper.builder().build().readValue(Files.readString(Path.of(Objects.requireNonNull(options.get("--source-journal")))), ExhibitionJournal.State.class);
                            if (!"COMPLETE".equals(state.status()) || !DemoDataExpansion.identity(sourceJdbc).equals(state.database()))
                                throw new IllegalStateException("Original journal does not belong to source");
                            Map<String, Long> scenarios = validateSource(sourceJdbc, state);
                            var baseline = DemoDataSnapshot.capture(sourceJdbc);
                            if (!baseline.equals(DemoDataSnapshot.capture(jdbc))) throw new IllegalStateException("Target is not an exact clone");
                            DemoDataExpansion.write(receiptFile, new DemoDataExpansion.Receipt(database, state.database(), state.anchor(), "READY",
                                    DemoDataExpansion.catalog(), baseline, null, scenarios));
                            source.rollback();
                            System.out.println("Clone verified against source; original admin scenario mapped to admin/moderator. Source provenance untouched.");
                        }
                    } else if (operation.startsWith("fresh-")) {
                        LocalDate anchor = LocalDate.parse(Objects.requireNonNull(options.get("--anchor")));
                        var legacy = new SeedUserManifest(directory.resolve("new-users.properties").toString());
                        var planner = new ExhibitionPlanner(new ExhibitionDatabase(jdbc), context.getBean(UserRepository.class), legacy, context.getBean(PlatformTransactionManager.class));
                        try (var journal = new ExhibitionJournal(directory.resolve("fresh.json"))) {
                            var state = journal.read(database, anchor.toString());
                            if (Files.exists(receiptFile)) { expansion.preview(DemoDataExpansion.read(receiptFile), database); }
                            else if (!state.owned().isEmpty()) throw new IllegalStateException("Unfinished fresh generation; restore checkpoint");
                            var plan = planner.preview(state, anchor, true);
                            if (plan.entries().stream().anyMatch(e -> e.action() == ExhibitionPlanner.Action.SKIP || e.action() == ExhibitionPlanner.Action.UPDATE))
                                throw new IllegalStateException("Fresh generation refuses existing unowned or changed data");
                            System.out.println("Plan: " + plan.counts() + "; token=" + plan.token());
                            if (operation.equals("fresh-apply")) {
                                state = context.getBean(ExhibitionWriter.class).apply(journal, state, planner, plan, options.get("--expect"), legacy, env.get("DEMO_NEW_USER_PASSWORD"));
                                Map<String, Long> scenarios = new TreeMap<>(); state.owned().forEach((key, owned) -> { if (owned.kind().equals("user")) scenarios.put(key, owned.id()); });
                                var snapshot = DemoDataSnapshot.capture(jdbc);
                                var receipt = new DemoDataExpansion.Receipt(database, "fresh", anchor.toString(), "COMPLETE", DemoDataExpansion.catalog(), snapshot, snapshot, scenarios);
                                expansion.verify(receipt); DemoDataExpansion.write(receiptFile, receipt);
                            }
                        }
                    } else if (operation.equals("verify-source")) {
                        var receipt = DemoDataExpansion.read(receiptFile);
                        if (!database.equals(receipt.database())) throw new IllegalStateException("Receipt target mismatch");
                        String sourceUrl = required(env, "DEMO_SOURCE_URL");
                        if (!sourceUrl.matches("jdbc:mysql://(localhost|127\\.0\\.0\\.1):[0-9]{1,5}/kkirikkiri_exhibition_20261031(\\?useSSL=false&allowPublicKeyRetrieval=true)?"))
                            throw new IllegalStateException("Unexpected original source");
                        try (var source = connect(env, "DEMO_SOURCE_")) {
                            source.setReadOnly(true); source.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ); source.setAutoCommit(false);
                            var sourceJdbc = new JdbcTemplate(new SingleConnectionDataSource(source, true));
                            if (!receipt.source().equals(DemoDataExpansion.identity(sourceJdbc))
                                    || !receipt.baseline().equals(DemoDataSnapshot.capture(sourceJdbc)))
                                throw new IllegalStateException("Original source changed since clone");
                            source.rollback();
                            System.out.println("Verified: every original source row is unchanged since clone.");
                        }
                    } else {
                        var receipt = DemoDataExpansion.read(receiptFile);
                        String token = expansion.preview(receipt, database);
                        System.out.println("Plan: " + (receipt.status().equals("COMPLETE") ? "KEEP; 0 changes" : "CREATE 20 students; CREATE 1580 directional matches; UPDATE/DELETE 0") + "; token=" + token);
                        if (operation.equals("apply")) expansion.apply(receiptFile, receipt, database, options.get("--expect"),
                                new SeedUserManifest(directory.resolve("new-users.properties").toString()), env.get("DEMO_NEW_USER_PASSWORD"));
                        if (operation.equals("verify") || operation.equals("apply")) {
                            expansion.verify(DemoDataExpansion.read(receiptFile));
                            System.out.println("Verified: 50 students, 18 criteria each, 2450 calculator results, every original row and relationship preserved.");
                        }
                    }
                }
            } finally {
                try (var statement = lockConnection.prepareStatement("SELECT RELEASE_LOCK(?)")) { statement.setString(1, lockName); statement.executeQuery().close(); }
            }
        }
    }
    private static Map<String, Long> validateSource(JdbcTemplate jdbc, ExhibitionJournal.State state) {
        var db = new ExhibitionDatabase(jdbc); Map<String, Long> scenarios = new TreeMap<>();
        for (var s : ExhibitionCatalog.students().subList(0, 30)) {
            var owned = Objects.requireNonNull(state.owned().get(s.key()));
            if (db.count("SELECT COUNT(*) FROM `user` u JOIN user_profile p ON p.user_id=u.user_id WHERE u.user_id=? AND u.school_email=? AND u.role='USER' AND p.nickname=?", owned.id(), s.email(), s.nickname()) != 1)
                throw new IllegalStateException("Original student identity/text mismatch");
            scenarios.put(s.key(), owned.id());
        }
        var admin = Objects.requireNonNull(state.owned().get("user/31"));
        if (db.count("SELECT COUNT(*) FROM `user` WHERE user_id=? AND school_email='exhibition_admin@hknu.ac.kr' AND role='ADMIN'", admin.id()) != 1)
            throw new IllegalStateException("Original moderator mismatch");
        scenarios.put(ExhibitionCatalog.MODERATOR_KEY, admin.id());
        for (var p : ExhibitionCatalog.posts()) {
            var owned = Objects.requireNonNull(state.owned().get(p.key()));
            if (db.count("SELECT COUNT(*) FROM community_post WHERE post_id=? AND author_id=? AND BINARY title=BINARY ? AND BINARY content=BINARY ?",
                    owned.id(), scenarios.get("user/" + p.slot()), p.title(), p.content()) != 1)
                throw new IllegalStateException("Original post text/owner mismatch");
            for (String child : List.of("comment", "reply")) {
                if (db.count("SELECT COUNT(*) FROM community_comment WHERE comment_id=? AND post_id=? AND BINARY content=BINARY ?",
                        owned.children().get(child), owned.id(), child.equals("comment") ? p.comment() : p.reply()) != 1)
                    throw new IllegalStateException("Original comment text mismatch");
            }
        }
        for (var chat : ExhibitionCatalog.conversations()) {
            var owned = Objects.requireNonNull(state.owned().get(chat.key()));
            var messages = jdbc.queryForList("SELECT content FROM chat_message WHERE chat_room_id=? ORDER BY message_id", String.class, owned.id());
            if (!messages.equals(chat.messages())) throw new IllegalStateException("Original chat text mismatch");
        }
        return scenarios;
    }
    private static Connection connect(Map<String, String> env, String prefix) throws SQLException {
        return DriverManager.getConnection(required(env, prefix + "URL"), required(env, prefix + "USERNAME"), required(env, prefix + "PASSWORD"));
    }
    private static String required(Map<String, String> env, String name) {
        String value = env.get(name); if (value == null || value.isBlank()) throw new IllegalStateException("Missing setting: " + name); return value;
    }
}
