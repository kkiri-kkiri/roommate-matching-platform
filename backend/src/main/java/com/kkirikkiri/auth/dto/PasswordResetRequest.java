package com.kkirikkiri.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 아이디와 이메일의 일치 여부 및 메일 전송은 서비스에서 처리한다.
 */
public record PasswordResetRequest(
		@NotBlank(message = "아이디를 입력해 주세요.")
		String loginId,
		@NotBlank(message = "이메일을 입력해 주세요.")
		@Email(message = "올바른 이메일 형식이어야 합니다.")
		String email) {
}
