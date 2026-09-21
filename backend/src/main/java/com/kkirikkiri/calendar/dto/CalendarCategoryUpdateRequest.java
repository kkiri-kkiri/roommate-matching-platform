package com.kkirikkiri.calendar.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** null인 필드는 변경하지 않는다. 소유권, 이름 중복 및 변경 제한은 서비스에서 검증한다. */
public record CalendarCategoryUpdateRequest(
		@Pattern(regexp = "(?s).*\\S.*") @Size(min = 1, max = 30) String label,
		@Pattern(regexp = "#[0-9a-fA-F]{6}") String color,
		Boolean enabled) {
}
