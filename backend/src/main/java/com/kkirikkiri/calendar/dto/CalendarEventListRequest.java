package com.kkirikkiri.calendar.dto;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** categories는 쉼표로 구분된 카테고리 코드 목록이다. */
public record CalendarEventListRequest(
		@NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
		@NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
		List<@NotBlank String> categories) {

	@JsonIgnore
	@AssertTrue(message = "조회 종료일은 시작일보다 빠를 수 없습니다.")
	public boolean isDateRangeValid() {
		return from == null || to == null || !to.isBefore(from);
	}
}
