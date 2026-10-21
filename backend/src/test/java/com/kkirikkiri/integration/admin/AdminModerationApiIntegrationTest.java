package com.kkirikkiri.integration.admin;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Map;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.kkirikkiri.community.comment.entity.CommunityComment;
import com.kkirikkiri.community.common.entity.CommunityContentStatus;
import com.kkirikkiri.community.post.entity.CommunityPost;
import com.kkirikkiri.integration.support.ApiIntegrationTest;
import com.kkirikkiri.integration.support.TestUsers;
import com.kkirikkiri.report.entity.Report;
import com.kkirikkiri.report.entity.ReportStatus;
import com.kkirikkiri.user.entity.User;
import com.kkirikkiri.user.entity.UserRole;
import com.kkirikkiri.user.entity.UserStatus;

@ApiIntegrationTest
class AdminModerationApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired TestUsers users;
    @Autowired EntityManager em;
    @Autowired ObjectMapper json;
    User admin, target, reporter;
    Long postId, commentId, replyId, siblingId, reportId;

    @BeforeEach
    void fixtures() {
        admin = users.createUser(UserRole.ADMIN);
        target = users.createUser();
        reporter = users.createUser();
        var post = new CommunityPost(target, null, "자유게시판", "moderation", "body");
        em.persist(post);
        var comment = new CommunityComment(post, target, null, "reported comment");
        em.persist(comment);
        var reply = new CommunityComment(post, reporter, comment, "reply");
        var sibling = new CommunityComment(post, reporter, null, "unrelated");
        em.persist(reply); em.persist(sibling);
        var report = Report.forComment(reporter, comment, "욕설·비방", null);
        em.persist(report); em.flush();
        postId = post.getPostId(); commentId = comment.getCommentId(); replyId = reply.getCommentId();
        siblingId = sibling.getCommentId(); reportId = report.getReportId();
    }

    @Test
    void deletingReportedCommentPersistsAuditAndUpdatesDashboardWithoutDeletingSiblings() throws Exception {
        JsonNode before = dashboard();
        mvc.perform(get("/api/admin/comments").param("reported", "true").with(users.authenticated(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(commentId));
        mvc.perform(get("/api/admin/comments").param("reported", "false").with(users.authenticated(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(3));
        process("DELETE_TARGET");
        em.flush(); em.clear();
        assertEquals(CommunityContentStatus.DELETED, em.find(CommunityComment.class, commentId).getStatus());
        assertEquals(CommunityContentStatus.DELETED, em.find(CommunityComment.class, replyId).getStatus());
        assertEquals(CommunityContentStatus.ACTIVE, em.find(CommunityComment.class, siblingId).getStatus());
        assertEquals(CommunityContentStatus.ACTIVE, em.find(CommunityPost.class, postId).getStatus());
        assertProcessed("DELETE_TARGET");
        JsonNode after = dashboard();
        assertEquals(before.get("totalComments").asLong() - 2, after.get("totalComments").asLong());
        assertEquals(before.get("pendingReports").asLong() - 1, after.get("pendingReports").asLong());
        assertEquals(before.get("totalReports"), after.get("totalReports"));
        mvc.perform(get("/api/admin/reports/{id}", reportId).with(users.authenticated(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.targetId").value(commentId))
                .andExpect(jsonPath("$.data.targetContent").value("reported comment"))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    @Test
    void reportSearchTypeAndStatusFiltersSelectPersistedRecords() throws Exception {
        var postReport = Report.forPost(reporter, em.find(CommunityPost.class, postId), "욕설·비방", null);
        var otherComment = new CommunityComment(em.find(CommunityPost.class, postId), target, null, "different content");
        em.persist(otherComment);
        var rejected = Report.forComment(reporter, otherComment, "욕설·비방", null);
        em.persist(postReport); em.persist(rejected); em.flush();
        rejected.process(ReportStatus.REJECTED, "NONE", admin, java.time.LocalDateTime.now());
        em.flush(); em.clear();
        mvc.perform(get("/api/admin/reports").param("q", "욕설·비방").param("type", "POST")
                        .with(users.authenticated(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(postReport.getReportId()));
        mvc.perform(get("/api/admin/reports").param("q", "욕설·비방").param("type", "COMMENT").param("status", "PENDING")
                        .with(users.authenticated(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(reportId));
        mvc.perform(get("/api/admin/reports").param("q", "reported comment").with(users.authenticated(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(reportId));
        process("REJECT");
        em.flush(); em.clear();
        mvc.perform(get("/api/admin/reports").param("type", "COMMENT").param("status", "REJECTED")
                        .with(users.authenticated(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    void suspendingReportedAuthorPersistsAuditAndInvalidatesExistingToken() throws Exception {
        var existingToken = users.authenticated(target);
        process("SUSPEND_USER");
        em.flush(); em.clear();
        assertEquals(UserStatus.SUSPENDED, em.find(User.class, target.getUserId()).getStatus());
        assertEquals(CommunityContentStatus.ACTIVE, em.find(CommunityComment.class, commentId).getStatus());
        assertProcessed("SUSPEND_USER");
        mvc.perform(get("/api/users/me/comments").with(existingToken))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    void memberStatusChangesTakeEffectForTheSameBearerToken() throws Exception {
        var existingToken = users.authenticated(target);
        updateStatus("SUSPENDED");
        em.flush(); em.clear();
        assertEquals(UserStatus.SUSPENDED, em.find(User.class, target.getUserId()).getStatus());
        mvc.perform(get("/api/users/me/comments").with(existingToken)).andExpect(status().isUnauthorized());
        updateStatus("ACTIVE");
        em.flush(); em.clear();
        assertEquals(UserStatus.ACTIVE, em.find(User.class, target.getUserId()).getStatus());
        mvc.perform(get("/api/users/me/comments").with(existingToken)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/members/{id}", target.getUserId()).with(users.authenticated(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.activity.postCount").value(1))
                .andExpect(jsonPath("$.data.activity.commentCount").value(1))
                .andExpect(jsonPath("$.data.activity.reportsReceived").value(1));
        updateStatus("WITHDRAWN");
        em.flush(); em.clear();
        assertEquals(UserStatus.WITHDRAWN, em.find(User.class, target.getUserId()).getStatus());
        mvc.perform(get("/api/users/me/comments").with(existingToken)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACTIVE", "SUSPENDED"})
    void withdrawnMemberCannotBeRestored(String status) throws Exception {
        target.changeStatus(UserStatus.WITHDRAWN);
        em.flush(); em.clear();
        mvc.perform(patch("/api/admin/members/{id}/status", target.getUserId()).with(users.authenticated(admin))
                        .contentType("application/json").content(json.writeValueAsString(Map.of("status", status))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        em.flush(); em.clear();
        assertEquals(UserStatus.WITHDRAWN, em.find(User.class, target.getUserId()).getStatus());
    }

    @Test
    void userReportCannotDeleteTargetOrLeaveProcessingHistory() throws Exception {
        var report = Report.forUser(reporter, target, "욕설·비방", null);
        em.persist(report); em.flush();
        mvc.perform(patch("/api/admin/reports/{id}", report.getReportId()).with(users.authenticated(admin))
                        .contentType("application/json").content("{\"action\":\"DELETE_TARGET\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        em.flush(); em.clear();
        var saved = em.find(Report.class, report.getReportId());
        assertEquals(ReportStatus.PENDING, saved.getStatus());
        assertNull(saved.getProcessedBy()); assertNull(saved.getProcessedAt()); assertNull(saved.getResolutionAction());
        assertEquals(UserStatus.ACTIVE, em.find(User.class, target.getUserId()).getStatus());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ANONYMOUS", "USER", "SUSPENDED_ADMIN"})
    void moderationWritesRequireAnActiveAdministrator(String caller) throws Exception {
        if (caller.equals("SUSPENDED_ADMIN")) { admin.changeStatus(UserStatus.SUSPENDED); em.flush(); em.clear(); }
        for (var request : new MockHttpServletRequestBuilder[] {
                patch("/api/admin/members/{id}/status", target.getUserId()).content("{\"status\":\"SUSPENDED\"}"),
                patch("/api/admin/reports/{id}", reportId).content("{\"action\":\"DELETE_TARGET\"}"),
                delete("/api/admin/comments/{id}", commentId)}) {
            if (!caller.equals("ANONYMOUS")) request.with(users.authenticated(caller.equals("USER") ? reporter : admin));
            mvc.perform(request.contentType("application/json"))
                    .andExpect(status().is(caller.equals("USER") ? 403 : 401))
                    .andExpect(jsonPath("$.errorCode").value(caller.equals("USER") ? "FORBIDDEN" : "UNAUTHORIZED"));
        }
        em.flush(); em.clear();
        assertEquals(UserStatus.ACTIVE, em.find(User.class, target.getUserId()).getStatus());
        assertEquals(ReportStatus.PENDING, em.find(Report.class, reportId).getStatus());
        assertEquals(CommunityContentStatus.ACTIVE, em.find(CommunityComment.class, commentId).getStatus());
    }

    @Test
    void invalidModerationPayloadsLeaveAccountAndReportUnchanged() throws Exception {
        mvc.perform(patch("/api/admin/members/{id}/status", target.getUserId()).with(users.authenticated(admin))
                        .contentType("application/json").content("{\"status\":\"UNKNOWN\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        mvc.perform(patch("/api/admin/reports/{id}", reportId).with(users.authenticated(admin))
                        .contentType("application/json").content("{\"action\":\"UNKNOWN\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        em.flush(); em.clear();
        assertEquals(UserStatus.ACTIVE, em.find(User.class, target.getUserId()).getStatus());
        assertEquals(ReportStatus.PENDING, em.find(Report.class, reportId).getStatus());
    }

    private void process(String action) throws Exception {
        mvc.perform(patch("/api/admin/reports/{id}", reportId).with(users.authenticated(admin))
                        .contentType("application/json").content(json.writeValueAsString(Map.of("action", action))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
    }

    private void updateStatus(String status) throws Exception {
        mvc.perform(patch("/api/admin/members/{id}/status", target.getUserId()).with(users.authenticated(admin))
                        .contentType("application/json").content(json.writeValueAsString(Map.of("status", status))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
    }

    private void assertProcessed(String action) {
        var saved = em.find(Report.class, reportId);
        assertEquals(ReportStatus.COMPLETED, saved.getStatus());
        assertEquals(action, saved.getResolutionAction());
        assertEquals(admin.getUserId(), saved.getProcessedBy().getUserId());
        assertNotNull(saved.getProcessedAt());
    }

    private JsonNode dashboard() throws Exception {
        var result = mvc.perform(get("/api/admin/dashboard").with(users.authenticated(admin)))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("data").get("stats");
    }
}
