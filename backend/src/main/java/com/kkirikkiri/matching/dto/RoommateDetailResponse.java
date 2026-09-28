package com.kkirikkiri.matching.dto;

import java.util.List;

import com.kkirikkiri.survey.entity.ProfileSurvey;
import com.kkirikkiri.user.entity.UserProfile;

public record RoommateDetailResponse(
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
		boolean liked,
		Answers answers) {

	public RoommateDetailResponse {
		tags = List.copyOf(tags);
	}

	public static RoommateDetailResponse from(
			UserProfile profile, ProfileSurvey survey, List<String> tags,
			Integer matchScore, boolean liked) {
		return new RoommateDetailResponse(
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
				liked,
				Answers.from(survey));
	}

	public record Answers(String sleepTime, String wakeTime) {

		public static Answers from(ProfileSurvey survey) {
			return new Answers(survey.getSleepTime(), survey.getWakeTime());
		}
	}
}
