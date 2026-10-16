package com.kkirikkiri.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.kkirikkiri.global.validation.PasswordByteLength;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 재설정 토큰의 유효성 및 만료 여부는 서비스에서 검증한다.
 */
public record PasswordResetConfirmRequest(
		@NotBlank(message = "비밀번호 재설정 토큰을 입력해 주세요.")
		String resetToken,
		@NotBlank(message = "새 비밀번호를 입력해 주세요.")
		@Size(min = 8, message = "새 비밀번호는 8자 이상이어야 합니다.")
		@Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s])\\S{8,}$",
				message = "비밀번호는 영문, 숫자, 특수문자를 포함해 8자 이상이어야 합니다.")
        @PasswordByteLength String newPassword,
		@NotBlank(message = "새 비밀번호 확인을 입력해 주세요.")
        @PasswordByteLength String newPasswordConfirm) {

	@JsonIgnore
	@AssertTrue(message = "새 비밀번호와 새 비밀번호 확인이 일치해야 합니다.")
	public boolean isNewPasswordMatching() {
		return newPassword == null || newPasswordConfirm == null
				|| newPassword.equals(newPasswordConfirm);
	}
}
