package com.kkirikkiri.admin.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

public record AdminPostListRequest(
		@Min(0) Integer page,
		@Min(1) Integer size,
		String q,
		@Pattern(regexp = "자유게시판|나눔게시판|택배게시판|민원게시판") String category,
		Boolean reported,
		@Pattern(regexp = "LATEST|VIEWS|LIKES") String sort) {

	public AdminPostListRequest {
		page = page == null ? 0 : page;
		size = size == null ? 6 : size;
		sort = sort == null ? "LATEST" : sort;
	}
}
