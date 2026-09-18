package com.kkirikkiri.survey.entity;

public enum PreferenceCriterionCode {
	DEPARTMENT,
	AGE,
	GRADE,
	BEDTIME,
	WAKE_TIME,
	SLEEP_TALKING,
	SNORING,
	TEETH_GRINDING,
	SMOKING,
	LIGHTING,
	SHOWER_TIME,
	SHOWER_DURATION,
	CLEANING_FREQUENCY,
	CLEANING_STYLE,
	INTERACTION,
	MEAL,
	SHARED_ITEMS,
	TEMPERATURE;

	public boolean isMultiSelect() {
		return switch (this) {
			case AGE, GRADE, BEDTIME, WAKE_TIME, CLEANING_FREQUENCY -> true;
			default -> false;
		};
	}
}
