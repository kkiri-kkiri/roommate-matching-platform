package com.kkirikkiri.community.post.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kkirikkiri.community.post.entity.PostLike;

/** 좋아요 수와 존재 여부는 CommunityPostReadRepository의 countLikes/isLiked를 재사용한다. */
public interface PostLikeRepository extends JpaRepository<PostLike, Long> {

	/** 서비스 트랜잭션에서 조회한 관계를 상속받은 delete로 삭제한다. */
	Optional<PostLike> findByPost_PostIdAndUser_UserId(Long postId, Long userId);
}
