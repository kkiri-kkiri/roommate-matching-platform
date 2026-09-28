package com.kkirikkiri.matching.dto;

import java.util.List;

import com.kkirikkiri.user.entity.UserProfile;

public record RoommateProfileResponse(
		Long id,
		String nickname,
		String admissionYear,
		String grade,
		String faculty,
		String major,
		String dormitory,
		String mbti,
		String introduction,
		List<String> tags,
		String profileImageUrl,
		Integer matchScore,
		boolean liked) {

	public RoommateProfileResponse {
		tags = List.copyOf(tags);
	}

	public static RoommateProfileResponse from(
			UserProfile profile, List<String> tags, Integer matchScore, boolean liked) {
		return new RoommateProfileResponse(
				profile.getUser().getUserId(),
				profile.getNickname(),
				profile.getAdmissionYear(),
				profile.getGrade(),
				profile.getFaculty().getFacultyName(),
				profile.getMajor() == null ? null : profile.getMajor().getMajorName(),
				profile.getDormitory().getDormitoryName(),
				profile.getMbti(),
				profile.getIntroduction(),
				tags,
				profile.getProfileImageUrl(),
				matchScore,
				liked);
	}
}
