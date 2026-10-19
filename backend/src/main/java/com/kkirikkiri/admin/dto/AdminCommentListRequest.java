package com.kkirikkiri.admin.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

public record AdminCommentListRequest(
		@Min(0) Integer page,
		@Min(1) @Max(100) Integer size,
		String q,
		Boolean reported) {

	public AdminCommentListRequest {
		page = page == null ? 0 : page;
		size = size == null ? 6 : size;
	}
}
