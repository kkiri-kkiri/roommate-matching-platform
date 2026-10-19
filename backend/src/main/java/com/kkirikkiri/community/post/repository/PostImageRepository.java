package com.kkirikkiri.community.post.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import com.kkirikkiri.community.post.entity.PostImage;

public interface PostImageRepository extends JpaRepository<PostImage, Long> {

	/** 게시글 잠금 이후 current read로 읽어 기존 RR 스냅샷의 이미지 목록을 사용하지 않는다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select i from PostImage i where i.post.postId = :postId order by i.sortOrder, i.postImageId")
	List<PostImage> findAllForUpdateByPostId(@Param("postId") Long postId);

	List<PostImage> findAllByPost_PostIdOrderBySortOrderAscPostImageIdAsc(Long postId);

	boolean existsByPost_PostId(Long postId);
}
