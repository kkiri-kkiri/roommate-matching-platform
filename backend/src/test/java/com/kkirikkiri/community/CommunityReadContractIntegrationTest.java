package com.kkirikkiri.community;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.kkirikkiri.community.comment.entity.CommunityComment;
import com.kkirikkiri.community.post.entity.*;
import com.kkirikkiri.global.security.AuthenticatedUser;
import com.kkirikkiri.report.entity.Report;
import com.kkirikkiri.user.entity.*;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.session_factory.statement_inspector=com.kkirikkiri.community.CommunityReadPerformanceIntegrationTest$SqlCapture")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CommunityReadContractIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactions;
    Long ownerId, visitorId, adminId, anonymousId, regularId, otherId;
    String key;
    record Endpoint(String path, String actor) {}
    static final List<Endpoint> ENDPOINTS = List.of(new Endpoint("/api/posts", "guest"), new Endpoint("/api/posts", "owner"),
            new Endpoint("/api/users/me/posts", "owner"), new Endpoint("/api/users/me/comments", "owner"),
            new Endpoint("/api/admin/posts", "admin"), new Endpoint("/api/admin/comments", "admin"), new Endpoint("/api/admin/reports", "admin"));

    @BeforeAll
    void fixtures() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            key = UUID.randomUUID().toString();
            User owner = new User("hash", key + "o@test.invalid"), visitor = new User("hash", key + "v@test.invalid");
            User admin = new User("hash", key + "a@test.invalid", UserRole.ADMIN);
            em.persist(owner); em.persist(visitor); em.persist(admin);
            Dormitory dormitory = new Dormitory("r-" + key.substring(0, 8), "r-" + key.substring(0, 8), true);
            Faculty faculty = new Faculty("r-" + key, true); em.persist(dormitory); em.persist(faculty);
            Major major = new Major(faculty, "r-" + key, true); em.persist(major);
            for (User user : List.of(owner, visitor)) em.persist(new UserProfile(user, "r-" + user.getUserId(), "남성",
                    dormitory, faculty, major, (short) 2004, "24", "2학년", "INTJ"));
            CommunityPost anonymous = new CommunityPost(owner, null, "자유게시판", key + " anonymous", "search body", true);
            CommunityPost regular = new CommunityPost(owner, dormitory, "나눔게시판", key + " regular", "search body");
            CommunityPost other = new CommunityPost(visitor, null, "자유게시판", key + " other", "search body");
            CommunityPost deleted = new CommunityPost(owner, null, "자유게시판", key + " deleted", "search body"); deleted.delete();
            em.persist(anonymous); em.persist(regular); em.persist(other); em.persist(deleted);
            CommunityComment parent = new CommunityComment(anonymous, visitor, null, "parent"); parent.changeAnonymous(true);
            CommunityComment reply = new CommunityComment(anonymous, owner, parent, "reply");
            CommunityComment removed = new CommunityComment(anonymous, visitor, null, "removed"); removed.changeAnonymous(true); removed.delete();
            CommunityComment ordinary = new CommunityComment(regular, visitor, null, "ordinary");
            em.persist(parent); em.persist(reply); em.persist(removed); em.persist(ordinary);
            em.persist(new PostImage(anonymous, "/api/post-images/fixture.png", "fixture.png", 0));
            em.persist(new PostLike(anonymous, visitor)); em.persist(new PostLike(regular, owner));
            em.persist(Report.forPost(visitor, anonymous, "욕설·비방", null));
            em.persist(Report.forComment(owner, ordinary, "욕설·비방", null));
            ownerId = owner.getUserId(); visitorId = visitor.getUserId(); adminId = admin.getUserId();
            anonymousId = anonymous.getPostId(); regularId = regular.getPostId(); otherId = other.getPostId();
        });
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String actor) {
        if (actor.equals("guest")) return request;
        Long user = actor.equals("admin") ? adminId : actor.equals("visitor") ? visitorId : ownerId;
        String role = actor.equals("admin") ? "ADMIN" : "USER";
        return request.with(authentication(new UsernamePasswordAuthenticationToken(new AuthenticatedUser(user, role), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)))));
    }

    private JsonNode read(MockHttpServletRequestBuilder request, String actor) throws Exception {
        return mapper.readTree(mvc.perform(as(request, actor)).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8)).get("data");
    }

    static Stream<Arguments> validSizes() {
        return ENDPOINTS.stream().flatMap(endpoint -> Stream.of(1, 20, 100).map(size -> Arguments.of(endpoint, size)));
    }

    @ParameterizedTest @MethodSource("validSizes")
    void acceptsSizeOneThroughOneHundred(Endpoint endpoint, int size) throws Exception {
        JsonNode page = read(get(endpoint.path()).param("size", Integer.toString(size)), endpoint.actor());
        assertEquals(size, page.get("size").asInt());
        assertEquals(0, page.get("page").asInt());
        assertTrue(page.get("content").size() <= size);
    }

    static Stream<Arguments> invalidSizes() {
        return ENDPOINTS.stream().flatMap(endpoint -> Stream.of("0", "-1", "101", "2147483647", "2147483648", "99999999999999999999", "1.5", "abc")
                .map(size -> Arguments.of(endpoint, size)));
    }

    @ParameterizedTest @MethodSource("invalidSizes")
    void rejectsInvalidSizeBeforeAnyCommunityDataQuery(Endpoint endpoint, String size) throws Exception {
        CommunityReadPerformanceIntegrationTest.SqlCapture.reset();
        mvc.perform(as(get(endpoint.path()).param("size", size), endpoint.actor())).andExpect(status().isBadRequest());
        assertNoCommunityQuery();
    }

    static Stream<Endpoint> endpoints() { return ENDPOINTS.stream(); }

    @ParameterizedTest @MethodSource("endpoints")
    void omittedSizeKeepsDefaultSixAndPageOneRemainsUsable(Endpoint endpoint) throws Exception {
        JsonNode initial = read(get(endpoint.path()), endpoint.actor());
        assertEquals(6, initial.get("size").asInt());
        assertEquals(0, initial.get("page").asInt());
        JsonNode next = read(get(endpoint.path()).param("page", "1").param("size", "1"), endpoint.actor());
        assertEquals(1, next.get("page").asInt());
        assertEquals(initial.get("totalElements"), next.get("totalElements"));
    }

    static Stream<Arguments> invalidPages() {
        return ENDPOINTS.stream().flatMap(endpoint -> Stream.of("-1", "2147483647", "2147483648", "1.5")
                .map(page -> Arguments.of(endpoint, page)));
    }

    @ParameterizedTest @MethodSource("invalidPages")
    void rejectsBadPageOrOffsetOverflowBeforeCommunityQuery(Endpoint endpoint, String page) throws Exception {
        CommunityReadPerformanceIntegrationTest.SqlCapture.reset();
        mvc.perform(as(get(endpoint.path()).param("page", page).param("size", "2"), endpoint.actor())).andExpect(status().isBadRequest());
        assertNoCommunityQuery();
    }

    private void assertNoCommunityQuery() {
        assertTrue(CommunityReadPerformanceIntegrationTest.SqlCapture.statements().stream().noneMatch(sql ->
                sql.contains(" from community_post ") || sql.contains(" from community_comment ") || sql.contains(" from report ")),
                "Invalid pagination may authenticate an admin, but must not query community rows");
    }

    @Test
    void maximumSupportedOffsetWithSizeOneIsStillAccepted() throws Exception {
        JsonNode page = read(get("/api/posts").param("page", "2147483647").param("size", "1"), "guest");
        assertEquals(Integer.MAX_VALUE, page.get("page").asInt());
        assertEquals(0, page.get("content").size());
    }

    @Test
    void batchedFieldsKeepViewerIsolationActiveCountsImagesAndAnonymousOwnership() throws Exception {
        for (String actor : List.of("guest", "owner", "visitor")) {
            for (String sort : List.of("LATEST", "VIEWS", "LIKES")) {
                JsonNode page = read(get("/api/posts").param("q", key).param("sort", sort), actor);
                assertEquals(3, page.get("totalElements").asLong());
                Map<Long, JsonNode> rows = new java.util.HashMap<>();
                for (JsonNode row : page.get("content")) rows.put(row.get("id").asLong(), row);
                JsonNode anonymous = rows.get(anonymousId), regular = rows.get(regularId), other = rows.get(otherId);
                assertTrue(anonymous.get("author").get("id").isNull());
                assertTrue(anonymous.get("author").get("major").isNull());
                assertEquals("익명", anonymous.get("author").get("nickname").asText());
                assertEquals(2, anonymous.get("commentCount").asLong());
                assertTrue(anonymous.get("hasImages").asBoolean());
                assertEquals(1, anonymous.get("likes").asLong());
                assertEquals(actor.equals("visitor"), anonymous.get("liked").asBoolean());
                assertEquals(actor.equals("visitor"), anonymous.get("reported").asBoolean());
                assertEquals(actor.equals("owner"), anonymous.get("mine").asBoolean());
                assertEquals(ownerId.longValue(), regular.get("author").get("id").asLong());
                assertFalse(regular.get("reported").asBoolean(), "A comment report is not a post report");
                assertFalse(regular.get("hasImages").asBoolean());
                assertEquals(1, regular.get("commentCount").asLong());
                assertEquals(actor.equals("owner"), regular.get("liked").asBoolean());
                assertEquals(actor.equals("visitor"), other.get("mine").asBoolean());
            }
        }
    }

    @Test
    void myActivityAndAdminReportsPreserveTheirSeparateIdentityContracts() throws Exception {
        JsonNode mine = read(get("/api/users/me/posts"), "owner");
        assertEquals(2, mine.get("totalElements").asInt());
        for (JsonNode row : mine.get("content")) assertTrue(row.get("mine").asBoolean());
        JsonNode replies = read(get("/api/users/me/comments"), "owner");
        assertEquals(1, replies.get("totalElements").asInt());
        assertTrue(replies.get("content").get(0).get("isReply").asBoolean());
        JsonNode admin = read(get("/api/admin/posts").param("q", key), "admin");
        JsonNode anonymous = java.util.stream.StreamSupport.stream(admin.get("content").spliterator(), false)
                .filter(row -> row.get("id").asLong() == anonymousId).findFirst().orElseThrow();
        assertEquals(ownerId.longValue(), anonymous.get("author").get("id").asLong());
        assertTrue(anonymous.get("reported").asBoolean());
        assertEquals(2, anonymous.get("commentCount").asLong());
        assertEquals(1, anonymous.get("likes").asLong());
        mvc.perform(as(get("/api/admin/posts"), "owner")).andExpect(status().isForbidden());
    }

    @Test
    void categorySearchAndEmptyPagesKeepMetadataAndSkipUnnecessaryBatchQueries() throws Exception {
        JsonNode filtered = read(get("/api/posts").param("q", key).param("category", "나눔게시판").param("sort", "LIKES"), "guest");
        assertEquals(1, filtered.get("totalElements").asLong());
        assertEquals(regularId.longValue(), filtered.get("content").get(0).get("id").asLong());
        CommunityReadPerformanceIntegrationTest.SqlCapture.reset();
        JsonNode empty = read(get("/api/posts").param("q", "missing-" + key), "owner");
        assertEquals(0, empty.get("content").size());
        assertEquals(0, empty.get("totalElements").asLong());
        assertTrue(CommunityReadPerformanceIntegrationTest.SqlCapture.statements().stream().noneMatch(sql ->
                sql.contains("user_profile") || sql.contains("post_like") || sql.contains("post_image") || sql.contains("community_comment")));
    }
}
