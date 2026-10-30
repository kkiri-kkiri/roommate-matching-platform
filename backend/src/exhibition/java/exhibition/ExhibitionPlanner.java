package exhibition;

import java.time.LocalDate;
import java.util.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.kkirikkiri.global.init.*;
import com.kkirikkiri.user.repository.UserRepository;
import static exhibition.ExhibitionDatabase.id;

/** A preview runs SELECTs only. Every ambiguous, changed, inactive or unowned target is skipped. */
public final class ExhibitionPlanner {
    public enum Action { CREATE, UPDATE, KEEP, SKIP }
    public record Entry(String key, String kind, Action action, Long id, Long owner, String before,
            String desired, String reason) {
        public boolean usable() { return action != Action.SKIP; }
        public boolean changes() { return action == Action.CREATE || action == Action.UPDATE; }
    }
    public record Plan(String database, String anchor, boolean reports, boolean textOnly, List<Entry> entries, Map<String, Long> effects, String token) {
        public Entry entry(String key) { return entries.stream().filter(e -> e.key().equals(key)).findFirst().orElseThrow(); }
        public Map<String, Long> counts() {
            Map<String, Long> counts = new TreeMap<>();
            entries.forEach(e -> counts.merge(e.kind() + ":" + e.action(), 1L, Long::sum));
            return counts;
        }
    }
    private final ExhibitionDatabase db;
    private final UserRepository users;
    private final SeedUserManifest legacy;
    private final TransactionTemplate read;
    public ExhibitionPlanner(ExhibitionDatabase db, UserRepository users, SeedUserManifest legacy,
            PlatformTransactionManager manager) {
        this.db = db; this.users = users; this.legacy = legacy;
        read = new TransactionTemplate(manager); read.setReadOnly(true);
    }
    public Plan preview(ExhibitionJournal.State state, LocalDate anchor, boolean reports) {
        return preview(state, anchor, reports, false);
    }
    public Plan preview(ExhibitionJournal.State state, LocalDate anchor, boolean reports, boolean textOnly) {
        ExhibitionCatalog.validateAnchor(anchor);
        if (!"COMPLETE".equals(state.status()) || !anchor.toString().equals(state.anchor()))
            throw new IllegalStateException("Invalid exhibition journal");
        return read.execute(status -> build(state, anchor, reports, textOnly));
    }
    private Plan build(ExhibitionJournal.State state, LocalDate anchor, boolean reports, boolean textOnly) {
        List<Entry> entries = new ArrayList<>();
        List<ExhibitionCatalog.Student> students = new ArrayList<>(ExhibitionCatalog.students());
        if (reports) students.add(ExhibitionCatalog.moderator());
        for (var student : students) entries.add(user(student, state));
        if (textOnly) entries.replaceAll(e -> !state.owned().containsKey(e.key())
                ? new Entry(e.key(), e.kind(), Action.SKIP, e.id(), e.owner(), e.before(), e.desired(), "text refresh requires existing exhibition provenance")
                : e.kind().equals("user") && !e.key().equals("user/31") && e.id() != null
                && !legacy.contains(users.findById(e.id()).orElseThrow())
                ? new Entry(e.key(), e.kind(), Action.SKIP, e.id(), e.owner(), e.before(), e.desired(), "original seed provenance missing or changed") : e);
        for (var post : ExhibitionCatalog.posts()) {
            Entry owner = find(entries, "user/" + post.slot());
            Entry planned = post(post, owner, state);
            Set<Integer> actors = new HashSet<>();
            actors.add(ExhibitionWriter.commenterSlot(post.slot()));
            for (int j = 0; j < 1 + post.slot() % 4; j++) actors.add(ExhibitionWriter.likerSlot(post.slot(), j));
            actors.add(ExhibitionWriter.likerSlot(post.slot(), 4));
            if (actors.stream().anyMatch(slot -> !find(entries, "user/" + slot).usable()))
                planned = new Entry(planned.key(), planned.kind(), Action.SKIP, planned.id(), planned.owner(),
                        planned.before(), planned.desired(), "comment/like participant skipped");
            entries.add(planned);
        }
        for (var chat : ExhibitionCatalog.conversations()) {
            Entry caller = find(entries, "user/" + chat.caller());
            Entry target = find(entries, "user/" + chat.target());
            String reason = !caller.usable() || !target.usable() ? "participant skipped" : null;
            if (chat.sourcePostKey() != null && !find(entries, chat.sourcePostKey()).usable()) reason = "source post skipped";
            if (reason == null && !state.owned().containsKey(chat.key()) && caller.id() != null && target.id() != null
                    && db.count("SELECT COUNT(*) FROM chat_room r JOIN chat_room_member a ON a.chat_room_id=r.chat_room_id JOIN chat_room_member b ON b.chat_room_id=r.chat_room_id WHERE a.user_id=? AND b.user_id=? AND r.category=?",
                        caller.id(), target.id(), chat.sourcePostKey() == null ? "ROOMMATE" : "COMMUNITY") > 0)
                reason = "existing unowned conversation; never append messages";
            entries.add(ownedOnly(chat.key(), "chat", caller.id(), chat, reason, state, textOnly));
        }
        for (int slot : List.of(1, 7, 19)) {
            Entry owner = find(entries, "user/" + slot);
            String reason = owner.usable() ? null : "owner skipped";
            String key = "category/" + slot;
            if (reason == null && !state.owned().containsKey(key) && owner.id() != null
                    && db.count("SELECT COUNT(*) FROM calendar_category WHERE owner_user_id=? AND default_name=?", owner.id(), "기숙사 생활") > 0)
                reason = "existing unowned calendar category";
            entries.add(ownedOnly(key, "category", owner.id(), List.of(slot, "기숙사 생활", "#4f7897"), reason, state, false));
        }
        for (var event : ExhibitionCatalog.events()) {
            Entry owner = find(entries, "user/" + event.slot());
            Entry category = find(entries, "category/" + event.slot());
            entries.add(ownedOnly(event.key(), "event", owner.id(), List.of(event, anchor),
                    category.usable() ? null : "category skipped", state, true));
        }
        if (reports) for (int i = 1; i <= 3; i++) {
            Entry reporter = find(entries, "user/" + (i + 3));
            Entry moderator = find(entries, "user/31");
            Entry post = find(entries, "post/자유게시판/" + i);
            String reason = !reporter.usable() || !moderator.usable() || !post.usable() ? "report participant/target skipped" : null;
            if (reason == null && !state.owned().containsKey("report/" + i) && reporter.id() != null && post.id() != null
                    && db.count("SELECT COUNT(*) FROM report WHERE reporter_id=? AND post_id=?", reporter.id(), post.id()) > 0)
                reason = "existing unowned report";
            entries.add(ownedOnly("report/" + i, "report", reporter.id(), List.of(i, "fictional report", anchor), reason, state, false));
        }
        // Also bind retired/unselected ledger entries, so dropping --reports cannot erase provenance.
        Map<String, String> retained = new TreeMap<>();
        state.owned().forEach((key, owned) -> retained.put(key, db.snapshot(owned.kind(), owned.id())));
        if (textOnly) {
            entries.replaceAll(e -> !state.owned().containsKey(e.key())
                    ? new Entry(e.key(), e.kind(), Action.SKIP, e.id(), e.owner(), e.before(), e.desired(), "text refresh requires existing exhibition provenance") : e);
            // A skipped participant or source must also exclude its dependent conversation.
            for (var chat : ExhibitionCatalog.conversations()) {
                if (!find(entries, "user/" + chat.caller()).usable() || !find(entries, "user/" + chat.target()).usable()
                        || chat.sourcePostKey() != null && !find(entries, chat.sourcePostKey()).usable()) {
                    entries.replaceAll(e -> e.key().equals(chat.key()) ? new Entry(e.key(), e.kind(), Action.SKIP,
                            e.id(), e.owner(), e.before(), e.desired(), "text refresh participant/source skipped") : e);
                }
            }
        }
        Map<String, Long> effects = new TreeMap<>();
        if (textOnly) {
            for (var e : entries) if (e.changes()) switch (e.kind()) {
                case "user" -> effects.merge("user_profile.nickname.update", 1L, Long::sum);
                case "post" -> { effects.merge("community_post.text.update", 1L, Long::sum); effects.merge("community_comment.text.update", 2L, Long::sum); }
                case "chat" -> effects.merge("chat_message.text.update", (long)ExhibitionCatalog.conversations().stream().filter(c -> c.key().equals(e.key())).findFirst().orElseThrow().messages().size(), Long::sum);
                default -> throw new IllegalStateException("Non-text catalog change requires ordinary review: " + e.key());
            }
        } else {
        for (var student : students) if (student.slot() <= 30 && find(entries, student.key()).changes()) {
            effects.merge("profile_survey.upsert", 1L, Long::sum);
            effects.merge("roommate_preference.upsert", 1L, Long::sum);
            effects.merge("preference_criterion.replace", 18L, Long::sum);
            effects.merge("profile_survey_option.replace", (long)student.survey().dormStayDays().size() + student.survey().mealPlan().size(), Long::sum);
            effects.merge("roommate_preference_option.replace", (long)student.preference().age().size() + student.preference().grade().size()
                    + student.preference().bedtime().size() + student.preference().wakeTime().size() + student.preference().cleaningFrequency().size(), Long::sum);
        }
        for (var post : ExhibitionCatalog.posts()) if (find(entries, post.key()).changes()) {
            var previous = state.owned().get(post.key());
            if (previous == null) {
                effects.merge("community_comment.create", 2L, Long::sum);
                effects.merge("post_like.create", (long)(1 + post.slot() % 4), Long::sum);
                effects.merge("comment_like.create", 1L, Long::sum);
            } else effects.merge("community_comment.update", 2L, Long::sum);
        }
        for (var chat : ExhibitionCatalog.conversations()) if (find(entries, chat.key()).action() == Action.CREATE) {
            effects.merge("chat_room_member.create", 2L, Long::sum);
            effects.merge("chat_message.create", (long)chat.messages().size(), Long::sum);
        }
        }
        String token = ExhibitionJournal.hash(List.of(ExhibitionCatalog.VERSION, state, anchor.toString(), reports, textOnly, entries, retained, effects));
        return new Plan(state.database(), anchor.toString(), reports, textOnly, List.copyOf(entries), Collections.unmodifiableMap(effects), token);
    }
    private Entry user(ExhibitionCatalog.Student student, ExhibitionJournal.State state) {
        String key = student.key(), desired = ExhibitionJournal.hash(student);
        var row = db.one("SELECT user_id,role,status,email_verified FROM `user` WHERE school_email=?", student.email());
        Long uid = id(row, "user_id");
        String before = uid == null ? "absent" : db.snapshot("user", uid);
        String reason = null;
        if (!referencesExist(student)) reason = "missing or inactive dormitory/faculty/major";
        if (db.count("SELECT COUNT(*) FROM user_profile WHERE nickname=? AND (? IS NULL OR user_id<>?)", student.nickname(), uid, uid) > 0)
            reason = "nickname already used; never rename another account";
        if (uid != null && (!Objects.equals(row.get("role"), student.slot() == 31 ? "ADMIN" : "USER")
                || !Objects.equals(row.get("status"), "ACTIVE") || !truth(row.get("email_verified")))) reason = "account role/status/verification conflict";
        var owned = state.owned().get(key);
        if (owned != null && (uid == null || owned.id() != uid || !owned.snapshot().equals(before))) reason = "owned account changed or missing";
        if (uid != null && owned == null && (student.slot() == 31 || !legacy.contains(users.findById(uid).orElseThrow())))
            reason = "account provenance unavailable; email alone is insufficient";
        if (uid != null && db.count("SELECT COUNT(*) FROM user_profile WHERE user_id=?", uid) != 1) reason = "existing profile missing; do not repair unknown state";
        if (uid != null && owned == null && db.count("SELECT COUNT(*) FROM user_profile WHERE user_id=? AND profile_image_url IS NOT NULL AND TRIM(profile_image_url)<>''", uid) > 0)
            reason = "existing photo requires manual provenance review; never introduce a real student's photo";
        return new Entry(key, "user", reason != null ? Action.SKIP : uid == null ? Action.CREATE
                : owned != null && desired.equals(owned.desired()) ? Action.KEEP : Action.UPDATE,
                uid, uid, before, desired, reason == null && uid != null ? "preserve ID/email/password/gender/birth/admission" : reason);
    }
    private boolean referencesExist(ExhibitionCatalog.Student s) {
        return db.count("SELECT COUNT(*) FROM dormitory WHERE dormitory_name=? AND is_active=TRUE", s.dormitory()) == 1
            && db.count("SELECT COUNT(*) FROM faculty f JOIN major m ON m.faculty_id=f.faculty_id WHERE f.faculty_name=? AND m.major_name=? AND f.is_active=TRUE AND m.is_active=TRUE", s.faculty(), s.major()) == 1;
    }
    private Entry post(ExhibitionCatalog.Post post, Entry owner, ExhibitionJournal.State state) {
        var owned = state.owned().get(post.key());
        String desired = ExhibitionJournal.hash(post);
        Long pid = owned == null ? null : owned.id();
        String reason = owner.usable() ? null : "author skipped";
        if (owned != null) {
            var row = db.one("SELECT author_id,status FROM community_post WHERE post_id=?", pid);
            if (!Objects.equals(id(row, "author_id"), owner.id()) || !"ACTIVE".equals(row.get("status"))) reason = "post owner/status changed";
        } else if (owner.id() != null && owner.usable()) {
            var candidates = db.rows("SELECT post_id,title,content,anonymous,status FROM community_post WHERE author_id=? AND category=?", owner.id(), post.category());
            var signature = CommunitySeedDataService.legacyPosts().stream()
                    .filter(p -> p.index() == post.slot() && p.category().equals(post.category())).findFirst().orElseThrow();
            if (candidates.size() == 1 && signature.title().equals(candidates.getFirst().get("title"))
                    && signature.content().equals(candidates.getFirst().get("content"))
                    && !truth(candidates.getFirst().get("anonymous")) && "ACTIVE".equals(candidates.getFirst().get("status"))) {
                pid = id(candidates.getFirst(), "post_id");
                if (hasLegacyInteractions(pid)) reason = "legacy post has unowned interactions; preserve all references";
            } else if (!candidates.isEmpty()) reason = "ambiguous/edited/unowned legacy post; do not create a duplicate";
        }
        String before = pid == null ? "absent" : db.snapshot("post", pid);
        if (owned != null && !owned.snapshot().equals(before)) reason = "post or interactions changed since last application";
        return new Entry(post.key(), "post", reason != null ? Action.SKIP : pid == null ? Action.CREATE
                : owned != null && desired.equals(owned.desired()) ? Action.KEEP : Action.UPDATE,
                pid, owner.id(), before, desired, reason);
    }
    private boolean hasLegacyInteractions(long post) {
        return db.count("SELECT COUNT(*) FROM community_comment WHERE post_id=?", post) > 0
            || db.count("SELECT COUNT(*) FROM post_like WHERE post_id=?", post) > 0
            || db.count("SELECT COUNT(*) FROM post_image WHERE post_id=?", post) > 0
            || db.count("SELECT COUNT(*) FROM report WHERE post_id=?", post) > 0
            || db.count("SELECT COUNT(*) FROM chat_room WHERE source_post_id=?", post) > 0
            || db.count("SELECT COUNT(*) FROM community_anonymous_author WHERE post_id=?", post) > 0;
    }
    private Entry ownedOnly(String key, String kind, Long owner, Object desiredValue, String reason,
            ExhibitionJournal.State state, boolean editable) {
        var owned = state.owned().get(key);
        String desired = ExhibitionJournal.hash(desiredValue);
        String before = owned == null ? "absent" : db.snapshot(kind, owned.id());
        if (owned != null && (!Objects.equals(owner, owned.owner()) || !owned.snapshot().equals(before))) reason = "owned data changed; preserve current state";
        if (owned != null && !editable && !desired.equals(owned.desired())) reason = "append-only scenario changed; requires a separately reviewed new slot";
        return new Entry(key, kind, reason != null ? Action.SKIP : owned == null ? Action.CREATE
                : desired.equals(owned.desired()) ? Action.KEEP : Action.UPDATE,
                owned == null ? null : owned.id(), owner, before, desired, reason);
    }
    private static Entry find(List<Entry> entries, String key) { return entries.stream().filter(e -> e.key().equals(key)).findFirst().orElseThrow(); }
    static boolean truth(Object value) { return value != null && Set.of("true", "TRUE", "1").contains(value.toString()); }
}
