package com.kkirikkiri.community.comment.repository;

import java.util.Optional;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kkirikkiri.community.comment.entity.CommentLike;

/** 댓글과 답글의 좋아요를 동일한 commentId 기준으로 처리한다. */
public interface CommentLikeRepository extends JpaRepository<CommentLike, Long> {

	interface LikeCount {
		Long getCommentId();
		long getLikes();
	}

	@Query("select l.comment.commentId as commentId, count(l) as likes from CommentLike l "
			+ "where l.comment.commentId in :commentIds group by l.comment.commentId")
	List<LikeCount> countLikesByCommentIds(@Param("commentIds") Collection<Long> commentIds);

	@Query("select l.comment.commentId from CommentLike l "
			+ "where l.comment.commentId in :commentIds and l.user.userId = :userId")
	List<Long> findLikedCommentIds(@Param("commentIds") Collection<Long> commentIds,
			@Param("userId") Long userId);

	boolean existsByComment_CommentIdAndUser_UserId(Long commentId, Long userId);

	/** 서비스 트랜잭션에서 조회한 관계를 상속받은 delete로 삭제한다. */
	Optional<CommentLike> findByComment_CommentIdAndUser_UserId(Long commentId, Long userId);
}
