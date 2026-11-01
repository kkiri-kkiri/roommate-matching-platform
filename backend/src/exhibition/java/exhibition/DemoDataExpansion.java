package exhibition;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import com.kkirikkiri.global.init.SeedUserManifest;
import com.kkirikkiri.user.repository.*;
import com.kkirikkiri.survey.repository.*;
import com.kkirikkiri.matching.repository.RoommateMatchRepository;
import com.kkirikkiri.matching.service.*;

/** Append-only expansion of a byte-for-byte verified clone. No original account is adopted or updated. */
public final class DemoDataExpansion {
    public static final String VERSION = "demo50-append-v1";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    public record Receipt(String database, String source, String anchor, String status, String catalog,
            DemoDataSnapshot baseline, DemoDataSnapshot completed, Map<String, Long> scenarios) {}
    private final ConfigurableApplicationContext context;
    private final JdbcTemplate jdbc;
    private final PlatformTransactionManager manager;
    public DemoDataExpansion(ConfigurableApplicationContext context) {
        this.context = context; jdbc = context.getBean(JdbcTemplate.class);
        manager = context.getBean(PlatformTransactionManager.class);
    }
    static String identity(JdbcTemplate jdbc) {
        try (var connection=Objects.requireNonNull(jdbc.getDataSource()).getConnection()) {
            if(connection.getMetaData().getURL().startsWith("jdbc:h2:mem:"))
                return ExhibitionJournal.hash(Map.of("schema_name",connection.getCatalog(),"server_uuid","test-in-memory"));
        } catch(java.sql.SQLException failure) { throw new IllegalStateException("Cannot identify data source",failure); }
        return ExhibitionJournal.hash(new ExhibitionDatabase(jdbc).one("SELECT DATABASE() AS schema_name, @@server_uuid AS server_uuid"));
    }
    static String catalog() { return ExhibitionJournal.hash(List.of(VERSION, ExhibitionCatalog.students().subList(30, 50))); }
    public static Receipt read(Path path) throws IOException { return JSON.readValue(Files.readString(path), Receipt.class); }
    public static void write(Path path, Receipt value) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        Path temp = Files.createTempFile(path.toAbsolutePath().getParent(), "demo50-", ".tmp");
        try {
            Files.writeString(temp, JSON.writeValueAsString(value));
            Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
    }
    public String preview(Receipt receipt, String database) {
        if (!database.equals(receipt.database()) || !catalog().equals(receipt.catalog())
                || !Set.of("READY", "COMPLETE").contains(receipt.status()))
            throw new IllegalStateException("Wrong database/catalog or incomplete apply; restore the isolated clone and receipt together");
        var current = DemoDataSnapshot.capture(jdbc);
        if (!current.equals(receipt.status().equals("COMPLETE") ? receipt.completed() : receipt.baseline()))
            throw new IllegalStateException("Clone changed since it was verified; no writes permitted");
        if (receipt.status().equals("READY")) {
            for (var student : ExhibitionCatalog.students().subList(30, 50)) {
                if (jdbc.queryForObject("SELECT COUNT(*) FROM `user` WHERE school_email=?", Long.class, student.email()) != 0
                        || jdbc.queryForObject("SELECT COUNT(*) FROM user_profile WHERE nickname=?", Long.class, student.nickname()) != 0)
                    throw new IllegalStateException("New account identity already exists; no adoption permitted");
            }
        }
        return ExhibitionJournal.hash(List.of(receipt, current, catalog()));
    }
    public Receipt apply(Path path, Receipt receipt, String database, String expected, SeedUserManifest manifest,
            String newPassword) throws IOException {
        if (!preview(receipt, database).equals(expected)) throw new IllegalStateException("Stale plan token");
        if (receipt.status().equals("COMPLETE")) { verify(receipt); return receipt; }
        // Barrier stays APPLYING across independent matching transactions and any failure.
        write(path, new Receipt(database, receipt.source(), receipt.anchor(), "APPLYING", receipt.catalog(), receipt.baseline(), null, receipt.scenarios()));
        var scenarios = new TreeMap<>(receipt.scenarios());
        var users = context.getBean(UserRepository.class);
        var writer = context.getBean(ExhibitionWriter.class);
        var tx = new TransactionTemplate(manager);
        tx.executeWithoutResult(status -> {
            receipt.scenarios().values().stream().distinct().sorted().forEach(id -> users.findByIdForAccountUpdate(id).orElseThrow());
            if (!DemoDataSnapshot.capture(jdbc).equals(receipt.baseline())) throw new IllegalStateException("Baseline changed while locking");
            for (var s : ExhibitionCatalog.students().subList(30, 50)) {
                var entry = new ExhibitionPlanner.Entry(s.key(), "user", ExhibitionPlanner.Action.CREATE,
                        null, null, "absent", ExhibitionJournal.hash(s), "new student only");
                long id = writer.writeStudent(s, entry, manifest, newPassword, LocalDate.parse(receipt.anchor()));
                scenarios.put(s.key(), id);
            }
        });
        receipt.baseline().assertPreservedBy(DemoDataSnapshot.capture(jdbc));
        var matching = context.getBean(MatchingRefreshService.class);
        List<Long> ids = ExhibitionCatalog.students().stream().map(s -> scenarios.get(s.key())).toList();
        Set<Long> added = new HashSet<>(ExhibitionCatalog.students().subList(30, 50).stream().map(s -> scenarios.get(s.key())).toList());
        for (Long id : ids) matching.refreshTargets(id, ids.stream()
                .filter(other -> !id.equals(other) && (added.contains(id) || added.contains(other))).toList());
        var completed = DemoDataSnapshot.capture(jdbc);
        receipt.baseline().assertPreservedBy(completed);
        var result = new Receipt(database, receipt.source(), receipt.anchor(), "COMPLETE", receipt.catalog(), receipt.baseline(), completed, scenarios);
        verify(result);
        write(path, result);
        return result;
    }
    public void verify(Receipt receipt) {
        if (!receipt.status().equals("COMPLETE") || !catalog().equals(receipt.catalog())) throw new IllegalStateException("Expansion not complete");
        var tx = new TransactionTemplate(manager);
        tx.setReadOnly(true); tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        tx.executeWithoutResult(status -> {
            var snapshot = DemoDataSnapshot.capture(jdbc);
            receipt.baseline().assertPreservedBy(snapshot);
            if (!snapshot.equals(receipt.completed())) throw new IllegalStateException("Completed data changed");
            List<Long> ids = ExhibitionCatalog.students().stream().map(s -> receipt.scenarios().get(s.key())).toList();
            if (ids.contains(null) || new HashSet<>(ids).size() != 50) throw new IllegalStateException("Cohort is not fifty distinct users");
            var users = context.getBean(UserRepository.class);
            var profiles = context.getBean(UserProfileRepository.class);
            var surveys = context.getBean(ProfileSurveyRepository.class);
            var preferences = context.getBean(RoommatePreferenceRepository.class);
            var options = context.getBean(RoommatePreferenceOptionRepository.class);
            var criteria = context.getBean(PreferenceCriterionRepository.class);
            var matches = context.getBean(RoommateMatchRepository.class);
            var calculator = context.getBean(RoommateMatchCalculator.class);
            int checked = 0;
            for (var s : ExhibitionCatalog.students()) {
                Long id = receipt.scenarios().get(s.key());
                var user = users.findById(id).orElseThrow();
                if (!s.email().equals(user.getSchoolEmail()) || user.getRole() != com.kkirikkiri.user.entity.UserRole.USER)
                    throw new IllegalStateException("Student/admin identity conflict");
                var requester = profiles.findByUser_UserId(id).orElseThrow();
                var preference = preferences.findByUser_UserId(id).orElseThrow();
                var optionRows = options.findAllByRoommatePreference_PreferenceIdInOrderByPreferenceOptionIdAsc(List.of(preference.getPreferenceId()));
                var weights = criteria.findAllByRoommatePreference_PreferenceIdIn(List.of(preference.getPreferenceId()));
                if (weights.size() != 18) throw new IllegalStateException("Missing criterion weights");
                var stored = matches.findAllByRequesterUser_UserIdInAndTargetUser_UserIdIn(List.of(id), ids);
                if (stored.size() != 49) throw new IllegalStateException("Missing or extra directional matches");
                for (Long target : ids) if (!target.equals(id)) {
                    var result = calculator.calculate(requester, profiles.findByUser_UserId(target).orElseThrow(),
                            surveys.findByUser_UserId(target).orElseThrow(), preference, optionRows, weights).orElseThrow();
                    var row = stored.stream().filter(m -> target.equals(m.getTargetUser().getUserId())).findFirst().orElseThrow();
                    if (row.getMatchScore() != result.score()) throw new IllegalStateException("Score differs from existing calculator");
                    checked++;
                }
            }
            if (checked != 2450) throw new IllegalStateException("Incomplete matching verification");
        });
    }
}
