package com.kkirikkiri.report.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import com.kkirikkiri.community.comment.entity.CommunityComment;
import com.kkirikkiri.community.comment.repository.CommunityCommentRepository;
import com.kkirikkiri.community.post.entity.CommunityPost;
import com.kkirikkiri.community.post.repository.CommunityPostRepository;
import com.kkirikkiri.global.exception.CustomException;
import com.kkirikkiri.global.exception.ErrorCode;
import com.kkirikkiri.report.dto.ReportCreateRequest;
import com.kkirikkiri.report.entity.Report;
import com.kkirikkiri.report.entity.ReportStatus;
import com.kkirikkiri.report.entity.ReportType;
import com.kkirikkiri.report.repository.ReportRepository;
import com.kkirikkiri.user.entity.User;
import com.kkirikkiri.user.repository.UserRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReportServiceTest {

	private static final ValidatorFactory VALIDATOR_FACTORY = Validation.buildDefaultValidatorFactory();
	private ReportRepository reports;
	private UserRepository users;
	private CommunityPostRepository posts;
	private CommunityCommentRepository comments;
	private ReportService service;
	private User reporter;
	private User targetUser;
	private CommunityPost post;
	private CommunityComment comment;
	private final List<Report> saved = new ArrayList<>();

	@BeforeEach
	void setUp() {
		reports = mock(ReportRepository.class);
		users = mock(UserRepository.class);
		posts = mock(CommunityPostRepository.class);
		comments = mock(CommunityCommentRepository.class);
		service = new ReportService(reports, users, posts, comments, VALIDATOR_FACTORY.getValidator());
		reporter = user(1L);
		targetUser = user(10L);
		post = mock(CommunityPost.class);
		comment = mock(CommunityComment.class);
		when(post.getAuthor()).thenReturn(targetUser);
		when(post.getPostId()).thenReturn(10L);
		when(comment.getAuthor()).thenReturn(targetUser);
		when(comment.getCommentId()).thenReturn(10L);
		when(users.findById(1L)).thenReturn(Optional.of(reporter));
		when(users.findById(2L)).thenReturn(Optional.of(user(2L)));
		when(users.findById(10L)).thenReturn(Optional.of(targetUser));
		when(posts.findById(10L)).thenReturn(Optional.of(post));
		when(comments.findById(10L)).thenReturn(Optional.of(comment));
		when(reports.save(any(Report.class))).thenAnswer(invocation -> {
			Report report = invocation.getArgument(0);
			ReflectionTestUtils.setField(report, "reportId", (long) saved.size() + 1);
			saved.add(report);
			return report;
		});
		when(reports.existsByReporter_UserIdAndReportTypeAndTargetUser_UserId(anyLong(), any(), anyLong()))
				.thenAnswer(invocation -> saved.stream().anyMatch(report ->
						report.getReporter().getUserId().equals(invocation.getArgument(0))
						&& report.getReportType() == invocation.getArgument(1)
						&& report.getTargetUser().getUserId().equals(invocation.getArgument(2))));
		when(reports.existsByReporter_UserIdAndReportTypeAndPost_PostId(anyLong(), any(), anyLong()))
				.thenAnswer(invocation -> saved.stream().anyMatch(report ->
						report.getReporter().getUserId().equals(invocation.getArgument(0))
						&& report.getReportType() == invocation.getArgument(1)
						&& report.getPost() != null
						&& report.getPost().getPostId().equals(invocation.getArgument(2))));
		when(reports.existsByReporter_UserIdAndReportTypeAndComment_CommentId(anyLong(), any(), anyLong()))
				.thenAnswer(invocation -> saved.stream().anyMatch(report ->
						report.getReporter().getUserId().equals(invocation.getArgument(0))
						&& report.getReportType() == invocation.getArgument(1)
						&& report.getComment() != null
						&& report.getComment().getCommentId().equals(invocation.getArgument(2))));
	}

	@AfterAll
	static void closeValidator() {
		VALIDATOR_FACTORY.close();
	}

	@ParameterizedTest
	@EnumSource(ReportType.class)
	void rejectsSecondReportButAllowsAnotherReporter(ReportType type) {
		ReportCreateRequest request = request(type);
		assertEquals(1L, service.createReport(1L, request));
		Report report = saved.getFirst();
		assertSame(reporter, report.getReporter());
		assertSame(targetUser, report.getTargetUser());
		assertEquals(type, report.getReportType());
		assertEquals(ReportStatus.PENDING, report.getStatus());
		assertSame(type == ReportType.POST ? post : null, report.getPost());
		assertSame(type == ReportType.COMMENT ? comment : null, report.getComment());
		assertEquals(request.reason(), report.getReason());
		assertNull(report.getDescription());
		assertError(ErrorCode.INVALID_REQUEST, () -> service.createReport(1L, request));
		assertEquals(1, saved.size());
		assertEquals(2L, service.createReport(2L, request));
		assertEquals(2L, saved.get(1).getReporter().getUserId());
	}

	@Test
	void allowsDifferentTargetTypesWithSameIdAndAuthor() {
		service.createReport(1L, request(ReportType.POST));
		service.createReport(1L, request(ReportType.COMMENT));
		service.createReport(1L, request(ReportType.USER));
		assertEquals(3, saved.size());
	}

	@ParameterizedTest
	@EnumSource(ReportType.class)
	void rejectsMissingTarget(ReportType type) {
		assertError(ErrorCode.INVALID_REQUEST, () -> service.createReport(1L,
				new ReportCreateRequest(type.name(), 99L, "욕설·비방", null)));
		verify(reports, never()).save(any());
	}

	@ParameterizedTest
	@MethodSource("invalidRequests")
	void rejectsInvalidRequestBeforeRepositoryAccess(ReportCreateRequest request) {
		assertError(ErrorCode.VALIDATION_ERROR, () -> service.createReport(1L, request));
		verifyNoInteractions(reports, users, posts, comments);
	}

	static Stream<ReportCreateRequest> invalidRequests() {
		return Stream.of(null,
				new ReportCreateRequest(null, 10L, "욕설·비방", null),
				new ReportCreateRequest("OTHER", 10L, "욕설·비방", null),
				new ReportCreateRequest("POST", null, "욕설·비방", null),
				new ReportCreateRequest("POST", 0L, "욕설·비방", null),
				new ReportCreateRequest("POST", -1L, "욕설·비방", null),
				new ReportCreateRequest("POST", 10L, null, null),
				new ReportCreateRequest("POST", 10L, " ", null),
				new ReportCreateRequest("POST", 10L, "없는 사유", null),
				new ReportCreateRequest("POST", 10L, "기타", null),
				new ReportCreateRequest("POST", 10L, "기타", " \t\n"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"욕설·비방", "광고·홍보", "음란성 콘텐츠", "개인정보 노출", "도배·스팸", "기타"})
	void acceptsSupportedReasonsAndPreservesDescription(String reason) {
		service.createReport(1L, new ReportCreateRequest("POST", 10L, reason, "상세 신고 내용"));
		assertEquals(reason, saved.getFirst().getReason());
		assertEquals("상세 신고 내용", saved.getFirst().getDescription());
	}

	@Test
	void rejectsInvalidOrMissingAuthenticatedReporter() {
		for (Long userId : new Long[] {null, 0L, -1L, 99L}) {
			assertError(ErrorCode.UNAUTHORIZED, () -> service.createReport(userId, request(ReportType.POST)));
		}
		verifyNoInteractions(reports, posts, comments);
	}

	private static User user(Long id) {
		User user = new User("user" + id, "encoded", "user" + id + "@example.com");
		ReflectionTestUtils.setField(user, "userId", id);
		return user;
	}

	private static ReportCreateRequest request(ReportType type) {
		return new ReportCreateRequest(type.name(), 10L, "욕설·비방", null);
	}

	private static void assertError(ErrorCode expected, Runnable action) {
		assertEquals(expected, assertThrows(CustomException.class, action::run).getErrorCode());
	}
}
