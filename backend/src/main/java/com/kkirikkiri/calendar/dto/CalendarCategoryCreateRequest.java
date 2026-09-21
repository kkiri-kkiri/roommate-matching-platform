package com.kkirikkiri.calendar.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 사용자 카테고리와 현재 사용자의 설정을 함께 생성한다. 이름 중복은 서비스에서 검증한다. */
public record CalendarCategoryCreateRequest(
		@NotBlank @Size(min = 1, max = 30) String label,
		@NotBlank @Pattern(regexp = "#[0-9a-fA-F]{6}") String color) {
}
