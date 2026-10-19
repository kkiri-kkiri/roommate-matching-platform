package com.kkirikkiri.community;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.IntStream;

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
import org.springframework.web.multipart.MultipartFile;

import com.kkirikkiri.admin.dto.AdminReportProcessRequest;
import com.kkirikkiri.admin.service.AdminService;
import com.kkirikkiri.community.comment.dto.CommunityCommentCreateRequest;
import com.kkirikkiri.community.comment.entity.CommunityComment;
import com.kkirikkiri.community.comment.repository.CommunityCommentRepository;
import com.kkirikkiri.community.comment.service.CommunityCommentService;
import com.kkirikkiri.community.post.dto.CommunityPostCreateRequest;
import com.kkirikkiri.community.post.dto.CommunityPostUpdateRequest;
import com.kkirikkiri.community.post.dto.PostImagesRequest;
import com.kkirikkiri.community.post.entity.CommunityPost;
import com.kkirikkiri.community.post.repository.PostImageRepository;
import com.kkirikkiri.community.post.service.CommunityPostService;
import com.kkirikkiri.community.post.service.PostImageStorage;
import com.kkirikkiri.global.exception.CustomException;
import com.kkirikkiri.global.exception.ErrorCode;
import com.kkirikkiri.report.entity.Report;
import com.kkirikkiri.user.entity.Dormitory;
import com.kkirikkiri.user.entity.Faculty;
import com.kkirikkiri.user.entity.User;
import com.kkirikkiri.user.entity.UserProfile;
import com.kkirikkiri.user.entity.UserRole;

/** Real JPA transactions and real files in a fresh JUnit temporary directory per test. */
@SpringBootTest(properties = {"spring.datasource.hikari.maximum-pool-size=12",
        "spring.datasource.hikari.transaction-isolation=TRANSACTION_READ_COMMITTED",
        "app.community.image-directory=unused-community-creation-test"})
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CommunityCreationAndImageConcurrencyIntegrationTest {
    @Autowired CommunityPostService posts;
    @Autowired CommunityCommentService comments;
    @Autowired CommunityCommentRepository commentRepository;
    @Autowired AdminService admins;
    @Autowired PostImageStorage storage;
    @Autowired PostImageRepository imageRepository;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @TempDir Path directory;

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
            var reply = new CommunityComment(post, other, parent, "existing reply"); em.persist(reply);
            authorId = author.getUserId(); otherId = other.getUserId(); adminId = admin.getUserId();
            postId = post.getPostId(); parentId = parent.getCommentId(); existingReplyId = reply.getCommentId();
            return null;
        });
    }

    @ParameterizedTest(name = "{0}, reply={1}, createFirst={2}, anonymous={3}")
    @CsvSource({
            "USER,false,false,false", "USER,false,false,true", "USER,false,true,false", "USER,false,true,true",
            "ADMIN,false,false,false", "ADMIN,false,false,true", "ADMIN,false,true,false", "ADMIN,false,true,true",
            "REPORT,false,false,false", "REPORT,false,false,true", "REPORT,false,true,false", "REPORT,false,true,true",
            "USER,true,false,false", "USER,true,false,true", "USER,true,true,false", "USER,true,true,true",
            "ADMIN,true,false,false", "ADMIN,true,false,true", "ADMIN,true,true,false", "ADMIN,true,true,true",
            "REPORT,true,false,false", "REPORT,true,false,true", "REPORT,true,true,false", "REPORT,true,true,true"})
    void creationAndPostDeletionLeaveNoActiveComments(String deletion, boolean reply, boolean createFirst,
            boolean anonymous) throws Exception {
        Long reportId = deletion.equals("REPORT") ? report(false) : null;
        AtomicReference<Long> created = new AtomicReference<>();
        Runnable create = () -> created.set(create(reply, anonymous));
        Runnable delete = () -> deletePost(deletion, reportId);
        contend(createFirst ? create : delete, createFirst ? delete : () ->
                assertError(ErrorCode.INVALID_REQUEST, create));
        assertEquals("DELETED", postStatus(postId));
        assertEquals(0, activeComments(postId));
        assertEquals(createFirst ? 3 : 2, commentCount(postId));
        if (createFirst) assertEquals("DELETED", commentStatus(created.get()));
        else assertNull(created.get());
        assertRelationsConsistent();
    }

    @ParameterizedTest(name = "{0}, createFirst={1}, anonymous={2}")
    @CsvSource({"USER,false,false", "USER,false,true", "USER,true,false", "USER,true,true",
            "ADMIN,false,false", "ADMIN,false,true", "ADMIN,true,false", "ADMIN,true,true",
            "REPORT,false,false", "REPORT,false,true", "REPORT,true,false", "REPORT,true,true"})
    void replyCreationAndParentDeletionSoftDeleteTheEntireThread(String deletion, boolean createFirst,
            boolean anonymous) throws Exception {
        Long unrelated = comments.createComment(otherId, postId, new CommunityCommentCreateRequest("unrelated"));
        Long unrelatedReply = comments.createReply(otherId, unrelated, new CommunityCommentCreateRequest("unrelated reply"));
        Long reportId = deletion.equals("REPORT") ? report(true) : null;
        AtomicReference<Long> created = new AtomicReference<>();
        Runnable create = () -> created.set(create(true, anonymous));
        Runnable delete = () -> deleteParent(deletion, reportId);
        contend(createFirst ? create : delete, createFirst ? delete : () ->
                assertError(ErrorCode.INVALID_REQUEST, create));
        assertEquals("ACTIVE", postStatus(postId));
        assertEquals("DELETED", commentStatus(parentId));
        assertEquals("DELETED", commentStatus(existingReplyId));
        if (createFirst) assertEquals("DELETED", commentStatus(created.get()));
        else assertNull(created.get());
        assertEquals(0, activeReplies(parentId));
        assertEquals("ACTIVE", commentStatus(unrelated));
        assertEquals("ACTIVE", commentStatus(unrelatedReply));
        var visibleIds = posts.getPost(null, postId).comments().stream().map(c -> c.id()).toList();
        assertEquals(Set.of(unrelated, unrelatedReply), new HashSet<>(visibleIds));
        assertRelationsConsistent();
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PARENT", "REPLY"})
    void committedDeletionAndReplyDepthAreRejectedWithoutInserts(String target) {
        if (target.equals("POST")) posts.deletePost(authorId, postId);
        if (target.equals("PARENT")) comments.deleteComment(authorId, parentId);
        int before = commentCount(postId);
        if (target.equals("POST")) assertError(ErrorCode.INVALID_REQUEST, () -> create(false, false));
        Long id = target.equals("REPLY") ? existingReplyId : parentId;
        assertError(ErrorCode.INVALID_REQUEST, () -> comments.createReply(otherId, id,
                new CommunityCommentCreateRequest("must not exist", true)));
        assertEquals(before, commentCount(postId));
        assertRelationsConsistent();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void previouslyLoadedPostAndParentCannotAuthorizeCreationAfterDeletion(boolean parentOnly) throws Exception {
        try (var executor = Executors.newSingleThreadExecutor()) {
            tx(() -> {
                em.find(CommunityPost.class, postId);
                em.find(CommunityComment.class, parentId);
                Future<?> deletion = executor.submit(() -> {
                    if (parentOnly) admins.deleteComment(adminId, parentId);
                    else admins.deletePost(adminId, postId);
                });
                get(deletion);
                // Assert outside the joining service transaction so it can fully roll back.
                assertError(ErrorCode.INVALID_REQUEST, () -> create(true, false));
                return null;
            }, true);
        }
        assertRelationsConsistent();
        assertEquals(2, commentCount(postId));
    }

    @ParameterizedTest
    @ValueSource(strings = {"USER", "ADMIN", "REPORT"})
    void parentDeletionIncludesRepliesCommittedAfterEarlierEntityAndListReads(String deletion) throws Exception {
        Long reportId = deletion.equals("REPORT") ? report(true) : null;
        AtomicReference<Long> created = new AtomicReference<>();
        try (var executor = Executors.newSingleThreadExecutor()) {
            tx(() -> {
                em.find(CommunityComment.class, parentId);
                // Deliberately cache the parent and read its earlier reply list in this transaction.
                jdbc.queryForObject("select count(*) from community_comment where post_id=?", Integer.class, postId);
                get(executor.submit(() -> created.set(create(true, false))));
                deleteParent(deletion, reportId);
                return null;
            });
        }
        assertEquals("DELETED", commentStatus(parentId));
        assertEquals("DELETED", commentStatus(existingReplyId));
        assertEquals("DELETED", commentStatus(created.get()));
        assertEquals(0, activeReplies(parentId));
        assertRelationsConsistent();
    }

    @Test
    void h2RepeatableReadIsNotAnInnoDbCurrentReadOracleForNewReplyRows() throws Exception {
        AtomicReference<Long> created = new AtomicReference<>();
        try (var executor = Executors.newSingleThreadExecutor()) {
            tx(TransactionDefinition.ISOLATION_REPEATABLE_READ, () -> {
                em.find(CommunityComment.class, parentId);
                jdbc.queryForObject("select count(*) from community_comment where post_id=?", Integer.class, postId);
                get(executor.submit(() -> created.set(create(true, false))));
                var lockedIds = commentRepository.findRepliesForUpdate(parentId,
                        com.kkirikkiri.community.common.entity.CommunityContentStatus.ACTIVE)
                        .stream().map(CommunityComment::getCommentId).toList();
                // H2's old RR snapshot omits the new row even with FOR UPDATE. Never treat this
                // database-specific diagnostic as proof of InnoDB current-read behavior.
                assertFalse(lockedIds.contains(created.get()));
                return null;
            }, true);
        }
        // The diagnostic only reads/rolls back; no deleted parent or orphan is committed.
        assertEquals("ACTIVE", commentStatus(parentId));
        assertEquals("ACTIVE", commentStatus(created.get()));
        assertRelationsConsistent();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void h2RepeatableReadCreationConflictDoesNotLeaveAnOrphan(boolean reply) throws Exception {
        contend(() -> posts.deletePost(authorId, postId), () ->
                assertThrows(PessimisticLockingFailureException.class, () ->
                        tx(TransactionDefinition.ISOLATION_REPEATABLE_READ, () -> create(reply, true), false)));
        assertEquals("DELETED", postStatus(postId));
        assertEquals(0, activeComments(postId));
        assertEquals(2, commentCount(postId));
        assertRelationsConsistent();
    }

    @Test
    void creationLockTimeoutRollsBackEarlierInsertAndNewFile() throws Exception {
        Long otherPost = newPost();
        contendWithFailure(() -> posts.deletePost(authorId, postId), () -> {
            assertThrows(PessimisticLockingFailureException.class, () -> tx(() -> {
                comments.createComment(otherId, otherPost, new CommunityCommentCreateRequest("roll back"));
                storage.store(file("rollback", 0));
                em.flush();
                jdbc.execute("SET LOCK_TIMEOUT 100");
                try { return create(false, false); }
                finally { jdbc.execute("SET LOCK_TIMEOUT 2000"); }
            }));
        });
        assertEquals(0, commentCount(otherPost));
        assertEquals(Set.of(), files());
        assertRelationsConsistent();
    }

    @ParameterizedTest
    @CsvSource({"4,1", "3,2"})
    void concurrentImageReplacementsNeverExceedFiveAndUseLatestConnections(int initial, int added) throws Exception {
        seedImages(initial);
        List<Long> keep = imageIds(postId);
        contend(() -> update(postId, keep, added, "first"), () -> update(postId, keep, added, "second"));
        var rows = images(postId);
        assertEquals(5, rows.size());
        assertEquals(keep, rows.subList(0, initial).stream().map(ImageRow::id).toList());
        assertTrue(rows.subList(initial, 5).stream().allMatch(i -> i.name().startsWith("second")));
        // keepImageIds is the full replacement list. Omitting the first upload removes its DB link.
        assertTrue(rows.stream().noneMatch(i -> i.name().startsWith("first")));
        assertImageIntegrity(postId);
        // Successful unlinking retains physical files under the existing policy (#9 is out of scope).
        assertEquals(initial + 2 * added, files().size());
    }

    @Test
    void fiveKeptImagesPlusOneIsRejectedBeforeAnyFileIsWritten() throws Exception {
        seedImages(5);
        var before = images(postId); var beforeFiles = files();
        assertError(ErrorCode.VALIDATION_ERROR, () -> update(postId, imageIds(postId), 1, "rejected"));
        assertEquals(before, images(postId)); assertEquals(beforeFiles, files());
        assertEquals("seed", postTitle());
        assertImageIntegrity(postId);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void concurrentAddAndRemoveValidateAgainstCurrentImageConnections(boolean addFirst) throws Exception {
        seedImages(4);
        var keep = imageIds(postId); var remaining = keep.subList(0, 2);
        Runnable add = () -> update(postId, keep, 1, "add");
        Runnable remove = () -> update(postId, remaining, 0, "remove");
        contend(addFirst ? add : remove, addFirst ? remove : () -> assertError(ErrorCode.INVALID_REQUEST, add));
        assertEquals(remaining, imageIds(postId));
        assertEquals("remove", postTitle());
        assertEquals(addFirst ? 5 : 4, files().size());
        assertImageIntegrity(postId);
    }

    @ParameterizedTest
    @CsvSource({"USER,false", "USER,true", "ADMIN,false", "ADMIN,true", "REPORT,false", "REPORT,true"})
    void imageAdditionAndPostDeletionUseTheSamePostLock(String deletion, boolean addFirst) throws Exception {
        seedImages(4);
        var keep = imageIds(postId);
        Long reportId = deletion.equals("REPORT") ? report(false) : null;
        Runnable add = () -> update(postId, keep, 1, "add");
        Runnable delete = () -> deletePost(deletion, reportId);
        contend(addFirst ? add : delete, addFirst ? delete : () -> assertError(ErrorCode.INVALID_REQUEST, add));
        assertEquals("DELETED", postStatus(postId));
        assertEquals(addFirst ? 5 : 4, images(postId).size());
        assertEquals(addFirst ? 5 : 4, files().size());
        assertImageIntegrity(postId);
        assertRelationsConsistent();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void imageAdditionAndOrdinaryEditApplySerialReplacementSemantics(boolean addFirst) throws Exception {
        seedImages(4);
        var keep = imageIds(postId);
        Runnable add = () -> update(postId, keep, 1, "add");
        Runnable edit = () -> update(postId, keep, 0, "edit");
        contend(addFirst ? add : edit, addFirst ? edit : add);
        assertEquals(addFirst ? 4 : 5, images(postId).size());
        assertEquals(addFirst ? "edit" : "add", postTitle());
        assertImageIntegrity(postId);
    }

    @Test
    void limitFailureRollsBackAnEarlierSuccessfulUploadInTheSameTransaction() throws Exception {
        seedImages(4);
        var before = images(postId); var beforeFiles = files();
        assertError(ErrorCode.VALIDATION_ERROR, () -> tx(() -> {
            update(postId, imageIds(postId), 1, "rolled back upload");
            em.flush();
            assertEquals(5, images(postId).size());
            update(postId, imageIds(postId), 1, "over limit");
            return null;
        }));
        assertEquals(before, images(postId)); assertEquals(beforeFiles, files());
        assertEquals("seed", postTitle());
        assertImageIntegrity(postId);
    }

    @Test
    void copyFailureAfterFirstNewFileRestoresDbAndCleansOnlyNewFiles() throws Exception {
        seedImages(3);
        var before = images(postId); var beforeFiles = files();
        MultipartFile broken = new MockMultipartFile("images", "broken.png", "image/png", new byte[]{1}) {
            @Override public InputStream getInputStream() {
                return new InputStream() {
                    @Override public int read() throws IOException { throw new IOException("test copy failure"); }
                };
            }
        };
        assertError(ErrorCode.INTERNAL_SERVER_ERROR, () -> posts.updatePost(authorId, postId,
                request("failed", List.of(before.getFirst().id())),
                new PostImagesRequest(List.of(file("copied before failure", 0), broken))));
        assertEquals(before, images(postId)); assertEquals(beforeFiles, files());
        assertEquals("seed", postTitle());
        assertImageIntegrity(postId);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5})
    void createAndUpdateSupportAllImageCountBoundaries(int count) throws Exception {
        Long id = posts.createPost(authorId, new CommunityPostCreateRequest("자유게시판", "created", "content", true),
                uploads("created", count));
        assertEquals(count, images(id).size()); assertImageIntegrity(id);
        var keep = imageIds(id);
        update(id, keep, 5 - count, "filled");
        assertEquals(5, images(id).size()); assertImageIntegrity(id);
        var detail = posts.getPost(null, id);
        assertEquals(5, detail.images().size()); assertTrue(detail.anonymous());
        assertEquals(imageIds(id), detail.images().stream().map(i -> i.id()).toList());
    }

    @Test
    void differentUsersPostsCanUploadWhileAnotherPostTransactionIsHeld() throws Exception {
        Long otherPost = tx(() -> {
            var post = new CommunityPost(em.find(User.class, otherId), null, "자유게시판", "other", "other");
            em.persist(post); return post.getPostId();
        });
        CountDownLatch held = new CountDownLatch(1), release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<?> first = executor.submit(() -> tx(() -> {
                update(postId, List.of(), 2, "first"); held.countDown(); await(release); return null;
            }));
            try {
                await(held);
                Future<?> second = executor.submit(() -> posts.updatePost(otherId, otherPost, request("second", List.of()), uploads("second", 2)));
                second.get(5, TimeUnit.SECONDS); // Must finish before the first transaction is released.
                assertEquals(2, images(otherPost).size());
            } finally { release.countDown(); }
            first.get(15, TimeUnit.SECONDS);
        }
        assertEquals(2, images(postId).size());
        assertImageIntegrity(postId); assertImageIntegrity(otherPost);
    }

    @Test
    void cachedPostAndImagesAreRefreshedBeforeApplyingTheNextReplacement() throws Exception {
        seedImages(3);
        var original = imageIds(postId);
        try (var executor = Executors.newSingleThreadExecutor()) {
            tx(() -> {
                em.find(CommunityPost.class, postId);
                imageRepository.findAllByPost_PostIdOrderBySortOrderAscPostImageIdAsc(postId);
                get(executor.submit(() -> {
                    update(postId, List.of(original.getFirst(), original.getLast()), 1, "other");
                    posts.getPost(null, postId);
                }));
                var latest = imageIds(postId);
                assertFalse(latest.contains(original.get(1)));
                update(postId, latest, 2, "latest");
                return null;
            });
        }
        assertEquals(5, images(postId).size()); assertImageIntegrity(postId);
        assertEquals(1, jdbc.queryForObject("select view_count from community_post where post_id=?", Integer.class, postId));
        assertEquals("latest", postTitle());
    }

    @Test
    void imagePermissionAndForeignKeepIdFailuresWriteNoFiles() throws Exception {
        seedImages(1);
        Long otherPost = newPost();
        var before = images(postId); var beforeFiles = files();
        assertError(ErrorCode.FORBIDDEN, () -> posts.updatePost(otherId, postId,
                request("forbidden", imageIds(postId)), uploads("forbidden", 1)));
        assertError(ErrorCode.INVALID_REQUEST, () -> update(otherPost, imageIds(postId), 1, "foreign"));
        assertEquals(before, images(postId)); assertEquals(beforeFiles, files());
        assertTrue(images(otherPost).isEmpty());
    }

    private Long create(boolean reply, boolean anonymous) {
        var request = new CommunityCommentCreateRequest("concurrent comment", anonymous);
        return reply ? comments.createReply(otherId, parentId, request) : comments.createComment(otherId, postId, request);
    }

    private Long report(boolean comment) {
        return tx(() -> {
            var reporter = em.find(User.class, otherId);
            var report = comment ? Report.forComment(reporter, em.find(CommunityComment.class, parentId), "욕설·비방", null)
                    : Report.forPost(reporter, em.find(CommunityPost.class, postId), "욕설·비방", null);
            em.persist(report);
            jdbc.update("update report set created_at=? where report_id=?",
                    java.sql.Timestamp.valueOf("2026-01-01 00:00:00"), report.getReportId());
            return report.getReportId();
        });
    }

    private void deletePost(String deletion, Long reportId) {
        switch (deletion) {
            case "USER" -> posts.deletePost(authorId, postId);
            case "ADMIN" -> admins.deletePost(adminId, postId);
            case "REPORT" -> admins.processReport(adminId, reportId, new AdminReportProcessRequest("DELETE_TARGET"));
            default -> throw new AssertionError(deletion);
        }
    }

    private void deleteParent(String deletion, Long reportId) {
        switch (deletion) {
            case "USER" -> comments.deleteComment(authorId, parentId);
            case "ADMIN" -> admins.deleteComment(adminId, parentId);
            case "REPORT" -> admins.processReport(adminId, reportId, new AdminReportProcessRequest("DELETE_TARGET"));
            default -> throw new AssertionError(deletion);
        }
    }

    private Long newPost() {
        return tx(() -> {
            var post = new CommunityPost(em.find(User.class, authorId), null, "자유게시판", "other", "other");
            em.persist(post); return post.getPostId();
        });
    }

    private void seedImages(int count) { update(postId, List.of(), count, "seed"); }
    private void update(Long id, List<Long> keep, int count, String tag) {
        posts.updatePost(authorId, id, request(tag, keep), uploads(tag, count));
    }
    private CommunityPostUpdateRequest request(String tag, List<Long> keep) {
        return new CommunityPostUpdateRequest("자유게시판", tag, tag, keep, null);
    }
    private PostImagesRequest uploads(String tag, int count) {
        return new PostImagesRequest(IntStream.range(0, count).mapToObj(i -> (MultipartFile) file(tag, i)).toList());
    }
    private MockMultipartFile file(String tag, int index) {
        return new MockMultipartFile("images", tag + "-" + index + ".png", "image/png", new byte[]{1, 2, (byte) index});
    }

    private record ImageRow(Long id, int order, String url, String name) {}
    private List<ImageRow> images(Long id) {
        return jdbc.query("select post_image_id,sort_order,image_url,original_name from post_image where post_id=? order by sort_order,post_image_id",
                (rs, n) -> new ImageRow(rs.getLong(1), rs.getInt(2), rs.getString(3), rs.getString(4)), id);
    }
    private List<Long> imageIds(Long id) { return images(id).stream().map(ImageRow::id).toList(); }
    private void assertImageIntegrity(Long id) throws IOException {
        var rows = images(id);
        assertTrue(rows.size() <= 5);
        assertEquals(IntStream.range(0, rows.size()).boxed().toList(), rows.stream().map(ImageRow::order).toList());
        assertEquals(rows.size(), rows.stream().map(ImageRow::id).distinct().count());
        assertEquals(rows.size(), rows.stream().map(ImageRow::url).distinct().count());
        for (var row : rows) {
            Path saved = directory.resolve(row.url().substring(row.url().lastIndexOf('/') + 1));
            assertTrue(Files.isRegularFile(saved));
            assertEquals(3, Files.size(saved));
        }
    }
    private Set<String> files() throws IOException {
        try (var paths = Files.list(directory)) {
            return paths.map(p -> p.getFileName().toString()).collect(java.util.stream.Collectors.toSet());
        }
    }
    private String postStatus(Long id) {
        return jdbc.queryForObject("select status from community_post where post_id=?", String.class, id);
    }
    private String commentStatus(Long id) {
        return jdbc.queryForObject("select status from community_comment where comment_id=?", String.class, id);
    }
    private String postTitle() {
        return jdbc.queryForObject("select title from community_post where post_id=?", String.class, postId);
    }
    private int activeComments(Long id) {
        return jdbc.queryForObject("select count(*) from community_comment where post_id=? and status='ACTIVE'", Integer.class, id);
    }
    private int activeReplies(Long id) {
        return jdbc.queryForObject("select count(*) from community_comment where parent_comment_id=? and status='ACTIVE'", Integer.class, id);
    }
    private int commentCount(Long id) {
        return jdbc.queryForObject("select count(*) from community_comment where post_id=?", Integer.class, id);
    }
    private void assertRelationsConsistent() {
        assertEquals(0, jdbc.queryForObject("""
                select count(*) from community_comment c join community_post p on p.post_id=c.post_id
                left join community_comment parent on parent.comment_id=c.parent_comment_id
                where c.post_id=? and c.status='ACTIVE'
                and (p.status<>'ACTIVE' or (c.parent_comment_id is not null and parent.status<>'ACTIVE'))
                """, Integer.class, postId));
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
                else contender.get(5, TimeUnit.SECONDS); // The deliberate timeout must fail before releasing the holder.
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
