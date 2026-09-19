package com.kkirikkiri.community.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.kkirikkiri.community.entity.CommunityPost;

/**
 * 게시글 상세 응답. T에는 댓글 응답 DTO만 사용하고 Entity를 전달하지 않는다.
 * API 7.2에는 comments 배열의 내부 필드가 정의되어 있지 않아 댓글 DTO 타입을 열어 둔다.
 * 서비스는 조회수 증가 후의 게시글과 집계/사용자 상태, 정렬된 이미지 및 댓글 DTO를 전달한다.
 */
public record CommunityPostDetailResponse<T>(
		Long id,
		String category,
		String dormitory,
		String title,
		String content,
		CommunityAuthorResponse author,
		LocalDateTime createdAt,
		int views,
		long likes,
		boolean liked,
		boolean reported,
		List<PostImageResponse> images,
		List<T> comments) {

	public CommunityPostDetailResponse {
		images = List.copyOf(images);
		comments = List.copyOf(comments);
	}

	public static <T> CommunityPostDetailResponse<T> from(
			CommunityPost post, CommunityAuthorResponse author, long likes, boolean liked,
			boolean reported, List<PostImageResponse> images, List<T> comments) {
		return new CommunityPostDetailResponse<>(
				post.getPostId(), post.getCategory(),
				post.getDormitory() == null ? null : post.getDormitory().getDormitoryName(),
				post.getTitle(), post.getContent(), author, post.getCreatedAt(), post.getViewCount(),
				likes, liked, reported, images, comments);
	}
}
