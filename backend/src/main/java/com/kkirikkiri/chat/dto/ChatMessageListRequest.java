package com.kkirikkiri.chat.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Positive;

/** 메시지 목록 조회 조건. */
public record ChatMessageListRequest(@Min(0) Integer page, @Min(1) @Max(100) Integer size, String q,
        @Positive Long beforeId, @Positive Long afterId) {

	public ChatMessageListRequest(Integer page, Integer size, String q) {
		this(page, size, q, null, null);
	}
	public static final int DEFAULT_SIZE = 50;
	public static final int MAX_SIZE = 100;

	@AssertTrue(message = "페이지 범위가 올바르지 않습니다.")
	public boolean isOffsetValid() {
		return (long) (page == null ? 0 : page) * (size == null ? DEFAULT_SIZE : size) <= Integer.MAX_VALUE;
	}

	@AssertTrue(message = "커서 조회는 page=0과 하나의 커서만 사용할 수 있습니다.")
	public boolean isCursorValid() {
		return (beforeId == null || afterId == null)
				&& ((beforeId == null && afterId == null) || page == null || page == 0);
	}
}
