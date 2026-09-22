package com.kkirikkiri.community.comment.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kkirikkiri.community.comment.entity.CommentLike;

/** 댓글과 답글의 좋아요를 동일한 commentId 기준으로 처리한다. */
public interface CommentLikeRepository extends JpaRepository<CommentLike, Long> {

	boolean existsByComment_CommentIdAndUser_UserId(Long commentId, Long userId);

	/** 서비스 트랜잭션에서 조회한 관계를 상속받은 delete로 삭제한다. */
	Optional<CommentLike> findByComment_CommentIdAndUser_UserId(Long commentId, Long userId);
}
