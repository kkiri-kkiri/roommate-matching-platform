package com.kkirikkiri.calendar.dto;

import com.kkirikkiri.calendar.entity.CalendarCategorySetting;

public record CalendarCategoryResponse(
		Long id,
		String code,
		String label,
		String color,
		boolean fixed,
		boolean enabled) {

	public static CalendarCategoryResponse from(CalendarCategorySetting setting) {
		return new CalendarCategoryResponse(
				setting.getCategory().getCategoryId(), setting.getCategory().getCategoryCode(),
				setting.getDisplayName(), setting.getColor(), setting.getCategory().isNameLocked(),
				setting.isEnabled());
	}
}
