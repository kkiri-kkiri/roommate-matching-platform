package com.kkirikkiri.community.post.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Pattern;

public record CommunityPostListRequest(
		@Min(0) Integer page,
		@Min(1) @Max(100) Integer size,
		@Pattern(regexp = "자유게시판|나눔게시판|택배게시판|민원게시판") String category,
		String q,
		@Pattern(regexp = "나래|호연|비봉|창조") String dormitory,
		@Pattern(regexp = "LATEST|VIEWS|LIKES") String sort) {

	public CommunityPostListRequest(Integer page, Integer size, String category, String q, String sort) {
		this(page, size, category, q, null, sort);
	}

	public CommunityPostListRequest {
		page = page == null ? 0 : page;
		size = size == null ? 6 : size;
		sort = sort == null ? "LATEST" : sort;
		// 화면의 생활관 명칭을 dormitory 기준정보에 저장된 이름으로 맞춘다.
		if (dormitory != null) {
			dormitory = switch (dormitory) {
				case "나래관" -> "나래";
				case "호연관" -> "호연";
				case "비봉관" -> "비봉";
				case "창조관" -> "창조";
				default -> dormitory;
			};
		}
	}
}
