package com.kkirikkiri.admin.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import com.kkirikkiri.admin.dto.AdminMemberStatusUpdateRequest;
import com.kkirikkiri.admin.dto.AdminPostListRequest;
import com.kkirikkiri.admin.dto.AdminCommentListRequest;
import com.kkirikkiri.community.post.repository.CommunityPostReadRepository;
import com.kkirikkiri.report.repository.ReportRepository;
import com.kkirikkiri.user.entity.UserProfile;
import com.kkirikkiri.admin.dto.AdminReportProcessRequest;
import com.kkirikkiri.calendar.entity.ScheduleCrawlCategory;
import com.kkirikkiri.calendar.entity.ScheduleCrawlRun;
import com.kkirikkiri.calendar.repository.AdminCrawledScheduleQueryRepository;
import com.kkirikkiri.calendar.repository.ScheduleCrawlRunRepository;
import com.kkirikkiri.community.comment.entity.CommunityComment;
import com.kkirikkiri.community.comment.repository.CommunityCommentRepository;
import com.kkirikkiri.community.common.entity.CommunityContentStatus;
import com.kkirikkiri.community.post.entity.CommunityPost;
import com.kkirikkiri.community.repository.AdminCommentQueryRepository;
import com.kkirikkiri.community.repository.AdminPostQueryRepository;
import com.kkirikkiri.global.exception.CustomException;
import com.kkirikkiri.global.exception.ErrorCode;
import com.kkirikkiri.report.entity.Report;
import com.kkirikkiri.report.entity.ReportStatus;
import com.kkirikkiri.report.repository.AdminReportQueryRepository;
import com.kkirikkiri.user.entity.User;
import com.kkirikkiri.user.entity.UserRole;
import com.kkirikkiri.user.entity.UserStatus;
import com.kkirikkiri.user.repository.AdminMemberQueryRepository;
import com.kkirikkiri.user.repository.UserRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminServiceTest {

	private final UserRepository users = mock(UserRepository.class);
	private final AdminMemberQueryRepository members = mock(AdminMemberQueryRepository.class);
	private final AdminPostQueryRepository posts = mock(AdminPostQueryRepository.class);
	private final AdminCommentQueryRepository comments = mock(AdminCommentQueryRepository.class);
	private final CommunityCommentRepository postComments = mock(CommunityCommentRepository.class);
	private final AdminReportQueryRepository reports = mock(AdminReportQueryRepository.class);
	private final AdminCrawledScheduleQueryRepository schedules = mock(AdminCrawledScheduleQueryRepository.class);
	private final ScheduleCrawlRunRepository runs = mock(ScheduleCrawlRunRepository.class);
	private final ReportRepository reportExistence = mock(ReportRepository.class);
	private final CommunityPostReadRepository postReads = mock(CommunityPostReadRepository.class);
	private final User admin = new User("admin", "encoded", "admin@example.com", UserRole.ADMIN);
	private final User member = new User("member", "encoded", "member@example.com");
	private ValidatorFactory validation;
	private AdminService service;

	@BeforeEach
	void setUp() {
		validation = Validation.buildDefaultValidatorFactory();
		service = new AdminService(users, members, posts, comments, postComments, reports,
				schedules, runs, reportExistence, postReads, validation.getValidator());
		when(users.findById(1L)).thenReturn(Optional.of(admin));
		when(users.findById(2L)).thenReturn(Optional.of(member));
	}

	@AfterEach
	void closeValidation() {
		validation.close();
	}

	@Test
	void nonAdminAndInactiveAdminCannotReadDashboard() {
		assertError(ErrorCode.FORBIDDEN, () -> service.getDashboard(2L));
		admin.changeStatus(UserStatus.SUSPENDED);
		assertError(ErrorCode.FORBIDDEN, () -> service.getDashboard(1L));
		assertError(ErrorCode.UNAUTHORIZED, () -> service.getDashboard(null));
		verifyNoInteractions(members, posts, comments, reports, schedules);
	}

	@Test
	void withdrawnMemberCannotBeReactivatedAndInvalidStatusIsRejected() {
		member.changeStatus(UserStatus.WITHDRAWN);
		assertError(ErrorCode.INVALID_REQUEST,
				() -> service.updateMemberStatus(1L, 2L, new AdminMemberStatusUpdateRequest("ACTIVE")));
		assertError(ErrorCode.VALIDATION_ERROR,
				() -> service.updateMemberStatus(1L, 2L, new AdminMemberStatusUpdateRequest("UNKNOWN")));
		assertEquals(UserStatus.WITHDRAWN, member.getStatus());
	}

	@Test
	void deletingReportedPostAlsoDeletesCommentsAndRepliesAndRecordsProcessor() {
		CommunityPost post = new CommunityPost(member, null, "자유게시판", "title", "body");
		ReflectionTestUtils.setField(post, "postId", 10L);
		CommunityComment comment = new CommunityComment(post, member, null, "comment");
		CommunityComment reply = new CommunityComment(post, member, comment, "reply");
		Report report = Report.forPost(admin, post, "욕설·비방", null);
		when(reports.findReportById(5L)).thenReturn(Optional.of(report));
		when(postComments.findAllByPost_PostIdAndStatusOrderByCreatedAtAscCommentIdAsc(
				10L, CommunityContentStatus.ACTIVE)).thenReturn(List.of(comment, reply));

		service.processReport(1L, 5L, new AdminReportProcessRequest("DELETE_TARGET"));

		assertEquals(CommunityContentStatus.DELETED, post.getStatus());
		assertEquals(CommunityContentStatus.DELETED, comment.getStatus());
		assertEquals(CommunityContentStatus.DELETED, reply.getStatus());
		assertEquals(ReportStatus.COMPLETED, report.getStatus());
		assertEquals("DELETE_TARGET", report.getResolutionAction());
		assertSame(admin, report.getProcessedBy());
		assertNotNull(report.getProcessedAt());
		assertSame(post, report.getPost());
	}

	@Test
	void processingCompletedReportCannotSuspendTarget() {
		Report report = Report.forUser(admin, member, "욕설·비방", null);
		report.process(ReportStatus.COMPLETED, "NONE", admin, LocalDateTime.now());
		when(reports.findReportById(5L)).thenReturn(Optional.of(report));
		assertError(ErrorCode.INVALID_REQUEST,
				() -> service.processReport(1L, 5L, new AdminReportProcessRequest("SUSPEND_USER")));
		assertEquals(UserStatus.ACTIVE, member.getStatus());
	}

	@Test
	void userReportCannotDeleteTargetOrSuspendWithdrawnMember() {
		Report report = Report.forUser(admin, member, "욕설·비방", null);
		when(reports.findReportById(5L)).thenReturn(Optional.of(report));
		assertError(ErrorCode.INVALID_REQUEST,
				() -> service.processReport(1L, 5L, new AdminReportProcessRequest("DELETE_TARGET")));
		member.changeStatus(UserStatus.WITHDRAWN);
		assertError(ErrorCode.INVALID_REQUEST,
				() -> service.processReport(1L, 5L, new AdminReportProcessRequest("SUSPEND_USER")));
		assertEquals(ReportStatus.PENDING, report.getStatus());
		assertNull(report.getProcessedBy());
	}

	@Test
	void suspendAndRejectHaveDifferentEffectsOnMember() {
		Report suspended = Report.forUser(admin, member, "욕설·비방", null);
		when(reports.findReportById(5L)).thenReturn(Optional.of(suspended));
		service.processReport(1L, 5L, new AdminReportProcessRequest("SUSPEND_USER"));
		assertEquals(UserStatus.SUSPENDED, member.getStatus());
		assertEquals(ReportStatus.COMPLETED, suspended.getStatus());
		assertEquals("SUSPEND_USER", suspended.getResolutionAction());
		member.changeStatus(UserStatus.ACTIVE);
		Report rejected = Report.forUser(admin, member, "욕설·비방", null);
		when(reports.findReportById(6L)).thenReturn(Optional.of(rejected));
		service.processReport(1L, 6L, new AdminReportProcessRequest("REJECT"));
		assertEquals(UserStatus.ACTIVE, member.getStatus());
		assertEquals(ReportStatus.REJECTED, rejected.getStatus());
		assertEquals("NONE", rejected.getResolutionAction());
	}

	@Test
	void deletingParentCommentPreservesReplyAndPost() {
		CommunityPost post = new CommunityPost(member, null, "자유게시판", "title", "body");
		CommunityComment parent = new CommunityComment(post, member, null, "parent");
		CommunityComment reply = new CommunityComment(post, member, parent, "reply");
		when(comments.findCommentById(10L)).thenReturn(Optional.of(parent));
		service.deleteComment(1L, 10L);
		assertEquals(CommunityContentStatus.DELETED, parent.getStatus());
		assertEquals(CommunityContentStatus.ACTIVE, reply.getStatus());
		assertEquals(CommunityContentStatus.ACTIVE, post.getStatus());
		verifyNoInteractions(postComments);
	}

	@Test
	void crawlStatusUsesActualScheduleCountAndDoesNotInventMissingRun() {
		ScheduleCrawlRun run = new ScheduleCrawlRun(ScheduleCrawlCategory.SCHOOL, LocalDateTime.now());
		run.complete(3, LocalDateTime.now());
		when(runs.findFirstByCategoryOrderByStartedAtDescCrawlRunIdDesc(ScheduleCrawlCategory.SCHOOL))
				.thenReturn(Optional.of(run));
		when(schedules.countSchedules(ScheduleCrawlCategory.SCHOOL)).thenReturn(28L);
		var statuses = service.getScheduleCrawlStatuses(1L);
		var school = statuses.stream().filter(s -> s.category() == ScheduleCrawlCategory.SCHOOL).findFirst().orElseThrow();
		var dormitory = statuses.stream().filter(s -> s.category() == ScheduleCrawlCategory.DORMITORY).findFirst().orElseThrow();
		assertEquals(3, school.recentCollectedCount());
		assertEquals(28, school.totalCollectedCount());
		assertNull(dormitory.status());
		assertNull(dormitory.lastRunAt());
		assertNull(dormitory.lastFailure());
		assertEquals(0, dormitory.recentCollectedCount());
	}

	private void assertError(ErrorCode code, org.junit.jupiter.api.function.Executable action) {
		assertEquals(code, assertThrows(CustomException.class, action).getErrorCode());
	}

	private CommunityPost post(Long id) {
		ReflectionTestUtils.setField(member, "userId", 2L);
		CommunityPost post = new CommunityPost(member, null, "자유게시판", "title", "body");
		ReflectionTestUtils.setField(post, "postId", id);
		ReflectionTestUtils.setField(post, "createdAt", LocalDateTime.of(2026, 9, 25, 12, 0));
		post.increaseViewCount();
		return post;
	}

	private void authorProfile() {
		UserProfile profile = mock(UserProfile.class);
		when(profile.getUser()).thenReturn(member);
		when(profile.getNickname()).thenReturn("nickname");
		when(members.findProfilesByUserIds(List.of(2L))).thenReturn(List.of(profile));
	}

	@Test
	void postListForwardsAllFiltersAndMapsCountsAndReportedPerPost() {
		CommunityPost first = post(10L);
		CommunityPost second = post(11L);
		authorProfile();
		var pageable = PageRequest.of(1, 2);
		when(posts.searchPosts("query", "자유게시판", true, "LIKES", pageable))
				.thenReturn(new PageImpl<>(List.of(first, second), pageable, 5));
		when(postReads.countLikes(10L)).thenReturn(7L);
		when(postComments.countByPost_PostIdAndStatus(10L, CommunityContentStatus.ACTIVE)).thenReturn(3L);
		when(reportExistence.existsByPost_PostId(10L)).thenReturn(true);
		var result = service.getPosts(1L, new AdminPostListRequest(1, 2, "query", "자유게시판", true, "LIKES"));
		assertEquals(1, result.page());
		assertEquals(5, result.totalElements());
		assertEquals(2, result.content().size());
		var item = result.content().getFirst();
		assertEquals(10L, item.id());
		assertEquals("자유게시판", item.category());
		assertEquals("title", item.title());
		assertEquals(first.getCreatedAt(), item.createdAt());
		assertEquals("nickname", item.author().nickname());
		assertEquals(2L, item.author().id());
		assertEquals(1, item.views());
		assertEquals(7, item.likes());
		assertEquals(3, item.commentCount());
		assertTrue(item.reported());
		assertFalse(result.content().get(1).reported());
		verify(members).findProfilesByUserIds(List.of(2L));
	}

	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	void postDetailMapsContentAndGlobalReported(boolean reported) {
		CommunityPost post = post(10L);
		when(posts.findPostById(10L)).thenReturn(Optional.of(post));
		when(reportExistence.existsByPost_PostId(10L)).thenReturn(reported);
		when(postReads.countLikes(10L)).thenReturn(4L);
		when(postComments.countByPost_PostIdAndStatus(10L, CommunityContentStatus.ACTIVE)).thenReturn(2L);
		var result = service.getPost(1L, 10L);
		assertEquals(10L, result.id());
		assertEquals("body", result.content());
		assertEquals("title", result.title());
		assertEquals("자유게시판", result.category());
		assertEquals(post.getCreatedAt(), result.createdAt());
		assertEquals(1, result.views());
		assertEquals(4, result.likes());
		assertEquals(2, result.commentCount());
		assertEquals(2L, result.author().id());
		assertNull(result.author().nickname());
		assertEquals(reported, result.reported());
		assertEquals(1, post.getViewCount());
	}

	@Test
	void missingPostDetailUsesExistingInvalidRequestPattern() {
		assertError(ErrorCode.INVALID_REQUEST, () -> service.getPost(1L, 404L));
		verifyNoInteractions(reportExistence, postReads, postComments);
	}

	private CommunityComment comment(Long id, CommunityPost post, CommunityComment parent) {
		CommunityComment comment = new CommunityComment(post, member, parent, "comment" + id);
		ReflectionTestUtils.setField(comment, "commentId", id);
		ReflectionTestUtils.setField(comment, "createdAt", LocalDateTime.of(2026, 9, 25, 13, 0));
		return comment;
	}

	@Test
	void commentListIncludesRepliesAndMapsReportedIndependently() {
		CommunityPost post = post(10L);
		CommunityComment parent = comment(20L, post, null);
		CommunityComment reply = comment(21L, post, parent);
		authorProfile();
		var pageable = PageRequest.of(1, 2);
		when(comments.searchComments("query", false, pageable))
				.thenReturn(new PageImpl<>(List.of(parent, reply), pageable, 5));
		when(reportExistence.existsByComment_CommentId(21L)).thenReturn(true);
		var result = service.getComments(1L, new AdminCommentListRequest(1, 2, "query", false));
		assertEquals(1, result.page());
		assertEquals(5, result.totalElements());
		assertEquals(2, result.content().size());
		assertFalse(result.content().getFirst().reported());
		var item = result.content().get(1);
		assertTrue(item.reported());
		assertEquals(21L, item.id());
		assertEquals("comment21", item.content());
		assertEquals(10L, item.postId());
		assertEquals("title", item.postTitle());
		assertEquals(reply.getCreatedAt(), item.createdAt());
		assertEquals("nickname", item.author().nickname());
		verify(members).findProfilesByUserIds(List.of(2L));
	}

	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	void commentDetailMapsGlobalReported(boolean reported) {
		CommunityComment comment = comment(20L, post(10L), null);
		when(comments.findCommentById(20L)).thenReturn(Optional.of(comment));
		when(reportExistence.existsByComment_CommentId(20L)).thenReturn(reported);
		var result = service.getComment(1L, 20L);
		assertEquals(20L, result.id());
		assertEquals("comment20", result.content());
		assertEquals(10L, result.postId());
		assertEquals("title", result.postTitle());
		assertEquals(comment.getCreatedAt(), result.createdAt());
		assertEquals(2L, result.author().id());
		assertNull(result.author().nickname());
		assertEquals(reported, result.reported());
	}

	@Test
	void missingCommentDetailUsesExistingInvalidRequestPattern() {
		assertError(ErrorCode.INVALID_REQUEST, () -> service.getComment(1L, 404L));
		verifyNoInteractions(reportExistence);
	}

	@Test
	void newReadsRejectNonAdminAndInvalidListRequests() {
		assertError(ErrorCode.FORBIDDEN, () -> service.getPosts(2L, new AdminPostListRequest(null, null, null, null, null, null)));
		assertError(ErrorCode.FORBIDDEN, () -> service.getPost(2L, 10L));
		assertError(ErrorCode.FORBIDDEN, () -> service.getComments(2L, new AdminCommentListRequest(null, null, null, null)));
		assertError(ErrorCode.FORBIDDEN, () -> service.getComment(2L, 20L));
		assertError(ErrorCode.VALIDATION_ERROR, () -> service.getPosts(1L, new AdminPostListRequest(-1, 0, null, null, null, "INVALID")));
		assertError(ErrorCode.VALIDATION_ERROR, () -> service.getComments(1L, new AdminCommentListRequest(-1, 0, null, null)));
		verifyNoInteractions(posts, comments, reportExistence);
	}
}
