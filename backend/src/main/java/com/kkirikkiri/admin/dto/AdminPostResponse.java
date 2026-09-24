package com.kkirikkiri.admin.dto;

import java.time.LocalDateTime;

import com.kkirikkiri.community.post.dto.CommunityAuthorResponse;

/** 작성자 요약, 좋아요/댓글 집계와 전체 신고 여부는 서비스에서 조립한다. */
public record AdminPostResponse(
		Long id,
		String category,
		String title,
		CommunityAuthorResponse author,
		LocalDateTime createdAt,
		long views,
		long likes,
		long commentCount,
		boolean reported) {
}
