package com.kkirikkiri.community;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.kkirikkiri.chat.dto.ChatMessageSendRequest;
import com.kkirikkiri.chat.service.ChatService;
import com.kkirikkiri.community.comment.dto.CommunityCommentCreateRequest;
import com.kkirikkiri.community.comment.service.CommunityCommentService;
import com.kkirikkiri.community.post.entity.CommunityPost;
import com.kkirikkiri.global.security.AuthenticatedUser;
import com.kkirikkiri.user.entity.*;

/** Real HTTP serialization, security, services and JPA; only the test H2 database is used. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CommunityPostPrivacyIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactions;
    @Autowired ChatService chats;
    @Autowired CommunityCommentService comments;
    Long authorId, visitorId, adminId, anonymousPostId, regularPostId;
    String nickname;

    @BeforeEach
    void fixtures() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            String key = UUID.randomUUID().toString();
            User author = new User("hash", key + "a@test.invalid");
            User visitor = new User("hash", key + "b@test.invalid");
            User admin = new User("hash", key + "c@test.invalid", UserRole.ADMIN);
            em.persist(author); em.persist(visitor); em.persist(admin);
            Dormitory dormitory = new Dormitory("p-" + key.substring(0, 8), "p-" + key.substring(0, 8), true);
            Faculty faculty = new Faculty("p-" + key, true);
            em.persist(dormitory); em.persist(faculty);
            nickname = "p-" + key.substring(0, 12);
            em.persist(new UserProfile(author, nickname, "남성", dormitory, faculty,
                    null, (short) 2004, "24", "2학년", "INTJ"));
            em.persist(new UserProfile(visitor, "v-" + key.substring(0, 12), "남성", dormitory, faculty,
                    null, (short) 2004, "24", "2학년", "INTJ"));
            CommunityPost anonymous = new CommunityPost(author, null, "자유게시판", "private-" + key, "내용", true);
            CommunityPost regular = new CommunityPost(author, null, "자유게시판", "public-" + key, "내용");
            em.persist(anonymous); em.persist(regular);
            authorId = author.getUserId(); visitorId = visitor.getUserId(); adminId = admin.getUserId();
            anonymousPostId = anonymous.getPostId(); regularPostId = regular.getPostId();
        });
    }

    private AbstractMockHttpServletRequestBuilder<?> as(AbstractMockHttpServletRequestBuilder<?> request, Long id) {
        if (id == null) return request;
        String role = id.equals(adminId) ? "ADMIN" : "USER";
        return request.with(authentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(id, role), null, List.of(new SimpleGrantedAuthority("ROLE_" + role)))));
    }

    private JsonNode read(AbstractMockHttpServletRequestBuilder<?> request, Long viewer) throws Exception {
        return mapper.readTree(mvc.perform(as(request, viewer)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).get("data");
    }

    private Long viewer(String name) {
        return "author".equals(name) ? authorId : "visitor".equals(name) ? visitorId : null;
    }

    private void assertAuthor(JsonNode post, boolean anonymous, boolean mine) {
        assertEquals(mine, post.get("mine").asBoolean());
        assertEquals(anonymous, post.get("anonymous").asBoolean());
        JsonNode author = post.get("author");
        assertEquals(anonymous ? "익명" : nickname, author.get("nickname").asText());
        if (anonymous) {
            assertTrue(author.get("id").isNull());
            assertTrue(author.get("major").isNull());
            assertFalse(post.toString().contains(nickname));
            assertFalse(post.toString().contains("authorId"));
        } else assertEquals(authorId.longValue(), author.get("id").asLong());
    }

    @ParameterizedTest
    @CsvSource({"LATEST,author", "LATEST,visitor", "LATEST,guest", "VIEWS,author", "VIEWS,visitor",
            "VIEWS,guest", "LIKES,author", "LIKES,visitor", "LIKES,guest"})
    void listAndSearchMaskAnonymousPostsAndComputeOwnership(String sort, String who) throws Exception {
        String query = new TransactionTemplate(transactions).execute(status -> em.find(CommunityPost.class, anonymousPostId).getTitle());
        JsonNode page = read(get("/api/posts").param("q", query).param("sort", sort), viewer(who));
        assertEquals(1, page.get("content").size());
        assertAuthor(page.get("content").get(0), true, "author".equals(who));
    }

    @ParameterizedTest
    @ValueSource(strings = {"author", "visitor", "guest"})
    void detailSeparatesAnonymousAndPublicIdentity(String who) throws Exception {
        assertAuthor(read(get("/api/posts/" + anonymousPostId), viewer(who)), true, "author".equals(who));
        assertAuthor(read(get("/api/posts/" + regularPostId), viewer(who)), false, "author".equals(who));
    }

    @Test
    void myActivityMasksAuthorAndRequiresAuthentication() throws Exception {
        JsonNode page = read(get("/api/users/me/posts"), authorId);
        JsonNode entry = java.util.stream.StreamSupport.stream(page.get("content").spliterator(), false)
                .filter(node -> node.get("id").asLong() == anonymousPostId).findFirst().orElseThrow();
        assertAuthor(entry, true, true);
        mvc.perform(get("/api/users/me/posts")).andExpect(status().isUnauthorized());
    }

    private AbstractMockHttpServletRequestBuilder<?> write(String method, Long id, String payload) {
        return multipart(id == null ? "/api/posts" : "/api/posts/" + id)
                .file(new MockMultipartFile("request", "blob", "application/json", payload.getBytes(StandardCharsets.UTF_8)))
                .with(request -> { request.setMethod(method); return request; });
    }

    @Test
    void anonymousCreateUpdateAndDeleteKeepOwnershipAndSafeResponses() throws Exception {
        String payload = "{\"category\":\"자유게시판\",\"title\":\"created\",\"content\":\"내용\",\"anonymous\":true}";
        Long id = read(write("POST", null, payload), authorId).asLong();
        assertAuthor(read(get("/api/posts/" + id), authorId), true, true);
        read(write("PATCH", id, payload.replace("created", "updated")), authorId);
        assertAuthor(read(get("/api/posts/" + id), authorId), true, true);
        read(delete("/api/posts/" + id), authorId);
        mvc.perform(get("/api/posts/" + id)).andExpect(status().isBadRequest());
    }

    @Test
    void forgedMineAndUserIdNeverGrantWritePermission() throws Exception {
        String payload = "{\"category\":\"자유게시판\",\"title\":\"forged\",\"content\":\"내용\",\"mine\":true,\"userId\":" + authorId + "}";
        mvc.perform(as(write("PATCH", anonymousPostId, payload), visitorId)).andExpect(status().isForbidden());
        mvc.perform(as(delete("/api/posts/" + anonymousPostId).param("mine", "true").param("userId", authorId.toString()), visitorId))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/posts/" + anonymousPostId)).andExpect(status().isUnauthorized());
        assertAuthor(read(get("/api/posts/" + anonymousPostId), authorId), true, true);
    }

    private JsonNode room(Long postId, Long suppliedTarget) throws Exception {
        String body = "{\"category\":\"COMMUNITY\",\"sourceFrom\":\"POST\",\"sourcePostId\":" + postId
                + (suppliedTarget == null ? "" : ",\"targetUserId\":" + suppliedTarget) + "}";
        return read(post("/api/chat/rooms").contentType(MediaType.APPLICATION_JSON).content(body), visitorId);
    }

    private void assertAnonymousProfile(JsonNode room) {
        JsonNode profile = room.get("counterpart");
        assertEquals("익명", profile.get("nickname").asText());
        assertEquals(0, profile.get("tags").size());
        profile.properties().forEach(field -> {
            if (!List.of("nickname", "tags").contains(field.getKey())) assertTrue(field.getValue().isNull(), field.getKey());
        });
        assertFalse(room.toString().contains("roomKey"));
        assertFalse(room.toString().contains("members"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1, 999999})
    void anonymousChatUsesPostSourceEvenWithAbsentOrForgedTarget(Long supplied) throws Exception {
        JsonNode created = room(anonymousPostId, supplied);
        assertAnonymousProfile(created);
        assertEquals(created.get("id"), room(anonymousPostId, authorId).get("id"));
        assertEquals(created.get("id"), room(anonymousPostId, visitorId).get("id"));
        JsonNode restored = read(get("/api/chat/rooms"), visitorId).get(0);
        assertEquals(created, restored);
        assertAnonymousProfile(restored);
    }

    @Test
    void regularAndRoommateRoomsRemainSeparateAndRejectForgedRegularTargets() throws Exception {
        JsonNode anonymous = room(anonymousPostId, null);
        JsonNode regular = room(regularPostId, null);
        assertEquals(authorId.longValue(), regular.get("counterpart").get("id").asLong());
        assertNotEquals(anonymous.get("id"), regular.get("id"));
        String roommate = "{\"category\":\"ROOMMATE\",\"targetUserId\":" + authorId + "}";
        JsonNode other = read(post("/api/chat/rooms").contentType(MediaType.APPLICATION_JSON).content(roommate), visitorId);
        assertNotEquals(other.get("id"), regular.get("id"));
        mvc.perform(as(post("/api/chat/rooms").contentType(MediaType.APPLICATION_JSON).content(
                "{\"category\":\"COMMUNITY\",\"sourceFrom\":\"POST\",\"sourcePostId\":" + regularPostId + ",\"targetUserId\":" + adminId + "}"), visitorId))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anonymousHistoryAndBroadcastHideSenderWhileAuthorSeesOwnDirection() throws Exception {
        long roomId = room(anonymousPostId, null).get("id").asLong();
        JsonNode broadcast = mapper.valueToTree(chats.sendMessage(authorId, roomId, new ChatMessageSendRequest("hello")));
        assertTrue(broadcast.get("senderId").isNull());
        JsonNode message = read(get("/api/chat/rooms/" + roomId + "/messages"), visitorId).get("content").get(0);
        assertTrue(message.get("senderId").isNull());
        assertEquals("OTHER", message.get("senderType").asText());
        JsonNode mine = read(get("/api/chat/rooms/" + roomId + "/messages"), authorId).get("content").get(0);
        assertTrue(mine.get("senderId").isNull());
        assertEquals("ME", mine.get("senderType").asText());
        assertEquals(visitorId.longValue(), read(get("/api/chat/rooms"), authorId).get(0).get("counterpart").get("id").asLong());
        mvc.perform(as(get("/api/chat/rooms/" + roomId + "/messages"), adminId)).andExpect(status().isForbidden());
    }

    @Test
    void deletedPostBlocksNewChatAndPreservesExistingAnonymousHistory() throws Exception {
        JsonNode created = room(anonymousPostId, null);
        read(delete("/api/posts/" + anonymousPostId), authorId);
        mvc.perform(as(post("/api/chat/rooms").contentType(MediaType.APPLICATION_JSON).content(
                "{\"category\":\"COMMUNITY\",\"sourceFrom\":\"POST\",\"sourcePostId\":" + anonymousPostId + "}"), visitorId))
                .andExpect(status().isBadRequest());
        assertEquals(created, read(get("/api/chat/rooms"), visitorId).get(0));
    }

    @Test
    void anonymousCommentsAndRepliesKeepNumbersOwnershipAndPrivateChat() throws Exception {
        Long first = comments.createComment(authorId, anonymousPostId, new CommunityCommentCreateRequest("first", true));
        Long reply = comments.createReply(authorId, first, new CommunityCommentCreateRequest("reply", true));
        JsonNode list = read(get("/api/posts/" + anonymousPostId), authorId).get("comments");
        assertEquals(2, list.size());
        for (JsonNode comment : list) {
            assertTrue(comment.get("author").get("id").isNull());
            assertTrue(comment.get("mine").asBoolean());
            assertEquals(list.get(0).get("anonymousNumber"), comment.get("anonymousNumber"));
        }
        String body = "{\"category\":\"COMMUNITY\",\"sourceFrom\":\"COMMENT\",\"sourcePostId\":" + anonymousPostId + ",\"sourceCommentId\":" + reply + "}";
        JsonNode created = read(post("/api/chat/rooms").contentType(MediaType.APPLICATION_JSON).content(body), visitorId);
        assertAnonymousProfile(created);
        JsonNode broadcast = mapper.valueToTree(chats.sendMessage(authorId, created.get("id").asLong(), new ChatMessageSendRequest("comment chat")));
        assertTrue(broadcast.get("senderId").isNull());
        comments.deleteComment(authorId, first);
        comments.createComment(authorId, anonymousPostId, new CommunityCommentCreateRequest("again", true));
        JsonNode again = read(get("/api/posts/" + anonymousPostId), visitorId).get("comments");
        assertEquals(1, again.size());
        assertEquals(list.get(0).get("anonymousNumber"), again.get(0).get("anonymousNumber"));
        assertFalse(again.get(0).get("mine").asBoolean());
        assertTrue(again.get(0).get("author").get("id").isNull());
    }

    @Test
    void adminIdentityRemainsRestrictedToAdminApi() throws Exception {
        JsonNode adminPost = read(get("/api/admin/posts/" + anonymousPostId), adminId);
        assertEquals(authorId.longValue(), adminPost.get("author").get("id").asLong());
        mvc.perform(as(get("/api/admin/posts/" + anonymousPostId), visitorId)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/posts/" + anonymousPostId)).andExpect(status().isUnauthorized());
        assertAuthor(read(get("/api/posts/" + anonymousPostId), adminId), true, false);
    }
}
