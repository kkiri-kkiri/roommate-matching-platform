package com.kkirikkiri.community;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.kkirikkiri.community.post.entity.CommunityPost;
import com.kkirikkiri.community.post.service.PostImageStorage;
import com.kkirikkiri.global.security.AuthenticatedUser;
import com.kkirikkiri.user.entity.*;

/** Exercises real multipart JSON, transaction rollback and H2 lock waits without external services. */
@SpringBootTest(properties = {"spring.datasource.hikari.maximum-pool-size=6",
        "spring.datasource.hikari.transaction-isolation=TRANSACTION_READ_COMMITTED",
        "app.community.image-directory=unused-post-anonymity-test"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CommunityPostAnonymityPolicyIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;
    @Autowired PostImageStorage storage;
    @TempDir Path directory;
    Long authorId, visitorId, adminId, anonymousPostId, regularPostId;

    @BeforeEach
    void fixtures() {
        ReflectionTestUtils.setField(storage, "directory", directory.toAbsolutePath().normalize());
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            String key = UUID.randomUUID().toString();
            User author = new User("hash", key + "a@test.invalid");
            User visitor = new User("hash", key + "b@test.invalid");
            User admin = new User("hash", key + "c@test.invalid", UserRole.ADMIN);
            em.persist(author); em.persist(visitor); em.persist(admin);
            Dormitory dormitory = new Dormitory("a-" + key.substring(0, 8), "a-" + key.substring(0, 8), true);
            Faculty faculty = new Faculty("a-" + key, true);
            em.persist(dormitory); em.persist(faculty);
            em.persist(new UserProfile(author, "a-" + key.substring(0, 12), "남성", dormitory, faculty,
                    null, (short) 2004, "24", "2학년", "INTJ"));
            CommunityPost anonymous = new CommunityPost(author, null, "자유게시판", "initial", "body", true);
            CommunityPost regular = new CommunityPost(author, null, "자유게시판", "initial", "body", false);
            em.persist(anonymous); em.persist(regular);
            authorId = author.getUserId(); visitorId = visitor.getUserId(); adminId = admin.getUserId();
            anonymousPostId = anonymous.getPostId(); regularPostId = regular.getPostId();
        });
    }

    private AbstractMockHttpServletRequestBuilder<?> as(AbstractMockHttpServletRequestBuilder<?> request, Long id) {
        String role = id.equals(adminId) ? "ADMIN" : "USER";
        return request.with(authentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(id, role), null, List.of(new SimpleGrantedAuthority("ROLE_" + role)))));
    }

    private JsonNode success(AbstractMockHttpServletRequestBuilder<?> request, Long id) throws Exception {
        return mapper.readTree(mvc.perform(as(request, id)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).get("data");
    }

    private JsonNode detail(Long id) throws Exception { return success(get("/api/posts/" + id), authorId); }
    private Long id(boolean anonymous) { return anonymous ? anonymousPostId : regularPostId; }
    private String payload(String title, String anonymousJson) {
        return "{\"category\":\"자유게시판\",\"title\":\"" + title + "\",\"content\":\"edited body\",\"keepImageIds\":[]"
                + (anonymousJson == null ? "" : ",\"anonymous\":" + anonymousJson) + "}";
    }

    private AbstractMockHttpServletRequestBuilder<?> edit(Long id, String body, MockMultipartFile... files) {
        var request = multipart("/api/posts/" + id).file(new MockMultipartFile("request", "blob",
                "application/json", body.getBytes(StandardCharsets.UTF_8)));
        for (var file : files) request.file(file);
        return request.with(value -> { value.setMethod("PATCH"); return value; });
    }

    private MockMultipartFile image(String name) {
        return new MockMultipartFile("images", name + ".png", "image/png", new byte[]{1, 2, 3});
    }

    private List<String> files() throws Exception {
        try (var stream = Files.list(directory)) { return stream.map(path -> path.getFileName().toString()).sorted().toList(); }
    }

    private void assertState(JsonNode post, boolean anonymous) {
        assertEquals(anonymous, post.get("anonymous").asBoolean());
        assertTrue(post.get("mine").asBoolean());
        if (anonymous) assertTrue(post.get("author").get("id").isNull());
        else assertEquals(authorId.longValue(), post.get("author").get("id").asLong());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void creationStillAcceptsEitherAnonymousChoice(boolean anonymous) throws Exception {
        var request = multipart("/api/posts").file(new MockMultipartFile("request", "blob", "application/json",
                payload("created", Boolean.toString(anonymous)).getBytes(StandardCharsets.UTF_8)));
        Long created = success(request, authorId).asLong();
        assertState(detail(created), anonymous);
    }

    @ParameterizedTest
    @CsvSource({"false,SAME", "false,OMITTED", "false,NULL", "true,SAME", "true,OMITTED", "true,NULL"})
    void sameOmittedAndExplicitNullPreserveStoredChoice(boolean anonymous, String mode) throws Exception {
        String value = "SAME".equals(mode) ? Boolean.toString(anonymous) : "NULL".equals(mode) ? "null" : null;
        success(edit(id(anonymous), payload("updated", value)), authorId);
        JsonNode post = detail(id(anonymous));
        assertState(post, anonymous);
        assertEquals("updated", post.get("title").asText());
        assertEquals("edited body", post.get("content").asText());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void oppositeChoiceRejectsWholeRequestBeforeContentOrImageChanges(boolean anonymous) throws Exception {
        success(edit(id(anonymous), payload("seed", null), image("original")), authorId);
        JsonNode before = detail(id(anonymous));
        List<String> beforeFiles = files();
        JsonNode error = mapper.readTree(mvc.perform(as(edit(id(anonymous), payload("must not persist", Boolean.toString(!anonymous)),
                image("must not upload")), authorId)).andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals("VALIDATION_ERROR", error.get("errorCode").asText());
        JsonNode after = detail(id(anonymous));
        assertState(after, anonymous);
        assertEquals(before.get("title"), after.get("title"));
        assertEquals(before.get("content"), after.get("content"));
        assertEquals(before.get("images"), after.get("images"));
        assertEquals(beforeFiles, files());
        for (String file : beforeFiles) assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(directory.resolve(file)));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void imageReplacementKeepsAnonymityAndOrdinaryContentEditing(boolean anonymous) throws Exception {
        success(edit(id(anonymous), payload("seed", null), image("old")), authorId);
        JsonNode before = detail(id(anonymous)).get("images").get(0);
        success(edit(id(anonymous), payload("replacement", Boolean.toString(anonymous)), image("new")), authorId);
        JsonNode after = detail(id(anonymous));
        assertState(after, anonymous);
        assertEquals("replacement", after.get("title").asText());
        assertEquals(1, after.get("images").size());
        assertNotEquals(before.get("id"), after.get("images").get(0).get("id"));
        assertEquals(1, files().size(), "Committed unlink removes the detached physical file");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void anotherUserCannotEditOrProbeAnonymity(boolean anonymous) throws Exception {
        mvc.perform(as(edit(id(anonymous), payload("forged", Boolean.toString(!anonymous))), visitorId))
                .andExpect(status().isForbidden());
        assertState(detail(id(anonymous)), anonymous);
        assertEquals("initial", detail(id(anonymous)).get("title").asText());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void deletedPostsRejectUpdates(boolean anonymous) throws Exception {
        success(delete("/api/posts/" + id(anonymous)), authorId);
        mvc.perform(as(edit(id(anonymous), payload("deleted", Boolean.toString(anonymous))), authorId))
                .andExpect(status().isBadRequest());
        assertEquals("initial", jdbc.queryForObject("select title from community_post where post_id=?", String.class, id(anonymous)));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void oppositeChoiceIsRejectedAfterActualDatabaseLockWait(boolean anonymous) throws Exception {
        CountDownLatch held = new CountDownLatch(1), release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<?> holder = executor.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
                var post = em.find(CommunityPost.class, id(anonymous), LockModeType.PESSIMISTIC_WRITE);
                post.updateContent(post.getCategory(), "concurrent title", "concurrent body");
                held.countDown();
                await(release);
            }));
            Future<?> contender = null;
            try {
                await(held);
                contender = executor.submit(() -> {
                    mvc.perform(as(edit(id(anonymous), payload("rejected", Boolean.toString(!anonymous))), authorId))
                            .andExpect(status().isBadRequest());
                    return null;
                });
                long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
                while (jdbc.queryForObject("select count(*) from information_schema.sessions where blocker_id is not null", Integer.class) == 0) {
                    if (contender.isDone()) { contender.get(); fail("Expected the update to wait for the held post lock"); }
                    assertTrue(System.nanoTime() < deadline, "Expected an actual H2 lock wait");
                    Thread.yield();
                }
            } finally { release.countDown(); }
            holder.get(15, TimeUnit.SECONDS);
            if (contender != null) contender.get(15, TimeUnit.SECONDS);
        }
        JsonNode after = detail(id(anonymous));
        assertState(after, anonymous);
        assertEquals("concurrent title", after.get("title").asText());
        assertEquals("concurrent body", after.get("content").asText());
    }

    private static void await(CountDownLatch latch) {
        try { assertTrue(latch.await(10, TimeUnit.SECONDS)); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
    }

    @ParameterizedTest
    @CsvSource({"PUT,false", "PATCH,true"})
    void noAlternateUserOrAdminWriteEndpointChangesAnonymity(String method, boolean admin) throws Exception {
        mvc.perform(as(request(HttpMethod.valueOf(method), admin ? "/api/admin/posts/" + regularPostId : "/api/posts/" + regularPostId)
                .contentType(MediaType.APPLICATION_JSON).content(payload("bypass", "true")), admin ? adminId : authorId))
                .andExpect(status().is4xxClientError());
        assertState(detail(regularPostId), false);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "[]"})
    void rejectsMalformedAnonymousJson(String value) throws Exception {
        mvc.perform(as(edit(regularPostId, payload("malformed", value)), authorId)).andExpect(status().isBadRequest());
        assertState(detail(regularPostId), false);
    }

    @Test
    void preservesLegacyPublicRoomWithoutRewritingPrePolicyTransitions() throws Exception {
        String body = "{\"category\":\"COMMUNITY\",\"sourceFrom\":\"POST\",\"sourcePostId\":" + regularPostId + "}";
        JsonNode room = success(post("/api/chat/rooms").contentType(MediaType.APPLICATION_JSON).content(body), visitorId);
        // Isolated H2 fixture only: represent a transition that happened before this policy existed.
        jdbc.update("update community_post set anonymous=true where post_id=?", regularPostId);
        success(edit(regularPostId, payload("legacy edit", null)), authorId);
        assertState(detail(regularPostId), true);
        JsonNode preserved = success(get("/api/chat/rooms"), visitorId).get(0);
        assertEquals(room, preserved);
        assertEquals(authorId.longValue(), preserved.get("counterpart").get("id").asLong());
    }
}
