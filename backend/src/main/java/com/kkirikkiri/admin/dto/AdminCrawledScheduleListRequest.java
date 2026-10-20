package com.kkirikkiri.admin.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Pattern;

public record AdminCrawledScheduleListRequest(
		@Min(0) Integer page,
		@Min(1) @Max(100) Integer size,
		String q,
		@Pattern(regexp = "SCHOOL|DORMITORY") String category) {

	public AdminCrawledScheduleListRequest {
		page = page == null ? 0 : page;
		size = size == null ? 6 : size;
	}
}
