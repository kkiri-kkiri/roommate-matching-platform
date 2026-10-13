package com.kkirikkiri.community.comment.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import com.kkirikkiri.community.comment.entity.CommunityComment;
import com.kkirikkiri.community.common.entity.CommunityContentStatus;

public interface CommunityCommentRepository extends JpaRepository<CommunityComment, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from CommunityComment c where c.post.postId = :postId and c.anonymous = true order by c.createdAt, c.commentId")
	List<CommunityComment> findAnonymousHistory(@Param("postId") Long postId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from CommunityComment c where c.commentId = :commentId and c.status = :status")
	Optional<CommunityComment> findForLikeUpdate(@Param("commentId") Long commentId,
			@Param("status") CommunityContentStatus status);

	/**
	 * 답글 작성의 부모 댓글 및 수정/소프트 삭제할 댓글·답글을 조회한다.
	 * 작성자 권한과 답글 깊이는 서비스에서 확인한다.
	 */
	Optional<CommunityComment> findByCommentIdAndStatus(Long commentId, CommunityContentStatus status);

	/**
	 * 인증 사용자의 ID와 ACTIVE 상태로 내가 작성한 댓글·답글을 함께 조회한다.
	 * 최신순은 Pageable의 createdAt, commentId 내림차순으로 지정한다.
	 */
	Page<CommunityComment> findAllByAuthor_UserIdAndStatus(Long userId, CommunityContentStatus status,
			Pageable pageable);

	/**
	 * 게시글 상세 표시 및 게시글 삭제 시 소프트 삭제할 댓글/답글을 조회한다.
	 */
	List<CommunityComment> findAllByPost_PostIdAndStatusOrderByCreatedAtAscCommentIdAsc(
			Long postId, CommunityContentStatus status);

	long countByPost_PostIdAndStatus(Long postId, CommunityContentStatus status);
}
