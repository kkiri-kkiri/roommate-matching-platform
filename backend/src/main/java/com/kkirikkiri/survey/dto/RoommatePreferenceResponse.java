package com.kkirikkiri.survey.dto;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.kkirikkiri.survey.entity.PreferenceCriterion;
import com.kkirikkiri.survey.entity.PreferenceCriterionCode;
import com.kkirikkiri.survey.entity.RoommatePreference;
import com.kkirikkiri.survey.entity.RoommatePreferenceOption;

public record RoommatePreferenceResponse(
		boolean completed,
		@JsonInclude(JsonInclude.Include.ALWAYS) Answers answers) {

	/**
	 * 설문이 없으면 미작성 응답을 반환한다.
	 * options와 criteria에는 해당 설문의 다중선택 응답과 조건 설정 전체를 전달한다.
	 */
	public static RoommatePreferenceResponse from(RoommatePreference preference,
			List<RoommatePreferenceOption> options, List<PreferenceCriterion> criteria) {
		if (preference == null) {
			return new RoommatePreferenceResponse(false, null);
		}
		return new RoommatePreferenceResponse(preference.isCompleted(), Answers.from(preference, options, criteria));
	}

	public record Answers(
			String department,
			List<String> age,
			List<String> grade,
			List<String> bedtime,
			List<String> wakeTime,
			String sleepTalking,
			String snoring,
			String teethGrinding,
			String smoking,
			String lightingPreference,
			String showerTime,
			String showerDuration,
			List<String> cleaningFrequency,
			String cleaningStyle,
			String interaction,
			String meal,
			String sharedItems,
			String temperature,
			Map<String, Integer> weights) {

		public static Answers from(RoommatePreference preference,
				List<RoommatePreferenceOption> options, List<PreferenceCriterion> criteria) {
			return new Answers(
					preference.getDepartmentPreference(),
					optionValues(options, PreferenceCriterionCode.AGE),
					optionValues(options, PreferenceCriterionCode.GRADE),
					optionValues(options, PreferenceCriterionCode.BEDTIME),
					optionValues(options, PreferenceCriterionCode.WAKE_TIME),
					preference.getSleepTalkingAllowance(),
					preference.getSnoringAllowance(),
					preference.getTeethGrindingAllowance(),
					preference.getSmokingPreference(),
					preference.getLightingPreference(),
					preference.getShowerTimePreference(),
					preference.getShowerDurationPreference(),
					optionValues(options, PreferenceCriterionCode.CLEANING_FREQUENCY),
					preference.getCleaningStylePreference(),
					preference.getInteractionPreference(),
					preference.getMealPreference(),
					preference.getSharedItemsPreference(),
					preference.getTemperaturePreference(),
					weights(criteria));
		}

		private static List<String> optionValues(List<RoommatePreferenceOption> options,
				PreferenceCriterionCode criterionCode) {
			return options.stream()
					.filter(option -> option.getCriterionCode() == criterionCode)
					.map(RoommatePreferenceOption::getOptionValue)
					.toList();
		}

		private static Map<String, Integer> weights(List<PreferenceCriterion> criteria) {
			Map<String, Integer> weights = new LinkedHashMap<>();
			criteria.forEach(criterion -> weights.put(
					apiKey(criterion.getCriterionCode()), (int) criterion.getWeight()));
			return weights;
		}

		private static String apiKey(PreferenceCriterionCode code) {
			return switch (code) {
				case DEPARTMENT -> "department";
				case AGE -> "age";
				case GRADE -> "grade";
				case BEDTIME -> "bedtime";
				case WAKE_TIME -> "wakeTime";
				case SLEEP_TALKING -> "sleepTalking";
				case SNORING -> "snoring";
				case TEETH_GRINDING -> "teethGrinding";
				case SMOKING -> "smoking";
				case LIGHTING -> "lightingPreference";
				case SHOWER_TIME -> "showerTime";
				case SHOWER_DURATION -> "showerDuration";
				case CLEANING_FREQUENCY -> "cleaningFrequency";
				case CLEANING_STYLE -> "cleaningStyle";
				case INTERACTION -> "interaction";
				case MEAL -> "meal";
				case SHARED_ITEMS -> "sharedItems";
				case TEMPERATURE -> "temperature";
			};
		}
	}
}
