package com.kkirikkiri.calendar.scheduler;

import java.time.Instant;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.kkirikkiri.calendar.service.CalendarCrawlingService;

@Slf4j
@Configuration
@Profile("!test")
@EnableScheduling
@RequiredArgsConstructor
public class CalendarCrawlingScheduler {

    private final CalendarCrawlingService service;
    private final TaskScheduler taskScheduler;

    @Value("${app.calendar.crawl-on-startup:false}")
    private boolean crawlOnStartup;

    @EventListener(ApplicationReadyEvent.class)
    public void collectOnStartup() {
        if (crawlOnStartup) {
            taskScheduler.schedule(this::collectAcademicCalendar, Instant.now());
        }
    }

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void collectAcademicCalendar() {
        try {
            log.info("HKNU academic calendar: {} events inserted",
                    service.crawlAcademicCalendar());
        } catch (RuntimeException e) {
            log.error("HKNU academic calendar crawling failed", e);
        }
    }
}
