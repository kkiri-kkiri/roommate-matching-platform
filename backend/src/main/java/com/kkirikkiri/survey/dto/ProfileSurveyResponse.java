package com.kkirikkiri.survey.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.kkirikkiri.survey.entity.ProfileSurvey;
import com.kkirikkiri.survey.entity.ProfileSurveyFieldCode;
import com.kkirikkiri.survey.entity.ProfileSurveyOption;

public record ProfileSurveyResponse(
		boolean completed,
		@JsonInclude(JsonInclude.Include.ALWAYS) Answers answers) {

	/**
	 * 설문이 없으면 미작성 응답을 반환한다.
	 * options에는 해당 설문의 다중선택 응답 전체를 전달한다.
	 */
	public static ProfileSurveyResponse from(ProfileSurvey survey, List<ProfileSurveyOption> options) {
		if (survey == null) {
			return new ProfileSurveyResponse(false, null);
		}
		return new ProfileSurveyResponse(survey.isCompleted(), Answers.from(survey, options));
	}

	public record Answers(
			String sleepTime,
			String wakeTime,
			String sleepTalking,
			String snoring,
			String teethGrinding,
			String sleepSensitivity,
			String indoorEating,
			String smoking,
			String lightingPreference,
			String phoneCall,
			String mediaSound,
			String showerTime,
			String showerDuration,
			String cleaningFrequency,
			String cleaningStyle,
			List<String> dormStayDays,
			List<String> mealPlan,
			String meal,
			String roommateInteraction,
			String temperaturePreference,
			String hairDryer,
			String bugHandling,
			String scentProductUse,
			String desktopUse,
			String sharedItems) {

		public static Answers from(ProfileSurvey survey, List<ProfileSurveyOption> options) {
			return new Answers(
					survey.getSleepTime(),
					survey.getWakeTime(),
					survey.getSleepTalking(),
					survey.getSnoring(),
					survey.getTeethGrinding(),
					survey.getSleepSensitivity(),
					survey.getIndoorEating(),
					survey.getSmoking(),
					survey.getLightingPreference(),
					survey.getPhoneCall(),
					survey.getMediaSound(),
					survey.getShowerTime(),
					survey.getShowerDuration(),
					survey.getCleaningFrequency(),
					survey.getCleaningStyle(),
					optionValues(options, ProfileSurveyFieldCode.DORM_STAY_DAY),
					optionValues(options, ProfileSurveyFieldCode.MEAL_PLAN),
					survey.getMeal(),
					survey.getRoommateInteraction(),
					survey.getTemperaturePreference(),
					survey.getHairDryerUse(),
					survey.getBugHandling(),
					survey.getScentProductUse(),
					survey.getDesktopUse(),
					survey.getSharedItems());
		}

		private static List<String> optionValues(List<ProfileSurveyOption> options,
				ProfileSurveyFieldCode fieldCode) {
			return options.stream()
					.filter(option -> option.getFieldCode() == fieldCode)
					.map(ProfileSurveyOption::getOptionValue)
					.toList();
		}
	}
}
