package com.kkirikkiri.community;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.kkirikkiri.admin.service.AdminService;
import com.kkirikkiri.community.comment.dto.CommunityCommentCreateRequest;
import com.kkirikkiri.community.comment.service.CommunityCommentService;
import com.kkirikkiri.community.comment.entity.CommunityComment;
import com.kkirikkiri.community.post.dto.CommunityPostUpdateRequest;
import com.kkirikkiri.community.post.entity.*;
import com.kkirikkiri.community.post.service.CommunityPostService;
import com.kkirikkiri.global.exception.CustomException;
import com.kkirikkiri.global.exception.ErrorCode;
import com.kkirikkiri.user.entity.*;

@SpringBootTest(properties = {"spring.datasource.hikari.maximum-pool-size=12",
        "spring.datasource.hikari.transaction-isolation=TRANSACTION_READ_COMMITTED",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector=com.kkirikkiri.community.CommunityReadPerformanceIntegrationTest$SqlCapture"})
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CommunityDetailReadConcurrencyIntegrationTest {
    @Autowired EntityManager em;
    @Autowired CommunityPostService posts;
    @Autowired CommunityCommentService comments;
    @Autowired AdminService admins;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;
    Long ownerId, otherId, adminId, postId;

    @BeforeEach
    void fixtures() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            String key = UUID.randomUUID().toString();
            User owner = new User("hash", key + "o@test.invalid"), other = new User("hash", key + "v@test.invalid");
            User admin = new User("hash", key + "a@test.invalid", UserRole.ADMIN);
            em.persist(owner); em.persist(other); em.persist(admin);
            Dormitory dorm = new Dormitory("d-" + key.substring(0, 8), "d-" + key.substring(0, 8), true);
            Faculty faculty = new Faculty("d-" + key, true); em.persist(dorm); em.persist(faculty);
            for (User user : List.of(owner, other)) em.persist(new UserProfile(user, "d-" + user.getUserId(), "남성", dorm,
                    faculty, null, (short) 2004, "24", "2학년", "INTJ"));
            CommunityPost post = new CommunityPost(owner, null, "자유게시판", "original", "original body"); em.persist(post);
            CommunityComment first = new CommunityComment(post, other, null, "anonymous one"); first.changeAnonymous(true);
            CommunityComment second = new CommunityComment(post, owner, first, "anonymous two"); second.changeAnonymous(true);
            em.persist(first); em.persist(second); em.persist(new CommunityComment(post, other, null, "public comment"));
            em.persist(new PostImage(post, "/api/post-images/legacy.png", "legacy.png", 0));
            ownerId = owner.getUserId(); otherId = other.getUserId(); adminId = admin.getUserId(); postId = post.getPostId();
        });
    }

    @Test
    void simultaneousDetailRequestsAccumulateViewsWithoutChangingOtherFields() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(8)) {
            var readers = java.util.stream.IntStream.range(0, 16).mapToObj(i -> executor.submit(() -> {
                await(start); posts.getPost(otherId, postId); return null;
            })).toList();
            start.countDown();
            for (var reader : readers) reader.get(30, TimeUnit.SECONDS);
        }
        assertEquals(16, views());
        assertEquals("original", title());
        assertEquals("ACTIVE", state());
        assertEquals(2, numberCount());
    }

    @ParameterizedTest
    @ValueSource(strings = {"EDIT", "USER_DELETE", "ADMIN_DELETE", "COMMENT", "LIKE"})
    void slowProfileReadDoesNotHoldPostLockAndLaterWriteRevalidatesActiveState(String action) throws Exception {
        CountDownLatch reading = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicBoolean paused = new AtomicBoolean();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var reader = executor.submit(() -> {
                CommunityReadPerformanceIntegrationTest.SqlCapture.hook(sql -> {
                    if (sql.contains("user_profile") && paused.compareAndSet(false, true)) { reading.countDown(); await(release); }
                });
                try { return posts.getPost(otherId, postId); }
                finally { CommunityReadPerformanceIntegrationTest.SqlCapture.unhook(); }
            });
            try {
                await(reading);
                executor.submit(() -> {
                    switch (action) {
                        case "EDIT" -> posts.updatePost(ownerId, postId,
                                new CommunityPostUpdateRequest("자유게시판", "edited", "edited body", List.of(), null), null);
                        case "USER_DELETE" -> posts.deletePost(ownerId, postId);
                        case "ADMIN_DELETE" -> admins.deletePost(adminId, postId);
                        case "COMMENT" -> comments.createComment(otherId, postId, new CommunityCommentCreateRequest("new comment", false));
                        case "LIKE" -> posts.likePost(otherId, postId);
                        default -> throw new AssertionError(action);
                    }
                    return null;
                }).get(10, TimeUnit.SECONDS);
            } finally { release.countDown(); }
            if (action.endsWith("DELETE")) {
                var failure = assertThrows(java.util.concurrent.ExecutionException.class, () -> reader.get(15, TimeUnit.SECONDS));
                assertEquals(ErrorCode.INVALID_REQUEST, assertInstanceOf(CustomException.class, failure.getCause()).getErrorCode());
                assertEquals(0, views());
                assertEquals("DELETED", state());
                assertEquals(0, numberCount());
            } else {
                var detail = reader.get(15, TimeUnit.SECONDS);
                assertEquals(1, detail.views());
                assertEquals(3, detail.comments().size(), "Comments belong to the prefetched response snapshot");
                assertEquals("original", detail.title());
                assertEquals(1, detail.images().size());
                assertEquals("EDIT".equals(action) ? "edited" : "original", title());
                assertEquals(1, views());
                assertEquals("ACTIVE", state());
                assertEquals("COMMENT".equals(action) ? 4 : 3,
                        jdbc.queryForObject("select count(*) from community_comment where post_id=? and status='ACTIVE'", Integer.class, postId));
                if (action.equals("LIKE")) assertEquals(1,
                        jdbc.queryForObject("select count(*) from post_like where post_id=?", Integer.class, postId));
            }
        }
    }

    @Test
    void postLockIsStillHeldDuringRequiredNumberInitializationAndReleasedAtCommit() throws Exception {
        CountDownLatch held = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicBoolean paused = new AtomicBoolean();
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<?> reader = executor.submit(() -> {
                CommunityReadPerformanceIntegrationTest.SqlCapture.hook(sql -> {
                    if (sql.contains("community_anonymous_author") && paused.compareAndSet(false, true)) { held.countDown(); await(release); }
                });
                try { posts.getPost(otherId, postId); return null; }
                finally { CommunityReadPerformanceIntegrationTest.SqlCapture.unhook(); }
            });
            Future<?> writer = null;
            try {
                await(held);
                writer = executor.submit(() -> comments.createComment(otherId, postId, new CommunityCommentCreateRequest("waiting", true)));
                long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
                while (jdbc.queryForObject("select count(*) from information_schema.sessions where blocker_id is not null", Integer.class) == 0) {
                    if (writer.isDone()) { writer.get(); fail("Comment writing must wait for number initialization"); }
                    assertTrue(System.nanoTime() < deadline, "Expected an actual H2 row-lock wait"); Thread.yield();
                }
                assertEquals(0, views(), "The uncommitted view increment is not visible yet");
            } finally { release.countDown(); }
            reader.get(15, TimeUnit.SECONDS);
            if (writer != null) writer.get(15, TimeUnit.SECONDS);
        }
        assertEquals(1, views());
        assertEquals(2, numberCount());
        var detail = posts.getPost(otherId, postId);
        var anonymous = detail.comments().stream().filter(comment -> comment.anonymous()).toList();
        assertEquals(3, anonymous.size());
        assertTrue(anonymous.stream().allMatch(comment -> comment.author().id() == null));
        assertEquals(anonymous.getFirst().anonymousNumber(), anonymous.getLast().anonymousNumber());
    }

    @Test
    void lateFailureRollsBackViewsAndPartiallyInitializedAnonymousNumbersTogether() {
        AtomicInteger inserts = new AtomicInteger();
        CommunityReadPerformanceIntegrationTest.SqlCapture.hook(sql -> {
            if (sql.contains("insert into community_anonymous_author") && inserts.incrementAndGet() == 2)
                throw new IllegalStateException("test failure after the first mapping insert");
        });
        try { assertThrows(RuntimeException.class, () -> posts.getPost(otherId, postId)); }
        finally { CommunityReadPerformanceIntegrationTest.SqlCapture.unhook(); }
        assertEquals(2, inserts.get());
        assertEquals(0, views());
        assertEquals(0, numberCount());
        assertEquals("ACTIVE", state());
        assertEquals("original", title());
        assertEquals(1, posts.getPost(otherId, postId).views());
        assertEquals(2, numberCount());
    }

    private int views() { return jdbc.queryForObject("select view_count from community_post where post_id=?", Integer.class, postId); }
    private String title() { return jdbc.queryForObject("select title from community_post where post_id=?", String.class, postId); }
    private String state() { return jdbc.queryForObject("select status from community_post where post_id=?", String.class, postId); }
    private int numberCount() { return jdbc.queryForObject("select count(*) from community_anonymous_author where post_id=?", Integer.class, postId); }
    private static void await(CountDownLatch latch) {
        try { assertTrue(latch.await(20, TimeUnit.SECONDS), "Timed out coordinating read/write phases"); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
    }
}
