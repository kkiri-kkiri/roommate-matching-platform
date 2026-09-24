package com.kkirikkiri.admin.dto;

import java.time.LocalDateTime;

import com.kkirikkiri.community.post.dto.CommunityAuthorResponse;

/** 댓글 목록/상세 공용. 현재 관리자 화면은 답글 여부를 표시하지 않는다. */
public record AdminCommentResponse(
		Long id,
		String content,
		CommunityAuthorResponse author,
		LocalDateTime createdAt,
		Long postId,
		String postTitle,
		boolean reported) {
}
