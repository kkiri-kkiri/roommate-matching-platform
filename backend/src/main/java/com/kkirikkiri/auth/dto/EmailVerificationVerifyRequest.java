package com.kkirikkiri.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 인증번호 만료 여부와 일치 여부는 서비스에서 검증한다.
 */
public record EmailVerificationVerifyRequest(
		@NotBlank(message = "이메일을 입력해 주세요.")
		@Email(message = "올바른 이메일 형식이어야 합니다.")
		@Pattern(regexp = "[^@\\s]{1,64}@hknu\\.ac\\.kr", message = "학교 이메일(@hknu.ac.kr)을 입력해 주세요. 이메일 아이디는 최대 64자입니다.")
		String email,
		@NotBlank(message = "인증번호를 입력해 주세요.")
		@Pattern(regexp = "[0-9]{6}", message = "인증번호는 숫자 6자리여야 합니다.")
		String code,
		@NotBlank(message = "인증 목적을 입력해 주세요.")
		@Pattern(regexp = "SIGNUP|FIND_ID", message = "인증 목적은 SIGNUP 또는 FIND_ID여야 합니다.")
		String purpose) {
}
