package com.kkirikkiri.report.repository;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kkirikkiri.report.entity.Report;
import com.kkirikkiri.report.entity.ReportType;

public interface ReportRepository extends JpaRepository<Report, Long> {

	@Query("select distinct r.post.postId from Report r where r.post.postId in :ids")
	List<Long> findReportedPostIds(@Param("ids") Collection<Long> ids);

	@Query("select distinct r.comment.commentId from Report r where r.comment.commentId in :ids")
	List<Long> findReportedCommentIds(@Param("ids") Collection<Long> ids);

	/** 관리자 응답용. 신고자와 처리 상태에 관계없이 전체 신고 존재 여부를 확인한다. */
	boolean existsByPost_PostId(Long postId);

	/** 댓글과 답글 모두 동일한 comment 연관관계로 조회한다. */
	boolean existsByComment_CommentId(Long commentId);

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
