package exhibition;

import java.sql.SQLException;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Local receipts contain row hashes only. Never print rows, password hashes or credentials. */
public record DemoDataSnapshot(Map<String, Map<String, String>> tables) {
    static final Set<String> APPEND_TABLES = Set.of("user", "user_profile", "profile_survey",
            "profile_survey_option", "roommate_preference", "roommate_preference_option",
            "preference_criterion", "roommate_match");

    public static DemoDataSnapshot capture(JdbcTemplate jdbc) {
        Map<String, List<String>> primaryKeys = new TreeMap<>();
        try (var connection = Objects.requireNonNull(jdbc.getDataSource()).getConnection()) {
            var metadata = connection.getMetaData();
            try (var rs = metadata.getTables(connection.getCatalog(), connection.getSchema(), "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    String table = rs.getString("TABLE_NAME").toLowerCase(Locale.ROOT);
                    if (!table.matches("[a-z][a-z0-9_]*")) throw new IllegalStateException("Unsupported table name");
                    var columns = new TreeMap<Integer, String>();
                    try (var keys = metadata.getPrimaryKeys(connection.getCatalog(), connection.getSchema(), rs.getString("TABLE_NAME"))) {
                        while (keys.next()) columns.put(keys.getInt("KEY_SEQ"), keys.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
                    }
                    if (columns.isEmpty()) throw new IllegalStateException("Table without a primary key: " + table);
                    primaryKeys.put(table, List.copyOf(columns.values()));
                }
            }
        } catch (SQLException failure) { throw new IllegalStateException("Cannot inspect isolated schema", failure); }
        if (primaryKeys.isEmpty()) throw new IllegalStateException("Empty schema");
        var db = new ExhibitionDatabase(jdbc);
        Map<String, Map<String, String>> tables = new TreeMap<>();
        primaryKeys.forEach((table, keys) -> {
            Map<String, String> rows = new TreeMap<>();
            for (var row : db.rows("SELECT * FROM `" + table + "`")) {
                String key = ExhibitionJournal.hash(keys.stream().map(row::get).toList());
                if (rows.put(key, ExhibitionJournal.hash(row)) != null) throw new IllegalStateException("Duplicate primary key");
            }
            tables.put(table, Collections.unmodifiableMap(rows));
        });
        return new DemoDataSnapshot(Collections.unmodifiableMap(tables));
    }

    public void assertPreservedBy(DemoDataSnapshot after) {
        if (!tables.keySet().equals(after.tables.keySet())) throw new IllegalStateException("Table set changed");
        tables.forEach((table, before) -> {
            var current = after.tables.get(table);
            before.forEach((key, hash) -> {
                if (!hash.equals(current.get(key))) throw new IllegalStateException("An original row changed: " + table);
            });
            if (!APPEND_TABLES.contains(table) && !before.equals(current))
                throw new IllegalStateException("Unexpected rows added: " + table);
        });
    }
}
