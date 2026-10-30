package exhibition;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Only fixed SQL and bound IDs. Snapshots never print credentials or personal fields. */
public final class ExhibitionDatabase {
    final JdbcTemplate jdbc;
    public ExhibitionDatabase(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public List<Map<String, Object>> rows(String sql, Object... args) {
        return jdbc.query(sql, (r, n) -> {
            Map<String, Object> row = new TreeMap<>();
            for (int c = 1; c <= r.getMetaData().getColumnCount(); c++) {
                String name = r.getMetaData().getColumnLabel(c).toLowerCase(Locale.ROOT);
                Object value = r.getObject(c);
                row.put(name, value == null ? null : value.toString());
            }
            return row;
        }, args);
    }
    public Map<String, Object> one(String sql, Object... args) {
        var rows = rows(sql, args);
        if (rows.size() > 1) throw new IllegalStateException("Ambiguous exhibition target");
        return rows.isEmpty() ? Map.of() : rows.getFirst();
    }
    public long count(String sql, Object... args) { return jdbc.queryForObject(sql, Long.class, args); }
    public static Long id(Map<String, Object> row, String column) {
        Object value = row.get(column);
        return value == null ? null : Long.valueOf(value.toString());
    }
    public String snapshot(String kind, long id) {
        List<Object> parts = new ArrayList<>();
        switch (kind) {
            case "user" -> {
                parts.add(rows("SELECT * FROM `user` WHERE user_id=?", id));
                parts.add(rows("SELECT * FROM user_profile WHERE user_id=?", id));
                parts.add(rows("SELECT * FROM profile_survey WHERE user_id=?", id));
                parts.add(rows("SELECT o.* FROM profile_survey_option o JOIN profile_survey s ON s.profile_survey_id=o.profile_survey_id WHERE s.user_id=? ORDER BY o.profile_survey_option_id", id));
                parts.add(rows("SELECT * FROM roommate_preference WHERE user_id=?", id));
                parts.add(rows("SELECT o.* FROM roommate_preference_option o JOIN roommate_preference p ON p.preference_id=o.preference_id WHERE p.user_id=? ORDER BY o.preference_option_id", id));
                parts.add(rows("SELECT c.* FROM preference_criterion c JOIN roommate_preference p ON p.preference_id=c.preference_id WHERE p.user_id=? ORDER BY c.preference_criterion_id", id));
            }
            case "post" -> {
                // Views and updated_at can change through legitimate read-only-looking detail requests.
                parts.add(rows("SELECT post_id,author_id,dormitory_id,category,title,content,anonymous,status,created_at FROM community_post WHERE post_id=?", id));
                parts.add(rows("SELECT * FROM community_comment WHERE post_id=? ORDER BY comment_id", id));
                parts.add(rows("SELECT * FROM post_like WHERE post_id=? ORDER BY post_like_id", id));
                parts.add(rows("SELECT l.* FROM comment_like l JOIN community_comment c ON c.comment_id=l.comment_id WHERE c.post_id=? ORDER BY l.comment_like_id", id));
                parts.add(rows("SELECT * FROM post_image WHERE post_id=? ORDER BY post_image_id", id));
                parts.add(rows("SELECT * FROM community_anonymous_author WHERE post_id=? ORDER BY anonymous_author_id", id));
                // Reports/chats are separately tracked and are not changed by post updates.
            }
            case "chat" -> {
                parts.add(rows("SELECT * FROM chat_room WHERE chat_room_id=?", id));
                parts.add(rows("SELECT * FROM chat_room_member WHERE chat_room_id=? ORDER BY chat_room_member_id", id));
                parts.add(rows("SELECT * FROM chat_message WHERE chat_room_id=? ORDER BY message_id", id));
            }
            case "category" -> {
                parts.add(rows("SELECT * FROM calendar_category WHERE category_id=?", id));
                parts.add(rows("SELECT * FROM calendar_category_setting WHERE category_id=? ORDER BY category_setting_id", id));
            }
            case "event" -> parts.add(rows("SELECT * FROM calendar_event WHERE event_id=?", id));
            case "report" -> parts.add(rows("SELECT * FROM report WHERE report_id=?", id));
            default -> throw new IllegalArgumentException("Unknown exhibition kind");
        }
        return ExhibitionJournal.hash(parts);
    }
}
