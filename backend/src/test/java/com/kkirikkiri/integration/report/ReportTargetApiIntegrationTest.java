package com.kkirikkiri.integration.report;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Map;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import com.kkirikkiri.community.comment.entity.CommunityComment;
import com.kkirikkiri.community.post.entity.CommunityPost;
import com.kkirikkiri.integration.support.ApiIntegrationTest;
import com.kkirikkiri.integration.support.TestUsers;
import com.kkirikkiri.report.dto.ReportCreateRequest;
import com.kkirikkiri.report.entity.Report;
import com.kkirikkiri.report.entity.ReportStatus;
import com.kkirikkiri.report.entity.ReportType;
import com.kkirikkiri.user.entity.User;
import com.kkirikkiri.user.entity.UserRole;
import com.kkirikkiri.user.entity.UserStatus;
import com.kkirikkiri.community.common.entity.CommunityContentStatus;

@ApiIntegrationTest
class ReportTargetApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired TestUsers users;
    @Autowired EntityManager em;
    @Autowired ObjectMapper json;
    User reporter, target, commentAuthor, admin;
    Long postId, commentId;

    @BeforeEach
    void fixtures() {
        reporter = users.createUser();
        target = users.createUser();
        commentAuthor = users.createUser();
        admin = users.createUser(UserRole.ADMIN);
        var post = new CommunityPost(target, null, "자유게시판", "reported post", "post body", true);
        em.persist(post);
        var comment = new CommunityComment(post, commentAuthor, null, "comment body");
        comment.changeAnonymous(true);
        em.persist(comment);
        em.flush();
        postId = post.getPostId(); commentId = comment.getCommentId();
    }

    @ParameterizedTest
    @CsvSource({"POST,PENDING", "POST,COMPLETE", "POST,REJECT", "COMMENT,PENDING", "COMMENT,COMPLETE", "COMMENT,REJECT"})
    void linksActualTargetAndRejectsDuplicatesRegardlessOfProcessing(String type, String action) throws Exception {
        long targetId = type.equals("POST") ? postId : commentId;
        var body = new ReportCreateRequest(type, targetId, "욕설·비방", null);
        long id = create(reporter, body);
        if (!action.equals("PENDING")) {
            mvc.perform(patch("/api/admin/reports/{id}", id).with(users.authenticated(admin))
                            .contentType("application/json").content(json.writeValueAsString(Map.of("action", action))))
                    .andExpect(status().isOk());
        }
        em.flush(); em.clear();
        var saved = em.find(Report.class, id);
        long actualAuthor = type.equals("POST") ? target.getUserId() : commentAuthor.getUserId();
        assertEquals(actualAuthor, saved.getTargetUser().getUserId());
        assertEquals(reporter.getUserId(), saved.getReporter().getUserId());
        assertEquals(ReportType.valueOf(type), saved.getReportType());
        ReportStatus expectedStatus = action.equals("PENDING") ? ReportStatus.PENDING : action.equals("REJECT")
                ? ReportStatus.REJECTED : ReportStatus.COMPLETED;
        assertEquals(expectedStatus, saved.getStatus());
        if (action.equals("PENDING")) {
            assertNull(saved.getProcessedBy()); assertNull(saved.getProcessedAt()); assertNull(saved.getResolutionAction());
        } else {
            assertEquals(admin.getUserId(), saved.getProcessedBy().getUserId());
            assertNotNull(saved.getProcessedAt()); assertEquals("NONE", saved.getResolutionAction());
        }
        assertEquals(UserStatus.ACTIVE, saved.getTargetUser().getStatus());
        assertEquals(CommunityContentStatus.ACTIVE, em.find(CommunityPost.class, postId).getStatus());
        assertEquals(CommunityContentStatus.ACTIVE, em.find(CommunityComment.class, commentId).getStatus());
        if (type.equals("POST")) {
            assertEquals(postId, saved.getPost().getPostId()); assertNull(saved.getComment());
        } else {
            assertEquals(commentId, saved.getComment().getCommentId()); assertNull(saved.getPost());
        }
        mvc.perform(get("/api/admin/reports/{id}", id).with(users.authenticated(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.targetId").value(targetId))
                .andExpect(jsonPath("$.data.targetUser.id").value(actualAuthor))
                .andExpect(jsonPath("$.data.reporter.id").value(reporter.getUserId()));
        // A different reporter remains entitled to report the same content.
        create(admin, body);
        mvc.perform(post("/api/reports").with(users.authenticated(reporter)).contentType("application/json")
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        em.flush(); em.clear();
        assertEquals(2L, em.createQuery("select count(r) from Report r where r.reporter.userId in :ids", Long.class)
                .setParameter("ids", java.util.List.of(reporter.getUserId(), admin.getUserId())).getSingleResult());
        assertEquals(expectedStatus, em.find(Report.class, id).getStatus());
    }

    @ParameterizedTest
    @ValueSource(strings = {"USER", "POST", "COMMENT"})
    void missingTargetIsRejectedWithoutSavingReport(String type) throws Exception {
        mvc.perform(post("/api/reports").with(users.authenticated(reporter)).contentType("application/json")
                        .content(json.writeValueAsString(new ReportCreateRequest(type, Long.MAX_VALUE, "욕설·비방", null))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        assertNoReports();
    }

    @ParameterizedTest
    @CsvSource({"UNKNOWN,욕설·비방", "POST,unsupported"})
    void invalidTypeOrReasonIsRejectedBeforePersistence(String type, String reason) throws Exception {
        mvc.perform(post("/api/reports").with(users.authenticated(reporter)).contentType("application/json")
                        .content(json.writeValueAsString(new ReportCreateRequest(type, postId, reason, null))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        assertNoReports();
    }

    private long create(User user, ReportCreateRequest body) throws Exception {
        var result = mvc.perform(post("/api/reports").with(users.authenticated(user)).contentType("application/json")
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isNumber()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("data").asLong();
    }

    private void assertNoReports() {
        em.flush(); em.clear();
        assertEquals(0L, em.createQuery("select count(r) from Report r where r.reporter.userId = :id", Long.class)
                .setParameter("id", reporter.getUserId()).getSingleResult());
    }
}
