package com.kkirikkiri.calendar.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 카테고리 존재 여부, 접근 권한 및 시스템 카테고리 생성 금지는 서비스에서 검증한다. */
public record CalendarEventCreateRequest(
		@NotBlank @Size(max = 80) String title,
		@NotNull LocalDate startDate,
		@NotNull LocalDate endDate,
		@Pattern(regexp = "([01][0-9]|2[0-3]):[0-5][0-9]") String startTime,
		@Pattern(regexp = "([01][0-9]|2[0-3]):[0-5][0-9]") String endTime,
		@NotNull @Positive Long categoryId,
		@Size(max = 2000) String description) {

	@JsonIgnore
	@AssertTrue(message = "날짜는 1900-01-01부터 2100-12-31까지여야 합니다.")
	public boolean isDateValid() {
		return (startDate == null || (!startDate.isBefore(LocalDate.of(1900, 1, 1))
				&& !startDate.isAfter(LocalDate.of(2100, 12, 31))))
				&& (endDate == null || (!endDate.isBefore(LocalDate.of(1900, 1, 1))
				&& !endDate.isAfter(LocalDate.of(2100, 12, 31))));
	}

	@JsonIgnore
	@AssertTrue(message = "종료시간은 시작시간보다 늦어야 합니다.")
	public boolean isTimeRangeValid() {
		return startDate == null || endDate == null || !startDate.equals(endDate)
				|| startTime == null || endTime == null || endTime.compareTo(startTime) > 0;
	}
}
