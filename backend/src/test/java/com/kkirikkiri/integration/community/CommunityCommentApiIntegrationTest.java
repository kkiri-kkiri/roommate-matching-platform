package com.kkirikkiri.integration.community;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Map;
import java.util.stream.Stream;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

import com.kkirikkiri.community.comment.entity.CommunityComment;
import com.kkirikkiri.community.common.entity.CommunityContentStatus;
import com.kkirikkiri.community.post.entity.CommunityPost;
import com.kkirikkiri.integration.support.ApiIntegrationTest;
import com.kkirikkiri.integration.support.TestUsers;
import com.kkirikkiri.user.entity.User;

@ApiIntegrationTest
class CommunityCommentApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired TestUsers users;
    @Autowired EntityManager em;
    @Autowired ObjectMapper json;
    User author, other;
    Long postId, commentId;

    @BeforeEach
    void fixtures() {
        author = users.createUser();
        other = users.createUser();
        users.createProfile(author);
        users.createProfile(other);
        var post = new CommunityPost(author, null, "자유게시판", "thread", "body");
        em.persist(post);
        var comment = new CommunityComment(post, author, null, "original");
        em.persist(comment);
        em.flush();
        postId = post.getPostId();
        commentId = comment.getCommentId();
    }

    @Test
    void publicCommentAndReplyWritesPersistPrincipalAndAppearInMyActivity() throws Exception {
        long created = create(post("/api/posts/{id}/comments", postId), "a".repeat(1000));
        long reply = create(post("/api/comments/{id}/replies", created), "reply");
        mvc.perform(patch("/api/comments/{id}", reply).with(users.authenticated(other))
                        .contentType("application/json").content("{\"content\":\"edited\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
        em.flush(); em.clear();
        var saved = em.find(CommunityComment.class, created);
        assertEquals(other.getUserId(), saved.getAuthor().getUserId());
        assertFalse(saved.isAnonymous());
        assertNull(saved.getParentComment());
        assertEquals(1000, saved.getContent().length());
        var savedReply = em.find(CommunityComment.class, reply);
        assertEquals(created, savedReply.getParentComment().getCommentId());
        assertEquals(postId, savedReply.getPost().getPostId());
        assertEquals("edited", savedReply.getContent());
        mvc.perform(get("/api/users/me/comments").with(users.authenticated(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[?(@.id == " + reply + ")].isReply").value(true))
                .andExpect(jsonPath("$.data.content[?(@.id == " + created + ")].postId").value(postId.intValue()));
        mvc.perform(delete("/api/comments/{id}", created).with(users.authenticated(other)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
        em.flush(); em.clear();
        assertEquals(CommunityContentStatus.DELETED, em.find(CommunityComment.class, created).getStatus());
        assertEquals(CommunityContentStatus.DELETED, em.find(CommunityComment.class, reply).getStatus());
        assertEquals(CommunityContentStatus.ACTIVE, em.find(CommunityComment.class, commentId).getStatus());
        mvc.perform(get("/api/users/me/comments").with(users.authenticated(other)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
    }

    static Stream<Arguments> invalidContent() {
        return Stream.of("COMMENT", "REPLY", "UPDATE")
                .flatMap(operation -> Stream.of(" ", "a".repeat(1001))
                        .map(content -> Arguments.of(operation, content)));
    }

    @ParameterizedTest
    @MethodSource("invalidContent")
    void invalidContentDoesNotInsertOrChangeComments(String operation, String content) throws Exception {
        var request = switch (operation) {
            case "COMMENT" -> post("/api/posts/{id}/comments", postId);
            case "REPLY" -> post("/api/comments/{id}/replies", commentId);
            default -> patch("/api/comments/{id}", commentId);
        };
        mvc.perform(request.with(users.authenticated(author)).contentType("application/json")
                        .content(json.writeValueAsString(Map.of("content", content))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        assertOriginalThread();
    }

    @ParameterizedTest
    @ValueSource(strings = {"UPDATE", "DELETE"})
    void anotherUserCannotChangeCommentEvenWithForgedAuthorId(String operation) throws Exception {
        var request = operation.equals("UPDATE") ? patch("/api/comments/{id}", commentId)
                : delete("/api/comments/{id}", commentId);
        mvc.perform(request.with(users.authenticated(other)).contentType("application/json")
                        .content(json.writeValueAsString(Map.of("content", "forged", "userId", author.getUserId()))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
        assertOriginalThread();
    }

    @Test
    void anonymousRequestsCannotCreateReplyUpdateOrDelete() throws Exception {
        for (var request : new MockHttpServletRequestBuilder[] {
                post("/api/posts/{id}/comments", postId), post("/api/comments/{id}/replies", commentId),
                patch("/api/comments/{id}", commentId), delete("/api/comments/{id}", commentId)}) {
            mvc.perform(request.contentType("application/json").content("{\"content\":\"blocked\"}"))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
        }
        assertOriginalThread();
    }

    private long create(MockHttpServletRequestBuilder request, String content) throws Exception {
        var result = mvc.perform(request.with(users.authenticated(other)).contentType("application/json")
                        .content(json.writeValueAsString(Map.of("content", content, "authorId", author.getUserId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isNumber()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("data").asLong();
    }

    private void assertOriginalThread() {
        em.flush(); em.clear();
        var saved = em.find(CommunityComment.class, commentId);
        assertEquals("original", saved.getContent());
        assertEquals(CommunityContentStatus.ACTIVE, saved.getStatus());
        assertEquals(1L, em.createQuery("select count(c) from CommunityComment c where c.post.postId = :id", Long.class)
                .setParameter("id", postId).getSingleResult());
    }
}
