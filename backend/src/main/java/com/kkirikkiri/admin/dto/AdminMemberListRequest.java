package com.kkirikkiri.admin.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

public record AdminMemberListRequest(
		@Min(0) Integer page,
		@Min(1) Integer size,
		String q,
		@Pattern(regexp = "ACTIVE|SUSPENDED|WITHDRAWN") String status,
		String dormitory) {

	public AdminMemberListRequest {
		page = page == null ? 0 : page;
		size = size == null ? 6 : size;
	}
}
