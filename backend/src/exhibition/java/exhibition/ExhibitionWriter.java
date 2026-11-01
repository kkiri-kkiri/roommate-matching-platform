package exhibition;

import java.time.*;
import java.sql.Timestamp;
import java.util.*;
import jakarta.persistence.EntityManager;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import com.kkirikkiri.auth.dto.SignupRequest;
import com.kkirikkiri.user.entity.*;
import com.kkirikkiri.user.repository.*;
import com.kkirikkiri.user.service.UserService;
import com.kkirikkiri.user.dto.UserUpdateRequest;
import com.kkirikkiri.survey.service.*;
import com.kkirikkiri.community.post.service.CommunityPostService;
import com.kkirikkiri.community.post.repository.CommunityPostRepository;
import com.kkirikkiri.community.post.dto.*;
import com.kkirikkiri.community.comment.service.CommunityCommentService;
import com.kkirikkiri.community.comment.dto.*;
import com.kkirikkiri.chat.service.ChatService;
import com.kkirikkiri.chat.dto.*;
import com.kkirikkiri.chat.entity.ChatRoomCategory;
import com.kkirikkiri.calendar.service.CalendarService;
import com.kkirikkiri.calendar.dto.*;
import com.kkirikkiri.matching.service.MatchingRefreshService;
import com.kkirikkiri.report.service.ReportService;
import com.kkirikkiri.report.dto.ReportCreateRequest;
import com.kkirikkiri.admin.service.AdminService;
import com.kkirikkiri.admin.dto.AdminReportProcessRequest;
import com.kkirikkiri.global.init.SeedUserManifest;
import static exhibition.ExhibitionPlanner.Action.*;

/** Opt-in orchestration of actual services. Not scanned or packaged by the application. */
@RequiredArgsConstructor
public class ExhibitionWriter {
    private final PlatformTransactionManager manager;
    private final JdbcTemplate jdbc;
    private final EntityManager em;
    private final Validator validator;
    private final PasswordEncoder encoder;
    private final UserRepository users;
    private final UserProfileRepository profiles;
    private final DormitoryRepository dormitories;
    private final FacultyRepository faculties;
    private final MajorRepository majors;
    private final CommunityPostRepository postRepository;
    private final UserService userService;
    private final ProfileSurveyService surveys;
    private final RoommatePreferenceService preferences;
    private final CommunityPostService posts;
    private final CommunityCommentService comments;
    private final ChatService chats;
    private final CalendarService calendar;
    private final MatchingRefreshService matching;
    private final ReportService reports;
    private final AdminService admin;

    public ExhibitionJournal.State apply(ExhibitionJournal journal, ExhibitionJournal.State state,
            ExhibitionPlanner planner, ExhibitionPlanner.Plan reviewed, String expectedToken,
            SeedUserManifest legacy, String newPassword) throws java.io.IOException {
        if (reviewed.textOnly()) return new ExhibitionTextRefresh(jdbc, manager, validator)
                .apply(journal, state, planner, reviewed, expectedToken, legacy, users);
        var current = planner.preview(state, LocalDate.parse(reviewed.anchor()), reviewed.reports());
        if (!current.token().equals(expectedToken) || !current.token().equals(reviewed.token()))
            throw new IllegalStateException("Preview is stale; obtain and review a new plan");
        if (current.entries().stream().noneMatch(ExhibitionPlanner.Entry::changes)) return state;
        // Persist before the first DB mutation. A partial commit can never silently become a duplicate retry.
        journal.write(new ExhibitionJournal.State(state.database(), state.anchor(), "APPLYING", state.owned()));
        Map<String, ExhibitionJournal.Owned> owned = new TreeMap<>(state.owned());
        ExhibitionDatabase db = new ExhibitionDatabase(jdbc);
        LocalDate anchor = LocalDate.parse(state.anchor());
        transaction().executeWithoutResult(status -> {
            // Account -> post -> comments matches the public service lock order.
            current.entries().stream().filter(e -> e.usable() && e.kind().equals("user") && e.id() != null)
                    .map(ExhibitionPlanner.Entry::id).sorted().forEach(id -> users.findByIdForAccountUpdate(id).orElseThrow());
            current.entries().stream().filter(e -> e.usable() && e.kind().equals("post") && e.id() != null)
                    .map(ExhibitionPlanner.Entry::id).sorted().forEach(id -> {
                        postRepository.findByIdForUpdate(id).orElseThrow();
                        jdbc.queryForList("SELECT comment_id FROM community_comment WHERE post_id=? ORDER BY comment_id FOR UPDATE", id);
                    });
            // Recheck after locks, not just before acquiring them.
            if (!planner.preview(state, anchor, current.reports()).token().equals(expectedToken))
                throw new IllegalStateException("Targets changed while acquiring locks; no core writes committed");
            for (var s : studentList(planner.students(), current.reports())) {
                var entry = current.entry(s.key());
                if (!entry.changes()) continue;
                long uid = writeStudent(s, entry, legacy, newPassword, anchor);
                owned.put(s.key(), marker(entry, uid, uid, Map.of()));
            }
            for (var p : ExhibitionCatalog.posts()) {
                var entry = current.entry(p.key());
                if (entry.changes()) writePost(p, entry, current, owned, anchor);
            }
            for (int slot : List.of(1, 7, 19)) {
                var entry = current.entry("category/" + slot);
                if (entry.action() == CREATE) {
                    long owner = uid(owned, slot);
                    long id = calendar.createCategory(owner, new CalendarCategoryCreateRequest("기숙사 생활", "#4f7897")).id();
                    owned.put(entry.key(), marker(entry, id, owner, Map.of()));
                }
            }
            for (var event : ExhibitionCatalog.events()) {
                var entry = current.entry(event.key());
                if (!entry.changes()) continue;
                long owner = uid(owned, event.slot()), category = owned.get("category/" + event.slot()).id();
                LocalDate day = anchor.plusDays(event.dayOffset());
                long id;
                if (entry.action() == CREATE) id = calendar.createEvent(owner, new CalendarEventCreateRequest(
                        event.title(), day, day, event.start(), event.end(), category, event.description())).id();
                else {
                    id = entry.id();
                    calendar.updateEvent(owner, id, new CalendarEventUpdateRequest(event.title(), day, day,
                            event.start(), event.end(), category, event.description()));
                }
                owned.put(entry.key(), marker(entry, id, owner, Map.of()));
            }
            em.flush();
            for (var entry : current.entries()) if (entry.usable() && owned.containsKey(entry.key())
                    && Set.of("user", "post", "category", "event").contains(entry.kind()))
                capture(owned, entry.key(), db);
        });
        // Chat creation intentionally uses the existing service's independent transaction.
        // No outer account locks are held while entering REQUIRES_NEW.
        for (var conversation : ExhibitionCatalog.conversations()) {
            var entry = current.entry(conversation.key());
            if (entry.action() == CREATE) writeConversation(conversation, entry, owned, anchor);
        }
        if (current.reports()) transaction().executeWithoutResult(status -> {
            for (int i = 1; i <= 3; i++) {
                var entry = current.entry("report/" + i);
                if (entry.action() != CREATE) continue;
                long reporter = uid(owned, i + 3), target = owned.get("post/자유게시판/" + i).id();
                long rid = reports.createReport(reporter, new ReportCreateRequest("POST", target, "기타",
                        List.of("게시판 분류 확인 요청입니다. 가상의 전시 시나리오이며 실제 위반 내용은 없습니다.",
                                "동일 내용 여부를 확인하는 가상의 신고 사례입니다. 실제 이용자 조치는 하지 않습니다.",
                                "안내 문구를 오해한 가상 신고입니다. 검토 후 반려하는 흐름을 보여 줍니다.").get(i - 1)));
                if (i > 1) admin.processReport(owned.get(ExhibitionCatalog.MODERATOR_KEY).id(), rid, new AdminReportProcessRequest(i == 2 ? "COMPLETE" : "REJECT"));
                owned.put(entry.key(), marker(entry, rid, reporter, Map.of()));
                em.flush();
                capture(owned, entry.key(), db);
            }
        });
        if (current.entries().stream().anyMatch(e -> e.kind().equals("user") && e.changes())) {
            List<Long> demoIds = planner.students().stream().filter(s -> current.entry(s.key()).usable())
                    .map(s -> uid(owned, s.slot())).sorted().toList();
            // Never refreshIncoming/outgoing: those would also touch real-user pairs.
            for (Long id : demoIds) matching.refreshTargets(id, demoIds.stream().filter(other -> !other.equals(id)).toList());
        }
        // Compare with the snapshots taken while the relevant stage locks were held. Never adopt
        // an unexpected live-server edit that arrived during another stage's independent commit.
        for (var entry : current.entries()) if (entry.usable() && owned.containsKey(entry.key())) {
            var item = owned.get(entry.key());
            if (!item.snapshot().equals(db.snapshot(item.kind(), item.id())))
                throw new IllegalStateException("Rows changed during exhibition application; incomplete journal retained for recovery");
        }
        var completed = new ExhibitionJournal.State(state.database(), state.anchor(), "COMPLETE", owned);
        journal.write(completed);
        return completed;
    }

    long writeStudent(ExhibitionCatalog.Student s, ExhibitionPlanner.Entry entry,
            SeedUserManifest legacy, String newPassword, LocalDate anchor) {
        long id;
        if (entry.action() == CREATE) {
            String password = newPassword == null ? UUID.randomUUID() + "!Aa1" : newPassword;
            var signup = new SignupRequest(password, password, s.nickname(), s.email(), "fictional-offline-seed",
                    s.gender(), s.dormitory(), s.faculty(), s.major(), s.birthYear(), s.admissionYear(), s.grade(),
                    s.mbti().substring(0, 1), s.mbti().substring(1, 2), s.mbti().substring(2, 3), s.mbti().substring(3, 4));
            if (!validator.validate(signup).isEmpty()) throw new IllegalStateException("Fictional signup violates current DTO constraints");
            var dorm = dormitories.findByDormitoryName(s.dormitory()).filter(Dormitory::isActive).orElseThrow();
            var faculty = faculties.findByFacultyName(s.faculty()).filter(Faculty::isActive).orElseThrow();
            var major = majors.findByFaculty_FacultyIdAndMajorName(faculty.getFacultyId(), s.major()).filter(Major::isActive).orElseThrow();
            User user = new User(encoder.encode(password), s.email(), s.moderator() ? UserRole.ADMIN : UserRole.USER);
            user.verifyEmail(); users.saveAndFlush(user); id = user.getUserId();
            profiles.saveAndFlush(new UserProfile(user, s.nickname(), s.gender(), dorm, faculty, major,
                    s.birthYear(), s.admissionYear(), s.grade(), s.mbti()));
            // The optional moderator must never become eligible for the old DELETE seed selector.
            if (!s.moderator()) legacy.registerAfterCommit(user);
        } else {
            id = entry.id();
            userService.updateMyInfo(id, new UserUpdateRequest(s.nickname(), s.dormitory(), s.grade(), s.faculty(), s.major(),
                    s.mbti().substring(0, 1), s.mbti().substring(1, 2), s.mbti().substring(2, 3), s.mbti().substring(3, 4)));
        }
        if (!s.moderator()) {
            surveys.saveOrUpdateMyProfileSurvey(id, s.survey());
            preferences.saveOrUpdateMyRoommatePreference(id, s.preference());
        }
        if (entry.action() == CREATE) {
            em.flush();
            LocalDateTime joined = earlier(anchor.minusDays(60 - s.slot()).atTime(14, s.slot() % 60), LocalDateTime.now().minusHours(1));
            jdbc.update("UPDATE `user` SET created_at=?,updated_at=? WHERE user_id=?", Timestamp.valueOf(joined), Timestamp.valueOf(joined), id);
            jdbc.update("UPDATE user_profile SET created_at=?,updated_at=? WHERE user_id=?", Timestamp.valueOf(joined), Timestamp.valueOf(joined), id);
            for (String table : List.of("profile_survey", "roommate_preference"))
                jdbc.update("UPDATE " + table + " SET created_at=?,updated_at=? WHERE user_id=?", Timestamp.valueOf(joined.plusMinutes(20)), Timestamp.valueOf(joined.plusMinutes(20)), id);
            em.clear();
        }
        return id;
    }
    private void writePost(ExhibitionCatalog.Post p, ExhibitionPlanner.Entry entry, ExhibitionPlanner.Plan plan,
            Map<String, ExhibitionJournal.Owned> owned, LocalDate anchor) {
        long owner = uid(owned, p.slot());
        long id;
        var old = owned.get(p.key());
        Map<String, Long> children = new TreeMap<>(old == null ? Map.of() : old.children());
        boolean fresh = entry.action() == CREATE;
        if (fresh) id = posts.createPost(owner, new CommunityPostCreateRequest(p.category(), p.title(), p.content(), p.anonymous()), null);
        else {
            id = entry.id();
            // Never change anonymity, IDs, image references, or category of a historical slot.
            posts.updatePost(owner, id, new CommunityPostUpdateRequest(p.category(), p.title(), p.content(), List.of(), null), null);
        }
        em.flush();
        if (fresh) {
            int board = List.of("자유게시판", "나눔게시판", "택배게시판", "민원게시판").indexOf(p.category());
            LocalDateTime joined = jdbc.queryForObject("SELECT created_at FROM `user` WHERE user_id=?", LocalDateTime.class, owner);
            LocalDateTime planned = anchor.minusDays(22 - p.slot()).atTime(9 + board, p.slot() % 6 * 7);
            backdate("community_post", "post_id", id, later(joined, earlier(planned, LocalDateTime.now())));
        }
        long commenter = uid(owned, commenterSlot(p.slot()));
        if (children.isEmpty()) {
            long parent = comments.createComment(commenter, id, new CommunityCommentCreateRequest(p.comment(), p.slot() % 2 == 1));
            long reply = comments.createReply(owner, parent, new CommunityCommentCreateRequest(p.reply(), p.slot() % 2 == 1));
            children.put("comment", parent); children.put("reply", reply);
            em.flush();
            LocalDateTime created = jdbc.queryForObject("SELECT created_at FROM community_post WHERE post_id=?", LocalDateTime.class, id);
            LocalDateTime commenterJoined = jdbc.queryForObject("SELECT created_at FROM `user` WHERE user_id=?", LocalDateTime.class, commenter);
            LocalDateTime parentTime = later(later(created, commenterJoined), earlier(created.plusHours(2).plusMinutes(p.slot()), LocalDateTime.now()));
            backdate("community_comment", "comment_id", parent, parentTime);
            backdate("community_comment", "comment_id", reply, later(parentTime, earlier(created.plusHours(3).plusMinutes(p.slot()), LocalDateTime.now())));
            for (int j = 0; j < 1 + p.slot() % 4; j++) posts.likePost(uid(owned, likerSlot(p.slot(), j)), id);
            comments.likeComment(uid(owned, likerSlot(p.slot(), 4)), parent);
        } else {
            comments.updateComment(commenter, children.get("comment"), new CommunityCommentUpdateRequest(p.comment()));
            comments.updateComment(owner, children.get("reply"), new CommunityCommentUpdateRequest(p.reply()));
        }
        owned.put(p.key(), marker(entry, id, owner, children));
    }
    private void writeConversation(ExhibitionCatalog.Conversation c, ExhibitionPlanner.Entry entry,
            Map<String, ExhibitionJournal.Owned> owned, LocalDate anchor) {
        long caller = uid(owned, c.caller()), target = uid(owned, c.target());
        var source = c.sourcePostKey() == null ? null : owned.get(c.sourcePostKey());
        // Recheck immediately before createOrGetRoom: never append to an unowned existing room.
        if (new ExhibitionDatabase(jdbc).count("SELECT COUNT(*) FROM chat_room r JOIN chat_room_member a ON a.chat_room_id=r.chat_room_id JOIN chat_room_member b ON b.chat_room_id=r.chat_room_id WHERE a.user_id=? AND b.user_id=? AND r.category=?",
                caller, target, source == null ? "ROOMMATE" : "COMMUNITY") > 0)
            throw new IllegalStateException("Unowned conversation appeared after preview; stop without appending");
        long room = chats.createNewRoom(caller, new ChatRoomCreateRequest(source == null ? target : null,
                source == null ? ChatRoomCategory.ROOMMATE : ChatRoomCategory.COMMUNITY,
                source == null ? null : source.id(), c.fromComment() ? source.children().get("comment") : null,
                source == null ? null : c.fromComment() ? "COMMENT" : "POST")).id();
        transaction().executeWithoutResult(status -> {
            List<Long> callerMessages = new ArrayList<>(), targetMessages = new ArrayList<>();
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime joined = jdbc.queryForObject("SELECT MAX(created_at) FROM `user` WHERE user_id IN (?,?)", LocalDateTime.class, caller, target);
            if (source != null) {
                LocalDateTime sourceTime = c.fromComment()
                    ? jdbc.queryForObject("SELECT created_at FROM community_comment WHERE comment_id=?", LocalDateTime.class, source.children().get("comment"))
                    : jdbc.queryForObject("SELECT created_at FROM community_post WHERE post_id=?", LocalDateTime.class, source.id());
                joined = later(joined, sourceTime);
            }
            LocalDateTime start = later(joined, earlier(anchor.minusDays(2).atTime(18, 10 + c.caller()), now));
            long step = Math.min(240, Math.max(0, Duration.between(start, now).getSeconds() / (c.messages().size() - 1)));
            for (int i = 0; i < c.messages().size(); i++) {
                long sender = i % 2 == 0 ? caller : target;
                long message = chats.sendMessage(sender, room, new ChatMessageSendRequest(c.messages().get(i), UUID.randomUUID().toString())).id();
                em.flush();
                jdbc.update("UPDATE chat_message SET sent_at=? WHERE message_id=? AND chat_room_id=? AND sender_id=?",
                        Timestamp.valueOf(start.plusSeconds(i * step)), message, room, sender);
                em.clear();
                (sender == caller ? callerMessages : targetMessages).add(message);
            }
            if (!targetMessages.isEmpty()) chats.markRead(caller, room, new ChatReadRequest(targetMessages));
            // Leave the last outgoing message unread in half the rooms.
            int read = c.caller() % 2 == 0 ? callerMessages.size() : Math.max(0, callerMessages.size() - 1);
            if (read > 0) chats.markRead(target, room, new ChatReadRequest(callerMessages.subList(0, read)));
            em.flush();
            jdbc.update("UPDATE chat_room SET created_at=? WHERE chat_room_id=?", Timestamp.valueOf(start), room);
            jdbc.update("UPDATE chat_room_member SET joined_at=? WHERE chat_room_id=?", Timestamp.valueOf(start), room);
            em.clear();
            owned.put(entry.key(), marker(entry, room, caller, Map.of()));
            capture(owned, entry.key(), new ExhibitionDatabase(jdbc));
        });
    }
    private void backdate(String table, String key, long id, LocalDateTime time) {
        // Fixed allowlist, and only called for newly created rows within their creation transaction.
        if (!Set.of("community_post/post_id", "community_comment/comment_id").contains(table + "/" + key))
            throw new IllegalArgumentException("Unsupported exhibition timestamp target");
        jdbc.update("UPDATE " + table + " SET created_at=?, updated_at=? WHERE " + key + "=?",
                Timestamp.valueOf(time), Timestamp.valueOf(time), id);
        em.clear();
    }
    private TransactionTemplate transaction() {
        var tx = new TransactionTemplate(manager);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return tx;
    }
    private static LocalDateTime earlier(LocalDateTime a, LocalDateTime b) { return a.isBefore(b) ? a : b; }
    private static LocalDateTime later(LocalDateTime a, LocalDateTime b) { return a.isAfter(b) ? a : b; }
    private static ExhibitionJournal.Owned marker(ExhibitionPlanner.Entry e, long id, long owner, Map<String, Long> children) {
        return new ExhibitionJournal.Owned(e.kind(), id, owner, e.desired(), "pending", children);
    }
    private static void capture(Map<String, ExhibitionJournal.Owned> owned, String key, ExhibitionDatabase db) {
        var item = owned.get(key);
        owned.put(key, new ExhibitionJournal.Owned(item.kind(), item.id(), item.owner(), item.desired(),
                db.snapshot(item.kind(), item.id()), item.children()));
    }
    private static long uid(Map<String, ExhibitionJournal.Owned> owned, int slot) { return owned.get("user/" + slot).id(); }
    static int commenterSlot(int slot) { return (slot + 1) % 30 + 1; }
    static int likerSlot(int slot, int offset) { return (slot + 3 + offset) % 30 + 1; }
    private static List<ExhibitionCatalog.Student> studentList(List<ExhibitionCatalog.Student> students, boolean moderator) {
        List<ExhibitionCatalog.Student> result = new ArrayList<>(students);
        if (moderator) result.add(ExhibitionCatalog.moderator());
        return result;
    }
}
