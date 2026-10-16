package com.kkirikkiri.calendar.scheduler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;
import com.kkirikkiri.calendar.service.CalendarCrawlingService;

class CalendarCrawlingSchedulerTest {
    private final CalendarCrawlingService service = mock(CalendarCrawlingService.class);
    private final TaskScheduler tasks = mock(TaskScheduler.class);
    private final CalendarCrawlingScheduler scheduler = new CalendarCrawlingScheduler(service, tasks);
    @Test void startupCrawlIsOptIn() {
        scheduler.collectOnStartup(); verifyNoInteractions(tasks, service);
        ReflectionTestUtils.setField(scheduler, "crawlOnStartup", true);
        scheduler.collectOnStartup(); verify(tasks).schedule(any(Runnable.class), any(Instant.class));
        verifyNoInteractions(service);
    }
    @Test void scheduledRunCallsTheTrackedService() {
        when(service.crawlAcademicCalendar()).thenReturn(3);
        scheduler.collectAcademicCalendar(); verify(service).crawlAcademicCalendar();
    }
    @Test void failureDoesNotEscapeAndStopFutureRuns() {
        when(service.crawlAcademicCalendar()).thenThrow(new IllegalStateException("private response"));
        assertDoesNotThrow(scheduler::collectAcademicCalendar); verify(service).crawlAcademicCalendar();
    }
}
