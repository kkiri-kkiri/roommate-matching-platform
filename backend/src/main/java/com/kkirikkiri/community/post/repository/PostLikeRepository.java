package com.kkirikkiri.community.post.repository;

import java.util.Optional;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kkirikkiri.community.post.entity.PostLike;

/** 목록의 페이지 ID에 대한 좋아요 수와 본인 여부를 일괄 조회한다. */
public interface PostLikeRepository extends JpaRepository<PostLike, Long> {

	interface LikeCount {
		Long getPostId();
		long getLikes();
	}

	@Query("select l.post.postId as postId, count(l) as likes from PostLike l "
			+ "where l.post.postId in :postIds group by l.post.postId")
	List<LikeCount> countLikesByPostIds(@Param("postIds") Collection<Long> postIds);

	@Query("select l.post.postId from PostLike l "
			+ "where l.post.postId in :postIds and l.user.userId = :userId")
	List<Long> findLikedPostIds(@Param("postIds") Collection<Long> postIds, @Param("userId") Long userId);

	/** 서비스 트랜잭션에서 조회한 관계를 상속받은 delete로 삭제한다. */
	Optional<PostLike> findByPost_PostIdAndUser_UserId(Long postId, Long userId);
}
