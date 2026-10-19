package com.kkirikkiri.community;

import static org.junit.jupiter.api.Assertions.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.kkirikkiri.admin.dto.AdminReportProcessRequest;
import com.kkirikkiri.admin.service.AdminService;
import com.kkirikkiri.community.post.dto.CommunityPostUpdateRequest;
import com.kkirikkiri.community.post.entity.*;
import com.kkirikkiri.community.post.service.*;
import com.kkirikkiri.report.entity.Report;
import com.kkirikkiri.user.entity.*;

/** Actual embedded HTTP server and TCP requests; no production network/storage/DB. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.community.image-directory=unused-image-http-test")
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CommunityImageHttpIntegrationTest {
    @Value("${local.server.port}") int port;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @Autowired PostImageStorage storage;
    @Autowired CommunityPostService posts;
    @Autowired AdminService admins;
    @Autowired JdbcTemplate jdbc;
    @TempDir Path directory;
    Long owner, admin, post, report;
    String url;
    byte[] bytes = {4, 5, 6};

    private void fixture(boolean anonymous) {
        ReflectionTestUtils.setField(storage, "directory", directory.toAbsolutePath().normalize());
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            String key = UUID.randomUUID().toString();
            var a = new User("hash", key + "a@test.invalid");
            var b = new User("hash", key + "b@test.invalid", UserRole.ADMIN);
            em.persist(a); em.persist(b);
            var p = new CommunityPost(a, null, "자유게시판", "title", "body", anonymous); em.persist(p);
            url = storage.store(new MockMultipartFile("images", "old.png", "image/png", bytes));
            em.persist(new PostImage(p, url, "old.png", 0));
            var r = Report.forPost(b, p, "욕설·비방", null); em.persist(r);
            em.flush();
            // The same historical Report fixture used by existing concurrency tests.
            jdbc.update("update report set created_at=? where report_id=?",
                    java.sql.Timestamp.valueOf("2026-01-01 00:00:00"), r.getReportId());
            em.refresh(r);
            owner = a.getUserId(); admin = b.getUserId(); post = p.getPostId(); report = r.getReportId();
        });
    }

    private HttpResponse<byte[]> get(HttpClient client, String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(java.time.Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }
    private Path file() { return directory.resolve(url.substring(url.lastIndexOf('/') + 1)); }

    @ParameterizedTest @CsvSource({"USER,false", "USER,true", "ADMIN,false", "REPORT,false"})
    void realHttpDeniesAllDeletedPostsWhileRetainingPhysicalFiles(String type, boolean anonymous) throws Exception {
        fixture(anonymous);
        try (var client = HttpClient.newHttpClient()) {
            var initial = get(client, url);
            assertEquals(200, initial.statusCode()); assertArrayEquals(bytes, initial.body());
            assertEquals("no-store", initial.headers().firstValue("cache-control").orElseThrow());
            switch (type) {
                case "USER" -> posts.deletePost(owner, post);
                case "ADMIN" -> admins.deletePost(admin, post);
                case "REPORT" -> admins.processReport(admin, report, new AdminReportProcessRequest("DELETE_TARGET"));
                default -> fail(type);
            }
            var removed = get(client, url);
            assertEquals(404, removed.statusCode()); assertEquals(0, removed.body().length);
            assertEquals("no-store", removed.headers().firstValue("cache-control").orElseThrow());
            assertTrue(Files.exists(file()));
            assertTrue(get(client, "/uploads/community/" + file().getFileName()).statusCode() >= 400);
        }
    }

    @Test void realHttpRejectsUnlinkedFileAndCommittedRemovalDeletesBytes() throws Exception {
        fixture(false);
        try (var client = HttpClient.newHttpClient()) {
            assertEquals(200, get(client, url).statusCode());
            Files.write(directory.resolve("unlinked.png"), bytes);
            assertEquals(404, get(client, "/api/post-images/unlinked.png").statusCode());
            posts.updatePost(owner, post, new CommunityPostUpdateRequest("자유게시판", "edited", "body", List.of(), null), null);
            assertFalse(Files.exists(file())); assertEquals(404, get(client, url).statusCode());
            assertTrue(Files.exists(directory.resolve("unlinked.png")), "Never sweep unrelated orphan files");
        }
    }
}
