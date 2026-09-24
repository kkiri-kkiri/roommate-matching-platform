package com.kkirikkiri.calendar.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.kkirikkiri.calendar.service.CalendarCrawlingService;

@Slf4j
@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class CalendarCrawlingScheduler {

	private final CalendarCrawlingService service;

	@Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
	public void collectAcademicCalendar() {
		try {
			log.info("HKNU academic calendar: {} events inserted", service.crawlAcademicCalendar());
		} catch (RuntimeException e) {
			log.error("HKNU academic calendar crawling failed", e);
		}
	}
}
