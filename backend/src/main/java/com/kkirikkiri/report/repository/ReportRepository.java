package com.kkirikkiri.report.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kkirikkiri.report.entity.Report;
import com.kkirikkiri.report.entity.ReportType;

public interface ReportRepository extends JpaRepository<Report, Long> {

	/**
	 * USER 유형으로 조회하여 게시글·댓글 작성자에 대한 신고 이력과 구분한다.
	 */
	boolean existsByReporter_UserIdAndReportTypeAndTargetUser_UserId(
			Long reporterId, ReportType reportType, Long targetUserId);

	/**
	 * POST 유형으로 동일 신고자의 게시글 신고 이력을 확인한다.
	 */
	boolean existsByReporter_UserIdAndReportTypeAndPost_PostId(
			Long reporterId, ReportType reportType, Long postId);

	/**
	 * COMMENT 유형으로 동일 신고자의 댓글 신고 이력을 확인한다.
	 */
	boolean existsByReporter_UserIdAndReportTypeAndComment_CommentId(
			Long reporterId, ReportType reportType, Long commentId);
}
