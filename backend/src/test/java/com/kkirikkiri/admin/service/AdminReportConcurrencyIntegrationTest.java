package com.kkirikkiri.admin.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.stream.Stream;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.kkirikkiri.admin.dto.AdminReportProcessRequest;
import com.kkirikkiri.community.comment.entity.CommunityComment;
import com.kkirikkiri.community.post.entity.CommunityPost;
import com.kkirikkiri.global.exception.CustomException;
import com.kkirikkiri.global.exception.ErrorCode;
import com.kkirikkiri.global.security.JwtTokenProvider;
import com.kkirikkiri.report.entity.*;
import com.kkirikkiri.report.repository.AdminReportQueryRepository;
import com.kkirikkiri.user.entity.*;

/** Independent real transactions and observed H2 row-lock waits; not MySQL or multi-JVM proof. */
@SpringBootTest(properties = {"spring.datasource.hikari.maximum-pool-size=10",
        "spring.datasource.hikari.transaction-isolation=TRANSACTION_READ_COMMITTED"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AdminReportConcurrencyIntegrationTest {
    @Autowired AdminService admins;
    @Autowired AdminReportQueryRepository reports;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokens;
    long adminA, adminB, reporter, target, post, comment, reply;

    @BeforeEach
    void fixtures() {
        tx(() -> {
            String key = UUID.randomUUID().toString();
            var a = new User("test-hash", key + "a@test.invalid", UserRole.ADMIN);
            var b = new User("test-hash", key + "b@test.invalid", UserRole.ADMIN);
            var r = new User("test-hash", key + "r@test.invalid");
            var t = new User("test-hash", key + "t@test.invalid");
            em.persist(a); em.persist(b); em.persist(r); em.persist(t);
            var p = new CommunityPost(t, null, "자유게시판", "original title", "original body", true);
            em.persist(p);
            var c = new CommunityComment(p, t, null, "original comment"); em.persist(c);
            var child = new CommunityComment(p, t, c, "original reply"); em.persist(child);
            adminA = a.getUserId(); adminB = b.getUserId(); reporter = r.getUserId(); target = t.getUserId();
            post = p.getPostId(); comment = c.getCommentId(); reply = child.getCommentId();
            return null;
        });
    }

    static Stream<Arguments> competingActions() {
        return Stream.of(
                Arguments.of(ReportType.USER, "SUSPEND_USER", "SUSPEND_USER"),
                Arguments.of(ReportType.USER, "SUSPEND_USER", "REJECT"),
                Arguments.of(ReportType.USER, "REJECT", "SUSPEND_USER"),
                Arguments.of(ReportType.POST, "DELETE_TARGET", "REJECT"),
                Arguments.of(ReportType.POST, "REJECT", "DELETE_TARGET"),
                Arguments.of(ReportType.COMMENT, "DELETE_TARGET", "REJECT"),
                Arguments.of(ReportType.COMMENT, "REJECT", "DELETE_TARGET"));
    }

    @ParameterizedTest
    @MethodSource("competingActions")
    void exactlyOneConcurrentDecisionCommits(ReportType type, String first, String second) throws Exception {
        long report = report(type);
        assertEquals(ErrorCode.INVALID_REQUEST, contend(report, first, second, false));
        assertResult(report, type, first, adminA);
        assertError(ErrorCode.INVALID_REQUEST, () -> process(adminB, report, second));
        assertResult(report, type, first, adminA);
    }

    @ParameterizedTest
    @EnumSource(ReportType.class)
    void rollbackReleasesReportAndSanctionsBeforeWaiterProcesses(ReportType type) throws Exception {
        long report = report(type);
        String first = type == ReportType.USER ? "SUSPEND_USER" : "DELETE_TARGET";
        assertNull(contend(report, first, "REJECT", true));
        assertResult(report, type, "REJECT", adminB);
    }

    @Test
    void staleManagedPendingReportIsRefreshedBeforeCheckingStatus() {
        long report = report(ReportType.USER);
        assertError(ErrorCode.INVALID_REQUEST, () -> tx(() -> {
            assertEquals(ReportStatus.PENDING, em.find(Report.class, report).getStatus());
            tx(() -> { process(adminA, report, "REJECT"); return null; });
            process(adminB, report, "SUSPEND_USER");
            return null;
        }));
        assertResult(report, ReportType.USER, "REJECT", adminA);
    }

    @Test
    void reentrantProcessingCannotDiscardUncommittedDecision() {
        long report = report(ReportType.USER);
        assertError(ErrorCode.INVALID_REQUEST, () -> tx(() -> {
            process(adminA, report, "SUSPEND_USER");
            process(adminB, report, "REJECT");
            return null;
        }));
        assertPending(report);
        assertContentAndAccount(false, false, ReportType.USER);
        process(adminB, report, "REJECT");
        assertResult(report, ReportType.USER, "REJECT", adminB);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, Long.MAX_VALUE})
    void missingOrInvalidReportReturnsBusinessErrorWithoutSanctions(long id) {
        assertError(ErrorCode.INVALID_REQUEST, () -> process(adminA, id, "SUSPEND_USER"));
        assertContentAndAccount(false, false, ReportType.USER);
    }

    @ParameterizedTest
    @EnumSource(ReportType.class)
    void timeValidationFailureRollsBackEverySanction(ReportType type) {
        long report = report(type);
        tx(() -> {
            jdbc.update("update report set created_at=? where report_id=?",
                    java.sql.Timestamp.valueOf(LocalDateTime.now().plusDays(1).withNano(0)), report);
            return null;
        });
        assertThrows(IllegalArgumentException.class,
                () -> process(adminA, report, type == ReportType.USER ? "SUSPEND_USER" : "DELETE_TARGET"));
        assertPending(report);
        assertContentAndAccount(false, false, type);
    }

    @Test
    void lockTimeoutReturns409WithoutMutationsAndLockCanBeReused() throws Exception {
        long report = report(ReportType.USER);
        var held = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(1)) {
            var holder = workers.submit(() -> tx(() -> {
                reports.findReportByIdForUpdate(report).orElseThrow();
                held.countDown(); await(release); return null;
            }));
            try {
                await(held);
                mvc.perform(patch("/api/admin/reports/" + report)
                        .header("Authorization", "Bearer " + tokens.createAccessToken(adminB, UserRole.ADMIN))
                        .contentType("application/json").content("{\"action\":\"SUSPEND_USER\"}"))
                        .andExpect(status().isConflict())
                        .andExpect(jsonPath("$.errorCode").value("ACCOUNT_UPDATE_CONFLICT"));
                assertPending(report);
                assertContentAndAccount(false, false, ReportType.USER);
            } finally { release.countDown(); }
            holder.get(15, TimeUnit.SECONDS);
        }
        process(adminB, report, "SUSPEND_USER");
        assertResult(report, ReportType.USER, "SUSPEND_USER", adminB);
    }

    private ErrorCode contend(long report, String first, String second, boolean rollback) throws Exception {
        var held = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var holder = workers.submit(() -> {
                try {
                    tx(() -> {
                        process(adminA, report, first);
                        em.flush();
                        held.countDown(); await(release);
                        if (rollback) throw new ForcedRollback();
                        return null;
                    });
                } catch (ForcedRollback expected) { assertTrue(rollback); }
            });
            Future<ErrorCode> waiter = null;
            try {
                await(held);
                waiter = workers.submit(() -> {
                    try { process(adminB, report, second); return null; }
                    catch (CustomException failure) { return failure.getErrorCode(); }
                });
                awaitDatabaseWait(waiter);
            } finally { release.countDown(); }
            holder.get(15, TimeUnit.SECONDS);
            return waiter.get(15, TimeUnit.SECONDS);
        }
    }

    private void awaitDatabaseWait(Future<?> waiter) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (jdbc.queryForObject("select count(*) from information_schema.sessions where blocker_id is not null",
                Integer.class) == 0) {
            if (waiter.isDone()) { waiter.get(); fail("Competing action completed without waiting for REPORT"); }
            assertTrue(System.nanoTime() < deadline, "No H2 row-lock wait observed");
            Thread.sleep(10);
        }
        assertFalse(waiter.isDone());
    }

    private long report(ReportType type) {
        return tx(() -> {
            var user = em.find(User.class, reporter);
            Report r = switch (type) {
                case USER -> Report.forUser(user, em.find(User.class, target), "욕설·비방", null);
                case POST -> Report.forPost(user, em.find(CommunityPost.class, post), "욕설·비방", null);
                case COMMENT -> Report.forComment(user, em.find(CommunityComment.class, comment), "욕설·비방", null);
            };
            em.persist(r); return r.getReportId();
        });
    }

    private void assertResult(long id, ReportType type, String action, long actor) {
        tx(() -> {
            var r = em.find(Report.class, id);
            assertEquals(action.equals("REJECT") ? ReportStatus.REJECTED : ReportStatus.COMPLETED, r.getStatus());
            assertEquals(action.equals("REJECT") ? "NONE" : action, r.getResolutionAction());
            assertEquals(actor, r.getProcessedBy().getUserId());
            assertNotNull(r.getProcessedAt());
            assertFalse(r.getProcessedAt().isBefore(r.getCreatedAt()));
            return null;
        });
        assertContentAndAccount(action.equals("SUSPEND_USER"), action.equals("DELETE_TARGET"), type);
    }

    private void assertPending(long id) {
        tx(() -> {
            var r = em.find(Report.class, id);
            assertEquals(ReportStatus.PENDING, r.getStatus());
            assertNull(r.getProcessedAt()); assertNull(r.getProcessedBy()); assertNull(r.getResolutionAction());
            return null;
        });
    }

    private void assertContentAndAccount(boolean suspended, boolean deleted, ReportType type) {
        assertEquals(suspended ? "SUSPENDED" : "ACTIVE",
                jdbc.queryForObject("select status from `user` where user_id=?", String.class, target));
        assertEquals(deleted && type == ReportType.POST ? "DELETED" : "ACTIVE",
                jdbc.queryForObject("select status from community_post where post_id=?", String.class, post));
        for (long id : new long[]{comment, reply}) {
            assertEquals(deleted ? "DELETED" : "ACTIVE",
                    jdbc.queryForObject("select status from community_comment where comment_id=?", String.class, id));
        }
        assertTrue(jdbc.queryForObject("select anonymous from community_post where post_id=?", Boolean.class, post));
        assertEquals("original body", jdbc.queryForObject("select content from community_post where post_id=?", String.class, post));
    }

    private void process(long actor, long report, String action) {
        admins.processReport(actor, report, new AdminReportProcessRequest(action));
    }

    private static void assertError(ErrorCode expected, Runnable action) {
        assertEquals(expected, assertThrows(CustomException.class, action::run).getErrorCode());
    }

    private <T> T tx(Supplier<T> action) {
        var tx = new TransactionTemplate(manager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return tx.execute(s -> action.get());
    }

    private static void await(CountDownLatch latch) {
        try { assertTrue(latch.await(15, TimeUnit.SECONDS)); }
        catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new AssertionError(failure); }
    }

    private static class ForcedRollback extends RuntimeException { }
}
