package exhibition;

import java.nio.file.*;
import java.time.*;
import java.util.*;
import jakarta.persistence.EntityManager;
import jakarta.validation.Validator;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.kkirikkiri.global.init.*;
import com.kkirikkiri.user.entity.*;
import com.kkirikkiri.user.repository.*;
import com.kkirikkiri.community.post.repository.CommunityPostRepository;
import com.kkirikkiri.community.post.service.CommunityPostService;
import com.kkirikkiri.community.comment.service.CommunityCommentService;
import com.kkirikkiri.community.comment.dto.CommunityCommentCreateRequest;
import com.kkirikkiri.community.post.dto.*;
import com.kkirikkiri.chat.service.ChatService;
import com.kkirikkiri.chat.dto.*;
import com.kkirikkiri.survey.service.*;
import com.kkirikkiri.survey.dto.ProfileSurveyRequest;
import com.kkirikkiri.calendar.service.CalendarService;
import com.kkirikkiri.calendar.dto.CalendarEventListRequest;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real services/repositories and H2 only; never starts the opt-in MySQL command. */
@SpringBootTest(classes = ExhibitionConfiguration.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ExhibitionDataIntegrationTest {
    private static final LocalDate ANCHOR = LocalDate.of(2026, 10, 30);
    @TempDir Path directory;
    @Autowired ConfigurableApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @Autowired EntityManager em;
    @Autowired ExhibitionWriter writer;
    @Autowired UserRepository users;
    @Autowired UserProfileRepository profiles;
    @Autowired DormitoryRepository dormitories;
    @Autowired FacultyRepository faculties;
    @Autowired MajorRepository majors;
    @Autowired CommunityPostRepository postRepository;
    @Autowired CommunityPostService posts;
    @Autowired CommunityCommentService comments;
    @Autowired ProfileSurveyService surveys;
    @MockitoSpyBean RoommatePreferenceService preferences;
    @MockitoSpyBean ChatService chats;
    @Autowired CalendarService calendar;
    @Autowired Validator validator;
    @Autowired com.kkirikkiri.matching.service.RoommateMatchingService matching;
    ExhibitionDatabase db;
    SeedUserManifest legacy;
    ExhibitionPlanner planner;
    @BeforeEach void prepareReferenceData() throws Exception {
        try (var c = Objects.requireNonNull(jdbc.getDataSource()).getConnection()) {
            assertTrue(c.getMetaData().getURL().startsWith("jdbc:h2:mem:"));
        }
        ExhibitionTool.assertIsolated(context);
        assertTrue(context.getBeansOfType(DevDataInitializer.class).isEmpty());
        assertTrue(context.getBeansOfType(SeedDataInitializer.class).isEmpty());
        new ResourceDatabasePopulator(new ClassPathResource("data-common.sql")).execute(jdbc.getDataSource());
        db = new ExhibitionDatabase(jdbc);
        legacy = new SeedUserManifest(directory.resolve("legacy.properties").toString());
        planner = new ExhibitionPlanner(db, users, legacy, manager);
    }
    private ExhibitionJournal.State empty() { return new ExhibitionJournal.State("h2-isolated", ANCHOR.toString(), "COMPLETE", Map.of()); }
    private ExhibitionJournal.State apply(ExhibitionJournal journal, ExhibitionJournal.State state, boolean reports) throws Exception {
        var plan = planner.preview(state, ANCHOR, reports);
        return writer.apply(journal, state, planner, plan, plan.token(), legacy, "Fictional123!");
    }
    private long count(String table) { return db.count("SELECT COUNT(*) FROM " + table); }

    @Test void textRefreshUpdatesOnlyTextRetainsEveryRelationshipAndRefusesManualOrStaleData() throws Exception {
        ExhibitionTextRefresh.validateCopy(validator);
        try (var journal = new ExhibitionJournal(directory.resolve("exhibition.json"))) {
            var initial = apply(journal, empty(), true);
            var previous = new TreeMap<>(initial.owned());
            for (var e : initial.owned().entrySet()) {
                var old = e.getValue();
                switch (old.kind()) {
                    case "user" -> {
                        if (e.getKey().equals("user/31")) continue;
                        jdbc.update("UPDATE user_profile SET nickname=? WHERE user_id=?", "이전이름" + old.id(), old.id());
                    }
                    case "post" -> {
                        jdbc.update("UPDATE community_post SET title=?,content=? WHERE post_id=?", "이전 제목", "이전 본문", old.id());
                        jdbc.update("UPDATE community_comment SET content=? WHERE post_id=?", "이전 댓글", old.id());
                    }
                    case "chat" -> jdbc.update("UPDATE chat_message SET content=? WHERE chat_room_id=?", "이전 메시지", old.id());
                    default -> { continue; }
                }
                previous.put(e.getKey(), new ExhibitionJournal.Owned(old.kind(), old.id(), old.owner(), "previous-reviewed-catalog", db.snapshot(old.kind(), old.id()), old.children()));
            }
            var oldState = new ExhibitionJournal.State(initial.database(), initial.anchor(), "COMPLETE", previous);
            journal.write(oldState);
            String protectedBefore = ExhibitionTextRefresh.protectedSnapshot(db);
            String originalManifest = Files.readString(directory.resolve("legacy.properties"));
            var plan = planner.preview(oldState, ANCHOR, true, true);
            assertEquals(30L, plan.counts().get("user:UPDATE"));
            assertEquals(80L, plan.counts().get("post:UPDATE"));
            assertEquals(8L, plan.counts().get("chat:UPDATE"));
            assertEquals(33L, plan.effects().get("chat_message.text.update"));
            assertTrue(plan.entries().stream().noneMatch(e -> e.action() == ExhibitionPlanner.Action.CREATE));
            var updated = writer.apply(journal, oldState, planner, plan, plan.token(), legacy, null);
            assertEquals(protectedBefore, ExhibitionTextRefresh.protectedSnapshot(db));
            assertEquals(originalManifest, Files.readString(directory.resolve("legacy.properties")));
            assertEquals(initial.owned().keySet(), updated.owned().keySet());
            initial.owned().forEach((key, old) -> {
                assertEquals(old.id(), updated.owned().get(key).id());
                assertEquals(old.children(), updated.owned().get(key).children());
            });
            var again = planner.preview(updated, ANCHOR, true, true);
            assertEquals(137L, again.counts().values().stream().mapToLong(Long::longValue).sum());
            assertTrue(again.entries().stream().allMatch(e -> e.action() == ExhibitionPlanner.Action.KEEP));
            var missingUser = new TreeMap<>(updated.owned());
            missingUser.remove("user/1");
            var missingPlan = planner.preview(new ExhibitionJournal.State(updated.database(), updated.anchor(), "COMPLETE", missingUser), ANCHOR, true, true);
            assertEquals(ExhibitionPlanner.Action.SKIP, missingPlan.entry("user/1").action());
            assertEquals(ExhibitionPlanner.Action.SKIP, missingPlan.entry("post/자유게시판/1").action());
            assertEquals(ExhibitionPlanner.Action.SKIP, missingPlan.entry("chat/morning").action());
            var noLegacy = new ExhibitionPlanner(db, users, new SeedUserManifest(directory.resolve("missing.properties").toString()), manager);
            assertEquals(30, noLegacy.preview(updated, ANCHOR, true, true).entries().stream()
                    .filter(e -> e.kind().equals("user") && e.action() == ExhibitionPlanner.Action.SKIP).count());
            String after = databaseSnapshot();
            assertEquals(updated, writer.apply(journal, updated, planner, again, again.token(), legacy, null));
            assertEquals(after, databaseSnapshot());
            long changedPost = updated.owned().get("post/자유게시판/1").id();
            jdbc.update("UPDATE community_post SET title='사용자가 고친 제목' WHERE post_id=?", changedPost);
            long changedRoom = updated.owned().get("chat/morning").id();
            jdbc.update("UPDATE chat_message SET content='수동 수정 대화' WHERE chat_room_id=?", changedRoom);
            var skip = planner.preview(updated, ANCHOR, true, true);
            assertEquals(ExhibitionPlanner.Action.SKIP, skip.entry("post/자유게시판/1").action());
            assertEquals(ExhibitionPlanner.Action.SKIP, skip.entry("chat/morning").action());
            assertEquals(ExhibitionPlanner.Action.SKIP, skip.entry("chat/anonymous-comment").action());
            assertThrows(IllegalStateException.class, () -> writer.apply(journal, updated, planner, again, again.token(), legacy, null));
            assertEquals("COMPLETE", journal.read(initial.database(), initial.anchor()).status());
            assertTrue(planner.preview(empty(), ANCHOR, true, true).entries().stream().allMatch(e -> e.action() == ExhibitionPlanner.Action.SKIP));
            var broken = new TreeMap<>(updated.owned());
            String brokenKey = "post/자유게시판/2";
            var brokenPost = broken.get(brokenKey);
            broken.put(brokenKey, new ExhibitionJournal.Owned(brokenPost.kind(), brokenPost.id(), brokenPost.owner(),
                    "old", brokenPost.snapshot(), Map.of("comment", brokenPost.children().get("comment"), "reply", -1L)));
            var brokenState = new ExhibitionJournal.State(updated.database(), updated.anchor(), "COMPLETE", broken);
            var brokenPlan = planner.preview(brokenState, ANCHOR, true, true);
            String rollbackBefore = databaseSnapshot();
            assertThrows(IllegalStateException.class, () -> writer.apply(journal, brokenState, planner, brokenPlan, brokenPlan.token(), legacy, null));
            assertEquals(rollbackBefore, databaseSnapshot(), "Partial text changes must roll back together");
            assertThrows(IllegalStateException.class, () -> journal.read(updated.database(), updated.anchor()));
        }
    }

    @Test void previewWritesNothingAndFullScenarioUsesValidServicesAndIsTrulyIdempotent() throws Exception {
        String before = databaseSnapshot();
        var preview = planner.preview(empty(), ANCHOR, true);
        assertEquals(before, databaseSnapshot());
        assertEquals(31L, preview.counts().get("user:CREATE"));
        assertEquals(80L, preview.counts().get("post:CREATE"));
        for (var student : ExhibitionCatalog.students()) {
            assertTrue(validator.validate(student.survey()).isEmpty());
            assertTrue(validator.validate(student.preference()).isEmpty());
        }
        try (var journal = new ExhibitionJournal(directory.resolve("exhibition.json"))) {
            var state = apply(journal, empty(), true);
            assertEquals(31, count("`user`")); assertEquals(31, count("user_profile"));
            assertEquals(30, count("profile_survey")); assertEquals(30, count("roommate_preference"));
            assertEquals(540, count("preference_criterion"));
            assertEquals(80, count("community_post")); assertEquals(160, count("community_comment"));
            assertEquals(200, count("post_like")); assertEquals(80, count("comment_like"));
            assertEquals(8, count("chat_room")); assertEquals(16, count("chat_room_member")); assertEquals(33, count("chat_message"));
            assertEquals(12, count("calendar_event")); assertEquals(3, count("report")); assertEquals(870, count("roommate_match"));
            assertEquals(15, db.count("SELECT COUNT(*) FROM user_profile p JOIN `user` u ON u.user_id=p.user_id WHERE p.gender='여자' AND u.role='USER'"));
            long viewer = state.owned().get("user/1").id();
            assertTrue(surveys.getMyProfileSurvey(viewer).completed());
            assertTrue(preferences.getMyRoommatePreference(viewer).completed());
            var page = posts.getPosts(viewer, new CommunityPostListRequest(0, 10, null, null, "LATEST"));
            assertFalse(page.content().isEmpty());
            var detail = posts.getPost(viewer, state.owned().get("post/자유게시판/1").id());
            assertEquals(2, detail.comments().size());
            assertTrue(detail.comments().stream().allMatch(c -> c.author().id() == null && c.anonymousNumber() != null));
            assertEquals(2, detail.comments().stream().map(c -> c.anonymousNumber()).distinct().count());
            long anonRoom = state.owned().get("chat/anonymous-comment").id();
            long anonCaller = state.owned().get("user/4").id();
            assertNull(chats.getRoom(anonCaller, anonRoom).counterpart().id());
            var messages = chats.getMessages(anonCaller, anonRoom, new ChatMessageListRequest(0, 50, null));
            assertEquals(4, messages.content().size());
            assertTrue(db.count("SELECT COUNT(*) FROM chat_message WHERE is_read=FALSE") > 0);
            assertTrue(db.count("SELECT COUNT(*) FROM chat_message WHERE is_read=TRUE") > 0);
            assertEquals(0, db.count("SELECT COUNT(*) FROM chat_message m JOIN chat_room r ON r.chat_room_id=m.chat_room_id WHERE m.sent_at<r.created_at"));
            assertEquals(1, db.count("SELECT COUNT(*) FROM report WHERE status='PENDING'"));
            assertEquals(1, db.count("SELECT COUNT(*) FROM report WHERE status='COMPLETED' AND resolution_action='NONE'"));
            assertEquals(1, db.count("SELECT COUNT(*) FROM report WHERE status='REJECTED' AND resolution_action='NONE'"));
            assertTrue(db.count("SELECT COUNT(*) FROM roommate_match WHERE requester_user_id=? AND match_score>=90", viewer) > 0);
            assertTrue(db.count("SELECT COUNT(*) FROM roommate_match WHERE requester_user_id=? AND match_score<60", viewer) > 0);
            assertFalse(matching.getRecommendedRoommates(viewer, org.springframework.data.domain.PageRequest.of(0, 9)).content().isEmpty());
            assertNotNull(matching.getRoommateDetail(viewer, state.owned().get("user/2").id()));
            assertEquals(0, db.count("SELECT COUNT(*) FROM community_post p JOIN `user` u ON u.user_id=p.author_id WHERE p.created_at<u.created_at"));
            assertEquals(0, db.count("SELECT COUNT(*) FROM community_comment c JOIN community_post p ON p.post_id=c.post_id JOIN `user` u ON u.user_id=c.author_id WHERE c.created_at<p.created_at OR c.created_at<u.created_at"));
            assertEquals(0, db.count("SELECT COUNT(*) FROM chat_message m JOIN `user` u ON u.user_id=m.sender_id WHERE m.sent_at<u.created_at"));
            assertEquals(0, db.count("SELECT COUNT(*) FROM `user` WHERE status<>'ACTIVE'"));
            assertEquals(12, calendar.getEvents(viewer, new CalendarEventListRequest(ANCHOR, ANCHOR.plusDays(10), null)).size()
                    + calendar.getEvents(state.owned().get("user/7").id(), new CalendarEventListRequest(ANCHOR, ANCHOR.plusDays(10), null)).size()
                    + calendar.getEvents(state.owned().get("user/19").id(), new CalendarEventListRequest(ANCHOR, ANCHOR.plusDays(10), null)).size());
            String applied = databaseSnapshot();
            var again = planner.preview(state, ANCHOR, true);
            assertTrue(again.entries().stream().allMatch(e -> e.action() == ExhibitionPlanner.Action.KEEP), again.entries().toString());
            assertEquals(state, writer.apply(journal, state, planner, again, again.token(), legacy, null));
            assertEquals(applied, databaseSnapshot(), "Repeat must preserve all IDs, timestamps, messages and receipts");

            // Catalog update: simulate an earlier recorded content version, with an owned ID.
            String key = "post/자유게시판/1";
            var owned = new TreeMap<>(state.owned()); var old = owned.get(key);
            jdbc.update("UPDATE community_post SET title=? WHERE post_id=?", "이전 전시 문구", old.id());
            owned.put(key, new ExhibitionJournal.Owned(old.kind(), old.id(), old.owner(), "old-catalog-version", db.snapshot("post", old.id()), old.children()));
            String eventKey = ExhibitionCatalog.events().getFirst().key();
            var oldEvent = owned.get(eventKey);
            jdbc.update("UPDATE calendar_event SET title=? WHERE event_id=?", "이전 일정 문구", oldEvent.id());
            owned.put(eventKey, new ExhibitionJournal.Owned(oldEvent.kind(), oldEvent.id(), oldEvent.owner(), "old-catalog-version", db.snapshot("event", oldEvent.id()), oldEvent.children()));
            var oldState = new ExhibitionJournal.State(state.database(), state.anchor(), "COMPLETE", owned);
            journal.write(oldState);
            var upgraded = apply(journal, oldState, true);
            assertEquals(old.id(), upgraded.owned().get(key).id());
            assertEquals(old.children(), upgraded.owned().get(key).children());
            assertEquals(80, count("community_post")); assertEquals(160, count("community_comment"));
            assertEquals(oldEvent.id(), upgraded.owned().get(eventKey).id()); assertEquals(12, count("calendar_event"));
            assertEquals(ExhibitionCatalog.events().getFirst().title(), db.one("SELECT title FROM calendar_event WHERE event_id=?", oldEvent.id()).get("title"));

            // A real interaction or changed profile is never silently adopted into provenance.
            long real = createUser("visitor@hknu.ac.kr", "방문기록", false, false);
            String realBefore = db.snapshot("user", real);
            comments.createComment(real, old.id(), new CommunityCommentCreateRequest("실제 이용자가 남긴 내용"));
            long user7 = upgraded.owned().get("user/7").id();
            jdbc.update("UPDATE user_profile SET introduction=? WHERE user_id=?", "시연 중 직접 편집한 소개", user7);
            var drift = planner.preview(upgraded, ANCHOR, true);
            assertEquals(ExhibitionPlanner.Action.SKIP, drift.entry(key).action());
            assertEquals(ExhibitionPlanner.Action.SKIP, drift.entry("user/7").action());
            writer.apply(journal, upgraded, planner, drift, drift.token(), legacy, null);
            assertEquals(realBefore, db.snapshot("user", real));
            assertEquals("시연 중 직접 편집한 소개", db.one("SELECT introduction FROM user_profile WHERE user_id=?", user7).get("introduction"));
            assertEquals(161, count("community_comment"));
        }
    }

    @Test void legacyMigrationPreservesIdentifiersCredentialsDemographicsAndHistoricalPostIds() throws Exception {
        long existing = createUser("seed_user01@hknu.ac.kr", "테스트룸메01", true, false);
        var oldUser = db.one("SELECT * FROM `user` WHERE user_id=?", existing);
        var oldProfile = db.one("SELECT * FROM user_profile WHERE user_id=?", existing);
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            var user = users.findById(existing).orElseThrow();
            var dorm = dormitories.findByDormitoryName("나래").orElseThrow();
            new CommunitySeedDataService(postRepository, jdbc).createPosts(user, dorm, 1);
        });
        var oldPosts = db.rows("SELECT post_id,created_at FROM community_post WHERE author_id=? ORDER BY post_id", existing);
        try (var journal = new ExhibitionJournal(directory.resolve("exhibition.json"))) {
            var plan = planner.preview(empty(), ANCHOR, false);
            assertEquals(ExhibitionPlanner.Action.UPDATE, plan.entry("user/1").action());
            assertEquals(4, plan.entries().stream().filter(e -> e.kind().equals("post") && e.action() == ExhibitionPlanner.Action.UPDATE).count());
            var state = apply(journal, empty(), false);
            assertEquals(existing, state.owned().get("user/1").id());
            assertEquals(oldUser, db.one("SELECT * FROM `user` WHERE user_id=?", existing));
            var updated = db.one("SELECT * FROM user_profile WHERE user_id=?", existing);
            for (String immutable : List.of("user_id", "user_profile_id", "gender", "birth_year", "admission_year", "created_at"))
                assertEquals(oldProfile.get(immutable), updated.get(immutable), immutable);
            assertEquals(ExhibitionCatalog.students().getFirst().nickname(), updated.get("nickname"));
            assertEquals(oldPosts, db.rows("SELECT post_id,created_at FROM community_post WHERE author_id=? ORDER BY post_id", existing));
            assertEquals(0, db.count("SELECT COUNT(*) FROM community_post WHERE title LIKE '[SEED]%'"));
            assertTrue(Boolean.TRUE.equals(new TransactionTemplate(manager).execute(status -> legacy.contains(users.findById(existing).orElseThrow()))));
        }
    }

    @Test void unknownAccountsEditedLegacyPostsExistingConversationsAndSchoolDataRemainUntouched() throws Exception {
        long unknown = createUser("seed_user02@hknu.ac.kr", "알수없는계정", false, false);
        long owner = createUser("seed_user01@hknu.ac.kr", "테스트룸메01", true, false);
        long peer = createUser("seed_user03@hknu.ac.kr", "테스트룸메03", true, false);
        long fourth = createUser("seed_user04@hknu.ac.kr", "테스트룸메04", true, false);
        long photo = createUser("seed_user30@hknu.ac.kr", "외부프로필", true, false);
        jdbc.update("UPDATE user_profile SET profile_image_url=? WHERE user_id=?", "https://example.invalid/unverified.jpg", photo);
        long visitor = createUser("visitor@hknu.ac.kr", "방문기록", false, false);
        long existingAdmin = createUser("admin01@hknu.ac.kr", "기존관리자", false, true);
        long edited = posts.createPost(owner, new CommunityPostCreateRequest("자유게시판", "수동 변경한 제목", "보존해야 하는 본문", false), null);
        var legacyPost = CommunitySeedDataService.legacyPosts().stream().filter(p -> p.index() == 1 && p.category().equals("나눔게시판")).findFirst().orElseThrow();
        long withRealComment = posts.createPost(owner, new CommunityPostCreateRequest(legacyPost.category(), legacyPost.title(), legacyPost.content(), false), null);
        comments.createComment(visitor, withRealComment, new CommunityCommentCreateRequest("보존할 댓글"));
        long unknownRoom = chats.createOrGetRoom(peer, new ChatRoomCreateRequest(fourth, com.kkirikkiri.chat.entity.ChatRoomCategory.ROOMMATE, null, null, null)).id();
        chats.sendMessage(peer, unknownRoom, new ChatMessageSendRequest("보존할 대화"));
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            em.persist(new com.kkirikkiri.matching.entity.RoommateMatch(users.findById(visitor).orElseThrow(), users.findById(owner).orElseThrow(), 42, "기존 관계 보존용 fixture"));
            em.persist(new com.kkirikkiri.matching.entity.RoommateMatch(users.findById(owner).orElseThrow(), users.findById(visitor).orElseThrow(), 73, "기존 관계 보존용 fixture"));
        });
        jdbc.update("INSERT INTO calendar_event (category_id,title,start_date,end_date,is_all_day,source_type,source_name,source_url,collected_at) SELECT category_id,'학교 수집 원본',?,?,TRUE,'CRAWLED','SCHOOL','https://example.invalid',CURRENT_TIMESTAMP FROM calendar_category WHERE category_code='SCHOOL'", ANCHOR, ANCHOR);
        String originalUnknown = db.snapshot("user", unknown), real = db.snapshot("user", visitor);
        String photoBefore = db.snapshot("user", photo), adminBefore = db.snapshot("user", existingAdmin);
        var realMatches = db.rows("SELECT * FROM roommate_match WHERE requester_user_id=? OR target_user_id=? ORDER BY match_id", visitor, visitor);
        String originalPost = db.snapshot("post", edited), originalThread = db.snapshot("post", withRealComment);
        String originalRoom = db.snapshot("chat", unknownRoom), school = ExhibitionJournal.hash(db.rows("SELECT * FROM calendar_event WHERE source_type='CRAWLED'"));
        try (var journal = new ExhibitionJournal(directory.resolve("exhibition.json"))) {
            var plan = planner.preview(empty(), ANCHOR, false);
            assertEquals(ExhibitionPlanner.Action.SKIP, plan.entry("user/2").action());
            assertEquals(ExhibitionPlanner.Action.SKIP, plan.entry("user/30").action());
            assertEquals(ExhibitionPlanner.Action.SKIP, plan.entry("chat/quiet").action());
            assertEquals(ExhibitionPlanner.Action.SKIP, plan.entry("post/자유게시판/1").action());
            assertEquals(ExhibitionPlanner.Action.SKIP, plan.entry("post/나눔게시판/1").action());
            var state = apply(journal, empty(), false);
            assertFalse(state.owned().containsKey("user/2"));
            assertEquals(originalUnknown, db.snapshot("user", unknown)); assertEquals(real, db.snapshot("user", visitor));
            assertEquals(photoBefore, db.snapshot("user", photo)); assertEquals(adminBefore, db.snapshot("user", existingAdmin));
            assertEquals(originalPost, db.snapshot("post", edited)); assertEquals(originalThread, db.snapshot("post", withRealComment));
            assertEquals(originalRoom, db.snapshot("chat", unknownRoom));
            assertEquals(school, ExhibitionJournal.hash(db.rows("SELECT * FROM calendar_event WHERE source_type='CRAWLED'")));
            assertEquals(1, db.count("SELECT COUNT(*) FROM community_post WHERE author_id=? AND category='자유게시판'", owner));
            assertEquals(realMatches, db.rows("SELECT * FROM roommate_match WHERE requester_user_id=? OR target_user_id=? ORDER BY match_id", visitor, visitor));
        }
    }

    @Test void stalePreviewIsRejectedBeforeMutationAndBeforeWritingAnApplyingJournal() throws Exception {
        var plan = planner.preview(empty(), ANCHOR, false);
        createUser("conflict@hknu.ac.kr", ExhibitionCatalog.students().getFirst().nickname(), false, false);
        String before = databaseSnapshot(); Path file = directory.resolve("exhibition.json");
        try (var journal = new ExhibitionJournal(file)) {
            assertThrows(IllegalStateException.class, () -> writer.apply(journal, empty(), planner, plan, plan.token(), legacy, null));
            assertFalse(Files.exists(file)); assertEquals(before, databaseSnapshot());
        }
    }

    @Test void validationFailureRollsBackCoreAndInterruptedJournalPreventsRetry() throws Exception {
        doThrow(new IllegalStateException("simulated preference failure")).when(preferences).saveOrUpdateMyRoommatePreference(anyLong(), any());
        String before = databaseSnapshot();
        try (var journal = new ExhibitionJournal(directory.resolve("exhibition.json"))) {
            var plan = planner.preview(empty(), ANCHOR, false);
            assertThrows(IllegalStateException.class, () -> writer.apply(journal, empty(), planner, plan, plan.token(), legacy, null));
            assertEquals(before, databaseSnapshot());
            assertFalse(Files.exists(directory.resolve("legacy.properties")), "Rolled-back users must have no provenance");
            assertThrows(IllegalStateException.class, () -> journal.read("h2-isolated", ANCHOR.toString()));
        }
    }

    @Test void independentChatCommitFailureIsRecordedAsIncompleteAndCannotBeSilentlyRetried() throws Exception {
        doThrow(new IllegalStateException("simulated message failure")).when(chats).sendMessage(anyLong(), anyLong(), any());
        try (var journal = new ExhibitionJournal(directory.resolve("exhibition.json"))) {
            var plan = planner.preview(empty(), ANCHOR, false);
            assertThrows(IllegalStateException.class, () -> writer.apply(journal, empty(), planner, plan, plan.token(), legacy, null));
            assertEquals(30, count("`user`")); assertEquals(80, count("community_post"));
            assertEquals(1, count("chat_room")); assertEquals(2, count("chat_room_member"));
            assertEquals(0, count("chat_message"));
            assertThrows(IllegalStateException.class, () -> journal.read("h2-isolated", ANCHOR.toString()));
            String partial = databaseSnapshot();
            assertThrows(IllegalStateException.class, () -> writer.apply(journal, empty(), planner, plan, plan.token(), legacy, null));
            assertEquals(partial, databaseSnapshot());
        }
    }

    @Test void strictRoomCreationDoesNotAdoptAnExistingRoomEvenWhenCreationRaces() throws Exception {
        long first = createUser("chat-first@hknu.ac.kr", "대화첫째", false, false);
        long second = createUser("chat-second@hknu.ac.kr", "대화둘째", false, false);
        var request = new ChatRoomCreateRequest(second, com.kkirikkiri.chat.entity.ChatRoomCategory.ROOMMATE, null, null, null);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        var ready = new java.util.concurrent.CountDownLatch(2);
        var go = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.Callable<Boolean> create = () -> {
            ready.countDown();
            if (!go.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("test barrier timeout");
            try { chats.createNewRoom(first, request); return true; }
            catch (com.kkirikkiri.global.exception.CustomException | org.springframework.dao.DataIntegrityViolationException conflict) { return false; }
        };
        try {
            var a = executor.submit(create); var b = executor.submit(create);
            assertTrue(ready.await(10, java.util.concurrent.TimeUnit.SECONDS)); go.countDown();
            boolean createdA = a.get(20, java.util.concurrent.TimeUnit.SECONDS), createdB = b.get(20, java.util.concurrent.TimeUnit.SECONDS);
            assertNotEquals(createdA, createdB);
            assertEquals(1, count("chat_room")); assertEquals(2, count("chat_room_member"));
            long room = db.count("SELECT chat_room_id FROM chat_room");
            String before = db.snapshot("chat", room);
            assertThrows(com.kkirikkiri.global.exception.CustomException.class, () -> chats.createNewRoom(first, request));
            assertEquals(before, db.snapshot("chat", room));
            assertEquals(room, chats.createOrGetRoom(first, request).id());
        } finally { executor.shutdownNow(); }
    }

    private long createUser(String email, String nickname, boolean provenance, boolean admin) {
        return new TransactionTemplate(manager).execute(status -> {
            var user = users.saveAndFlush(new User("fixture-credential-not-used-for-login", email, admin ? UserRole.ADMIN : UserRole.USER));
            user.verifyEmail(); users.flush();
            var faculty = faculties.findByFacultyName("컴퓨터응용수학부").orElseThrow();
            profiles.saveAndFlush(new UserProfile(user, nickname, "남자", dormitories.findByDormitoryName("나래").orElseThrow(),
                    faculty, majors.findByFaculty_FacultyIdAndMajorName(faculty.getFacultyId(), "소프트웨어융합전공").orElseThrow(),
                    (short)2001, "22", "3학년", "INTJ"));
            if (provenance) legacy.registerAfterCommit(user);
            return user.getUserId();
        });
    }
    private String databaseSnapshot() {
        List<Object> snapshot = new ArrayList<>();
        for (String table : List.of("user", "user_profile", "profile_survey", "profile_survey_option", "roommate_preference",
                "roommate_preference_option", "preference_criterion", "roommate_match", "community_post", "community_comment",
                "post_like", "comment_like", "community_anonymous_author", "chat_room", "chat_room_member", "chat_message",
                "calendar_category", "calendar_category_setting", "calendar_event", "report"))
            snapshot.add(db.rows("SELECT * FROM `" + table + "` ORDER BY 1"));
        return ExhibitionJournal.hash(snapshot);
    }
}
