package com.kkirikkiri.calendar.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 기존 부분 수정 DTO와 동일하게 null인 필드는 변경하지 않는다.
 * 서비스에서 기존 시간과 병합한 뒤 시간 순서 및 카테고리 접근 권한을 검증한다.
 */
public record CalendarEventUpdateRequest(
		@Pattern(regexp = "(?s).*\\S.*") @Size(max = 80) String title,
		LocalDate date,
		@Pattern(regexp = "([01][0-9]|2[0-3]):[0-5][0-9]") String startTime,
		@Pattern(regexp = "([01][0-9]|2[0-3]):[0-5][0-9]") String endTime,
		@Positive Long categoryId,
		@Size(max = 2000) String description) {

	@JsonIgnore
	@AssertTrue(message = "날짜는 1900-01-01부터 2100-12-31까지여야 합니다.")
	public boolean isDateValid() {
		return date == null || (!date.isBefore(LocalDate.of(1900, 1, 1))
				&& !date.isAfter(LocalDate.of(2100, 12, 31)));
	}

	@JsonIgnore
	@AssertTrue(message = "종료시간은 시작시간보다 늦어야 합니다.")
	public boolean isTimeRangeValid() {
		return startTime == null || endTime == null || endTime.compareTo(startTime) > 0;
	}
}
