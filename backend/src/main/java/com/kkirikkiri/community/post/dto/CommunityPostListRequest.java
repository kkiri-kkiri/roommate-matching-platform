package com.kkirikkiri.community.post.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

public record CommunityPostListRequest(
		@Min(0) Integer page,
		@Min(1) Integer size,
		@Pattern(regexp = "자유게시판|나눔게시판|택배게시판|민원게시판") String category,
		String q,
		@Pattern(regexp = "LATEST|VIEWS|LIKES") String sort) {

	public CommunityPostListRequest {
		page = page == null ? 0 : page;
		size = size == null ? 6 : size;
		sort = sort == null ? "LATEST" : sort;
	}
}
