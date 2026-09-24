package com.kkirikkiri.admin.dto;

import java.time.LocalDateTime;

import com.kkirikkiri.community.post.dto.CommunityAuthorResponse;

/**
 * 관리자 게시글 상세: 목록 필드와 본문.
 * CommunityPostDetailResponse에는 commentCount가 없고 관리자 화면에서 사용하지 않는
 * dormitory/liked/images/comments가 있으므로 해당 응답과 구분한다.
 */
public record AdminPostDetailResponse(
		Long id,
		String category,
		String title,
		CommunityAuthorResponse author,
		LocalDateTime createdAt,
		long views,
		long likes,
		long commentCount,
		boolean reported,
		String content) {
}
