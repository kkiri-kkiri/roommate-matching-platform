package com.kkirikkiri.report.repository;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import com.kkirikkiri.community.comment.entity.CommunityComment;
import com.kkirikkiri.community.post.entity.CommunityPost;
import com.kkirikkiri.report.entity.Report;
import com.kkirikkiri.report.entity.ReportStatus;
import com.kkirikkiri.report.entity.ReportType;
import com.kkirikkiri.user.entity.User;
import com.kkirikkiri.user.entity.UserRole;

import static org.junit.jupiter.api.Assertions.*;

/** 기존 스키마를 변경하지 않고 테스트 데이터는 종료 시 롤백한다. */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=none", "spring.sql.init.mode=never"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ReportRepositoryIntegrationTest {

	@Autowired EntityManager em;
	@Autowired ReportRepository reports;

	private final String token = UUID.randomUUID().toString().replace("-", "");

	private <T> T persist(T entity) {
		em.persist(entity);
		return entity;
	}

	private User user(String suffix, UserRole role) {
		return persist(new User(token + suffix, "encoded", token + suffix + "@test.invalid", role));
	}

	@Test
	void postExistsIgnoresReporterAndResolutionButDoesNotIncludeUserOrCommentReports() {
		User author = user("author", UserRole.USER);
		User reporter = user("reporter", UserRole.USER);
		User admin = user("admin", UserRole.ADMIN);
		CommunityPost post = persist(new CommunityPost(author, null, "자유게시판", "title", "body"));
		CommunityPost other = persist(new CommunityPost(author, null, "자유게시판", "other", "body"));
		CommunityComment comment = persist(new CommunityComment(post, author, null, "comment"));
		persist(Report.forUser(reporter, author, "욕설·비방", null));
		persist(Report.forComment(reporter, comment, "욕설·비방", null));
		em.flush();
		assertFalse(reports.existsByPost_PostId(post.getPostId()));
		Report report = persist(Report.forPost(reporter, post, "욕설·비방", null));
		em.flush();
		assertTrue(reports.existsByPost_PostId(post.getPostId()));
		assertFalse(reports.existsByPost_PostId(other.getPostId()));
		assertFalse(reports.existsByReporter_UserIdAndReportTypeAndPost_PostId(
				admin.getUserId(), ReportType.POST, post.getPostId()));
		report.process(ReportStatus.REJECTED, "NONE", admin, LocalDateTime.now());
		post.delete();
		em.flush();
		em.clear();
		assertTrue(reports.existsByPost_PostId(post.getPostId()));
	}

	@Test
	void commentExistsIncludesRepliesAndCompletedReportsWithoutMatchingPostReports() {
		User author = user("author", UserRole.USER);
		User reporter = user("reporter", UserRole.USER);
		User admin = user("admin", UserRole.ADMIN);
		CommunityPost post = persist(new CommunityPost(author, null, "자유게시판", "title", "body"));
		CommunityComment parent = persist(new CommunityComment(post, author, null, "parent"));
		CommunityComment reply = persist(new CommunityComment(post, author, parent, "reply"));
		persist(Report.forPost(reporter, post, "욕설·비방", null));
		em.flush();
		assertFalse(reports.existsByComment_CommentId(parent.getCommentId()));
		assertFalse(reports.existsByComment_CommentId(reply.getCommentId()));
		Report report = persist(Report.forComment(reporter, reply, "욕설·비방", null));
		em.flush();
		assertTrue(reports.existsByComment_CommentId(reply.getCommentId()));
		assertFalse(reports.existsByComment_CommentId(parent.getCommentId()));
		assertFalse(reports.existsByReporter_UserIdAndReportTypeAndComment_CommentId(
				admin.getUserId(), ReportType.COMMENT, reply.getCommentId()));
		report.process(ReportStatus.COMPLETED, "DELETE_TARGET", admin, LocalDateTime.now());
		reply.delete();
		em.flush();
		em.clear();
		assertTrue(reports.existsByComment_CommentId(reply.getCommentId()));
	}
}
