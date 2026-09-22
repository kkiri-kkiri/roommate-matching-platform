package com.kkirikkiri.chat.dto;

import jakarta.validation.constraints.Pattern;

/** 채팅방 목록 조회 조건. */
public record ChatRoomListRequest(
		@Pattern(regexp = "ALL|ROOMMATE|COMMUNITY") String category,
		String q) {
}
