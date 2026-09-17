package com.kkirikkiri.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record EmailVerificationSendRequest(
		@NotBlank(message = "이메일을 입력해 주세요.")
		@Email(message = "올바른 이메일 형식이어야 합니다.")
		@Pattern(regexp = "[^@\\s]{1,64}@hknu\\.ac\\.kr", message = "학교 이메일(@hknu.ac.kr)을 입력해 주세요. 이메일 아이디는 최대 64자입니다.")
		String email) {
}
