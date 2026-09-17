package com.kkirikkiri.member.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordChangeRequest(
		@NotBlank(message = "현재 비밀번호를 입력해 주세요.")
		String currentPassword,
		@NotBlank(message = "새 비밀번호를 입력해 주세요.")
		@Size(min = 8, message = "새 비밀번호는 8자 이상이어야 합니다.")
		String newPassword,
		@NotBlank(message = "새 비밀번호 확인을 입력해 주세요.")
		String newPasswordConfirm) {

	@JsonIgnore
	@AssertTrue(message = "새 비밀번호 확인이 일치하지 않습니다.")
	public boolean isNewPasswordConfirmed() {
		return newPassword == null || newPasswordConfirm == null
				|| newPassword.equals(newPasswordConfirm);
	}
}
