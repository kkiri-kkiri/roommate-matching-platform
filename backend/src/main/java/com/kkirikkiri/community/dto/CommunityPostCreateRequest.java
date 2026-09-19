package com.kkirikkiri.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** multipart/form-data의 request JSON part. 파일은 PostImagesRequest로 검증한다. */
public record CommunityPostCreateRequest(
		@NotBlank
		@Pattern(regexp = "자유게시판|나눔게시판|택배게시판|민원게시판")
		String category,
		@NotBlank @Size(max = 100) String title,
		@NotBlank @Size(max = 5000) String content) {
}
