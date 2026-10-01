package com.kkirikkiri.admin.dto;

import java.time.LocalDate;

import com.kkirikkiri.user.entity.UserStatus;

/** 회원/nullable 프로필과 활동 집계는 서비스에서 조립한다. 가입일은 USER.createdAt 기준이다. */
public record AdminMemberDetailResponse(
		Long id,
		String nickname,
		String schoolEmail,
		String gender,
		String dormitory,
		String faculty,
		String major,
		String admissionYear,
		String grade,
		String mbti,
		LocalDate createdAt,
		UserStatus status,
		Activity activity) {

	public record Activity(long postCount, long commentCount, long reportsReceived) {
	}
}
