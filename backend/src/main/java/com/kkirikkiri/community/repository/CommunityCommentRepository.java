package com.kkirikkiri.community.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.kkirikkiri.community.entity.CommunityComment;
import com.kkirikkiri.community.entity.CommunityStatus;

public interface CommunityCommentRepository extends JpaRepository<CommunityComment, Long> {

	Optional<CommunityComment> findByCommentIdAndStatusAndPost_Status(
			Long commentId, CommunityStatus status, CommunityStatus postStatus);

	/**
	 * 답글의 부모 구조 보존을 위해 삭제된 댓글도 포함한다.
	 * 응답 생성 시 삭제된 댓글의 내용을 노출하지 않도록 처리해야 한다.
	 */
	List<CommunityComment> findByPost_PostIdOrderByCreatedAtAscCommentIdAsc(Long postId);

	long countByPost_PostIdAndStatus(Long postId, CommunityStatus status);

	Page<CommunityComment> findByAuthor_UserIdAndStatusAndPost_Status(
			Long userId, CommunityStatus status, CommunityStatus postStatus, Pageable pageable);
}
