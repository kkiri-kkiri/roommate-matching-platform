package com.kkirikkiri.calendar.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.kkirikkiri.calendar.entity.CalendarEvent;

/** editable은 서비스에서 현재 사용자의 소유권과 일정 출처를 확인하여 전달한다. */
public record CalendarEventResponse(
		Long id,
		String title,
		LocalDate date,
		@JsonFormat(pattern = "HH:mm") LocalTime startTime,
		@JsonFormat(pattern = "HH:mm") LocalTime endTime,
		String category,
		Long categoryId,
		String description,
		boolean editable,
		String source) {

	public static CalendarEventResponse from(CalendarEvent event, boolean editable) {
		return new CalendarEventResponse(
				event.getEventId(), event.getTitle(), event.getEventDate(),
				event.getStartTime(), event.getEndTime(), event.getCategory().getCategoryCode(),
				event.getCategory().getCategoryId(), event.getDescription(), editable, event.getSourceName());
	}
}
