package com.kkirikkiri.calendar.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * PUT /api/calendar/categories의 배열 항목. List<@Valid CalendarCategorySettingRequest>로 검증한다.
 * id는 카테고리 PK이다. 이름 중복, 카테고리 접근 권한 및 이름 변경 제한은 서비스에서 검증한다.
 */
public record CalendarCategorySettingRequest(
		@NotNull @Positive Long id,
		@NotBlank @Size(min = 1, max = 30) String label,
		@NotBlank @Pattern(regexp = "#[0-9a-fA-F]{6}") String color,
		@NotNull Boolean enabled) {
}
