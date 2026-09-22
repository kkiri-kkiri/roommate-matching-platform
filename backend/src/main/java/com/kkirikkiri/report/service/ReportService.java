package com.kkirikkiri.report.service;

import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kkirikkiri.community.comment.entity.CommunityComment;
import com.kkirikkiri.community.comment.repository.CommunityCommentRepository;
import com.kkirikkiri.community.post.entity.CommunityPost;
import com.kkirikkiri.community.post.repository.CommunityPostRepository;
import com.kkirikkiri.global.exception.CustomException;
import com.kkirikkiri.global.exception.ErrorCode;
import com.kkirikkiri.report.dto.ReportCreateRequest;
import com.kkirikkiri.report.entity.Report;
import com.kkirikkiri.report.entity.ReportType;
import com.kkirikkiri.report.repository.ReportRepository;
import com.kkirikkiri.user.entity.User;
import com.kkirikkiri.user.repository.UserRepository;

/** userId에는 요청 본문이 아닌 인증된 사용자의 ID를 전달한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

	private final ReportRepository reportRepository;
	private final UserRepository userRepository;
	private final CommunityPostRepository communityPostRepository;
	private final CommunityCommentRepository communityCommentRepository;
	private final Validator validator;

	/** 동일 신고자의 대상별 중복 신고를 거절하고 생성된 신고 ID를 반환한다. */
	@Transactional
	public Long createReport(Long userId, ReportCreateRequest request) {
		if (userId == null || userId <= 0) {
			throw new CustomException(ErrorCode.UNAUTHORIZED);
		}
		if (request == null || !validator.validate(request).isEmpty()) {
			throw new CustomException(ErrorCode.VALIDATION_ERROR);
		}
		User reporter = userRepository.findById(userId)
				.orElseThrow(() -> new CustomException(ErrorCode.UNAUTHORIZED));
		Report report = switch (ReportType.valueOf(request.targetType())) {
			case USER -> createUserReport(userId, reporter, request);
			case POST -> createPostReport(userId, reporter, request);
			case COMMENT -> createCommentReport(userId, reporter, request);
		};
		return reportRepository.save(report).getReportId();
	}

	private Report createUserReport(Long userId, User reporter, ReportCreateRequest request) {
		User target = userRepository.findById(request.targetId())
				.orElseThrow(() -> new CustomException(ErrorCode.INVALID_REQUEST));
		if (reportRepository.existsByReporter_UserIdAndReportTypeAndTargetUser_UserId(
				userId, ReportType.USER, request.targetId())) {
			throw new CustomException(ErrorCode.INVALID_REQUEST);
		}
		return Report.forUser(reporter, target, request.reason(), request.description());
	}

	private Report createPostReport(Long userId, User reporter, ReportCreateRequest request) {
		CommunityPost target = communityPostRepository.findById(request.targetId())
				.orElseThrow(() -> new CustomException(ErrorCode.INVALID_REQUEST));
		if (reportRepository.existsByReporter_UserIdAndReportTypeAndPost_PostId(
				userId, ReportType.POST, request.targetId())) {
			throw new CustomException(ErrorCode.INVALID_REQUEST);
		}
		return Report.forPost(reporter, target, request.reason(), request.description());
	}

	private Report createCommentReport(Long userId, User reporter, ReportCreateRequest request) {
		CommunityComment target = communityCommentRepository.findById(request.targetId())
				.orElseThrow(() -> new CustomException(ErrorCode.INVALID_REQUEST));
		if (reportRepository.existsByReporter_UserIdAndReportTypeAndComment_CommentId(
				userId, ReportType.COMMENT, request.targetId())) {
			throw new CustomException(ErrorCode.INVALID_REQUEST);
		}
		return Report.forComment(reporter, target, request.reason(), request.description());
	}
}
