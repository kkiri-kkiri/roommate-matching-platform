package com.kkirikkiri.chat.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.AssertTrue;

/** 채팅방 목록 조회 조건. */
public record ChatRoomListRequest(
		@Pattern(regexp = "ALL|ROOMMATE|COMMUNITY") String category,
		String q,
		@Min(0) Integer page,
		@Min(1) @Max(100) Integer size) {
	public static final int DEFAULT_SIZE = 50;
	public static final int MAX_SIZE = 100;

	public ChatRoomListRequest(String category, String q) {
		this(category, q, null, null);
	}

	@AssertTrue(message = "페이지 범위가 올바르지 않습니다.")
	public boolean isOffsetValid() {
		return (long) (page == null ? 0 : page) * (size == null ? DEFAULT_SIZE : size) <= Integer.MAX_VALUE;
	}
}
