package com.kkirikkiri.community.post.dto;

import java.time.LocalDateTime;

import com.kkirikkiri.community.post.entity.CommunityPost;

/**
 * 게시글 목록 항목. 페이징은 기존 RoommatePageResponse의 공통 페이징 계약을 재사용한다.
 * 미리보기, 집계 및 현재 사용자 상태는 서비스에서 전달한다.
 */
public record CommunityPostResponse(
		Long id,
		String category,
		String dormitory,
		String title,
		String contentPreview,
		CommunityAuthorResponse author,
		LocalDateTime createdAt,
		int views,
		long likes,
		long commentCount,
		boolean hasImages,
		boolean liked,
		boolean reported) {

	public static CommunityPostResponse from(
			CommunityPost post, CommunityAuthorResponse author, String contentPreview,
			long likes, long commentCount, boolean hasImages, boolean liked, boolean reported) {
		return new CommunityPostResponse(
				post.getPostId(), post.getCategory(),
				post.getDormitory() == null ? null : post.getDormitory().getDormitoryName(),
				post.getTitle(), contentPreview, author, post.getCreatedAt(), post.getViewCount(),
				likes, commentCount, hasImages, liked, reported);
	}
}
