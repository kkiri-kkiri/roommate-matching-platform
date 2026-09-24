package com.kkirikkiri.calendar.crawler;

import java.time.LocalDate;
import java.util.Objects;

/** 외부 HTML에서 추출한 내부 전달 객체이며 API DTO가 아니다. */
public record CrawledAcademicEvent(String title, LocalDate startDate, LocalDate endDate) {

	public CrawledAcademicEvent {
		Objects.requireNonNull(title, "title");
		Objects.requireNonNull(startDate, "startDate");
		Objects.requireNonNull(endDate, "endDate");
		if (title.isBlank() || title.length() > 80 || endDate.isBefore(startDate)) {
			throw new IllegalArgumentException("invalid academic event");
		}
	}
}
