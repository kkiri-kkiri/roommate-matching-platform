package com.kkirikkiri.chat.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import com.kkirikkiri.chat.entity.ChatRoomCategory;

/**
 * 채팅방 생성 또는 기존 방 조회 요청.
 * 출처별 필수 필드, 댓글의 게시글 소속 및 대상 작성자 일치는 서비스에서 검증한다.
 * COMMENT 출처는 API 계약에 따라 sourcePostId와 sourceCommentId를 모두 받는다.
 * COMMUNITY의 targetUserId는 생략 가능하며, 서버에서 출처 작성자를 결정한다.
 * 익명 출처에 전달된 targetUserId는 무시하여 ID 추측 및 대상 변경을 방지한다.
 */
public record ChatRoomCreateRequest(
		Long targetUserId,
		@NotNull ChatRoomCategory category,
		Long sourcePostId,
		Long sourceCommentId,
		@Pattern(regexp = "POST|COMMENT") String sourceFrom) {
}
