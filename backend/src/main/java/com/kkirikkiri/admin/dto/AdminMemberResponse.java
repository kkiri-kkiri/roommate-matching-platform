package com.kkirikkiri.admin.dto;

import java.time.LocalDate;

import com.kkirikkiri.user.entity.UserStatus;

/** 회원 목록 항목. 프로필이 없는 회원의 nickname/dormitory/major는 null일 수 있다. */
public record AdminMemberResponse(
		Long id,
		String loginId,
		String nickname,
		String schoolEmail,
		String dormitory,
		String major,
		LocalDate createdAt,
		UserStatus status) {
}
