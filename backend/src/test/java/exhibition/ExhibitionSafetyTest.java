package exhibition;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ExhibitionSafetyTest {
    @Test void demoDataNeverTargetsNormalOrOriginalSchemas() {
        for (String schema : List.of("kkirikkiri", "kkirikkiri_exhibition_20261031", "mysql", "kkirikkiri_demo50_x;DROP")) {
            assertThrows(IllegalStateException.class, () -> DemoDataTool.validateTarget(Map.of(
                    "SPRING_PROFILES_ACTIVE", "local", "DEMO_DB_SCHEMA", schema,
                    "DEMO_DB_URL", "jdbc:mysql://localhost:3306/" + schema,
                    "DEMO_DB_USERNAME", "unused", "DEMO_DB_PASSWORD", "unused")));
        }
        assertNotEquals(ExhibitionCatalog.students().get(30).key(), ExhibitionCatalog.moderator().key());
        assertEquals("seed_user31@hknu.ac.kr", ExhibitionCatalog.students().get(30).email());
        assertEquals("admin/moderator", ExhibitionCatalog.moderator().key());
    }
    @TempDir Path directory;
    private Map<String, String> local() {
        return new HashMap<>(Map.of("SPRING_PROFILES_ACTIVE", "local", "EXHIBITION_ENVIRONMENT", "local-exhibition",
                "EXHIBITION_DB_SCHEMA", "kkiri_demo", "EXHIBITION_DB_URL", "jdbc:mysql://127.0.0.1:3306/kkiri_demo",
                "EXHIBITION_DB_USERNAME", "demo", "EXHIBITION_DB_PASSWORD", "not-a-real-credential"));
    }
    @Test void rejectsProductionMixedDefaultRemoteAndDestructiveModesBeforeConnecting() {
        var env = local();
        assertDoesNotThrow(() -> ExhibitionTool.validateEnvironment(env, false));
        for (String profile : List.of("prod", "local,prod", "test", "", "exhibition")) {
            env.put("SPRING_PROFILES_ACTIVE", profile);
            assertThrows(IllegalStateException.class, () -> ExhibitionTool.validateEnvironment(env, false));
        }
        env.put("SPRING_PROFILES_ACTIVE", "local");
        for (String mode : List.of("CREATE", "DELETE")) {
            env.put("APP_SEED_MODE", mode);
            assertThrows(IllegalStateException.class, () -> ExhibitionTool.validateEnvironment(env, false));
        }
        env.put("APP_SEED_MODE", "NONE");
        env.put("SPRING_PROFILES_INCLUDE", "prod");
        assertThrows(IllegalStateException.class, () -> ExhibitionTool.validateEnvironment(env, false));
        env.remove("SPRING_PROFILES_INCLUDE");
        env.put("SPRING_APPLICATION_JSON", "{\"spring\":{\"profiles\":{\"active\":\"prod\"}}}");
        assertThrows(IllegalStateException.class, () -> ExhibitionTool.validateEnvironment(env, false));
        env.remove("SPRING_APPLICATION_JSON");
        for (String url : List.of("jdbc:mysql://remote:3306/kkiri_demo", "jdbc:mysql://127.0.0.1:3306/real_db",
                "jdbc:mysql://127.0.0.1:3306/kkiri_demo?allowMultiQueries=true")) {
            env.put("EXHIBITION_DB_URL", url);
            assertThrows(IllegalStateException.class, () -> ExhibitionTool.validateEnvironment(env, false));
        }
        env.put("EXHIBITION_DB_URL", "jdbc:mysql://127.0.0.1:3306/kkiri_demo");
        assertThrows(IllegalStateException.class, () -> ExhibitionTool.validateEnvironment(env, true));
        env.put("EXHIBITION_WRITES", "ALLOW_REVIEWED_PLAN"); env.put("EXHIBITION_OFFLINE", "CONFIRMED");
        assertDoesNotThrow(() -> ExhibitionTool.validateEnvironment(env, true));
    }
    @Test void commandRequiresExplicitAnchorReviewedTokenAndKnownOptions() {
        assertThrows(RuntimeException.class, () -> ExhibitionTool.parse(new String[]{"apply", "--anchor=2026-10-30"}));
        assertThrows(RuntimeException.class, () -> ExhibitionTool.parse(new String[]{"DELETE", "--anchor=2026-10-30"}));
        assertThrows(RuntimeException.class, () -> ExhibitionTool.parse(new String[]{"preview", "--spring.profiles.active=prod"}));
        assertThrows(RuntimeException.class, () -> ExhibitionTool.parse(new String[]{"preview", "--anchor=2026-10-30", "--anchor=2026-10-31"}));
        assertFalse(ExhibitionTool.parse(new String[]{"preview", "--anchor=2026-10-30"}).apply());
        assertTrue(ExhibitionTool.parse(new String[]{"preview", "--anchor=2026-10-30", "--refresh-text"}).textOnly());
        assertThrows(RuntimeException.class, () -> ExhibitionTool.parse(new String[]{"preview", "--anchor=2026-10-30", "--refresh-text", "--refresh-text"}));
    }
    @Test void provenanceIsDatabaseAndDateBoundAndInterruptedRunsFailClosed() throws Exception {
        Path path = directory.resolve("journal.json");
        try (var journal = new ExhibitionJournal(path)) {
            var state = journal.read("database-a", "2026-10-30");
            assertFalse(Files.exists(path));
            journal.write(state);
            assertEquals(state, journal.read("database-a", "2026-10-30"));
            assertThrows(IllegalStateException.class, () -> journal.read("database-b", "2026-10-30"));
            assertThrows(IllegalStateException.class, () -> journal.read("database-a", "2026-10-31"));
            journal.write(new ExhibitionJournal.State(state.database(), state.anchor(), "APPLYING", state.owned()));
            assertThrows(IllegalStateException.class, () -> journal.read("database-a", "2026-10-30"));
        }
    }
    @Test void twoProcessesCannotOwnOneJournalAndMalformedProvenanceIsNotRebuilt() throws Exception {
        Path path = directory.resolve("journal.json");
        try (var journal = new ExhibitionJournal(path)) {
            assertThrows(RuntimeException.class, () -> new ExhibitionJournal(path));
            Files.writeString(path, "not-json");
            assertThrows(RuntimeException.class, () -> journal.read("db", "2026-10-30"));
        }
    }
}
