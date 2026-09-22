package com.kkirikkiri.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * FIND_ID 토큰의 이메일 일치, 만료 및 사용 여부는 서비스에서 검증한다.
 */
public record FindIdRequest(
		@NotBlank(message = "이메일을 입력해 주세요.")
		@Email(message = "올바른 이메일 형식이어야 합니다.")
		String email,
		@NotBlank(message = "학교 이메일 인증 토큰을 입력해 주세요.")
		String emailVerificationToken) {
}
