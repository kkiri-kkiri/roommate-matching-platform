package com.kkirikkiri.admin.dto;

import java.time.LocalDateTime;

import com.kkirikkiri.calendar.entity.ScheduleCrawlCategory;
import com.kkirikkiri.calendar.entity.ScheduleCrawlRun;
import com.kkirikkiri.calendar.entity.ScheduleCrawlStatus;

/** 최신 실행/성공/실패 이력과 실제 일정 총수는 서비스에서 조립하여 이 DTO의 배열로 반환한다. */
public record AdminScheduleCrawlStatusResponse(
		ScheduleCrawlCategory category,
		ScheduleCrawlStatus status,
		LocalDateTime lastRunAt,
		long recentCollectedCount,
		long totalCollectedCount,
		LocalDateTime lastCompletedAt,
		LastFailure lastFailure) {

	public record LastFailure(LocalDateTime occurredAt, String message) {
		public static LastFailure from(ScheduleCrawlRun failedRun) {
			return new LastFailure(failedRun.getCompletedAt(), failedRun.getErrorMessage());
		}
	}
}
