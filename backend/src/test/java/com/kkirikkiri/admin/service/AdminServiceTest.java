package com.kkirikkiri.admin.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import jakarta.persistence.Tuple;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import com.kkirikkiri.admin.dto.*;
import com.kkirikkiri.calendar.entity.ScheduleCrawlCategory;
import com.kkirikkiri.calendar.entity.ScheduleCrawlRun;
import com.kkirikkiri.calendar.entity.ScheduleCrawlStatus;
import com.kkirikkiri.calendar.repository.AdminCrawledScheduleQueryRepository;
import com.kkirikkiri.calendar.repository.ScheduleCrawlRunRepository;
import com.kkirikkiri.community.comment.entity.CommunityComment;
import com.kkirikkiri.community.comment.repository.CommunityCommentRepository;
import com.kkirikkiri.community.common.entity.CommunityContentStatus;
import com.kkirikkiri.community.post.entity.CommunityPost;
import com.kkirikkiri.community.post.repository.CommunityPostReadRepository;
import com.kkirikkiri.community.repository.AdminCommentQueryRepository;
import com.kkirikkiri.community.repository.AdminPostQueryRepository;
import com.kkirikkiri.global.exception.CustomException;
import com.kkirikkiri.global.exception.ErrorCode;
import com.kkirikkiri.report.entity.Report;
import com.kkirikkiri.report.entity.ReportStatus;
import com.kkirikkiri.report.entity.ReportType;
import com.kkirikkiri.report.repository.AdminReportQueryRepository;
import com.kkirikkiri.report.repository.ReportRepository;
import com.kkirikkiri.user.entity.User;
import com.kkirikkiri.user.entity.UserProfile;
import com.kkirikkiri.user.entity.UserRole;
import com.kkirikkiri.user.entity.UserStatus;
import com.kkirikkiri.user.repository.AdminMemberQueryRepository;
import com.kkirikkiri.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {
    @Mock UserRepository userRepository;
    @Mock AdminMemberQueryRepository memberRepository;
    @Mock AdminPostQueryRepository postRepository;
    @Mock AdminCommentQueryRepository commentRepository;
    @Mock CommunityCommentRepository communityCommentRepository;
    @Mock AdminReportQueryRepository reportRepository;
    @Mock AdminCrawledScheduleQueryRepository scheduleRepository;
    @Mock ScheduleCrawlRunRepository crawlRunRepository;
    @Mock ReportRepository reportExistenceRepository;
    @Mock CommunityPostReadRepository postReadRepository;
    @Mock Validator validator;
    @Mock ConstraintViolation<AdminReportProcessRequest> violation;
    @InjectMocks AdminService service;

    private final User admin = user(1L, UserRole.ADMIN);
    private final User target = user(2L, UserRole.USER);
    private final User reporter = user(3L, UserRole.USER);
    private final CommunityPost post = post(10L);
    private final CommunityComment comment = comment(20L, null);

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1})
    void rejectsInvalidAdminIdBeforeRepositoryAccess(Long adminId) {
        // when / then
        assertError(ErrorCode.UNAUTHORIZED, () -> service.getDashboard(adminId));
        verifyNoInteractions(userRepository, memberRepository, postRepository, reportRepository);
    }

    @Test
    void rejectsAdminWhoNoLongerExists() {
        // when / then
        assertError(ErrorCode.UNAUTHORIZED, () -> service.getDashboard(1L));
        verifyNoInteractions(memberRepository, postRepository, reportRepository);
    }

    @ParameterizedTest
    @CsvSource({"USER,ACTIVE", "ADMIN,SUSPENDED", "ADMIN,WITHDRAWN"})
    void rejectsUnauthorizedRoleOrInactiveAdminBeforeChangingData(UserRole role, UserStatus status) {
        // given
        User actor = user(1L, role);
        actor.changeStatus(status);
        when(userRepository.findById(1L)).thenReturn(Optional.of(actor));
        // when / then
        assertError(ErrorCode.FORBIDDEN,
                () -> service.processReport(1L, 30L, new AdminReportProcessRequest("SUSPEND_USER")));
        verifyNoInteractions(validator, reportRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"getDashboard", "getMembers", "getMember", "updateMemberStatus",
            "getPosts", "getPost", "deletePost", "getComments", "getComment", "deleteComment",
            "getReports", "getReport", "processReport", "getScheduleCrawlStatuses",
            "getCrawledSchedules", "getCrawledSchedule"})
    void protectsEveryAdminOperationFromOrdinaryUsers(String operation) {
        // given
        when(userRepository.findById(1L)).thenReturn(Optional.of(target));
        // when / then
        assertError(ErrorCode.FORBIDDEN, () -> invoke(operation, 2L));
        verifyNoInteractions(validator, memberRepository, postRepository, commentRepository,
                communityCommentRepository, reportRepository, scheduleRepository, crawlRunRepository,
                reportExistenceRepository, postReadRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"getMember", "getPost", "deletePost", "getComment", "deleteComment",
            "getReport", "processReport", "updateMemberStatus", "getCrawledSchedule"})
    void rejectsMissingData(String operation) {
        // given
        stubAdmin();
        // when / then
        assertError(ErrorCode.INVALID_REQUEST, () -> invoke(operation, 99L));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1})
    void rejectsInvalidTargetIdBeforeDeleting(Long id) {
        // given
        stubAdmin();
        // when / then
        assertError(ErrorCode.INVALID_REQUEST, () -> service.deletePost(1L, id));
        verifyNoInteractions(postRepository, communityCommentRepository);
    }

    @Test
    void rejectsNullProcessingRequestBeforeLoadingReport() {
        // given
        stubAdmin();
        // when / then
        assertError(ErrorCode.VALIDATION_ERROR, () -> service.processReport(1L, 30L, null));
        verifyNoInteractions(validator, reportRepository);
    }

    @Test
    void rejectsConstraintViolationBeforeLoadingReport() {
        // given
        stubAdmin();
        var request = new AdminReportProcessRequest("UNKNOWN");
        when(validator.validate(request)).thenReturn(Set.of(violation));
        // when / then
        assertError(ErrorCode.VALIDATION_ERROR, () -> service.processReport(1L, 30L, request));
        verifyNoInteractions(reportRepository);
    }

    @ParameterizedTest
    @CsvSource({"ACTIVE,SUSPENDED", "SUSPENDED,ACTIVE", "ACTIVE,WITHDRAWN",
            "SUSPENDED,WITHDRAWN", "WITHDRAWN,WITHDRAWN", "SUSPENDED,SUSPENDED"})
    void appliesAllowedMemberStatusTransitions(UserStatus initial, UserStatus requested) {
        // given
        stubAdmin();
        target.changeStatus(initial);
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        // when
        service.updateMemberStatus(1L, 2L, new AdminMemberStatusUpdateRequest(requested.name()));
        // then
        assertEquals(requested, target.getStatus());
    }

    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = {"ACTIVE", "SUSPENDED"})
    void rejectsRestoringWithdrawnMemberWithoutChangingStatus(UserStatus requested) {
        // given
        stubAdmin();
        target.changeStatus(UserStatus.WITHDRAWN);
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        // when / then
        assertError(ErrorCode.INVALID_REQUEST,
                () -> service.updateMemberStatus(1L, 2L, new AdminMemberStatusUpdateRequest(requested.name())));
        assertEquals(UserStatus.WITHDRAWN, target.getStatus());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void deletesPostWithOrWithoutActiveComments(boolean hasComments) {
        // given
        stubAdmin();
        CommunityComment reply = comment(21L, comment);
        when(postRepository.findPostById(10L)).thenReturn(Optional.of(post));
        when(communityCommentRepository.findAllByPost_PostIdAndStatusOrderByCreatedAtAscCommentIdAsc(
                10L, CommunityContentStatus.ACTIVE)).thenReturn(hasComments ? List.of(comment, reply) : List.of());
        // when
        service.deletePost(1L, 10L);
        // then
        assertEquals(CommunityContentStatus.DELETED, post.getStatus());
        if (hasComments) {
            assertEquals(CommunityContentStatus.DELETED, comment.getStatus());
            assertEquals(CommunityContentStatus.DELETED, reply.getStatus());
        }
    }

    @Test
    void deletesOnlySelectedCommentWithoutDeletingPost() {
        // given
        stubAdmin();
        when(commentRepository.findCommentById(20L)).thenReturn(Optional.of(comment));
        // when
        service.deleteComment(1L, 20L);
        // then
        assertEquals(CommunityContentStatus.DELETED, comment.getStatus());
        assertEquals(CommunityContentStatus.ACTIVE, post.getStatus());
    }

    @ParameterizedTest
    @CsvSource({"COMPLETE,COMPLETED", "REJECT,REJECTED"})
    void processesDecisionWithoutSanctioningTarget(String action, ReportStatus expected) {
        // given
        Report report = stubReport(ReportType.POST);
        // when
        service.processReport(1L, 30L, new AdminReportProcessRequest(action));
        // then
        assertProcessed(report, expected, "NONE");
        assertEquals(UserStatus.ACTIVE, target.getStatus());
        assertEquals(CommunityContentStatus.ACTIVE, post.getStatus());
        verifyNoInteractions(communityCommentRepository);
    }

    @ParameterizedTest
    @EnumSource(ReportType.class)
    void suspendsActualTargetUserForEveryReportType(ReportType type) {
        // given
        Report report = stubReport(type);
        // when
        service.processReport(1L, 30L, new AdminReportProcessRequest("SUSPEND_USER"));
        // then
        assertEquals(UserStatus.SUSPENDED, target.getStatus());
        assertEquals(UserStatus.ACTIVE, reporter.getStatus());
        assertEquals(CommunityContentStatus.ACTIVE, post.getStatus());
        assertEquals(CommunityContentStatus.ACTIVE, comment.getStatus());
        assertProcessed(report, ReportStatus.COMPLETED, "SUSPEND_USER");
    }

    @ParameterizedTest
    @EnumSource(value = ReportType.class, names = {"POST", "COMMENT"})
    void deletesReportedContentAndCompletesReport(ReportType type) {
        // given
        Report report = stubReport(type);
        if (type == ReportType.POST) {
            when(communityCommentRepository.findAllByPost_PostIdAndStatusOrderByCreatedAtAscCommentIdAsc(
                    10L, CommunityContentStatus.ACTIVE)).thenReturn(List.of(comment));
        }
        // when
        service.processReport(1L, 30L, new AdminReportProcessRequest("DELETE_TARGET"));
        // then
        assertEquals(CommunityContentStatus.DELETED, comment.getStatus());
        assertEquals(type == ReportType.POST ? CommunityContentStatus.DELETED : CommunityContentStatus.ACTIVE,
                post.getStatus());
        assertEquals(UserStatus.ACTIVE, target.getStatus());
        assertProcessed(report, ReportStatus.COMPLETED, "DELETE_TARGET");
    }

    @Test
    void rejectsDeletingUserReportWithoutProcessingIt() {
        // given
        Report report = stubReport(ReportType.USER);
        // when / then
        assertError(ErrorCode.INVALID_REQUEST,
                () -> service.processReport(1L, 30L, new AdminReportProcessRequest("DELETE_TARGET")));
        assertPending(report);
        assertEquals(UserStatus.ACTIVE, target.getStatus());
    }

    @Test
    void rejectsSuspendingWithdrawnTargetWithoutProcessingReport() {
        // given
        Report report = stubReport(ReportType.USER);
        target.changeStatus(UserStatus.WITHDRAWN);
        // when / then
        assertError(ErrorCode.INVALID_REQUEST,
                () -> service.processReport(1L, 30L, new AdminReportProcessRequest("SUSPEND_USER")));
        assertPending(report);
        assertEquals(UserStatus.WITHDRAWN, target.getStatus());
    }

    @ParameterizedTest
    @EnumSource(value = ReportStatus.class, names = {"COMPLETED", "REJECTED"})
    void rejectsAlreadyProcessedReportBeforeApplyingSanction(ReportStatus status) {
        // given
        Report report = stubReport(ReportType.POST);
        LocalDateTime processedAt = LocalDateTime.of(2026, 1, 1, 12, 0);
        report.process(status, "NONE", admin, processedAt);
        // when / then
        assertError(ErrorCode.INVALID_REQUEST,
                () -> service.processReport(1L, 30L, new AdminReportProcessRequest("DELETE_TARGET")));
        assertEquals(CommunityContentStatus.ACTIVE, post.getStatus());
        assertEquals(status, report.getStatus());
        assertEquals(processedAt, report.getProcessedAt());
        assertEquals("NONE", report.getResolutionAction());
        verifyNoInteractions(communityCommentRepository);
    }

    @Test
    void assemblesPostDetailsWithGlobalReportsAndActiveCommentCount() {
        // given
        stubAdmin();
        when(postRepository.findPostById(10L)).thenReturn(Optional.of(post));
        when(postReadRepository.countLikes(10L)).thenReturn(4L);
        when(communityCommentRepository.countByPost_PostIdAndStatus(10L, CommunityContentStatus.ACTIVE))
                .thenReturn(3L);
        when(reportExistenceRepository.existsByPost_PostId(10L)).thenReturn(true);
        // when
        var result = service.getPost(1L, 10L);
        // then
        assertEquals(4L, result.likes());
        assertEquals(3L, result.commentCount());
        assertTrue(result.reported());
        assertEquals(post.getContent(), result.content());
        assertEquals(2L, result.author().id());
        assertNull(result.author().nickname());
    }

    @Test
    void loadsSharedAuthorProfileOnceAndPreservesPostPagination() {
        // given
        stubAdmin();
        CommunityPost second = post(11L);
        var request = new AdminPostListRequest(1, 2, "query", "자유게시판", true, "LIKES");
        when(postRepository.searchPosts("query", "자유게시판", true, "LIKES", PageRequest.of(1, 2)))
                .thenReturn(new PageImpl<>(List.of(post, second), PageRequest.of(1, 2), 5));
        UserProfile targetProfile = profile(target, "target");
        when(memberRepository.findProfilesByUserIds(List.of(2L))).thenReturn(List.of(targetProfile));
        // when
        var result = service.getPosts(1L, request);
        // then
        assertEquals(List.of(10L, 11L), result.content().stream().map(AdminPostResponse::id).toList());
        assertTrue(result.content().stream().allMatch(p -> p.author().nickname().equals("target")));
        assertEquals(1, result.page());
        assertEquals(2, result.size());
        assertEquals(5, result.totalElements());
        assertEquals(3, result.totalPages());
        assertFalse(result.first());
        assertFalse(result.last());
        verify(memberRepository).findProfilesByUserIds(List.of(2L));
    }

    @Test
    void returnsMemberWithoutProfileAndIncludesActivityCounts() {
        // given
        stubAdmin();
        Tuple row = mock(Tuple.class);
        when(row.get("member", User.class)).thenReturn(target);
        when(memberRepository.findMemberById(2L)).thenReturn(Optional.of(row));
        when(postRepository.countActivePostsByUserId(2L)).thenReturn(4L);
        when(commentRepository.countActiveCommentsByUserId(2L)).thenReturn(7L);
        when(reportRepository.countReportsReceivedByUserId(2L)).thenReturn(2L);
        // when
        var result = service.getMember(1L, 2L);
        // then
        assertEquals(target.getCreatedAt().toLocalDate(), result.createdAt());
        assertNull(result.nickname());
        assertNull(result.dormitory());
        assertNull(result.faculty());
        assertNull(result.major());
        assertNull(result.gender());
        assertEquals(new AdminMemberDetailResponse.Activity(4, 7, 2), result.activity());
    }

    @ParameterizedTest
    @EnumSource(ReportType.class)
    void resolvesReportTargetIdAndContentByType(ReportType type) {
        // given
        stubReport(type);
        UserProfile targetProfile = profile(target, "target");
        UserProfile reporterProfile = profile(reporter, "reporter");
        when(memberRepository.findProfilesByUserIds(List.of(2L, 3L)))
                .thenReturn(List.of(targetProfile, reporterProfile));
        // when
        var result = service.getReport(1L, 30L);
        // then
        assertEquals(switch (type) { case USER -> 2L; case POST -> 10L; case COMMENT -> 20L; }, result.targetId());
        assertEquals(switch (type) {
            case USER -> "target"; case POST -> post.getContent(); case COMMENT -> comment.getContent();
        }, result.targetContent());
        assertEquals(2L, result.targetUser().id());
        assertEquals("target", result.targetUser().nickname());
        assertEquals(3L, result.reporter().id());
        assertEquals("reporter", result.reporter().nickname());
    }

    @Test
    void returnsUserReportWithoutProfiles() {
        // given
        stubReport(ReportType.USER);
        // when
        var result = service.getReport(1L, 30L);
        // then
        assertEquals(2L, result.targetId());
        assertNull(result.targetContent());
        assertNull(result.targetUser().nickname());
        assertNull(result.reporter().nickname());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"SUSPENDED"})
    void passesOptionalMemberStatusAndPagingToSearch(String status) {
        // given
        stubAdmin();
        var pageable = PageRequest.of(0, 1);
        when(memberRepository.searchMembers("user", status == null ? null : UserStatus.SUSPENDED, "나래관", pageable))
                .thenReturn(Page.empty(pageable));
        // when
        var result = service.getMembers(1L, new AdminMemberListRequest(0, 1, "user", status, "나래관"));
        // then
        assertTrue(result.content().isEmpty());
        assertEquals(0, result.totalElements());
        verify(memberRepository).searchMembers("user", status == null ? null : UserStatus.SUSPENDED, "나래관", pageable);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"POST"})
    void passesOptionalReportFiltersToSearch(String type) {
        // given
        stubAdmin();
        var pageable = PageRequest.of(0, 6);
        String status = type == null ? null : "PENDING";
        when(reportRepository.searchReports("query", type == null ? null : ReportType.POST,
                status == null ? null : ReportStatus.PENDING, pageable)).thenReturn(Page.empty(pageable));
        // when
        var result = service.getReports(1L, new AdminReportListRequest(null, null, "query", type, status));
        // then
        assertTrue(result.content().isEmpty());
        verify(reportRepository).searchReports("query", type == null ? null : ReportType.POST,
                status == null ? null : ReportStatus.PENDING, pageable);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"SCHOOL", "DORMITORY"})
    void passesOptionalCrawlCategoryAndDefaultPagingToSearch(String category) {
        // given
        stubAdmin();
        var pageable = PageRequest.of(0, 6);
        var expected = category == null ? null : ScheduleCrawlCategory.valueOf(category);
        when(scheduleRepository.searchSchedules("query", expected, pageable)).thenReturn(Page.empty(pageable));
        // when
        var result = service.getCrawledSchedules(1L, new AdminCrawledScheduleListRequest(null, null, "query", category));
        // then
        assertTrue(result.content().isEmpty());
        verify(scheduleRepository).searchSchedules("query", expected, pageable);
    }

    @Test
    void includesCommentReportFlagAndParentPostWithMissingProfile() {
        // given
        stubAdmin();
        when(commentRepository.searchComments("query", true, PageRequest.of(0, 6)))
                .thenReturn(new PageImpl<>(List.of(comment), PageRequest.of(0, 6), 1));
        when(reportExistenceRepository.existsByComment_CommentId(20L)).thenReturn(true);
        // when
        var result = service.getComments(1L, new AdminCommentListRequest(null, null, "query", true)).content().getFirst();
        // then
        assertTrue(result.reported());
        assertEquals(10L, result.postId());
        assertEquals(post.getTitle(), result.postTitle());
        assertEquals(2L, result.author().id());
        assertNull(result.author().nickname());
    }

    @Test
    void combinesDashboardCountsWithoutMixingCategoriesOrPendingReports() {
        // given
        stubAdmin();
        when(memberRepository.countMembers()).thenReturn(10L);
        when(postRepository.countActivePosts()).thenReturn(20L);
        when(commentRepository.countActiveComments()).thenReturn(30L);
        when(reportRepository.countReports()).thenReturn(8L);
        when(reportRepository.countPendingReports()).thenReturn(3L);
        when(scheduleRepository.countSchedules(ScheduleCrawlCategory.SCHOOL)).thenReturn(6L);
        when(scheduleRepository.countSchedules(ScheduleCrawlCategory.DORMITORY)).thenReturn(4L);
        // when
        var result = service.getDashboard(1L);
        // then
        assertEquals(new AdminDashboardResponse.Stats(10, 20, 30, 8, 3, 6, 4), result.stats());
        assertTrue(result.recentMembers().isEmpty());
        assertTrue(result.recentPosts().isEmpty());
        assertTrue(result.recentReports().isEmpty());
        assertTrue(result.recentSchoolSchedules().isEmpty());
        assertTrue(result.recentDormitorySchedules().isEmpty());
    }

    @Test
    void returnsEveryCrawlCategoryWithDefaultsWhenHistoryIsMissing() {
        // given
        stubAdmin();
        when(scheduleRepository.countSchedules(ScheduleCrawlCategory.SCHOOL)).thenReturn(5L);
        // when
        var result = service.getScheduleCrawlStatuses(1L);
        // then
        assertEquals(List.of(ScheduleCrawlCategory.values()), result.stream().map(AdminScheduleCrawlStatusResponse::category).toList());
        for (var status : result) {
            assertNull(status.status());
            assertNull(status.lastRunAt());
            assertEquals(0, status.recentCollectedCount());
            assertEquals(status.category() == ScheduleCrawlCategory.SCHOOL ? 5 : 0, status.totalCollectedCount());
            assertNull(status.lastCompletedAt());
            assertNull(status.lastFailure());
        }
    }

    @Test
    void combinesLatestRunWithIndependentSuccessFailureAndStoredScheduleCount() {
        // given
        stubAdmin();
        var category = ScheduleCrawlCategory.SCHOOL;
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 6, 0);
        var completed = new ScheduleCrawlRun(category, start);
        completed.complete(7, start.plusMinutes(1));
        var failed = new ScheduleCrawlRun(category, start.plusDays(1));
        failed.fail(2, "timeout", start.plusDays(1).plusMinutes(1));
        var running = new ScheduleCrawlRun(category, start.plusDays(2));
        when(crawlRunRepository.findFirstByCategoryOrderByStartedAtDescCrawlRunIdDesc(category))
                .thenReturn(Optional.of(running));
        when(crawlRunRepository.findFirstByCategoryAndStatusOrderByCompletedAtDescCrawlRunIdDesc(category, ScheduleCrawlStatus.COMPLETED))
                .thenReturn(Optional.of(completed));
        when(crawlRunRepository.findFirstByCategoryAndStatusOrderByCompletedAtDescCrawlRunIdDesc(category, ScheduleCrawlStatus.FAILED))
                .thenReturn(Optional.of(failed));
        when(scheduleRepository.countSchedules(category)).thenReturn(25L);
        // when
        var result = service.getScheduleCrawlStatuses(1L).stream().filter(s -> s.category() == category).findFirst().orElseThrow();
        // then
        assertEquals(ScheduleCrawlStatus.RUNNING, result.status());
        assertEquals(running.getStartedAt(), result.lastRunAt());
        assertEquals(0, result.recentCollectedCount());
        assertEquals(25, result.totalCollectedCount());
        assertEquals(completed.getCompletedAt(), result.lastCompletedAt());
        assertEquals(new AdminScheduleCrawlStatusResponse.LastFailure(failed.getCompletedAt(), "timeout"), result.lastFailure());
    }

    private void stubAdmin() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
    }

    private Report stubReport(ReportType type) {
        stubAdmin();
        Report report = switch (type) {
            case USER -> Report.forUser(reporter, target, "욕설·비방", null);
            case POST -> Report.forPost(reporter, post, "욕설·비방", null);
            case COMMENT -> Report.forComment(reporter, comment, "욕설·비방", null);
        };
        when(reportRepository.findReportById(30L)).thenReturn(Optional.of(report));
        return report;
    }

    private static User user(Long id, UserRole role) {
        User user = new User("encoded", "user" + id + "@example.com", role);
        ReflectionTestUtils.setField(user, "userId", id);
        ReflectionTestUtils.setField(user, "createdAt", LocalDateTime.of(2026, 1, 1, 12, 0));
        return user;
    }

    private CommunityPost post(Long id) {
        var post = new CommunityPost(target, null, "자유게시판", "title", "post content");
        ReflectionTestUtils.setField(post, "postId", id);
        return post;
    }

    private CommunityComment comment(Long id, CommunityComment parent) {
        var comment = new CommunityComment(post, target, parent, "comment content");
        ReflectionTestUtils.setField(comment, "commentId", id);
        return comment;
    }

    private UserProfile profile(User user, String nickname) {
        UserProfile profile = mock(UserProfile.class);
        when(profile.getUser()).thenReturn(user);
        when(profile.getNickname()).thenReturn(nickname);
        return profile;
    }

    private void assertProcessed(Report report, ReportStatus status, String action) {
        assertEquals(status, report.getStatus());
        assertEquals(action, report.getResolutionAction());
        assertSame(admin, report.getProcessedBy());
        assertNotNull(report.getProcessedAt());
    }

    private void assertPending(Report report) {
        assertEquals(ReportStatus.PENDING, report.getStatus());
        assertNull(report.getResolutionAction());
        assertNull(report.getProcessedBy());
        assertNull(report.getProcessedAt());
    }

    private void assertError(ErrorCode code, Executable action) {
        assertEquals(code, assertThrows(CustomException.class, action).getErrorCode());
    }

    // Explicit calls keep authorization and missing-data cases tied to the public service API.
    private void invoke(String operation, Long id) {
        switch (operation) {
            case "getDashboard" -> service.getDashboard(1L);
            case "getMembers" -> service.getMembers(1L, new AdminMemberListRequest(null, null, null, null, null));
            case "getMember" -> service.getMember(1L, id);
            case "updateMemberStatus" -> service.updateMemberStatus(1L, id, new AdminMemberStatusUpdateRequest("SUSPENDED"));
            case "getPosts" -> service.getPosts(1L, new AdminPostListRequest(null, null, null, null, null, null));
            case "getPost" -> service.getPost(1L, id);
            case "deletePost" -> service.deletePost(1L, id);
            case "getComments" -> service.getComments(1L, new AdminCommentListRequest(null, null, null, null));
            case "getComment" -> service.getComment(1L, id);
            case "deleteComment" -> service.deleteComment(1L, id);
            case "getReports" -> service.getReports(1L, new AdminReportListRequest(null, null, null, null, null));
            case "getReport" -> service.getReport(1L, id);
            case "processReport" -> service.processReport(1L, id, new AdminReportProcessRequest("COMPLETE"));
            case "getScheduleCrawlStatuses" -> service.getScheduleCrawlStatuses(1L);
            case "getCrawledSchedules" -> service.getCrawledSchedules(1L, new AdminCrawledScheduleListRequest(null, null, null, null));
            case "getCrawledSchedule" -> service.getCrawledSchedule(1L, id);
            default -> throw new IllegalArgumentException(operation);
        }
    }
}
