package com.kkirikkiri.member.dto;

public record MemberResponse(
		Long id,
		String loginId,
		String nickname,
		String schoolEmail,
		String gender,
		String dormitory,
		String faculty,
		String major,
		Integer birthYear,
		String studentId,
		String grade,
		String mbtiEnergy,
		String mbtiInformation,
		String mbtiDecision,
		String mbtiLifestyle) {
}
