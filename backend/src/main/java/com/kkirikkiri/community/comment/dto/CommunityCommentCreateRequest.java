package com.kkirikkiri.community.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 댓글 및 답글 작성에 공통으로 사용하는 요청 본문. */
public record CommunityCommentCreateRequest(
		@NotBlank @Size(max = 1000) String content) {
}
