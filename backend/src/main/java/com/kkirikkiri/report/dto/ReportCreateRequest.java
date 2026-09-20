package com.kkirikkiri.report.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/** 신고 대상 조회와 중복 신고 확인, 인증 사용자 결정은 서비스에서 처리한다. */
public record ReportCreateRequest(
		@NotBlank @Pattern(regexp = "USER|POST|COMMENT") String targetType,
		@NotNull @Positive Long targetId,
		@NotBlank
		@Pattern(regexp = "욕설·비방|광고·홍보|음란성 콘텐츠|개인정보 노출|도배·스팸|기타")
		String reason,
		String description) {

	@JsonIgnore
	@AssertTrue(message = "기타 신고 사유를 선택하면 상세 내용을 입력해야 합니다.")
	public boolean isDescriptionValid() {
		return !"기타".equals(reason) || (description != null && !description.isBlank());
	}
}
