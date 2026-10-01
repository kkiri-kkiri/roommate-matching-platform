package com.kkirikkiri.user.dto;

import com.kkirikkiri.user.entity.User;
import com.kkirikkiri.user.entity.UserProfile;

public record UserResponse(
		Long id,
		String nickname,
		String schoolEmail,
		String gender,
		String dormitory,
		String faculty,
		String major,
		Short birthYear,
		String admissionYear,
		String grade,
		String mbtiEnergy,
		String mbtiInformation,
		String mbtiDecision,
		String mbtiLifestyle) {

	public static UserResponse from(UserProfile profile) {
		User user = profile.getUser();
		String mbti = profile.getMbti();
		return new UserResponse(
				user.getUserId(),
				profile.getNickname(),
				user.getSchoolEmail(),
				profile.getGender(),
				profile.getDormitory().getDormitoryName(),
				profile.getFaculty().getFacultyName(),
				profile.getMajor() == null ? null : profile.getMajor().getMajorName(),
				profile.getBirthYear(),
				profile.getAdmissionYear(),
				profile.getGrade(),
				mbti.substring(0, 1),
				mbti.substring(1, 2),
				mbti.substring(2, 3),
				mbti.substring(3, 4));
	}
}
