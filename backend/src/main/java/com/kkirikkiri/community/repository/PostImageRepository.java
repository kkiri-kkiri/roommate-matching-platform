package com.kkirikkiri.community.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kkirikkiri.community.entity.PostImage;

public interface PostImageRepository extends JpaRepository<PostImage, Long> {

	List<PostImage> findAllByPost_PostIdOrderBySortOrderAscPostImageIdAsc(Long postId);

	boolean existsByPost_PostId(Long postId);
}
