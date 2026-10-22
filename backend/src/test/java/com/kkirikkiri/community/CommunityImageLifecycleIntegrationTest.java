package com.kkirikkiri.community;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.kkirikkiri.community.post.dto.*;
import com.kkirikkiri.community.post.entity.*;
import com.kkirikkiri.community.post.repository.*;
import com.kkirikkiri.community.post.service.*;
import com.kkirikkiri.global.security.AuthenticatedUser;
import com.kkirikkiri.report.entity.Report;
import com.kkirikkiri.user.entity.*;

/** Real H2 transactions, real temporary files, and the production security/MVC chain. */
@SpringBootTest(properties = {"spring.datasource.hikari.maximum-pool-size=8",
        "spring.datasource.hikari.transaction-isolation=TRANSACTION_READ_COMMITTED",
        "app.community.image-directory=unused-image-lifecycle-test"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CommunityImageLifecycleIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @Autowired JdbcTemplate jdbc;
    @Autowired CommunityPostService posts;
    @Autowired PostImageStorage storage;
    @Autowired PostImageRepository images;
    @Autowired PostImageCleanupRepository jobs;
    @Autowired PostImageCleanupService cleanup;
    @Autowired PostImageCleanupWorker worker;
    @TempDir Path directory;
    Long owner, visitor, admin, post, anonymous;

    @BeforeEach void fixture() {
        ReflectionTestUtils.setField(storage, "directory", directory.toAbsolutePath().normalize());
        // Each case has its own files; never retry another case's queue against this root.
        jobs.deleteAll();
        tx(() -> {
            String key = UUID.randomUUID().toString();
            User a = new User("hash", key + "a@test.invalid");
            User b = new User("hash", key + "b@test.invalid");
            User c = new User("hash", key + "c@test.invalid", UserRole.ADMIN);
            em.persist(a); em.persist(b); em.persist(c);
            var dorm = new Dormitory(key.substring(0, 15), key.substring(0, 15), true);
            var faculty = new Faculty(key, true);
            em.persist(dorm); em.persist(faculty);
            em.persist(new UserProfile(a, key.substring(0, 15), "남성", dorm, faculty,
                    null, (short) 2004, "24", "2학년", "INTJ"));
            owner = a.getUserId(); visitor = b.getUserId(); admin = c.getUserId();
            return null;
        });
        post = create(false, 2);
        anonymous = create(true, 1);
    }

    private <T> T tx(Supplier<T> action) {
        return new TransactionTemplate(manager).execute(status -> action.get());
    }
    private Long create(boolean anonymous, int count) {
        return posts.createPost(owner, new CommunityPostCreateRequest("자유게시판", "title", "body", anonymous),
                new PostImagesRequest(java.util.stream.IntStream.range(0, count).mapToObj(i -> file(i))
                        .map(f -> (org.springframework.web.multipart.MultipartFile) f).toList()));
    }
    private MockMultipartFile file(int n) {
        return new MockMultipartFile("images", "../../photo" + n + ".png", "image/png", new byte[]{1, 2, (byte)n});
    }
    private List<PostImage> rows(Long id) {
        return images.findAllByPost_PostIdOrderBySortOrderAscPostImageIdAsc(id);
    }
    private Path path(String url) { return directory.resolve(url.substring(url.lastIndexOf('/') + 1)); }
    private void update(List<Long> keep, int added) {
        posts.updatePost(owner, post, new CommunityPostUpdateRequest("자유게시판", "edited", "body", keep, null),
                new PostImagesRequest(java.util.stream.IntStream.range(0, added).mapToObj(i -> file(i))
                        .map(f -> (org.springframework.web.multipart.MultipartFile) f).toList()));
    }
    private void visible(String url) throws Exception {
        mvc.perform(get(url)).andExpect(status().isOk()).andExpect(content().contentType("image/png"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().doesNotExist("ETag"))
                .andExpect(content().bytes(Files.readAllBytes(path(url))));
    }
    private void hidden(String url) throws Exception {
        mvc.perform(get(url).header("If-None-Match", "old-tag").header("If-Modified-Since", "Wed, 21 Oct 2099 07:28:00 GMT"))
                .andExpect(status().isNotFound()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(""));
    }
    private org.springframework.test.web.servlet.request.RequestPostProcessor as(Long id) {
        String role = id.equals(admin) ? "ADMIN" : "USER";
        return authentication(new UsernamePasswordAuthenticationToken(new AuthenticatedUser(id, role), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }
    private void delete(String type) throws Exception {
        switch (type) {
            case "USER" -> mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .delete("/api/posts/" + post).with(as(owner))).andExpect(status().isOk());
            case "ADMIN" -> mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .delete("/api/admin/posts/" + post).with(as(admin))).andExpect(status().isOk());
            case "REPORT" -> {
                Long report = tx(() -> {
                    var r = Report.forPost(em.find(User.class, visitor), em.find(CommunityPost.class, post), "욕설·비방", null);
                    em.persist(r); em.flush();
                    // Historical fixture, as in the existing concurrency tests: avoid DATETIME rounding
                    // turning this test of image deletion into Report's wall-clock boundary test.
                    jdbc.update("update report set created_at=? where report_id=?",
                            java.sql.Timestamp.valueOf("2026-01-01 00:00:00"), r.getReportId());
                    em.refresh(r);
                    return r.getReportId();
                });
                mvc.perform(patch("/api/admin/reports/" + report).with(as(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"DELETE_TARGET\"}"))
                        .andExpect(status().isOk());
            }
            default -> fail(type);
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void publicAnonymousAndMultipleImagesSurviveNewStorageInstance(boolean anonymousPost) throws Exception {
        var rows = rows(anonymousPost ? anonymous : post);
        for (int i = 0; i < rows.size(); i++) {
            assertEquals(i, rows.get(i).getSortOrder());
            visible(rows.get(i).getImageUrl());
            var restarted = new PostImageAccessService(images, new PostImageStorage(directory.toString()));
            assertArrayEquals(Files.readAllBytes(path(rows.get(i).getImageUrl())),
                    restarted.read(path(rows.get(i).getImageUrl()).getFileName().toString()).orElseThrow());
        }
    }

    @Test void committedMultipartReplacementCleansOnlyRemovedImageAndPreservesUrlsAndOrder() throws Exception {
        var before = rows(post);
        String removed = before.getFirst().getImageUrl();
        String kept = before.getLast().getImageUrl();
        String request = "{\"category\":\"자유게시판\",\"title\":\"edited\",\"content\":\"body\",\"keepImageIds\":["
                + before.getLast().getPostImageId() + "]}";
        mvc.perform(multipart("/api/posts/" + post).file(file(9))
                .file(new MockMultipartFile("request", "blob", "application/json", request.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .with(r -> { r.setMethod("PATCH"); return r; }).with(as(owner))).andExpect(status().isOk());
        assertFalse(Files.exists(path(removed))); hidden(removed); visible(kept);
        var after = rows(post);
        assertEquals(2, after.size());
        assertEquals(kept, after.getFirst().getImageUrl());
        assertEquals(List.of(0, 1), after.stream().map(PostImage::getSortOrder).toList());
        visible(after.getLast().getImageUrl());
        visible(rows(anonymous).getFirst().getImageUrl());
        assertEquals(0, jobs.count());
    }

    @Test void removedFilesAndNewUploadsRespectOuterRollbackAndUncommittedVisibility() throws Exception {
        var old = rows(post);
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            update(List.of(), 1);
            em.flush();
            for (var image : old) assertTrue(Files.exists(path(image.getImageUrl())));
            // A separate request/transaction still sees committed associations, not this pending unlink.
            try (var executor = Executors.newSingleThreadExecutor()) {
                executor.submit(() -> { visible(old.getFirst().getImageUrl()); return null; }).get(10, TimeUnit.SECONDS);
            } catch (Exception e) { throw new AssertionError(e); }
            assertEquals(2, jobs.count());
            status.setRollbackOnly();
        });
        assertEquals(old.stream().map(PostImage::getImageUrl).toList(), rows(post).stream().map(PostImage::getImageUrl).toList());
        assertEquals(0, jobs.count());
        try (var files = Files.list(directory)) { assertEquals(3, files.count()); }
        for (var image : old) visible(image.getImageUrl());
    }

    @ParameterizedTest @ValueSource(strings = {"USER", "ADMIN", "REPORT"})
    void allDeletionRoutesDenyPublicImagesAndRetainFilesAndMetadata(String type) throws Exception {
        var old = rows(post);
        delete(type);
        assertEquals(old.size(), rows(post).size());
        assertEquals(0, jobs.count());
        for (var image : old) {
            assertTrue(Files.exists(path(image.getImageUrl())));
            hidden(image.getImageUrl());
            mvc.perform(get(image.getImageUrl()).with(as(admin))).andExpect(status().isNotFound());
        }
        cleanup.retryPending();
        for (var image : old) assertTrue(Files.exists(path(image.getImageUrl())));
        visible(rows(anonymous).getFirst().getImageUrl());
    }

    @ParameterizedTest @ValueSource(strings = {"USER", "ADMIN", "REPORT"})
    void failedDeletionTransactionRestoresPublicAccessAndRetainsFiles(String type) throws Exception {
        var old = rows(post);
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            try { delete(type); } catch (Exception e) { throw new AssertionError(e); }
            em.flush(); status.setRollbackOnly();
        });
        for (var image : old) visible(image.getImageUrl());
        assertEquals(0, jobs.count());
    }

    private record FailedCleanup(String url, byte[] bytes, Long job) { }
    private FailedCleanup failCleanup() throws Exception {
        var image = rows(post).getFirst();
        String url = image.getImageUrl(); byte[] bytes = Files.readAllBytes(path(url));
        // A nonempty directory at the target deterministically causes a real deletion failure on Windows/Linux.
        Files.delete(path(url)); Files.createDirectory(path(url)); Files.write(path(url).resolve("blocker"), bytes);
        update(List.of(rows(post).getLast().getPostImageId()), 0);
        var job = jobs.findAll().getFirst();
        assertEquals(1, job.getAttempts()); hidden(url);
        return new FailedCleanup(url, bytes, job.getCleanupId());
    }
    private void repair(FailedCleanup failure) throws IOException {
        Files.delete(path(failure.url()).resolve("blocker")); Files.delete(path(failure.url()));
        Files.write(path(failure.url()), failure.bytes());
        jdbc.update("update post_image_cleanup set next_attempt_at=? where cleanup_id=?",
                LocalDateTime.now().minusMinutes(2), failure.job());
    }

    @Test void failedPhysicalDeletionIsDurableAndFreshWorkerRetriesWithoutRequestFailure() throws Exception {
        var failure = failCleanup(); repair(failure);
        var freshWorker = new PostImageCleanupWorker(jobs, images, new PostImageStorage(directory.toString()), manager);
        new PostImageCleanupService(jobs, freshWorker).retryPending();
        assertFalse(Files.exists(path(failure.url()))); assertEquals(0, jobs.count()); hidden(failure.url());
        visible(rows(post).getFirst().getImageUrl());
    }

    @Test void workerPreservesFutureRetryRowAndRepairedFileUntilDeadline() throws Exception {
        var failure = failCleanup();
        // Repair the filesystem without moving the committed retry deadline into the past.
        Files.delete(path(failure.url()).resolve("blocker")); Files.delete(path(failure.url()));
        Files.write(path(failure.url()), failure.bytes());
        var before = jdbc.queryForMap("select * from post_image_cleanup where cleanup_id=?", failure.job());
        assertTrue(jobs.findById(failure.job()).orElseThrow().getNextAttemptAt().isAfter(LocalDateTime.now()));

        worker.process(failure.job());

        assertEquals(before, jdbc.queryForMap("select * from post_image_cleanup where cleanup_id=?", failure.job()));
        assertArrayEquals(failure.bytes(), Files.readAllBytes(path(failure.url())));
    }

    @Test void waitingWorkerRechecksDeadlineCommittedByFailedPredecessor() throws Exception {
        var failure = failCleanup();
        jdbc.update("update post_image_cleanup set attempts=0, next_attempt_at=? where cleanup_id=?",
                LocalDateTime.now().minusMinutes(2), failure.job());
        byte[] blocker = Files.readAllBytes(path(failure.url()).resolve("blocker"));
        CountDownLatch firstDeleting = new CountDownLatch(1), releaseFirst = new CountDownLatch(1);
        CountDownLatch secondAcquired = new CountDownLatch(1);
        AtomicReference<Map<String, Object>> firstCommitted = new AtomicReference<>();
        var gatedStorage = new PostImageStorage(directory.toString()) {
            @Override public void delete(String filename) throws IOException {
                firstDeleting.countDown();
                try { assertTrue(releaseFirst.await(15, TimeUnit.SECONDS)); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
                super.delete(filename); // The real nonregular target still causes IOException.
            }
        };
        var firstWorker = new PostImageCleanupWorker(jobs, images, gatedStorage, manager);
        // Observe the real repository's return after its database lock wait; do not mock the row read.
        var factory = new ProxyFactory();
        factory.setInterfaces(PostImageCleanupRepository.class); factory.setTarget(jobs);
        factory.addAdvice((MethodInterceptor) invocation -> {
            Object result = invocation.proceed();
            if (invocation.getMethod().getName().equals("findForUpdate")) {
                firstCommitted.set(jdbc.queryForMap("select * from post_image_cleanup where cleanup_id=?", failure.job()));
                secondAcquired.countDown();
            }
            return result;
        });
        var secondWorker = new PostImageCleanupWorker((PostImageCleanupRepository) factory.getProxy(), images, storage, manager);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> firstWorker.process(failure.job()));
            Future<?> second = null;
            try {
                assertTrue(firstDeleting.await(5, TimeUnit.SECONDS));
                second = executor.submit(() -> secondWorker.process(failure.job()));
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (jdbc.queryForObject("select count(*) from information_schema.sessions where blocker_id is not null", Integer.class) == 0) {
                    if (second.isDone()) { second.get(); fail("Expected a real H2 row-lock wait"); }
                    assertTrue(System.nanoTime() < deadline, "No H2 row-lock wait observed");
                    Thread.yield();
                }
                assertFalse(second.isDone());
            } finally { releaseFirst.countDown(); }
            first.get(10, TimeUnit.SECONDS);
            if (second != null) second.get(10, TimeUnit.SECONDS);
            assertTrue(secondAcquired.await(1, TimeUnit.SECONDS), "Waiter must actually acquire the lock, not time out");
        }
        assertNotNull(firstCommitted.get());
        assertEquals(1, ((Number) firstCommitted.get().get("ATTEMPTS")).intValue());
        assertEquals(firstCommitted.get(), jdbc.queryForMap("select * from post_image_cleanup where cleanup_id=?", failure.job()));
        assertEquals(1, jobs.findById(failure.job()).orElseThrow().getAttempts());
        assertTrue(Files.isDirectory(path(failure.url())));
        assertArrayEquals(blocker, Files.readAllBytes(path(failure.url()).resolve("blocker")));
    }

    private static final class SimulatedProcessExit extends Error { }

    @Test void exitAfterPhysicalDeleteBeforeQueueCommitLeavesDurableJobAndRetryIsIdempotent() throws Exception {
        var failure = failCleanup(); repair(failure);
        var crashingStorage = new PostImageStorage(directory.toString()) {
            @Override public void delete(String filename) throws IOException {
                super.delete(filename); throw new SimulatedProcessExit();
            }
        };
        var crashingWorker = new PostImageCleanupWorker(jobs, images, crashingStorage, manager);
        assertThrows(SimulatedProcessExit.class, () -> crashingWorker.process(failure.job()));
        assertFalse(Files.exists(path(failure.url()))); assertTrue(jobs.existsById(failure.job()));
        var restarted = new PostImageCleanupWorker(jobs, images, new PostImageStorage(directory.toString()), manager);
        restarted.process(failure.job()); restarted.process(failure.job());
        assertEquals(0, jobs.count()); hidden(failure.url());
    }

    @Test void committedQueueSurvivesBeforeAnyWorkerRunsAndMissingFilesAreSuccessful() throws Exception {
        String url = rows(post).getFirst().getImageUrl();
        // Simulate the crash window between DB commit and AFTER_COMMIT delivery.
        Long job = tx(() -> {
            em.remove(em.find(PostImage.class, rows(post).getFirst().getPostImageId()));
            var pending = new PostImageCleanup(url); em.persist(pending); return pending.getCleanupId();
        });
        hidden(url); assertTrue(Files.exists(path(url)));
        Files.delete(path(url));
        jdbc.update("update post_image_cleanup set next_attempt_at=? where cleanup_id=?",
                LocalDateTime.now().minusMinutes(2), job);
        new PostImageCleanupService(jobs, new PostImageCleanupWorker(jobs, images,
                new PostImageStorage(directory.toString()), manager)).retryPending();
        assertFalse(jobs.existsById(job));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void otherActiveOrDeletedPostReferenceProtectsSharedPhysicalFile(boolean deleted) throws Exception {
        var image = rows(post).getFirst(); String url = image.getImageUrl();
        tx(() -> {
            var shared = em.find(CommunityPost.class, anonymous);
            em.persist(new PostImage(shared, url, "legacy-shared.png", 1));
            if (deleted) shared.delete();
            return null;
        });
        update(List.of(rows(post).getLast().getPostImageId()), 0);
        assertTrue(Files.exists(path(url))); assertEquals(0, jobs.count());
        if (deleted) hidden(url); else visible(url);
    }

    @Test void duplicateJobsAndConcurrentWorkersNeverDeleteNewUploadsOrRetainedImages() throws Exception {
        String old = rows(post).getFirst().getImageUrl();
        update(List.of(rows(post).getLast().getPostImageId()), 1);
        Long a = jobs.save(new PostImageCleanup(old)).getCleanupId();
        Long b = jobs.save(new PostImageCleanup(old)).getCleanupId();
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(3)) {
            var futures = List.of(a, a, b).stream().map(id -> executor.submit(() -> {
                assertTrue(start.await(5, TimeUnit.SECONDS)); worker.process(id); return null;
            })).toList();
            start.countDown();
            for (var future : futures) future.get(10, TimeUnit.SECONDS);
        }
        assertEquals(0, jobs.count()); hidden(old);
        for (var row : rows(post)) visible(row.getImageUrl());
    }

    @ParameterizedTest @ValueSource(strings = {"unknown.png", "missing.png", "legacy-photo.png"})
    void unlinkedMissingAndHistoricalSafeBasenamesAreHandledWithoutFilesystemDisclosure(String filename) throws Exception {
        String url = PostImageStorage.URL_PREFIX + filename;
        if (!filename.equals("missing.png")) Files.write(path(url), new byte[]{4, 5});
        if (filename.equals("legacy-photo.png")) tx(() -> {
            em.persist(new PostImage(em.find(CommunityPost.class, post), url, filename, 2)); return null;
        });
        if (filename.equals("legacy-photo.png")) visible(url); else hidden(url);
    }

    @Test void activeMetadataWithMissingPhysicalFileReturnsGeneric404() throws Exception {
        String url = rows(post).getFirst().getImageUrl(); Files.delete(path(url)); hidden(url);
    }

    @ParameterizedTest @CsvSource({"image/jpeg,jpg", "image/png,png", "image/webp,webp", "image/gif,gif"})
    void everySupportedUploadTypeKeepsItsExistingPublicUrlAndMediaType(String type, String extension) throws Exception {
        String url = tx(() -> {
            String uploaded = storage.store(new MockMultipartFile("images", "photo." + extension, type, new byte[]{8, 9}));
            em.persist(new PostImage(em.find(CommunityPost.class, post), uploaded, "photo." + extension, 2));
            return uploaded;
        });
        mvc.perform(get(url)).andExpect(status().isOk()).andExpect(content().contentType(type))
                .andExpect(content().bytes(new byte[]{8, 9})).andExpect(header().string("Cache-Control", "no-store"));
    }

    @ParameterizedTest @ValueSource(strings = {
            "/api/post-images/../application.properties", "/api/post-images/%2e%2e/application.properties",
            "/api/post-images/%2e%2e%2fapplication.properties", "/api/post-images/%252e%252e%252fsecret.png",
            "/api/post-images/..%5csecret.png", "/api/post-images/a%00.png", "/api/post-images/.hidden.png",
            "/api/post-images/secret.png/extra", "/api/post-images/C:%5csecret.png",
            "/api/post-images/unsafe.svg", "/api/post-images/secret.png;anything"})
    void traversalAndEncodedPathsAreRejectedWithoutInternalDetails(String url) throws Exception {
        var response = mvc.perform(get(URI.create(url))).andReturn().getResponse();
        assertTrue(response.getStatus() >= 400 && response.getStatus() < 500, "status=" + response.getStatus());
        String body = response.getContentAsString();
        assertFalse(body.contains(directory.toString())); assertFalse(body.contains("java.nio"));
        assertFalse(body.contains("Exception"));
        visible(rows(post).getFirst().getImageUrl());
    }

    @ParameterizedTest @ValueSource(strings = {"/uploads/community/", "/community/", "/static/", "/api/uploads/"})
    void uploadRootIsNotExposedThroughAlternativeStaticRoutes(String prefix) throws Exception {
        String filename = path(rows(post).getFirst().getImageUrl()).getFileName().toString();
        var response = mvc.perform(get(prefix + filename).with(as(owner))).andReturn().getResponse();
        assertTrue(response.getStatus() >= 400 && response.getStatus() < 500);
    }

    @ParameterizedTest @ValueSource(strings = {"../outside.png", "%2e%2e%2foutside.png", "a/b.png", "a\\b.png", "a.png:stream"})
    void storageRejectsUnsafeReadAndDeleteIdentifiers(String filename) throws Exception {
        assertThrows(IOException.class, () -> storage.read(filename));
        assertThrows(IOException.class, () -> storage.delete(filename));
        for (var row : rows(post)) visible(row.getImageUrl());
    }

    @Test void pendingQueueWithoutAnyReferenceCannotDeleteOutsideRootOrLinkedTarget() throws Exception {
        String invalid = "/api/post-images/../outside.png";
        Long id = jobs.save(new PostImageCleanup(invalid)).getCleanupId();
        worker.process(id);
        assertEquals(1, jobs.findById(id).orElseThrow().getAttempts());
        for (var row : rows(post)) visible(row.getImageUrl());
    }

    @Test void conditionalAndRangeHeadersStillRequireLiveAssociationAndNeverProduce304() throws Exception {
        String url = rows(post).getFirst().getImageUrl();
        mvc.perform(get(url).header("If-Modified-Since", "Wed, 21 Oct 2099 07:28:00 GMT")
                .header("If-None-Match", "old-tag").header("Range", "bytes=0-1"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        update(List.of(rows(post).getLast().getPostImageId()), 0);
        mvc.perform(get(url).header("Range", "bytes=0-1")).andExpect(status().isNotFound());
    }
}
