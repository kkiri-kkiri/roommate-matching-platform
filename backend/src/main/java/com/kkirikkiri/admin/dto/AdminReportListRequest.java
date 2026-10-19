package com.kkirikkiri.admin.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Pattern;

public record AdminReportListRequest(
		@Min(0) Integer page,
		@Min(1) @Max(100) Integer size,
		String q,
		@Pattern(regexp = "USER|POST|COMMENT") String type,
		@Pattern(regexp = "PENDING|COMPLETED|REJECTED") String status) {

	public AdminReportListRequest {
		page = page == null ? 0 : page;
		size = size == null ? 6 : size;
	}
}
