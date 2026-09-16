package com.kkirikkiri.community.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kkirikkiri.community.entity.PostLike;

public interface PostLikeRepository extends JpaRepository<PostLike, Long> {

	Optional<PostLike> findByPost_PostIdAndUser_UserId(Long postId, Long userId);

	boolean existsByPost_PostIdAndUser_UserId(Long postId, Long userId);

	long countByPost_PostId(Long postId);
}
