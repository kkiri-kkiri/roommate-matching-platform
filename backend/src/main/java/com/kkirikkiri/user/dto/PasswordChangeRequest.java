package com.kkirikkiri.user.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 현재 비밀번호와 저장된 해시의 일치 여부는 서비스에서 검증한다.
 */
public record PasswordChangeRequest(
		@NotBlank(message = "현재 비밀번호를 입력해 주세요.")
		String currentPassword,
		@NotBlank(message = "새 비밀번호를 입력해 주세요.")
		@Size(min = 8, message = "새 비밀번호는 8자 이상이어야 합니다.")
		@Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s])\\S{8,}$",
				message = "비밀번호는 영문, 숫자, 특수문자를 포함해 8자 이상이어야 합니다.")
		String newPassword,
		@NotBlank(message = "새 비밀번호 확인을 입력해 주세요.")
		String newPasswordConfirm) {

	@JsonIgnore
	@AssertTrue(message = "새 비밀번호와 새 비밀번호 확인이 일치해야 합니다.")
	public boolean isNewPasswordMatching() {
		return newPassword == null || newPasswordConfirm == null
				|| newPassword.equals(newPasswordConfirm);
	}
}
