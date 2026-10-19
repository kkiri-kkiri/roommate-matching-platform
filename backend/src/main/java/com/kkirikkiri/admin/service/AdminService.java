package com.kkirikkiri.admin.service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Tuple;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kkirikkiri.admin.dto.AdminCrawledScheduleListRequest;
import com.kkirikkiri.admin.dto.AdminCommentListRequest;
import com.kkirikkiri.admin.dto.AdminCommentResponse;
import com.kkirikkiri.admin.dto.AdminPostListRequest;
import com.kkirikkiri.admin.dto.AdminPostResponse;
import com.kkirikkiri.admin.dto.AdminPostDetailResponse;
import com.kkirikkiri.community.post.repository.CommunityPostReadRepository;
import com.kkirikkiri.community.post.repository.PostLikeRepository;
import com.kkirikkiri.community.post.repository.CommunityPostRepository;
import com.kkirikkiri.report.repository.ReportRepository;
import com.kkirikkiri.admin.dto.AdminCrawledScheduleResponse;
import com.kkirikkiri.admin.dto.AdminDashboardResponse;
import com.kkirikkiri.admin.dto.AdminMemberDetailResponse;
import com.kkirikkiri.admin.dto.AdminMemberListRequest;
import com.kkirikkiri.admin.dto.AdminMemberResponse;
import com.kkirikkiri.admin.dto.AdminMemberStatusUpdateRequest;
import com.kkirikkiri.admin.dto.AdminReportDetailResponse;
import com.kkirikkiri.admin.dto.AdminReportListRequest;
import com.kkirikkiri.admin.dto.AdminReportProcessRequest;
import com.kkirikkiri.admin.dto.AdminReportResponse;
import com.kkirikkiri.admin.dto.AdminScheduleCrawlStatusResponse;
import com.kkirikkiri.calendar.entity.ScheduleCrawlCategory;
import com.kkirikkiri.calendar.entity.ScheduleCrawlRun;
import com.kkirikkiri.calendar.entity.ScheduleCrawlStatus;
import com.kkirikkiri.calendar.repository.AdminCrawledScheduleQueryRepository;
import com.kkirikkiri.calendar.repository.ScheduleCrawlRunRepository;
import com.kkirikkiri.community.comment.entity.CommunityComment;
import com.kkirikkiri.community.comment.service.CommunityCommentWriteService;
import com.kkirikkiri.community.comment.repository.CommunityCommentRepository;
import com.kkirikkiri.community.common.entity.CommunityContentStatus;
import com.kkirikkiri.community.post.dto.CommunityAuthorResponse;
import com.kkirikkiri.community.post.entity.CommunityPost;
import com.kkirikkiri.community.repository.AdminCommentQueryRepository;
import com.kkirikkiri.community.repository.AdminPostQueryRepository;
import com.kkirikkiri.global.exception.CustomException;
import com.kkirikkiri.global.exception.ErrorCode;
import com.kkirikkiri.matching.dto.RoommatePageResponse;
import com.kkirikkiri.report.entity.Report;
import com.kkirikkiri.report.entity.ReportStatus;
import com.kkirikkiri.report.entity.ReportType;
import com.kkirikkiri.report.repository.AdminReportQueryRepository;
import com.kkirikkiri.user.entity.User;
import com.kkirikkiri.user.entity.UserProfile;
import com.kkirikkiri.user.entity.UserRole;
import com.kkirikkiri.user.entity.UserStatus;
import com.kkirikkiri.user.repository.AdminMemberQueryRepository;
import com.kkirikkiri.user.repository.UserRepository;

/**
 * adminId에는 인증된 사용자의 ID를 전달한다. 모든 공개 메서드에서 관리자 권한을 확인한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminService {

	private final UserRepository userRepository;
	private final AdminMemberQueryRepository memberRepository;
	private final AdminPostQueryRepository postRepository;
	private final AdminCommentQueryRepository commentRepository;
	private final CommunityCommentRepository communityCommentRepository;
	private final CommunityPostRepository communityPostRepository;
	private final EntityManager entityManager;
	private final CommunityCommentWriteService commentWriteService;
	private final AdminReportQueryRepository reportRepository;
	private final AdminCrawledScheduleQueryRepository scheduleRepository;
	private final ScheduleCrawlRunRepository crawlRunRepository;
	private final ReportRepository reportExistenceRepository;
	private final CommunityPostReadRepository postReadRepository;
	private final PostLikeRepository postLikeRepository;
	private final Validator validator;

	public RoommatePageResponse<AdminPostResponse> getPosts(Long adminId, AdminPostListRequest request) {
		requireAdmin(adminId);
		validateRequest(request);
		validateCommunityPage(request.page(), request.size());
		Page<CommunityPost> page = postRepository.searchPosts(request.q(), request.category(), request.reported(),
				request.sort(), PageRequest.of(request.page(), request.size()));
		var profiles = profiles(page.getContent().stream().map(CommunityPost::getAuthor).toList());
		var ids = page.getContent().stream().map(CommunityPost::getPostId).toList();
		Map<Long, Long> likes = ids.isEmpty() ? Map.of() : postLikeRepository.countLikesByPostIds(ids).stream()
				.collect(Collectors.toMap(PostLikeRepository.LikeCount::getPostId,
						PostLikeRepository.LikeCount::getLikes));
		Map<Long, Long> counts = ids.isEmpty() ? Map.of() : communityCommentRepository.countByPostIds(ids, CommunityContentStatus.ACTIVE).stream()
				.collect(Collectors.toMap(CommunityCommentRepository.PostCommentCount::getPostId,
						CommunityCommentRepository.PostCommentCount::getComments));
		var reported = ids.isEmpty() ? Set.<Long>of() : new HashSet<>(reportExistenceRepository.findReportedPostIds(ids));
		return RoommatePageResponse.from(page.map(post -> new AdminPostResponse(post.getPostId(), post.getCategory(),
				post.getTitle(), author(post.getAuthor(), profiles), post.getCreatedAt(), post.getViewCount(),
				likes.getOrDefault(post.getPostId(), 0L), counts.getOrDefault(post.getPostId(), 0L), reported.contains(post.getPostId()))));
	}

	public AdminPostDetailResponse getPost(Long adminId, Long postId) {
		requireAdmin(adminId);
		validateId(postId);
		CommunityPost post = postRepository.findPostById(postId).orElseThrow(this::invalidRequest);
		AdminPostResponse summary = postResponse(post, profiles(List.of(post.getAuthor())));
		return new AdminPostDetailResponse(summary.id(), summary.category(), summary.title(), summary.author(),
				summary.createdAt(), summary.views(), summary.likes(), summary.commentCount(), summary.reported(),
				post.getContent());
	}

	public RoommatePageResponse<AdminCommentResponse> getComments(Long adminId, AdminCommentListRequest request) {
		requireAdmin(adminId);
		validateRequest(request);
		validateCommunityPage(request.page(), request.size());
		Page<CommunityComment> page = commentRepository.searchComments(request.q(), request.reported(),
				PageRequest.of(request.page(), request.size()));
		var profiles = profiles(page.getContent().stream().map(CommunityComment::getAuthor).toList());
		var ids = page.getContent().stream().map(CommunityComment::getCommentId).toList();
		var reported = ids.isEmpty() ? Set.<Long>of() : new HashSet<>(reportExistenceRepository.findReportedCommentIds(ids));
		return RoommatePageResponse.from(page.map(comment -> new AdminCommentResponse(comment.getCommentId(), comment.getContent(),
				author(comment.getAuthor(), profiles), comment.getCreatedAt(), comment.getPost().getPostId(),
				comment.getPost().getTitle(), reported.contains(comment.getCommentId()))));
	}

	public AdminCommentResponse getComment(Long adminId, Long commentId) {
		requireAdmin(adminId);
		validateId(commentId);
		CommunityComment comment = commentRepository.findCommentById(commentId).orElseThrow(this::invalidRequest);
		return commentResponse(comment, profiles(List.of(comment.getAuthor())));
	}

	private AdminPostResponse postResponse(CommunityPost post, Map<Long, UserProfile> profiles) {
		Long postId = post.getPostId();
		return new AdminPostResponse(postId, post.getCategory(), post.getTitle(), author(post.getAuthor(), profiles),
				post.getCreatedAt(), post.getViewCount(), postReadRepository.countLikes(postId),
				communityCommentRepository.countByPost_PostIdAndStatus(postId, CommunityContentStatus.ACTIVE),
				reportExistenceRepository.existsByPost_PostId(postId));
	}

	private AdminCommentResponse commentResponse(CommunityComment comment, Map<Long, UserProfile> profiles) {
		return new AdminCommentResponse(comment.getCommentId(), comment.getContent(),
				author(comment.getAuthor(), profiles), comment.getCreatedAt(), comment.getPost().getPostId(),
				comment.getPost().getTitle(), reportExistenceRepository.existsByComment_CommentId(comment.getCommentId()));
	}

	public AdminDashboardResponse getDashboard(Long adminId) {
		requireAdmin(adminId);
		var posts = postRepository.findRecentPosts();
		var profiles = profiles(posts.stream().map(CommunityPost::getAuthor).toList());
		return new AdminDashboardResponse(
				new AdminDashboardResponse.Stats(memberRepository.countMembers(),
						postRepository.countActivePosts(), commentRepository.countActiveComments(),
						reportRepository.countReports(), reportRepository.countPendingReports(),
						scheduleRepository.countSchedules(ScheduleCrawlCategory.SCHOOL),
						scheduleRepository.countSchedules(ScheduleCrawlCategory.DORMITORY)),
				memberRepository.findRecentMembers().stream().map(this::memberResponse)
						.map(m -> new AdminDashboardResponse.MemberSummary(m.id(), m.nickname(), m.major(),
								m.dormitory(), m.createdAt(), m.status())).toList(),
				posts.stream().map(p -> new AdminDashboardResponse.PostSummary(p.getPostId(), p.getTitle(),
						p.getCategory(), author(p.getAuthor(), profiles), p.getCreatedAt())).toList(),
				reportRepository.findRecentReports().stream().map(r -> new AdminDashboardResponse.ReportSummary(
						r.getReportId(), r.getStatus(), r.getReportType(), r.getReason(), r.getCreatedAt())).toList(),
				recentSchedules(ScheduleCrawlCategory.SCHOOL), recentSchedules(ScheduleCrawlCategory.DORMITORY));
	}

	public RoommatePageResponse<AdminMemberResponse> getMembers(Long adminId, AdminMemberListRequest request) {
		requireAdmin(adminId);
		validateRequest(request);
		return RoommatePageResponse.from(memberRepository.searchMembers(request.q(),
				request.status() == null ? null : UserStatus.valueOf(request.status()), request.dormitory(),
				PageRequest.of(request.page(), request.size())).map(this::memberResponse));
	}

	public AdminMemberDetailResponse getMember(Long adminId, Long memberId) {
		requireAdmin(adminId);
		validateId(memberId);
		Tuple row = memberRepository.findMemberById(memberId).orElseThrow(this::invalidRequest);
		User user = row.get("member", User.class);
		UserProfile profile = row.get("profile", UserProfile.class);
		AdminMemberResponse member = memberResponse(row);
		return new AdminMemberDetailResponse(member.id(), member.nickname(), member.schoolEmail(),
				profile == null ? null : profile.getGender(), member.dormitory(),
				profile == null ? null : profile.getFaculty().getFacultyName(), member.major(),
				profile == null ? null : profile.getAdmissionYear(), profile == null ? null : profile.getGrade(),
				profile == null ? null : profile.getMbti(), member.createdAt(), user.getStatus(),
				new AdminMemberDetailResponse.Activity(postRepository.countActivePostsByUserId(memberId),
						commentRepository.countActiveCommentsByUserId(memberId),
						reportRepository.countReportsReceivedByUserId(memberId)));
	}

	@Transactional
	public void updateMemberStatus(Long adminId, Long memberId, AdminMemberStatusUpdateRequest request) {
		requireAdmin(adminId);
		validateRequest(request);
		validateId(memberId);
		changeStatus(userRepository.findByIdForAccountUpdate(memberId).orElseThrow(this::invalidRequest),
				UserStatus.valueOf(request.status()));
	}

	@Transactional
	public void deletePost(Long adminId, Long postId) {
		requireAdmin(adminId);
		validateId(postId);
		deletePost(communityPostRepository.findDetailForUpdate(postId, CommunityContentStatus.ACTIVE)
				.orElseThrow(this::invalidRequest));
	}

	@Transactional
	public void deleteComment(Long adminId, Long commentId) {
		requireAdmin(adminId);
		validateId(commentId);
		commentWriteService.deleteLocked(commentWriteService.lockForDeletion(commentId));
	}

	public RoommatePageResponse<AdminReportResponse> getReports(Long adminId, AdminReportListRequest request) {
		requireAdmin(adminId);
		validateRequest(request);
		validateCommunityPage(request.page(), request.size());
		Page<Report> page = reportRepository.searchReports(request.q(),
				request.type() == null ? null : ReportType.valueOf(request.type()),
				request.status() == null ? null : ReportStatus.valueOf(request.status()),
				PageRequest.of(request.page(), request.size()));
		var profiles = profiles(page.getContent().stream()
				.flatMap(r -> Stream.of(r.getReporter(), r.getTargetUser())).toList());
		return RoommatePageResponse.from(page.map(r -> new AdminReportResponse(r.getReportId(), r.getReportType(),
				targetContent(r, profiles), author(r.getReporter(), profiles), author(r.getTargetUser(), profiles),
				r.getReason(), r.getCreatedAt(), r.getStatus())));
	}

	public AdminReportDetailResponse getReport(Long adminId, Long reportId) {
		requireAdmin(adminId);
		Report report = findReport(reportId);
		User target = report.getTargetUser();
		User reporter = report.getReporter();
		var profiles = profiles(List.of(target, reporter));
		Long targetId = switch (report.getReportType()) {
			case USER -> target.getUserId();
			case POST -> report.getPost().getPostId();
			case COMMENT -> report.getComment().getCommentId();
		};
		return new AdminReportDetailResponse(report.getReportId(), report.getReportType(), targetId,
				new AdminReportDetailResponse.TargetUser(target.getUserId(), author(target, profiles).nickname(),
						target.getSchoolEmail(), target.getStatus()),
				targetContent(report, profiles),
				new AdminReportDetailResponse.Reporter(reporter.getUserId(), author(reporter, profiles).nickname(),
						reporter.getSchoolEmail()),
				report.getReason(), report.getDescription(), report.getCreatedAt(), report.getStatus());
	}

	/** 대상 조치와 신고 처리 이력을 같은 트랜잭션에서 저장한다. */
	@Transactional
	public void processReport(Long adminId, Long reportId, AdminReportProcessRequest request) {
		User admin = requireAdmin(adminId);
		validateRequest(request);
		Report report = findReport(reportId);
		if (report.getStatus() != ReportStatus.PENDING) {
			throw invalidRequest();
		}
		switch (request.action()) {
			case "COMPLETE", "REJECT" -> { }
			case "SUSPEND_USER" -> changeStatus(userRepository.findByIdForAccountUpdate(
                    report.getTargetUser().getUserId()).orElseThrow(this::invalidRequest), UserStatus.SUSPENDED);
			case "DELETE_TARGET" -> {
				switch (report.getReportType()) {
					case POST -> {
						CommunityPost post = report.getPost();
						// 신고 조회가 이미 로딩한 Entity도 잠금 후 최신 상태(조회수 포함)로 교체한다.
						entityManager.refresh(post, LockModeType.PESSIMISTIC_WRITE);
						deletePost(post);
					}
					case COMMENT -> {
						CommunityComment comment = report.getComment();
						commentWriteService.deleteReported(comment);
					}
					case USER -> throw invalidRequest();
				}
			}
			default -> throw invalidRequest();
		}
		ReportStatus status = request.action().equals("REJECT") ? ReportStatus.REJECTED : ReportStatus.COMPLETED;
		String resolution = switch (request.action()) {
			case "SUSPEND_USER", "DELETE_TARGET" -> request.action();
			default -> "NONE";
		};
		report.process(status, resolution, admin, LocalDateTime.now());
	}

	/** 실행 이력이 없는 카테고리는 상태와 실행 시각을 null, 최근 수집 수를 0으로 반환한다. */
	public List<AdminScheduleCrawlStatusResponse> getScheduleCrawlStatuses(Long adminId) {
		requireAdmin(adminId);
		return Arrays.stream(ScheduleCrawlCategory.values()).map(category -> {
			var latest = crawlRunRepository.findFirstByCategoryOrderByStartedAtDescCrawlRunIdDesc(category);
			var completed = crawlRunRepository.findFirstByCategoryAndStatusOrderByCompletedAtDescCrawlRunIdDesc(
					category, ScheduleCrawlStatus.COMPLETED);
			var failed = crawlRunRepository.findFirstByCategoryAndStatusOrderByCompletedAtDescCrawlRunIdDesc(
					category, ScheduleCrawlStatus.FAILED);
			return new AdminScheduleCrawlStatusResponse(category, latest.map(ScheduleCrawlRun::getStatus).orElse(null),
					latest.map(ScheduleCrawlRun::getStartedAt).orElse(null),
					latest.map(ScheduleCrawlRun::getCollectedCount).orElse(0), scheduleRepository.countSchedules(category),
					completed.map(ScheduleCrawlRun::getCompletedAt).orElse(null),
					failed.map(AdminScheduleCrawlStatusResponse.LastFailure::from).orElse(null));
		}).toList();
	}

	public RoommatePageResponse<AdminCrawledScheduleResponse> getCrawledSchedules(
			Long adminId, AdminCrawledScheduleListRequest request) {
		requireAdmin(adminId);
		validateRequest(request);
		return RoommatePageResponse.from(scheduleRepository.searchSchedules(request.q(),
				request.category() == null ? null : ScheduleCrawlCategory.valueOf(request.category()),
				PageRequest.of(request.page(), request.size())).map(AdminCrawledScheduleResponse::from));
	}

	public AdminCrawledScheduleResponse getCrawledSchedule(Long adminId, Long scheduleId) {
		requireAdmin(adminId);
		validateId(scheduleId);
		return AdminCrawledScheduleResponse.from(
				scheduleRepository.findScheduleById(scheduleId).orElseThrow(this::invalidRequest));
	}

	private User requireAdmin(Long adminId) {
		if (adminId == null || adminId <= 0) {
			throw new CustomException(ErrorCode.UNAUTHORIZED);
		}
		User admin = userRepository.findById(adminId)
				.orElseThrow(() -> new CustomException(ErrorCode.UNAUTHORIZED));
		if (admin.getRole() != UserRole.ADMIN || admin.getStatus() != UserStatus.ACTIVE) {
			throw new CustomException(ErrorCode.FORBIDDEN);
		}
		return admin;
	}

	private AdminMemberResponse memberResponse(Tuple row) {
		User user = row.get("member", User.class);
		UserProfile profile = row.get("profile", UserProfile.class);
		return new AdminMemberResponse(user.getUserId(),
				profile == null ? null : profile.getNickname(), user.getSchoolEmail(),
				profile == null ? null : profile.getDormitory().getDormitoryName(),
				profile == null || profile.getMajor() == null ? null : profile.getMajor().getMajorName(),
				user.getCreatedAt().toLocalDate(), user.getStatus());
	}

	private Map<Long, UserProfile> profiles(List<User> users) {
		return memberRepository.findProfilesByUserIds(users.stream().map(User::getUserId).distinct().toList())
				.stream().collect(Collectors.toMap(p -> p.getUser().getUserId(), Function.identity()));
	}

	private CommunityAuthorResponse author(User user, Map<Long, UserProfile> profiles) {
		UserProfile profile = profiles.get(user.getUserId());
		return new CommunityAuthorResponse(user.getUserId(), profile == null ? null : profile.getNickname());
	}

	private String targetContent(Report report, Map<Long, UserProfile> profiles) {
		return switch (report.getReportType()) {
			case USER -> author(report.getTargetUser(), profiles).nickname();
			case POST -> report.getPost().getContent();
			case COMMENT -> report.getComment().getContent();
		};
	}

	private List<AdminDashboardResponse.ScheduleSummary> recentSchedules(ScheduleCrawlCategory category) {
		return scheduleRepository.findRecentSchedules(category).stream()
				.map(e -> new AdminDashboardResponse.ScheduleSummary(
						e.getEventId(), e.getTitle(), e.getStartDate(), e.getCollectedAt())).toList();
	}

	private Report findReport(Long reportId) {
		validateId(reportId);
		return reportRepository.findReportById(reportId).orElseThrow(this::invalidRequest);
	}

	private void changeStatus(User user, UserStatus status) {
		if (user.getStatus() == UserStatus.WITHDRAWN && status != UserStatus.WITHDRAWN) {
			throw invalidRequest();
		}
		user.changeStatus(status);
	}

	private void deletePost(CommunityPost post) {
		communityCommentRepository.findAllForUpdateByPostId(
				post.getPostId(), CommunityContentStatus.ACTIVE).forEach(CommunityComment::delete);
		post.delete();
	}

	private void validateRequest(Object request) {
		if (request == null || !validator.validate(request).isEmpty()) {
			throw new CustomException(ErrorCode.VALIDATION_ERROR);
		}
	}

	private void validateCommunityPage(int page, int size) {
		if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
			throw new CustomException(ErrorCode.VALIDATION_ERROR);
		}
	}

	private void validateId(Long id) {
		if (id == null || id <= 0) {
			throw invalidRequest();
		}
	}

	private CustomException invalidRequest() {
		return new CustomException(ErrorCode.INVALID_REQUEST);
	}
}
