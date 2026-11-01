package exhibition;

import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import jakarta.validation.Validator;
import com.kkirikkiri.global.init.SeedUserManifest;
import com.kkirikkiri.user.repository.UserRepository;
import com.kkirikkiri.user.dto.UserUpdateRequest;
import com.kkirikkiri.community.post.dto.CommunityPostCreateRequest;
import com.kkirikkiri.community.comment.dto.CommunityCommentCreateRequest;
import com.kkirikkiri.chat.dto.ChatMessageSendRequest;

/** Offline maintenance only: exact provenance, locked recheck, guarded UPDATEs, one DB transaction. */
final class ExhibitionTextRefresh {
    private final JdbcTemplate jdbc;
    private final ExhibitionDatabase db;
    private final PlatformTransactionManager manager;
    private final Validator validator;
    ExhibitionTextRefresh(JdbcTemplate jdbc, PlatformTransactionManager manager, Validator validator) {
        this.jdbc = jdbc; this.db = new ExhibitionDatabase(jdbc); this.manager = manager; this.validator = validator;
    }

    ExhibitionJournal.State apply(ExhibitionJournal journal, ExhibitionJournal.State state,
            ExhibitionPlanner planner, ExhibitionPlanner.Plan reviewed, String expected,
            SeedUserManifest legacy, UserRepository users) throws java.io.IOException {
        validateCopy(validator);
        var current = planner.preview(state, LocalDate.parse(reviewed.anchor()), reviewed.reports(), true);
        if (!current.token().equals(expected) || !reviewed.token().equals(expected))
            throw new IllegalStateException("Preview is stale; review a new text refresh plan");
        if (current.entries().stream().noneMatch(ExhibitionPlanner.Entry::changes)) return state;
        journal.write(new ExhibitionJournal.State(state.database(), state.anchor(), "APPLYING", state.owned()));
        var tx = new TransactionTemplate(manager);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        ExhibitionJournal.State completed = tx.execute(status -> {
            // Lock accounts, posts/comments and chats/receipts; recheck all recorded snapshots below.
            for (var e : current.entries().stream().filter(ExhibitionPlanner.Entry::usable).sorted(Comparator.comparing(ExhibitionPlanner.Entry::key)).toList()) {
                switch (e.kind()) {
                    case "user" -> {
                        jdbc.queryForList("SELECT user_id FROM `user` WHERE user_id=? FOR UPDATE", e.id());
                        jdbc.queryForList("SELECT user_id FROM user_profile WHERE user_id=? FOR UPDATE", e.id());
                        if (e.changes() && !legacy.contains(users.findById(e.id()).orElseThrow()))
                            throw new IllegalStateException("Original seed account provenance missing");
                    }
                    case "post" -> {
                        jdbc.queryForList("SELECT post_id FROM community_post WHERE post_id=? FOR UPDATE", e.id());
                        jdbc.queryForList("SELECT comment_id FROM community_comment WHERE post_id=? ORDER BY comment_id FOR UPDATE", e.id());
                    }
                    case "chat" -> {
                        jdbc.queryForList("SELECT chat_room_id FROM chat_room WHERE chat_room_id=? FOR UPDATE", e.id());
                        jdbc.queryForList("SELECT chat_room_member_id FROM chat_room_member WHERE chat_room_id=? ORDER BY chat_room_member_id FOR UPDATE", e.id());
                        jdbc.queryForList("SELECT message_id FROM chat_message WHERE chat_room_id=? ORDER BY message_id FOR UPDATE", e.id());
                    }
                    default -> { /* unchanged calendar and report entries are still bound into the preview token */ }
                }
            }
            if (!planner.preview(state, LocalDate.parse(reviewed.anchor()), reviewed.reports(), true).token().equals(expected))
                throw new IllegalStateException("Targets changed while acquiring locks");
            String protectedBefore = protectedSnapshot(db);
            var owned = new TreeMap<>(state.owned());
            for (var s : ExhibitionCatalog.students()) {
                var e = current.entry(s.key());
                if (!e.changes()) continue;
                var row = db.one("SELECT nickname FROM user_profile WHERE user_id=?", e.id());
                update("UPDATE user_profile SET nickname=? WHERE user_id=? AND nickname=?", s.nickname(), e.id(), row.get("nickname"));
            }
            for (var p : ExhibitionCatalog.posts()) {
                var e = current.entry(p.key());
                if (!e.changes()) continue;
                var old = state.owned().get(p.key());
                var row = db.one("SELECT title,content FROM community_post WHERE post_id=?", e.id());
                update("UPDATE community_post SET title=?,content=? WHERE post_id=? AND author_id=? AND title=? AND content=?",
                        p.title(), p.content(), e.id(), e.owner(), row.get("title"), row.get("content"));
                if (!old.children().keySet().equals(Set.of("comment", "reply"))) throw new IllegalStateException("Unknown comment provenance");
                long commenter = state.owned().get("user/" + ExhibitionWriter.commenterSlot(p.slot())).id();
                updateComment(old.children().get("comment"), e.id(), commenter, null, p.comment());
                updateComment(old.children().get("reply"), e.id(), e.owner(), old.children().get("comment"), p.reply());
            }
            for (var c : ExhibitionCatalog.conversations()) {
                var e = current.entry(c.key());
                if (!e.changes()) continue;
                var messages = db.rows("SELECT message_id,sender_id,content FROM chat_message WHERE chat_room_id=? ORDER BY message_id", e.id());
                if (messages.size() != c.messages().size()) throw new IllegalStateException("Message count changed; never append or delete");
                for (int i = 0; i < messages.size(); i++) {
                    var row = messages.get(i);
                    long sender = state.owned().get("user/" + (i % 2 == 0 ? c.caller() : c.target())).id();
                    if (!Objects.equals(ExhibitionDatabase.id(row, "sender_id"), sender)) throw new IllegalStateException("Message sender changed");
                    update("UPDATE chat_message SET content=? WHERE message_id=? AND chat_room_id=? AND sender_id=? AND content=?",
                            c.messages().get(i), row.get("message_id"), e.id(), sender, row.get("content"));
                }
            }
            if (!protectedBefore.equals(protectedSnapshot(db))) throw new IllegalStateException("Non-text data changed; rolling back text refresh");
            for (var e : current.entries()) if (e.changes()) {
                var old = owned.get(e.key());
                owned.put(e.key(), new ExhibitionJournal.Owned(old.kind(), old.id(), old.owner(), e.desired(), db.snapshot(old.kind(), old.id()), old.children()));
            }
            System.out.println("Preserved non-text data SHA256=" + protectedBefore);
            return new ExhibitionJournal.State(state.database(), state.anchor(), "COMPLETE", owned);
        });
        journal.write(Objects.requireNonNull(completed));
        return completed;
    }

    private void updateComment(long id, long post, long author, Long parent, String text) {
        var row = db.one("SELECT content,parent_comment_id FROM community_comment WHERE comment_id=? AND post_id=? AND author_id=?", id, post, author);
        if (row.isEmpty() || !Objects.equals(ExhibitionDatabase.id(row, "parent_comment_id"), parent))
            throw new IllegalStateException("Comment author/parent changed");
        update("UPDATE community_comment SET content=? WHERE comment_id=? AND post_id=? AND author_id=? AND content=?", text, id, post, author, row.get("content"));
    }
    private void update(String sql, Object... args) {
        if (jdbc.update(sql, args) != 1) throw new IllegalStateException("Text target missing or concurrently changed");
    }
    static void validateCopy(Validator validator) {
        Set<String> names = new HashSet<>();
        for (var s : ExhibitionCatalog.students()) {
            if (!names.add(s.nickname())) throw new IllegalStateException("Duplicate exhibition nickname");
            validate(validator, new UserUpdateRequest(s.nickname(), null, null, null, null, null, null, null, null));
        }
        for (var p : ExhibitionCatalog.posts()) {
            validate(validator, new CommunityPostCreateRequest(p.category(), p.title(), p.content(), p.anonymous()));
            validate(validator, new CommunityCommentCreateRequest(p.comment()));
            validate(validator, new CommunityCommentCreateRequest(p.reply()));
        }
        for (var c : ExhibitionCatalog.conversations()) for (String text : c.messages()) validate(validator, new ChatMessageSendRequest(text, null));
    }
    private static void validate(Validator validator, Object dto) {
        if (!validator.validate(dto).isEmpty()) throw new IllegalStateException("Exhibition copy violates current DTO constraints");
    }
    // Full rows minus the only five permitted text columns. Includes IDs, timestamps, options and scores.
    static String protectedSnapshot(ExhibitionDatabase db) {
        Map<String, Set<String>> editable = Map.of("user_profile", Set.of("nickname"), "community_post", Set.of("title", "content"),
                "community_comment", Set.of("content"), "chat_message", Set.of("content"));
        List<Object> parts = new ArrayList<>();
        for (String table : List.of("user", "user_profile", "profile_survey", "profile_survey_option", "roommate_preference",
                "roommate_preference_option", "preference_criterion", "roommate_match", "community_post", "community_comment", "post_like",
                "comment_like", "post_image", "community_anonymous_author", "chat_room", "chat_room_member", "chat_message",
                "calendar_category", "calendar_category_setting", "calendar_event", "report")) {
            var rows = db.rows("SELECT * FROM `" + table + "` ORDER BY 1");
            List<Object> projected = new ArrayList<>();
            for (var row : rows) {
                var copy = new TreeMap<>(row);
                copy.keySet().removeIf(k -> editable.getOrDefault(table, Set.of()).contains(k.toLowerCase(Locale.ROOT)));
                projected.add(copy);
            }
            parts.add(List.of(table, projected));
        }
        return ExhibitionJournal.hash(parts);
    }
}
