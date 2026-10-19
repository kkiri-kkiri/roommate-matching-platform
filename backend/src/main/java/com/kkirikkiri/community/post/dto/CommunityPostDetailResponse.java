package com.kkirikkiri.community.post.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.kkirikkiri.community.post.entity.CommunityPost;

/**
 * 게시글 상세 응답. T에는 댓글 응답 DTO만 사용하고 Entity를 전달하지 않는다.
 * API 7.2에는 comments 배열의 내부 필드가 정의되어 있지 않아 댓글 DTO 타입을 열어 둔다.
 * 서비스는 선행 읽기 스냅샷에 원자 증가한 조회수와 익명 번호를 적용한 댓글 DTO를 결합한다.
 */
public record CommunityPostDetailResponse<T>(
		Long id,
		String category,
		String dormitory,
		String title,
		String content,
		CommunityAuthorResponse author,
		boolean anonymous,
		LocalDateTime createdAt,
		int views,
		long likes,
		boolean liked,
		boolean reported,
		List<PostImageResponse> images,
		List<T> comments,
		boolean mine) {

	public CommunityPostDetailResponse {
		images = List.copyOf(images);
		comments = List.copyOf(comments);
	}

	/** Keep the prefetched read snapshot; attach this request's serialized view count and comments. */
	public CommunityPostDetailResponse<T> withViewsAndComments(int currentViews, List<T> currentComments) {
		return new CommunityPostDetailResponse<>(id, category, dormitory, title, content, author, anonymous,
				createdAt, currentViews, likes, liked, reported, images, currentComments, mine);
	}

	public static <T> CommunityPostDetailResponse<T> from(
			CommunityPost post, CommunityAuthorResponse author, long likes, boolean liked,
			boolean reported, List<PostImageResponse> images, List<T> comments, Long viewerId) {
		return new CommunityPostDetailResponse<>(
				post.getPostId(), post.getCategory(),
				post.getDormitory() == null ? null : post.getDormitory().getDormitoryName(),
				post.getTitle(), post.getContent(), author.forPost(post.isAnonymous()), post.isAnonymous(), post.getCreatedAt(), post.getViewCount(),
				likes, liked, reported, images, comments,
				viewerId != null && viewerId.equals(post.getAuthor().getUserId()));
	}
}
