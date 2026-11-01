package exhibition;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import com.kkirikkiri.global.init.SeedUserManifest;
import com.kkirikkiri.user.repository.UserRepository;

/** Not a server endpoint or startup runner. Only this separate classpath can run the tool. */
public final class ExhibitionTool {
    private ExhibitionTool() {}
    public record Options(boolean apply, LocalDate anchor, boolean reports, boolean textOnly, Path journal, Path legacy, String expected) {}
    public static Options parse(String[] args) {
        if (args.length < 2 || !Set.of("preview", "apply").contains(args[0]))
            throw new IllegalArgumentException("Usage: preview|apply --anchor=YYYY-MM-DD [--reports] [--journal=path] [--legacy-manifest=path] [--expect=plan-token]");
        Map<String, String> values = new HashMap<>(); boolean reports = false; boolean textOnly = false;
        for (int i = 1; i < args.length; i++) {
            if (args[i].equals("--reports")) { if (reports) throw new IllegalArgumentException("Duplicate flag"); reports = true; continue; }
            if (args[i].equals("--refresh-text")) { if (textOnly) throw new IllegalArgumentException("Duplicate flag"); textOnly = true; continue; }
            String[] pair = args[i].split("=", 2);
            if (pair.length != 2 || !Set.of("--anchor", "--journal", "--legacy-manifest", "--expect").contains(pair[0])
                    || pair[1].isBlank() || values.putIfAbsent(pair[0], pair[1]) != null)
                throw new IllegalArgumentException("Unsupported or duplicate option");
        }
        LocalDate anchor = LocalDate.parse(Objects.requireNonNull(values.get("--anchor"), "Explicit anchor required"));
        ExhibitionCatalog.validateAnchor(anchor);
        String expected = values.get("--expect");
        if (args[0].equals("apply") && (expected == null || !expected.matches("[a-f0-9]{64}")))
            throw new IllegalArgumentException("Apply requires the reviewed preview token");
        return new Options(args[0].equals("apply"), anchor, reports, textOnly,
                Path.of(values.getOrDefault("--journal", "local-data/exhibition.json")),
                Path.of(values.getOrDefault("--legacy-manifest", "local-data/seed-users.properties")), expected);
    }
    public static void validateEnvironment(Map<String, String> env, boolean apply) {
        if (!"local".equals(env.get("SPRING_PROFILES_ACTIVE")) || !"local-exhibition".equals(env.get("EXHIBITION_ENVIRONMENT")))
            throw new IllegalStateException("Only explicit local-exhibition is permitted; production/test/default profiles are rejected");
        if (!"NONE".equals(env.getOrDefault("APP_SEED_MODE", "NONE")))
            throw new IllegalStateException("APP_SEED_MODE must be NONE; CREATE and DELETE are not exhibition modes");
        if (!env.getOrDefault("SPRING_PROFILES_INCLUDE", "").isBlank() || !env.getOrDefault("SPRING_APPLICATION_JSON", "").isBlank()
                || !Set.of("", "local").contains(env.getOrDefault("SPRING_PROFILES_DEFAULT", "")))
            throw new IllegalStateException("Additional/default profile and JSON overrides are not permitted");
        String schema = required(env, "EXHIBITION_DB_SCHEMA");
        if (!schema.matches("[A-Za-z][A-Za-z0-9_]{0,63}")) throw new IllegalStateException("Invalid explicit schema name");
        String url = required(env, "EXHIBITION_DB_URL");
        if (!url.matches("jdbc:mysql://(127\\.0\\.0\\.1|localhost):[0-9]{1,5}/" + schema
                + "(\\?(useSSL=false&allowPublicKeyRetrieval=true|allowPublicKeyRetrieval=true&useSSL=false))?"))
            throw new IllegalStateException("Only the explicit local MySQL schema is permitted; no remote hosts, aliases or arbitrary URL options");
        required(env, "EXHIBITION_DB_USERNAME"); required(env, "EXHIBITION_DB_PASSWORD");
        if (apply && (!"ALLOW_REVIEWED_PLAN".equals(env.get("EXHIBITION_WRITES")) || !"CONFIRMED".equals(env.get("EXHIBITION_OFFLINE"))))
            throw new IllegalStateException("Apply requires a reviewed backup and all application instances stopped; set the explicit write/offline guards");
    }
    public static void main(String[] args) throws Exception {
        Options options = parse(args);
        Map<String, String> env = System.getenv();
        validateEnvironment(env, options.apply()); // Before Spring, the datasource or any file is opened.
        if (options.textOnly() && !"kkirikkiri_exhibition_20261031".equals(env.get("EXHIBITION_DB_SCHEMA")))
            throw new IllegalStateException("Text refresh is restricted to the exhibition schema");
        if (!Set.of("", "local").contains(System.getProperty("spring.profiles.active", "")))
            throw new IllegalStateException("Conflicting JVM profile; production and test launches are rejected");
        var app = new SpringApplication(ExhibitionConfiguration.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        // Highest-priority safe settings also neutralize inherited JVM/SPRING_APPLICATION_JSON overrides.
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("spring.config.location", "optional:classpath:/exhibition-tool.properties");
        settings.put("spring.config.import", ""); settings.put("spring.profiles.active", "local");
        settings.put("spring.profiles.include", ""); settings.put("spring.profiles.default", "none");
        settings.put("spring.datasource.url", required(env, "EXHIBITION_DB_URL"));
        settings.put("spring.datasource.username", required(env, "EXHIBITION_DB_USERNAME"));
        settings.put("spring.datasource.password", required(env, "EXHIBITION_DB_PASSWORD"));
        settings.put("spring.datasource.driver-class-name", "com.mysql.cj.jdbc.Driver");
        settings.put("spring.jpa.hibernate.ddl-auto", "validate"); settings.put("spring.sql.init.mode", "never");
        settings.put("spring.jpa.defer-datasource-initialization", "false");
        settings.put("spring.jpa.open-in-view", "false"); settings.put("spring.jpa.show-sql", "false");
        settings.put("spring.jpa.properties.hibernate.hbm2ddl.auto", "validate");
        settings.put("spring.flyway.enabled", "false"); settings.put("spring.liquibase.enabled", "false");
        settings.put("spring.main.banner-mode", "off"); settings.put("app.seed.mode", "NONE");
        settings.put("app.calendar.crawl-on-startup", "false"); settings.put("logging.level.root", "ERROR");
        settings.put("logging.level.org.hibernate.SQL", "OFF"); settings.put("logging.level.org.hibernate.orm.jdbc.bind", "OFF");
        // ConfigData runs before ApplicationContextInitializers. Supply this environment BEFORE run,
        // so application-local.properties and its .env import are never even loaded.
        var environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addFirst(new MapPropertySource("exhibition-safety", settings));
        app.setEnvironment(environment);
        try (ConfigurableApplicationContext context = app.run()) {
            assertIsolated(context);
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            var metadata = new ExhibitionDatabase(jdbc).one("SELECT DATABASE() AS schema_name, @@server_uuid AS server_uuid");
            if (!required(env, "EXHIBITION_DB_SCHEMA").equals(metadata.get("schema_name")))
                throw new IllegalStateException("Connected schema does not match the explicitly selected target");
            String identity = ExhibitionJournal.hash(metadata);
            var legacy = new SeedUserManifest(options.legacy().toString());
            var planner = new ExhibitionPlanner(new ExhibitionDatabase(jdbc), context.getBean(UserRepository.class),
                    legacy, context.getBean(PlatformTransactionManager.class));
            if (options.textOnly()) ExhibitionTextRefresh.validateCopy(context.getBean(jakarta.validation.Validator.class));
            try (var journal = new ExhibitionJournal(options.journal())) {
                var state = journal.read(identity, options.anchor().toString());
                var oldModerator = state.owned().get("user/31");
                if (oldModerator != null && jdbc.queryForObject("SELECT COUNT(*) FROM `user` WHERE user_id=? AND role='ADMIN'", Long.class, oldModerator.id()) > 0)
                    throw new IllegalStateException("Legacy moderator key detected. Preserve source; use the verified clone expansion workflow instead.");
                var plan = planner.preview(state, options.anchor(), options.reports(), options.textOnly());
                printPlan(plan);
                if (options.textOnly()) printTextReview(new ExhibitionDatabase(jdbc), plan);
                if (options.apply()) {
                    // A dedicated connection holds the same DB-wide lock regardless of journal file path.
                    try (var connection = Objects.requireNonNull(jdbc.getDataSource()).getConnection()) {
                        String lockName = "kkiri-exhibition-" + identity.substring(0, 32);
                        try (var statement = connection.prepareStatement("SELECT GET_LOCK(?, 0)")) {
                            statement.setString(1, lockName);
                            try (var result = statement.executeQuery()) {
                                if (!result.next() || result.getInt(1) != 1) throw new IllegalStateException("Another exhibition writer owns the database");
                            }
                        }
                        try {
                            context.getBean(ExhibitionWriter.class).apply(journal, state, planner, plan, options.expected(), legacy,
                                    env.get("EXHIBITION_NEW_USER_PASSWORD"));
                            System.out.println("Exhibition application completed. IDs and row fingerprints are in the local journal.");
                        } finally {
                            try (var release = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
                                release.setString(1, lockName); release.executeQuery().close();
                            }
                        }
                    }
                }
            }
        }
    }
    public static void assertIsolated(ConfigurableApplicationContext context) {
        if (!context.getBeansOfType(org.springframework.boot.ApplicationRunner.class).isEmpty()
                || !context.getBeansOfType(org.springframework.boot.CommandLineRunner.class).isEmpty()
                || !context.getBeansOfType(org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor.class).isEmpty()
                || !context.getBeansOfType(com.kkirikkiri.matching.service.MatchingRefreshListener.class).isEmpty()
                || !context.getBeansOfType(com.kkirikkiri.calendar.crawler.HknuAcademicCalendarCrawler.class).isEmpty())
            throw new IllegalStateException("Exhibition context unexpectedly contains automatic/background work");
    }
    private static void printPlan(ExhibitionPlanner.Plan plan) {
        System.out.println("Database fingerprint: " + plan.database() + "; anchor: " + plan.anchor());
        plan.counts().forEach((key, count) -> System.out.println(key + "=" + count));
        plan.effects().forEach((key, count) -> System.out.println("Affected rows " + key + "=" + count));
        for (var entry : plan.entries()) System.out.println(entry.action() + " " + entry.key() + " id=" + entry.id()
                + " owner=" + entry.owner() + (entry.reason() == null ? "" : " reason=" + entry.reason()));
        long participants = plan.entries().stream().filter(e -> e.kind().equals("user") && !e.key().equals(ExhibitionCatalog.MODERATOR_KEY) && e.usable()).count();
        System.out.println(plan.textOnly() ? "Text refresh: no inserts/deletes; profile, survey, matching, timestamps and relationships preserved."
                : "Matching scope: only the verified fictional cohort, at most " + participants * (participants - 1) + " directional pairs; existing calculator only.");
        System.out.println("Preview token: " + plan.token());
    }
    private static void printTextReview(ExhibitionDatabase db, ExhibitionPlanner.Plan plan) {
        var json = tools.jackson.databind.json.JsonMapper.builder().build();
        Map<String, Object> catalog = new TreeMap<>();
        catalog.put("students", ExhibitionCatalog.students().stream().map(s -> Map.of("key", s.key(), "nickname", s.nickname())).toList());
        catalog.put("posts", ExhibitionCatalog.posts());
        catalog.put("conversations", ExhibitionCatalog.conversations());
        System.out.println("Text catalog: " + json.writeValueAsString(catalog));
        for (var e : plan.entries()) if (e.changes()) {
            Object before = switch (e.kind()) {
                case "user" -> db.rows("SELECT nickname FROM user_profile WHERE user_id=?", e.id());
                case "post" -> List.of(db.rows("SELECT title,content FROM community_post WHERE post_id=?", e.id()),
                        db.rows("SELECT comment_id,parent_comment_id,content FROM community_comment WHERE post_id=? ORDER BY comment_id", e.id()));
                case "chat" -> db.rows("SELECT message_id,sender_id,content FROM chat_message WHERE chat_room_id=? ORDER BY message_id", e.id());
                default -> List.of();
            };
            System.out.println("Before " + e.key() + ": " + json.writeValueAsString(before));
        }
        System.out.println("Protected data fingerprint: " + ExhibitionTextRefresh.protectedSnapshot(db));
    }
    private static String required(Map<String, String> env, String key) {
        String value = env.get(key);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing explicit exhibition setting: " + key);
        return value;
    }
}
