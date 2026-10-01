package com.kkirikkiri.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 학교 이메일의 가입 여부 및 메일 전송은 서비스에서 처리한다.
 */
public record PasswordResetRequest(
		@NotBlank(message = "이메일을 입력해 주세요.")
		@Email(message = "올바른 이메일 형식이어야 합니다.")
		@Pattern(regexp = "[^@\\s]{1,64}@hknu\\.ac\\.kr", message = "학교 이메일(@hknu.ac.kr)을 입력해 주세요.")
		String email) {
}
