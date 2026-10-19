package com.kkirikkiri.community;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PessimisticLockException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.kkirikkiri.admin.dto.AdminReportProcessRequest;
import com.kkirikkiri.admin.service.AdminService;
import com.kkirikkiri.community.comment.dto.CommunityCommentCreateRequest;
import com.kkirikkiri.community.comment.dto.CommunityCommentUpdateRequest;
import com.kkirikkiri.community.comment.entity.CommunityComment;
import com.kkirikkiri.community.comment.service.CommunityCommentService;
import com.kkirikkiri.community.common.entity.CommunityContentStatus;
import com.kkirikkiri.community.post.dto.CommunityPostCreateRequest;
import com.kkirikkiri.community.post.dto.CommunityPostListRequest;
import com.kkirikkiri.community.post.dto.CommunityPostUpdateRequest;
import com.kkirikkiri.community.post.entity.CommunityPost;
import com.kkirikkiri.community.post.entity.PostImage;
import com.kkirikkiri.community.post.service.CommunityPostService;
import com.kkirikkiri.global.exception.CustomException;
import com.kkirikkiri.global.exception.ErrorCode;
import com.kkirikkiri.report.entity.Report;
import com.kkirikkiri.report.entity.ReportStatus;
import com.kkirikkiri.report.repository.AdminReportQueryRepository;
import com.kkirikkiri.user.entity.Dormitory;
import com.kkirikkiri.user.entity.Faculty;
import com.kkirikkiri.user.entity.User;
import com.kkirikkiri.user.entity.UserProfile;
import com.kkirikkiri.user.entity.UserRole;

/**
 * Real, independently committed JPA transactions, always against the test H2 memory DB.
 * Latches hold the first transaction open; H2 SESSIONS proves the contender is actually
 * waiting for a database row lock before the first transaction is allowed to commit.
 * This does not establish MySQL/InnoDB or multi-JVM behavior.
 */
@SpringBootTest(properties = {"spring.datasource.hikari.maximum-pool-size=12",
        "spring.datasource.hikari.transaction-isolation=TRANSACTION_READ_COMMITTED"})
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CommunityContentConcurrencyIntegrationTest {
    @Autowired CommunityPostService posts;
    @Autowired CommunityCommentService comments;
    @Autowired AdminService admins;
    @Autowired AdminReportQueryRepository reports;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    private Long authorId, otherId, adminId, postId, commentId, replyId;

    @BeforeEach
    void fixtures() {
        tx(() -> {
            String suffix = UUID.randomUUID().toString();
            var author = new User("test-hash", suffix + "a@test.invalid");
            var other = new User("test-hash", suffix + "b@test.invalid");
            var admin = new User("test-hash", suffix + "c@test.invalid", UserRole.ADMIN);
            em.persist(author); em.persist(other); em.persist(admin);
            var dormitory = new Dormitory("cc-" + suffix.substring(0, 8), "나래-" + suffix.substring(0, 16), true);
            var faculty = new Faculty("cc-" + suffix, true);
            em.persist(dormitory); em.persist(faculty);
            em.persist(new UserProfile(author, suffix.substring(0, 16), "남성", dormitory, faculty,
                    null, (short) 2004, "24", "2학년", "INTJ"));
            var post = new CommunityPost(author, null, "자유게시판", "original title", "original post");
            em.persist(post);
            var comment = new CommunityComment(post, author, null, "original comment");
            em.persist(comment);
            var reply = new CommunityComment(post, author, comment, "original reply");
            em.persist(reply);
            authorId = author.getUserId(); otherId = other.getUserId(); adminId = admin.getUserId();
            postId = post.getPostId(); commentId = comment.getCommentId(); replyId = reply.getCommentId();
            return null;
        });
    }

    @ParameterizedTest(name = "admin={0}, editFirst={1}")
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void postEditAndUserOrAdminDeleteSerializeInBothOrders(boolean admin, boolean editFirst) throws Exception {
        Runnable edit = this::editPost;
        Runnable delete = () -> deletePost(admin);
        contend(editFirst ? edit : delete, editFirst ? delete : () ->
                assertError(ErrorCode.INVALID_REQUEST, edit));
        assertPostDeleted();
        assertEquals(editFirst ? "edited title" : "original title", post().getTitle());
        assertEquals(CommunityContentStatus.DELETED, comment(commentId).getStatus());
        assertEquals(CommunityContentStatus.DELETED, comment(replyId).getStatus());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectsPostEditAfterDeletionHasAlreadyCommitted(boolean admin) {
        deletePost(admin);
        assertError(ErrorCode.INVALID_REQUEST, this::editPost);
        assertError(ErrorCode.INVALID_REQUEST, () -> posts.getPost(null, postId));
        assertPostDeleted();
        assertEquals(0, post().getViewCount());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void deletesAfterEditHasAlreadyCommitted(boolean admin) {
        editPost();
        deletePost(admin);
        assertPostDeleted();
        assertEquals("edited title", post().getTitle());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void postEditAndViewPreserveBothContentAndCount(boolean editFirst) throws Exception {
        Runnable view = () -> assertEquals(1, posts.getPost(null, postId).views());
        contend(editFirst ? this::editPost : view, editFirst ? view : this::editPost);
        assertEquals(1, post().getViewCount());
        assertEquals("edited title", post().getTitle());
        assertEquals("edited post", post().getContent());
        assertEquals(CommunityContentStatus.ACTIVE, post().getStatus());
    }

    @ParameterizedTest(name = "admin={0}, viewFirst={1}")
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void postDeletionAndViewPreserveDeletionAndCommittedViewCount(boolean admin, boolean viewFirst) throws Exception {
        Runnable view = () -> assertEquals(1, posts.getPost(null, postId).views());
        Runnable delete = () -> deletePost(admin);
        contend(viewFirst ? view : delete, viewFirst ? delete : () ->
                assertError(ErrorCode.INVALID_REQUEST, view));
        assertPostDeleted();
        assertEquals(viewFirst ? 1 : 0, post().getViewCount());
    }

    @Test
    void simultaneousViewsEachIncrementExactlyOnceAndReturnIncrementedCounts() throws Exception {
        int workers = 8;
        CountDownLatch ready = new CountDownLatch(workers), start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(workers)) {
            List<Future<Integer>> results = new ArrayList<>();
            try {
                for (int i = 0; i < workers; i++) {
                    final Long viewer = i % 2 == 0 ? null : authorId;
                    results.add(executor.submit(() -> {
                        ready.countDown(); await(start);
                        return tx(() -> posts.getPost(viewer, postId).views());
                    }));
                }
                await(ready); start.countDown();
                List<Integer> counts = new ArrayList<>();
                for (var result : results) counts.add(result.get(15, TimeUnit.SECONDS));
                assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8), counts.stream().sorted().toList());
            } finally {
                start.countDown();
                executor.shutdownNow();
            }
        }
        assertEquals(workers, post().getViewCount());
        assertEquals("original title", post().getTitle());
    }

    @ParameterizedTest(name = "reply={0}, admin={1}, editFirst={2}")
    @CsvSource({"false,false,false", "false,false,true", "false,true,false", "false,true,true",
            "true,false,false", "true,false,true", "true,true,false", "true,true,true"})
    void commentOrReplyEditAndUserOrAdminDeleteSerialize(boolean reply, boolean admin, boolean editFirst)
            throws Exception {
        Long id = reply ? replyId : commentId;
        Runnable edit = () -> comments.updateComment(authorId, id, new CommunityCommentUpdateRequest("edited comment"));
        Runnable delete = () -> deleteComment(admin, id);
        contend(editFirst ? edit : delete, editFirst ? delete : () ->
                assertError(ErrorCode.INVALID_REQUEST, edit));
        assertEquals(CommunityContentStatus.DELETED, comment(id).getStatus());
        assertEquals(editFirst ? "edited comment" : reply ? "original reply" : "original comment",
                comment(id).getContent());
        assertEquals(CommunityContentStatus.ACTIVE, post().getStatus());
    }

    @ParameterizedTest(name = "reply={0}, admin={1}, editFirst={2}")
    @CsvSource({"false,false,false", "false,false,true", "false,true,false", "false,true,true",
            "true,false,false", "true,false,true", "true,true,false", "true,true,true"})
    void commentOrReplyEditCannotUndoContainingPostDeletion(boolean reply, boolean admin, boolean editFirst)
            throws Exception {
        Long id = reply ? replyId : commentId;
        Runnable edit = () -> comments.updateComment(authorId, id, new CommunityCommentUpdateRequest("edited comment"));
        Runnable delete = () -> deletePost(admin);
        contend(editFirst ? edit : delete, editFirst ? delete : () ->
                assertError(ErrorCode.INVALID_REQUEST, edit));
        assertPostDeleted();
        assertEquals(CommunityContentStatus.DELETED, comment(commentId).getStatus());
        assertEquals(CommunityContentStatus.DELETED, comment(replyId).getStatus());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectsEditsOfPreviouslyDeletedCommentsAndReplies(boolean reply) {
        Long id = reply ? replyId : commentId;
        comments.deleteComment(authorId, id);
        assertError(ErrorCode.INVALID_REQUEST, () -> comments.updateComment(authorId, id,
                new CommunityCommentUpdateRequest("must not return")));
        assertEquals(CommunityContentStatus.DELETED, comment(id).getStatus());
    }

    @Test
    void crudPermissionsAnonymousLikesAndViewPolicyRemainUnchanged() {
        Long created = posts.createPost(authorId,
                new CommunityPostCreateRequest("자유게시판", "new post", "new content", true), null);
        Long first = comments.createComment(authorId, created, new CommunityCommentCreateRequest("anonymous", true));
        Long reply = comments.createReply(authorId, first, new CommunityCommentCreateRequest("reply", true));
        var edit = new CommunityPostUpdateRequest("자유게시판", "changed", "content", List.of(), null);
        assertError(ErrorCode.FORBIDDEN, () -> posts.updatePost(otherId, created, edit, null));
        assertError(ErrorCode.FORBIDDEN, () -> posts.deletePost(otherId, created));
        assertError(ErrorCode.FORBIDDEN, () -> comments.updateComment(otherId, first, new CommunityCommentUpdateRequest("bad")));
        assertError(ErrorCode.FORBIDDEN, () -> comments.deleteComment(otherId, reply));
        assertError(ErrorCode.FORBIDDEN, () -> admins.deletePost(otherId, created));
        assertError(ErrorCode.FORBIDDEN, () -> admins.deleteComment(otherId, first));
        posts.updatePost(authorId, created, edit, null);
        comments.updateComment(authorId, first, new CommunityCommentUpdateRequest("changed comment"));
        comments.updateComment(authorId, reply, new CommunityCommentUpdateRequest("changed reply"));
        posts.likePost(otherId, created); posts.likePost(otherId, created);
        comments.likeComment(otherId, first); comments.likeComment(otherId, first);
        // Lists and admin details do not increment; every public detail, including guests/repeats, does.
        posts.getPosts(null, new CommunityPostListRequest(0, 6, null, null, "LATEST"));
        assertEquals(0, admins.getPost(adminId, created).views());
        var detail = posts.getPost(otherId, created);
        assertEquals(1, detail.views()); assertEquals(1, detail.likes()); assertTrue(detail.liked());
        assertTrue(detail.anonymous()); assertEquals("익명", detail.author().nickname());
        assertEquals("changed", detail.title());
        assertEquals(2, detail.comments().size());
        assertTrue(detail.comments().stream().allMatch(c -> c.anonymous() && c.author().id() == null
                && c.anonymousNumber() == 1L && !c.mine()));
        var firstDto = detail.comments().stream().filter(c -> c.id().equals(first)).findFirst().orElseThrow();
        assertEquals(1, firstDto.likes()); assertTrue(firstDto.liked());
        assertEquals(2, posts.getPost(null, created).views());
        assertEquals(3, posts.getPost(otherId, created).views());
        posts.unlikePost(otherId, created); posts.unlikePost(otherId, created);
        comments.unlikeComment(otherId, first); comments.unlikeComment(otherId, first);
        var unliked = posts.getPost(otherId, created);
        assertEquals(4, unliked.views()); assertEquals(0, unliked.likes()); assertFalse(unliked.liked());
        assertTrue(unliked.comments().stream().allMatch(c -> c.likes() == 0 && !c.liked()));
        comments.deleteComment(authorId, reply);
        comments.deleteComment(authorId, first);
        assertTrue(posts.getPost(null, created).comments().isEmpty());
        posts.deletePost(authorId, created);
        assertError(ErrorCode.INVALID_REQUEST, () -> posts.getPost(null, created));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void reportedTargetIsRefreshedUnderLockAfterEarlierEntityLoad(boolean commentTarget) throws Exception {
        Long reportId = createReport(commentTarget);
        CountDownLatch loaded = new CountDownLatch(1), release = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            Future<?> deletion = executor.submit(() -> tx(() -> {
                reports.findReportById(reportId).orElseThrow(); // fetch joins deliberately cache the old target
                loaded.countDown(); await(release);
                admins.processReport(adminId, reportId, new AdminReportProcessRequest("DELETE_TARGET"));
                return null;
            }));
            try {
                await(loaded);
                if (commentTarget) {
                    comments.updateComment(authorId, commentId, new CommunityCommentUpdateRequest("latest comment"));
                } else {
                    editPost();
                    assertEquals(1, posts.getPost(null, postId).views());
                }
            } finally {
                release.countDown();
            }
            deletion.get(15, TimeUnit.SECONDS);
        }
        if (commentTarget) {
            assertEquals(CommunityContentStatus.DELETED, comment(commentId).getStatus());
            assertEquals("latest comment", comment(commentId).getContent());
        } else {
            assertPostDeleted();
            assertEquals(1, post().getViewCount());
            assertEquals("edited title", post().getTitle());
        }
        assertEquals(ReportStatus.COMPLETED, tx(() -> em.find(Report.class, reportId).getStatus()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST_DELETE", "POST_VIEW", "POST_EDIT", "COMMENT_DELETE", "REPLY_DELETE"})
    void h2RepeatableReadConflictRollsBackWithoutResurrectionOrLostViews(String scenario) throws Exception {
        Runnable first = switch (scenario) {
            case "POST_DELETE" -> () -> deletePost(true);
            case "POST_VIEW" -> () -> posts.getPost(null, postId);
            case "POST_EDIT" -> this::editPost;
            case "COMMENT_DELETE" -> () -> deleteComment(true, commentId);
            case "REPLY_DELETE" -> () -> deleteComment(true, replyId);
            default -> throw new AssertionError(scenario);
        };
        Runnable second = switch (scenario) {
            case "POST_DELETE", "POST_VIEW" -> this::editPost;
            case "POST_EDIT" -> () -> posts.getPost(null, postId);
            case "COMMENT_DELETE" -> () -> comments.updateComment(authorId, commentId,
                    new CommunityCommentUpdateRequest("must roll back"));
            case "REPLY_DELETE" -> () -> comments.updateComment(authorId, replyId,
                    new CommunityCommentUpdateRequest("must roll back"));
            default -> throw new AssertionError(scenario);
        };
        // Unlike InnoDB current reads, H2 RR rejects this changed-snapshot lock acquisition.
        contend(first, () -> assertThrows(PessimisticLockingFailureException.class,
                () -> tx(TransactionDefinition.ISOLATION_REPEATABLE_READ, () -> { second.run(); return null; })));
        assertEquals(scenario.equals("POST_DELETE") ? CommunityContentStatus.DELETED : CommunityContentStatus.ACTIVE,
                post().getStatus());
        assertEquals(scenario.equals("POST_VIEW") ? 1 : 0, post().getViewCount());
        assertEquals(scenario.equals("POST_EDIT") ? "edited title" : "original title", post().getTitle());
        if (scenario.equals("COMMENT_DELETE") || scenario.equals("REPLY_DELETE")) {
            Long id = scenario.equals("COMMENT_DELETE") ? commentId : replyId;
            assertEquals(CommunityContentStatus.DELETED, comment(id).getStatus());
            assertNotEquals("must roll back", comment(id).getContent());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void h2RepeatableReadStaleReportTargetConflictLeavesReportPending(boolean commentTarget) throws Exception {
        Long reportId = createReport(commentTarget);
        CountDownLatch loaded = new CountDownLatch(1), release = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            Future<?> deletion = executor.submit(() -> assertThrows(PessimisticLockException.class,
                    () -> tx(TransactionDefinition.ISOLATION_REPEATABLE_READ, () -> {
                        reports.findReportById(reportId).orElseThrow();
                        loaded.countDown(); await(release);
                        admins.processReport(adminId, reportId, new AdminReportProcessRequest("DELETE_TARGET"));
                        return null;
                    })));
            try {
                await(loaded);
                if (commentTarget) {
                    comments.updateComment(authorId, commentId, new CommunityCommentUpdateRequest("latest comment"));
                } else {
                    assertEquals(1, posts.getPost(null, postId).views());
                }
            } finally {
                release.countDown();
            }
            deletion.get(15, TimeUnit.SECONDS);
        }
        assertEquals(CommunityContentStatus.ACTIVE, post().getStatus());
        assertEquals(commentTarget ? 0 : 1, post().getViewCount());
        assertEquals(CommunityContentStatus.ACTIVE, comment(commentId).getStatus());
        assertEquals(commentTarget ? "latest comment" : "original comment", comment(commentId).getContent());
        assertEquals(ReportStatus.PENDING, tx(() -> em.find(Report.class, reportId).getStatus()));
    }

    @Test
    void lockTimeoutRollsBackEarlierChangesInLosingTransaction() throws Exception {
        CountDownLatch locked = new CountDownLatch(1), release = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            Future<?> holder = executor.submit(() -> tx(() -> {
                editPost(); locked.countDown(); await(release); return null;
            }));
            try {
                await(locked);
                assertThrows(PessimisticLockingFailureException.class, () -> tx(() -> {
                    jdbc.execute("SET LOCK_TIMEOUT 100");
                    em.find(CommunityComment.class, commentId).updateContent("must roll back");
                    em.flush(); // The losing transaction really writes before the lock conflict.
                    try {
                        editPost();
                    } finally {
                        jdbc.execute("SET LOCK_TIMEOUT 2000");
                    }
                    return null;
                }));
            } finally {
                release.countDown();
            }
            holder.get(15, TimeUnit.SECONDS);
        }
        assertEquals("original comment", comment(commentId).getContent());
        assertEquals("edited title", post().getTitle());
        // The same lock is usable again after rollback.
        assertEquals(1, posts.getPost(null, postId).views());
    }

    @Test
    void failedPostEditRollsBackContentAndImagesWithoutChangingAnonymity() {
        Long imageId = tx(() -> {
            var image = new PostImage(em.find(CommunityPost.class, postId), "/api/post-images/test.png", "test.png", 0);
            em.persist(image); return image.getPostImageId();
        });
        assertThrows(IllegalStateException.class, () -> tx(() -> {
            posts.updatePost(authorId, postId,
                    new CommunityPostUpdateRequest("자유게시판", "changed", "changed", List.of(), false), null);
            em.flush();
            throw new IllegalStateException("test rollback after all writes");
        }));
        assertEquals("original title", post().getTitle()); assertFalse(post().isAnonymous());
        assertTrue(tx(() -> em.find(PostImage.class, imageId) != null));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void reportedTargetAndProcessingHistoryRollBackTogether(boolean commentTarget) {
        Long reportId = createReport(commentTarget);
        assertThrows(IllegalStateException.class, () -> tx(() -> {
            admins.processReport(adminId, reportId, new AdminReportProcessRequest("DELETE_TARGET"));
            em.flush();
            throw new IllegalStateException("test rollback after report processing");
        }));
        assertEquals(CommunityContentStatus.ACTIVE, post().getStatus());
        assertEquals(CommunityContentStatus.ACTIVE, comment(commentId).getStatus());
        assertEquals(CommunityContentStatus.ACTIVE, comment(replyId).getStatus());
        tx(() -> {
            var report = em.find(Report.class, reportId);
            assertEquals(ReportStatus.PENDING, report.getStatus());
            assertNull(report.getProcessedAt()); assertNull(report.getProcessedBy());
            return null;
        });
    }

    private Long createReport(boolean commentTarget) {
        return tx(() -> {
            var reporter = em.find(User.class, otherId);
            var report = commentTarget
                    ? Report.forComment(reporter, em.find(CommunityComment.class, commentId), "욕설·비방", null)
                    : Report.forPost(reporter, em.find(CommunityPost.class, postId), "욕설·비방", null);
            em.persist(report);
            // A historical fixture avoids coupling concurrency checks to Report's time validation.
            jdbc.update("update report set created_at = ? where report_id = ?",
                    java.sql.Timestamp.valueOf("2026-01-01 00:00:00"), report.getReportId());
            return report.getReportId();
        });
    }

    private void editPost() {
        posts.updatePost(authorId, postId,
                new CommunityPostUpdateRequest("자유게시판", "edited title", "edited post", List.of(), null), null);
    }

    private void deletePost(boolean admin) {
        if (admin) admins.deletePost(adminId, postId);
        else posts.deletePost(authorId, postId);
    }

    private void deleteComment(boolean admin, Long id) {
        if (admin) admins.deleteComment(adminId, id);
        else comments.deleteComment(authorId, id);
    }

    private CommunityPost post() { return tx(() -> em.find(CommunityPost.class, postId)); }
    private CommunityComment comment(Long id) { return tx(() -> em.find(CommunityComment.class, id)); }
    private void assertPostDeleted() { assertEquals(CommunityContentStatus.DELETED, post().getStatus()); }

    private <T> T tx(Supplier<T> action) {
        return tx(TransactionDefinition.ISOLATION_READ_COMMITTED, action);
    }

    private <T> T tx(int isolation, Supplier<T> action) {
        var transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(isolation);
        return transaction.execute(status -> action.get());
    }

    private void contend(Runnable first, Runnable second) throws Exception {
        CountDownLatch held = new CountDownLatch(1), release = new CountDownLatch(1), started = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<?> holder = executor.submit(() -> tx(() -> {
                first.run(); held.countDown(); await(release); return null;
            }));
            Future<?> contender = null;
            try {
                await(held);
                // Let the service proxy finish/roll back its own transaction before asserting errors.
                contender = executor.submit(() -> {
                    started.countDown(); second.run(); return null;
                });
                await(started);
                awaitDatabaseLockWait(contender);
            } finally {
                release.countDown();
            }
            holder.get(15, TimeUnit.SECONDS);
            if (contender != null) contender.get(15, TimeUnit.SECONDS);
        }
    }

    private void awaitDatabaseLockWait(Future<?> contender) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        // Observe an actual H2 wait, rather than inferring scheduling from a sleep or a Future timeout.
        while (jdbc.queryForObject("select count(*) from information_schema.sessions where blocker_id is not null",
                Integer.class) == 0) {
            if (contender.isDone()) {
                contender.get(1, TimeUnit.SECONDS);
                fail("The competing transaction finished before the held row lock was released");
            }
            assertTrue(System.nanoTime() < deadline, "Expected a database lock wait");
            Thread.yield();
        }
        assertFalse(contender.isDone());
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(10, TimeUnit.SECONDS), "Timed out coordinating test transactions");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    private static void assertError(ErrorCode code, Runnable action) {
        assertEquals(code, assertThrows(CustomException.class, action::run).getErrorCode());
    }
}
