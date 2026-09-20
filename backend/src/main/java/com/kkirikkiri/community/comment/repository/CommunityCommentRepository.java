package com.kkirikkiri.community.comment.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kkirikkiri.community.comment.entity.CommunityComment;
import com.kkirikkiri.community.common.entity.CommunityContentStatus;

public interface CommunityCommentRepository extends JpaRepository<CommunityComment, Long> {

	/**
	 * 게시글 상세 표시 및 게시글 삭제 시 소프트 삭제할 댓글/답글을 조회한다.
	 */
	List<CommunityComment> findAllByPost_PostIdAndStatusOrderByCreatedAtAscCommentIdAsc(
			Long postId, CommunityContentStatus status);

	long countByPost_PostIdAndStatus(Long postId, CommunityContentStatus status);
}
