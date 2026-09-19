package com.kkirikkiri.community.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * request JSON part. keepImageIds에 지정한 기존 이미지와 새 파일을 함께 유지한다.
 * 생략한 keepImageIds는 빈 목록으로 처리한다. 이미지 소유권과 유지/추가 합계는 서비스에서 검증한다.
 */
public record CommunityPostUpdateRequest(
		@NotBlank
		@Pattern(regexp = "자유게시판|나눔게시판|택배게시판|민원게시판")
		String category,
		@NotBlank @Size(max = 100) String title,
		@NotBlank @Size(max = 5000) String content,
		@Size(max = 5) List<@NotNull @Positive Long> keepImageIds) {

	public CommunityPostUpdateRequest {
		keepImageIds = keepImageIds == null ? List.of() : keepImageIds;
	}
}
