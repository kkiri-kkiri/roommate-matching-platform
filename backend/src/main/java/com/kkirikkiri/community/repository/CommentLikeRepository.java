package com.kkirikkiri.community.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kkirikkiri.community.entity.CommentLike;

public interface CommentLikeRepository extends JpaRepository<CommentLike, Long> {

	Optional<CommentLike> findByComment_CommentIdAndUser_UserId(Long commentId, Long userId);

	boolean existsByComment_CommentIdAndUser_UserId(Long commentId, Long userId);

	long countByComment_CommentId(Long commentId);
}
