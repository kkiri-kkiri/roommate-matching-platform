package exhibition;

import java.sql.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;

/** Bound data-only import into an already verified clone; original IDs never become target IDs implicitly. */
final class DemoMergeRows {
    static final Map<String, String> PK = Map.ofEntries(
            Map.entry("user", "user_id"), Map.entry("user_profile", "user_profile_id"),
            Map.entry("profile_survey", "profile_survey_id"), Map.entry("profile_survey_option", "profile_survey_option_id"),
            Map.entry("roommate_preference", "preference_id"), Map.entry("roommate_preference_option", "preference_option_id"),
            Map.entry("preference_criterion", "preference_criterion_id"), Map.entry("community_post", "post_id"),
            Map.entry("community_comment", "comment_id"), Map.entry("post_like", "post_like_id"),
            Map.entry("comment_like", "comment_like_id"), Map.entry("community_anonymous_author", "anonymous_author_id"),
            Map.entry("chat_room", "chat_room_id"), Map.entry("chat_room_member", "chat_room_member_id"), Map.entry("chat_message", "message_id"),
            Map.entry("calendar_category", "category_id"), Map.entry("calendar_category_setting", "category_setting_id"),
            Map.entry("calendar_event", "event_id"), Map.entry("report", "report_id"),
            Map.entry("dormitory", "dormitory_id"), Map.entry("faculty", "faculty_id"), Map.entry("major", "major_id"),
            Map.entry("roommate_match", "match_id"));
    private final JdbcTemplate jdbc;
    DemoMergeRows(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    List<Map<String, Object>> rows(String table) {
        requireTable(table);
        return query("SELECT * FROM `" + table + "` ORDER BY `" + PK.get(table) + "`");
    }
    List<Map<String, Object>> query(String sql, Object... arguments) {
        return jdbc.query(sql, (rs, n) -> {
            Map<String, Object> row = new TreeMap<>();
            for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++)
                row.put(rs.getMetaData().getColumnLabel(i).toLowerCase(Locale.ROOT), rs.getObject(i));
            return row;
        }, arguments);
    }
    Map<String, Object> one(String table, long id) {
        requireTable(table);
        return query("SELECT * FROM `" + table + "` WHERE `" + PK.get(table) + "`=?", id).stream().findFirst().orElseThrow();
    }
    long insert(String table, Map<String, Object> input) {
        requireTable(table);
        Map<String, Object> row = new TreeMap<>(input); row.remove(PK.get(table));
        row.keySet().forEach(DemoMergeRows::requireColumn);
        String sql = "INSERT INTO `" + table + "` (" + String.join(",", row.keySet().stream().map(c -> "`" + c + "`").toList())
                + ") VALUES (" + String.join(",", Collections.nCopies(row.size(), "?")) + ")";
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            int i = 1; for (Object value : row.values()) statement.setObject(i++, value);
            return statement;
        }, key);
        return Objects.requireNonNull(key.getKey()).longValue();
    }
    void update(String table, long id, Map<String, Object> input) {
        // Account rows and community identity/anonymity cannot be changed through this helper.
        if (!Set.of("user_profile", "profile_survey", "community_post").contains(table)) throw new IllegalArgumentException("Forbidden update");
        var row = new TreeMap<>(input); row.remove(PK.get(table)); row.remove("user_id"); row.remove("created_at");
        if (table.equals("community_post")) row.keySet().retainAll(Set.of("title", "content"));
        row.keySet().forEach(DemoMergeRows::requireColumn);
        List<Object> values = new ArrayList<>(row.values()); values.add(id);
        int changed = jdbc.update("UPDATE `" + table + "` SET " + String.join(",", row.keySet().stream().map(c -> "`" + c + "`=?").toList())
                + " WHERE `" + PK.get(table) + "`=?", values.toArray());
        if (changed != 1) throw new IllegalStateException("Expected one owned row");
    }
    static long id(String table, Map<String, Object> row) { return number(row.get(PK.get(table))); }
    static long number(Object value) { return value instanceof Number n ? n.longValue() : Long.parseLong(value.toString()); }
    static boolean same(Object a, Object b) {
        if (a == null || b == null) return a == b;
        if (a instanceof Boolean || b instanceof Boolean) return truth(a) == truth(b);
        return a.toString().equals(b.toString());
    }
    static boolean truth(Object value) { return value != null && Set.of("true", "TRUE", "1").contains(value.toString()); }
    static String rowKey(String table, Map<String, Object> row) { return ExhibitionJournal.hash(List.of(row.get(PK.get(table)).toString())); }
    private static void requireTable(String table) { if (!PK.containsKey(table)) throw new IllegalArgumentException("Unsupported import table"); }
    private static void requireColumn(String column) { if (!column.matches("[a-z][a-z0-9_]*")) throw new IllegalArgumentException("Unsupported column"); }
}
