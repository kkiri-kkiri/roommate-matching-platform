package com.kkirikkiri.report.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.Set;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {
    @Mock ReportRepository reportRepository;
    @Mock UserRepository userRepository;
    @Mock CommunityPostRepository communityPostRepository;
    @Mock CommunityCommentRepository communityCommentRepository;
    @Mock Validator validator;
    @Mock ConstraintViolation<ReportCreateRequest> violation;
    @InjectMocks ReportService service;

    private final User reporter = new User("encoded", "reporter@example.com");
    private final User target = new User("encoded", "target@example.com");
    private final CommunityPost post = new CommunityPost(target, null, "자유게시판", "제목", "내용");
    private final CommunityComment comment = new CommunityComment(post, target, null, "댓글");

    @ParameterizedTest
    @EnumSource(ReportType.class)
    void savesReportForAuthenticatedUserAndCorrectTargetAndReturnsId(ReportType type) {
        // given
        stubReporterAndTarget(type);
        when(reportRepository.save(any())).thenAnswer(invocation -> {
            Report saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "reportId", 42L);
            return saved;
        });
        // when
        Long id = service.createReport(7L, request(type));
        // then
        assertEquals(42L, id);
        var captor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).save(captor.capture());
        Report saved = captor.getValue();
        assertSame(reporter, saved.getReporter());
        assertSame(target, saved.getTargetUser());
        assertSame(type == ReportType.POST ? post : null, saved.getPost());
        assertSame(type == ReportType.COMMENT ? comment : null, saved.getComment());
        assertEquals(type, saved.getReportType());
        assertEquals(ReportStatus.PENDING, saved.getStatus());
        assertEquals("기타", saved.getReason());
        assertEquals("신고 상세 내용", saved.getDescription());
        switch (type) {
            case USER -> verify(reportRepository).existsByReporter_UserIdAndReportTypeAndTargetUser_UserId(7L, type, 10L);
            case POST -> verify(reportRepository).existsByReporter_UserIdAndReportTypeAndPost_PostId(7L, type, 10L);
            case COMMENT -> verify(reportRepository).existsByReporter_UserIdAndReportTypeAndComment_CommentId(7L, type, 10L);
        }
    }

    @ParameterizedTest
    @EnumSource(ReportType.class)
    void rejectsDuplicateReportWithoutSaving(ReportType type) {
        // given
        stubReporterAndTarget(type);
        switch (type) {
            case USER -> when(reportRepository.existsByReporter_UserIdAndReportTypeAndTargetUser_UserId(7L, type, 10L)).thenReturn(true);
            case POST -> when(reportRepository.existsByReporter_UserIdAndReportTypeAndPost_PostId(7L, type, 10L)).thenReturn(true);
            case COMMENT -> when(reportRepository.existsByReporter_UserIdAndReportTypeAndComment_CommentId(7L, type, 10L)).thenReturn(true);
        }
        // when / then
        assertError(ErrorCode.INVALID_REQUEST, () -> service.createReport(7L, request(type)));
        verify(reportRepository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(ReportType.class)
    void rejectsMissingTargetWithoutSaving(ReportType type) {
        // given
        when(userRepository.findById(7L)).thenReturn(Optional.of(reporter));
        // when / then
        assertError(ErrorCode.INVALID_REQUEST, () -> service.createReport(7L, request(type)));
        verifyNoInteractions(reportRepository);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1})
    void rejectsUnauthenticatedUserBeforeValidationOrRepositoryAccess(Long userId) {
        // when / then
        assertError(ErrorCode.UNAUTHORIZED, () -> service.createReport(userId, request(ReportType.USER)));
        verifyNoInteractions(validator, userRepository, reportRepository, communityPostRepository, communityCommentRepository);
    }

    @Test
    void rejectsReporterWhoNoLongerExists() {
        // when / then
        assertError(ErrorCode.UNAUTHORIZED, () -> service.createReport(7L, request(ReportType.USER)));
        verifyNoInteractions(reportRepository, communityPostRepository, communityCommentRepository);
    }

    @Test
    void rejectsNullRequestBeforeRepositoryAccess() {
        // when / then
        assertError(ErrorCode.VALIDATION_ERROR, () -> service.createReport(7L, null));
        verifyNoInteractions(validator, userRepository, reportRepository, communityPostRepository, communityCommentRepository);
    }

    @Test
    void rejectsConstraintViolationsBeforeRepositoryAccess() {
        // given
        ReportCreateRequest request = request(ReportType.USER);
        when(validator.validate(request)).thenReturn(Set.of(violation));
        // when / then
        assertError(ErrorCode.VALIDATION_ERROR, () -> service.createReport(7L, request));
        verifyNoInteractions(userRepository, reportRepository, communityPostRepository, communityCommentRepository);
    }

    private void stubReporterAndTarget(ReportType type) {
        when(userRepository.findById(7L)).thenReturn(Optional.of(reporter));
        switch (type) {
            case USER -> when(userRepository.findById(10L)).thenReturn(Optional.of(target));
            case POST -> when(communityPostRepository.findById(10L)).thenReturn(Optional.of(post));
            case COMMENT -> when(communityCommentRepository.findById(10L)).thenReturn(Optional.of(comment));
        }
    }

    private ReportCreateRequest request(ReportType type) {
        return new ReportCreateRequest(type.name(), 10L, "기타", "신고 상세 내용");
    }

    private void assertError(ErrorCode code, org.junit.jupiter.api.function.Executable action) {
        assertEquals(code, assertThrows(CustomException.class, action).getErrorCode());
    }
}
