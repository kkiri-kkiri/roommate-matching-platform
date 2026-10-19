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

	/** Entity를 미리 로딩하지 않고 게시글 잠금의 대상 ID만 찾는다. 상태는 잠금 후 검증한다. */
	@Query("select c.post.postId from CommunityComment c where c.commentId = :commentId")
	Optional<Long> findPostIdByCommentId(@Param("commentId") Long commentId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from CommunityComment c where c.parentComment.commentId = :parentId and c.status = :status order by c.commentId")
	List<CommunityComment> findRepliesForUpdate(@Param("parentId") Long parentId,
			@Param("status") CommunityContentStatus status);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from CommunityComment c where c.post.postId = :postId and c.anonymous = true order by c.createdAt, c.commentId")
	List<CommunityComment> findAnonymousHistory(@Param("postId") Long postId);

	/** 수정·일반/관리자 삭제·좋아요가 공유한다. 상태와 작성자 권한은 잠금 조회 후 검증한다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from CommunityComment c where c.commentId = :commentId and c.status = :status")
	Optional<CommunityComment> findForUpdate(@Param("commentId") Long commentId,
			@Param("status") CommunityContentStatus status);

	/** 게시글 잠금 이후 댓글 ID 순서로 잠근다. 단일 댓글 작업은 게시글 잠금을 역으로 획득하지 않는다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from CommunityComment c where c.post.postId = :postId and c.status = :status order by c.commentId")
	List<CommunityComment> findAllForUpdateByPostId(@Param("postId") Long postId,
			@Param("status") CommunityContentStatus status);

	/**
	 * 잠금이 필요하지 않은 일반 조회용이다. 작성·수정·삭제의 상태 검증에 사용하지 않는다.
	 */
	Optional<CommunityComment> findByCommentIdAndStatus(Long commentId, CommunityContentStatus status);

	/**
	 * 인증 사용자의 ID와 ACTIVE 상태로 내가 작성한 댓글·답글을 함께 조회한다.
	 * 최신순은 Pageable의 createdAt, commentId 내림차순으로 지정한다.
	 */
	Page<CommunityComment> findAllByAuthor_UserIdAndStatus(Long userId, CommunityContentStatus status,
			Pageable pageable);

	/**
	 * 게시글 상세에 표시할 댓글/답글을 조회한다. 전체 삭제는 findAllForUpdateByPostId를 사용한다.
	 */
	List<CommunityComment> findAllByPost_PostIdAndStatusOrderByCreatedAtAscCommentIdAsc(
			Long postId, CommunityContentStatus status);

	long countByPost_PostIdAndStatus(Long postId, CommunityContentStatus status);
}
