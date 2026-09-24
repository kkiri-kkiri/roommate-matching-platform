package com.kkirikkiri.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 탈퇴 회원의 상태 전이 제한은 User.changeStatus에서 검증한다. */
public record AdminMemberStatusUpdateRequest(
		@NotBlank @Pattern(regexp = "ACTIVE|SUSPENDED|WITHDRAWN") String status) {
}
