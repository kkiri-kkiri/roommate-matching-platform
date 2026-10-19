package com.kkirikkiri.community;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.kkirikkiri.admin.dto.AdminReportProcessRequest;
import com.kkirikkiri.admin.service.AdminService;
import com.kkirikkiri.community.comment.dto.CommunityCommentCreateRequest;
import com.kkirikkiri.community.comment.entity.CommunityComment;
import com.kkirikkiri.community.comment.service.CommunityCommentService;
import com.kkirikkiri.community.post.dto.CommunityPostCreateRequest;
import com.kkirikkiri.community.post.dto.CommunityPostUpdateRequest;
import com.kkirikkiri.community.post.dto.PostImagesRequest;
import com.kkirikkiri.community.post.entity.CommunityPost;
import com.kkirikkiri.community.post.service.CommunityPostService;
import com.kkirikkiri.community.post.service.PostImageStorage;
import com.kkirikkiri.global.exception.CustomException;
import com.kkirikkiri.global.exception.ErrorCode;
import com.kkirikkiri.user.entity.Dormitory;
import com.kkirikkiri.user.entity.Faculty;
import com.kkirikkiri.user.entity.User;
import com.kkirikkiri.user.entity.UserProfile;
import com.kkirikkiri.user.entity.UserRole;

/** Real JPA transactions and real files in a fresh JUnit temporary directory per test. */
@SpringBootTest(properties = {"spring.datasource.hikari.maximum-pool-size=12",
        "spring.datasource.hikari.transaction-isolation=TRANSACTION_READ_COMMITTED",
        "app.community.image-directory=unused-community-account-test"})
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CommunityAccountConcurrencyIntegrationTest {
    @Autowired CommunityPostService posts;
    @Autowired CommunityCommentService comments;
    @Autowired AdminService admins;
    @Autowired PostImageStorage storage;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @TempDir Path directory;

    @Autowired com.kkirikkiri.user.repository.UserRepository users;
    @Autowired com.kkirikkiri.global.security.JwtAuthenticationProvider authentication;
    @Autowired com.kkirikkiri.global.security.JwtTokenProvider tokens;
    @Autowired com.kkirikkiri.report.service.ReportService reports;
    Long authorId, otherId, adminId, postId, parentId, existingReplyId;

    @BeforeEach
    void fixtures() {
        // This context belongs only to this class; no test ever writes to the real upload directory.
        ReflectionTestUtils.setField(storage, "directory", directory.toAbsolutePath().normalize());
        tx(() -> {
            String suffix = UUID.randomUUID().toString();
            var author = new User("test-hash", suffix + "a@test.invalid");
            var other = new User("test-hash", suffix + "b@test.invalid");
            var admin = new User("test-hash", suffix + "c@test.invalid", UserRole.ADMIN);
            em.persist(author); em.persist(other); em.persist(admin);
            var dormitory = new Dormitory("ci-" + suffix.substring(0, 10), "ci-" + suffix.substring(0, 20), true);
            var faculty = new Faculty("ci-" + suffix, true);
            em.persist(dormitory); em.persist(faculty);
            em.persist(new UserProfile(author, suffix.substring(0, 16), "남성", dormitory, faculty,
                    null, (short) 2004, "24", "2학년", "INTJ"));
            var post = new CommunityPost(author, null, "자유게시판", "initial", "initial");
            em.persist(post);
            var parent = new CommunityComment(post, author, null, "parent"); em.persist(parent);
            var reply = new CommunityComment(post, author, parent, "existing reply"); em.persist(reply);
            authorId = author.getUserId(); otherId = other.getUserId(); adminId = admin.getUserId();
            postId = post.getPostId(); parentId = parent.getCommentId(); existingReplyId = reply.getCommentId();
            return null;
        });
    }


    enum Write { POST_CREATE, POST_UPDATE, POST_DELETE, POST_LIKE, POST_UNLIKE,
        COMMENT_CREATE, REPLY_CREATE, COMMENT_UPDATE, REPLY_UPDATE, COMMENT_DELETE, REPLY_DELETE,
        COMMENT_LIKE, COMMENT_UNLIKE, REPLY_LIKE, REPLY_UNLIKE, IMAGE_UPDATE, IMAGE_REMOVE, IMAGE_REPLACE,
        ANONYMOUS_POST, ANONYMOUS_COMMENT, ANONYMOUS_REPLY, REPORT }

    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> races() {
        return java.util.Arrays.stream(Write.values()).flatMap(write ->
            java.util.stream.Stream.of("SUSPENDED", "WITHDRAWN").flatMap(state ->
                java.util.stream.Stream.of(false, true).map(first ->
                    org.junit.jupiter.params.provider.Arguments.of(write, state, first))));
    }

    @ParameterizedTest(name = "{0}, state={1}, writeFirst={2}")
    @org.junit.jupiter.params.provider.MethodSource("races")
    void accountAndEveryCommunityWriteSerialize(Write write, String state, boolean writeFirst) throws Exception {
        seed(write);
        var before = snapshot();
        Runnable operation = () -> write(write, authorId);
        Runnable change = () -> change(state);
        if (writeFirst) contend(operation, change);
        else contend(change, () -> assertError(ErrorCode.UNAUTHORIZED, operation));
        assertEquals(state, accountStatus());
        if (writeFirst) assertWritten(write);
        else assertEquals(before, snapshot());
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"SUSPENDED,false", "WITHDRAWN,false", "SUSPENDED,true", "WITHDRAWN,true"})
    void authenticationAndCachedActiveEntityCannotAuthorizeLaterWrite(String state, boolean cached) {
        var token = tokens.createAccessToken(authorId, UserRole.USER);
        assertTrue(authentication.authenticate(token).isAuthenticated());
        if (cached) tx(() -> {
            assertEquals(com.kkirikkiri.user.entity.UserStatus.ACTIVE, em.find(User.class, authorId).getStatus());
            tx(() -> { change(state); return null; }); // Suspend the stale persistence context.
            assertError(ErrorCode.UNAUTHORIZED, () -> write(Write.POST_CREATE, authorId));
            return null;
        }, true);
        else {
            change(state);
            assertError(ErrorCode.UNAUTHORIZED, () -> write(Write.POST_CREATE, authorId));
        }
        assertEquals(state, accountStatus());
        assertEquals(1, count("community_post", "author_id", authorId));
        assertError(ErrorCode.UNAUTHORIZED, () -> authentication.authenticate(token));
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value=Write.class, names={"POST_UPDATE","POST_DELETE","COMMENT_UPDATE","REPLY_UPDATE","COMMENT_DELETE","REPLY_DELETE","IMAGE_UPDATE"})
    void ownershipIsStillRequiredAfterAccountLock(Write write) throws Exception {
        var before = snapshot();
        assertError(ErrorCode.FORBIDDEN, () -> write(write, otherId));
        assertEquals(before, snapshot());
        assertEquals("ACTIVE", accountStatus());
    }

    @org.junit.jupiter.api.Test
    void unrelatedAccountsAndPostsDoNotWait() throws Exception {
        Long independent = tx(() -> {
            var post = new CommunityPost(em.find(User.class, otherId), null, "자유게시판", "other", "other");
            em.persist(post); return post.getPostId();
        });
        CountDownLatch held = new CountDownLatch(1), release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> tx(() -> { write(Write.COMMENT_CREATE, authorId); held.countDown(); await(release); return null; }));
            try {
                await(held);
                executor.submit(() -> comments.createComment(otherId, independent,
                        new CommunityCommentCreateRequest("independent"))).get(5, TimeUnit.SECONDS);
            } finally { release.countDown(); }
            first.get(10, TimeUnit.SECONDS);
        }
        assertEquals(1, count("community_comment", "post_id", independent));
        assertEquals(3, count("community_comment", "post_id", postId));
        assertEquals("ACTIVE", accountStatus());
    }

    @ParameterizedTest
    @ValueSource(strings={"POST", "COMMENT"})
    void accountLockIsHeldBeforeWaitingOnContent(String target) throws Exception {
        CountDownLatch held=new CountDownLatch(1), release=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(3)) {
            var holder=executor.submit(() -> tx(() -> {
                if(target.equals("POST")) em.find(CommunityPost.class,postId,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
                else em.find(CommunityComment.class,parentId,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
                held.countDown(); await(release); return null;
            }));
            Future<?> writer=null, changer=null;
            try {
                await(held);
                writer=executor.submit(() -> write(target.equals("POST") ? Write.POST_UPDATE : Write.COMMENT_UPDATE, authorId));
                awaitDatabaseLockWait(writer);
                changer=executor.submit(() -> change("SUSPENDED"));
                long deadline=System.nanoTime()+Duration.ofSeconds(5).toNanos();
                while(jdbc.queryForObject("select count(*) from information_schema.sessions where blocker_id is not null",Integer.class)<2) {
                    assertFalse(changer.isDone()); assertTrue(System.nanoTime()<deadline); Thread.yield();
                }
            } finally { release.countDown(); }
            holder.get(10,TimeUnit.SECONDS); writer.get(10,TimeUnit.SECONDS); changer.get(10,TimeUnit.SECONDS);
        }
        assertEquals("SUSPENDED",accountStatus());
        assertWritten(target.equals("POST") ? Write.POST_UPDATE : Write.COMMENT_UPDATE);
    }

    @org.junit.jupiter.api.Test
    void accountTimeoutRollsBackEarlierDatabaseAndFileChanges() throws Exception {
        var before=snapshot();
        contendWithFailure(() -> users.findByIdForAccountUpdate(authorId), () ->
            assertThrows(PessimisticLockingFailureException.class, () -> tx(() -> {
                jdbc.execute("SET LOCK_TIMEOUT 100");
                try {
                    comments.createComment(otherId,postId,new CommunityCommentCreateRequest("rollback"));
                    storage.store(new MockMultipartFile("images","rollback.png","image/png",new byte[]{1,2}));
                    write(Write.POST_CREATE,authorId);
                    return null;
                } finally { jdbc.execute("SET LOCK_TIMEOUT 5000"); }
            })));
        assertEquals(before,snapshot());
        assertEquals("ACTIVE",accountStatus());
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    void reportSuspensionSharesTheSameAccountLock(boolean writeFirst) throws Exception {
        Long reportId=reports.createReport(otherId,new com.kkirikkiri.report.dto.ReportCreateRequest("USER",authorId,"욕설·비방",null));
        // As in the existing report concurrency fixtures, avoid timestamp precision/zone differences.
        jdbc.update("update report set created_at=? where report_id=?",
                java.sql.Timestamp.valueOf("2026-01-01 00:00:00"),reportId);
        Runnable create=() -> write(Write.COMMENT_CREATE,authorId);
        Runnable suspend=() -> admins.processReport(adminId,reportId,new AdminReportProcessRequest("SUSPEND_USER"));
        if(writeFirst) contend(create,suspend);
        else contend(suspend,() -> assertError(ErrorCode.UNAUTHORIZED,create));
        assertEquals("SUSPENDED",accountStatus());
        assertEquals(writeFirst ? 3 : 2,count("community_comment","post_id",postId));
        assertEquals("COMPLETED",jdbc.queryForObject("select status from report where report_id=?",String.class,reportId));
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    void administratorDeletionAndAccountGuardDoNotReverseLocks(boolean adminFirst) throws Exception {
        Runnable delete=()->admins.deletePost(adminId,postId);
        Runnable edit=()->write(Write.POST_UPDATE,authorId);
        if(adminFirst) contend(delete,()->assertError(ErrorCode.INVALID_REQUEST,edit));
        else contend(edit,delete);
        assertEquals("DELETED",jdbc.queryForObject("select status from community_post where post_id=?",String.class,postId));
        assertEquals("ACTIVE",accountStatus());
    }

    private void change(String state) {
        admins.updateMemberStatus(adminId,authorId,new com.kkirikkiri.admin.dto.AdminMemberStatusUpdateRequest(state));
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.NullSource
    @ValueSource(longs={0,-1,Long.MAX_VALUE})
    void invalidOrMissingAccountsCannotReachAnyWrite(Long userId) throws Exception {
        var before=snapshot();
        for(Write write:Write.values()) assertError(ErrorCode.UNAUTHORIZED,() -> write(write,userId));
        assertEquals(before,snapshot());
        assertEquals("ACTIVE",accountStatus());
    }

    @ParameterizedTest
    @ValueSource(strings={"SUSPENDED", "WITHDRAWN"})
    void repeatableReadStaleAccountConflictRollsBackAllWrites(String state) throws Exception {
        var before=snapshot();
        assertThrows(PessimisticLockingFailureException.class, () -> tx(
                TransactionDefinition.ISOLATION_REPEATABLE_READ, () -> {
                    em.find(User.class,authorId);
                    tx(() -> { change(state); return null; });
                    write(Write.COMMENT_CREATE,authorId);
                    return null;
                },false));
        assertEquals(state,accountStatus());
        assertEquals(before,snapshot());
    }

    @org.junit.jupiter.api.Test
    void reentrantAccountCheckDoesNotDiscardUncommittedSuspension() throws Exception {
        var before=snapshot();
        assertError(ErrorCode.UNAUTHORIZED, () -> tx(() -> {
            write(Write.IMAGE_UPDATE,authorId);
            change("SUSPENDED");
            write(Write.COMMENT_CREATE,authorId);
            return null;
        }));
        assertEquals("ACTIVE",accountStatus());
        assertEquals(before,snapshot());
    }

    @org.junit.jupiter.api.Test
    void sameAccountSerializesWritesEvenOnDifferentPosts() throws Exception {
        Long another=tx(() -> {
            var post=new CommunityPost(em.find(User.class,authorId),null,"자유게시판","another","another");
            em.persist(post); return post.getPostId();
        });
        contend(() -> write(Write.COMMENT_CREATE,authorId), () ->
                comments.createComment(authorId,another,new CommunityCommentCreateRequest("second")));
        assertEquals(3,count("community_comment","post_id",postId));
        assertEquals(1,count("community_comment","post_id",another));
        assertEquals("ACTIVE",accountStatus());
    }
    private void seed(Write write) {
        if(write==Write.IMAGE_REMOVE || write==Write.IMAGE_REPLACE) write(Write.IMAGE_UPDATE,authorId);
        if(write==Write.POST_UNLIKE) posts.likePost(authorId,postId);
        if(write==Write.COMMENT_UNLIKE) comments.likeComment(authorId,parentId);
        if(write==Write.REPLY_UNLIKE) comments.likeComment(authorId,existingReplyId);
    }
    private void write(Write write, Long userId) {
        var update=new CommunityPostUpdateRequest("자유게시판","changed","changed",List.of(),null);
        var comment=new CommunityCommentCreateRequest("new",write==Write.ANONYMOUS_COMMENT || write==Write.ANONYMOUS_REPLY);
        switch(write) {
            case POST_CREATE, ANONYMOUS_POST -> posts.createPost(userId,new CommunityPostCreateRequest("자유게시판","new","new",write==Write.ANONYMOUS_POST),null);
            case POST_UPDATE, IMAGE_REMOVE -> posts.updatePost(userId,postId,update,null);
            case POST_DELETE -> posts.deletePost(userId,postId);
            case POST_LIKE -> posts.likePost(userId,postId);
            case POST_UNLIKE -> posts.unlikePost(userId,postId);
            case COMMENT_CREATE, ANONYMOUS_COMMENT -> comments.createComment(userId,postId,comment);
            case REPLY_CREATE, ANONYMOUS_REPLY -> comments.createReply(userId,parentId,comment);
            case COMMENT_UPDATE, REPLY_UPDATE -> comments.updateComment(userId,write==Write.COMMENT_UPDATE ? parentId : existingReplyId,new com.kkirikkiri.community.comment.dto.CommunityCommentUpdateRequest("changed"));
            case COMMENT_DELETE, REPLY_DELETE -> comments.deleteComment(userId,write==Write.COMMENT_DELETE ? parentId : existingReplyId);
            case COMMENT_LIKE, REPLY_LIKE -> comments.likeComment(userId,write==Write.COMMENT_LIKE ? parentId : existingReplyId);
            case COMMENT_UNLIKE, REPLY_UNLIKE -> comments.unlikeComment(userId,write==Write.COMMENT_UNLIKE ? parentId : existingReplyId);
            case IMAGE_UPDATE, IMAGE_REPLACE -> posts.updatePost(userId,postId,update,new PostImagesRequest(List.of(new MockMultipartFile("images","new.png","image/png",new byte[]{1,2}))));
            case REPORT -> reports.createReport(userId,new com.kkirikkiri.report.dto.ReportCreateRequest("POST",postId,"욕설·비방",null));
        }
    }
    private void assertWritten(Write write) {
        switch(write) {
            case POST_CREATE, ANONYMOUS_POST -> assertEquals(2,count("community_post","author_id",authorId));
            case POST_UPDATE, IMAGE_UPDATE, IMAGE_REMOVE, IMAGE_REPLACE -> assertEquals("changed",jdbc.queryForObject("select title from community_post where post_id=?",String.class,postId));
            case POST_DELETE -> assertEquals("DELETED",jdbc.queryForObject("select status from community_post where post_id=?",String.class,postId));
            case POST_LIKE -> assertEquals(1,count("post_like","post_id",postId));
            case POST_UNLIKE -> assertEquals(0,count("post_like","post_id",postId));
            case COMMENT_CREATE, REPLY_CREATE, ANONYMOUS_COMMENT, ANONYMOUS_REPLY -> assertEquals(3,count("community_comment","post_id",postId));
            case COMMENT_UPDATE, REPLY_UPDATE -> assertEquals("changed",jdbc.queryForObject("select content from community_comment where comment_id=?",String.class,write==Write.COMMENT_UPDATE ? parentId : existingReplyId));
            case COMMENT_DELETE, REPLY_DELETE -> assertEquals("DELETED",jdbc.queryForObject("select status from community_comment where comment_id=?",String.class,write==Write.COMMENT_DELETE ? parentId : existingReplyId));
            case COMMENT_LIKE, REPLY_LIKE -> assertEquals(1,count("comment_like","user_id",authorId));
            case COMMENT_UNLIKE, REPLY_UNLIKE -> assertEquals(0,count("comment_like","user_id",authorId));
            case REPORT -> assertEquals(1,count("report","reporter_id",authorId));
        }
        if(write==Write.IMAGE_UPDATE || write==Write.IMAGE_REPLACE) assertEquals(1,count("post_image","post_id",postId));
        if(write==Write.IMAGE_REMOVE) assertEquals(0,count("post_image","post_id",postId));
    }
    private int count(String table,String column,Long id) {
        return jdbc.queryForObject("select count(*) from "+table+" where "+column+"=?",Integer.class,id);
    }
    private String accountStatus() { return jdbc.queryForObject("select status from user where user_id=?",String.class,authorId); }
    private List<Object> snapshot() throws Exception {
        try(var files=Files.list(directory)) {
            return List.of(jdbc.queryForList("select post_id,title,content,status from community_post where author_id=? order by post_id",authorId),
                jdbc.queryForList("select comment_id,content,status from community_comment where post_id=? order by comment_id",postId),
                jdbc.queryForList("select * from post_like where user_id=?",authorId),jdbc.queryForList("select * from comment_like where user_id=?",authorId),
                jdbc.queryForList("select * from post_image where post_id=?",postId),jdbc.queryForList("select * from report where reporter_id=?",authorId),files.map(Path::toString).sorted().toList());
        }
    }

    private <T> T tx(Supplier<T> action) { return tx(TransactionDefinition.ISOLATION_READ_COMMITTED, action, false); }
    private <T> T tx(Supplier<T> action, boolean rollback) {
        return tx(TransactionDefinition.ISOLATION_READ_COMMITTED, action, rollback);
    }
    private <T> T tx(int isolation, Supplier<T> action, boolean rollback) {
        var transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(isolation);
        return transaction.execute(status -> {
            if (rollback) status.setRollbackOnly();
            return action.get();
        });
    }
    private void contend(Runnable first, Runnable second) throws Exception {
        contend(first, second, true);
    }
    private void contendWithFailure(Runnable first, Runnable second) throws Exception {
        contend(first, second, false);
    }
    private void contend(Runnable first, Runnable second, boolean observeWait) throws Exception {
        CountDownLatch held = new CountDownLatch(1), release = new CountDownLatch(1), started = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<?> holder = executor.submit(() -> tx(() -> {
                first.run(); held.countDown(); await(release); return null;
            }));
            Future<?> contender = null;
            try {
                await(held);
                contender = executor.submit(() -> { started.countDown(); second.run(); });
                await(started);
                if (observeWait) awaitDatabaseLockWait(contender);
                // The shared account repository supplies its own 5000 ms lock hint.
                else contender.get(8, TimeUnit.SECONDS); // Failure must precede releasing the holder.
            } finally { release.countDown(); }
            holder.get(15, TimeUnit.SECONDS);
            if (contender != null) contender.get(15, TimeUnit.SECONDS);
        }
    }
    private void awaitDatabaseLockWait(Future<?> contender) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (jdbc.queryForObject("select count(*) from information_schema.sessions where blocker_id is not null",
                Integer.class) == 0) {
            if (contender.isDone()) {
                contender.get(1, TimeUnit.SECONDS);
                fail("Expected an actual row-lock wait before releasing the holder");
            }
            assertTrue(System.nanoTime() < deadline, "Expected H2 database lock wait");
            Thread.yield();
        }
        assertFalse(contender.isDone());
    }
    private static void await(CountDownLatch latch) {
        try { assertTrue(latch.await(10, TimeUnit.SECONDS), "Timed out coordinating transactions"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
    }
    private static void get(Future<?> future) {
        try { future.get(10, TimeUnit.SECONDS); }
        catch (Exception e) { throw new AssertionError(e); }
    }
    private static void assertError(ErrorCode code, Runnable action) {
        assertEquals(code, assertThrows(CustomException.class, action::run).getErrorCode());
    }
}
