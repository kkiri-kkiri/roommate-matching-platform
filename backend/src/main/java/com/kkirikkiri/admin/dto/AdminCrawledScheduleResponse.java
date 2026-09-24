package com.kkirikkiri.admin.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.kkirikkiri.calendar.entity.CalendarEvent;

/**
 * API 13.15 목록과 13.16 상세 공용. 두 화면 모두 단일 date만 사용한다.
 * date는 CalendarEvent.startDate이며, 현재 화면에는 endDate를 표시할 필드가 없다.
 */
public record AdminCrawledScheduleResponse(
		Long id,
		String title,
		LocalDate date,
		String content,
		String category,
		String source,
		LocalDateTime collectedAt) {

	public static AdminCrawledScheduleResponse from(CalendarEvent event) {
		return new AdminCrawledScheduleResponse(
				event.getEventId(), event.getTitle(), event.getStartDate(), event.getDescription(),
				event.getCategory().getCategoryCode(), event.getSourceName(), event.getCollectedAt());
	}
}
