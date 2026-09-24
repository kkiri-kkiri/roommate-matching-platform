package com.kkirikkiri.calendar.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import com.kkirikkiri.calendar.crawler.CrawledAcademicEvent;
import com.kkirikkiri.calendar.crawler.HknuAcademicCalendarCrawler;
import com.kkirikkiri.calendar.entity.CalendarCategory;
import com.kkirikkiri.calendar.entity.CalendarEvent;
import com.kkirikkiri.calendar.entity.CalendarEventSourceType;
import com.kkirikkiri.calendar.repository.CalendarCategoryRepository;
import com.kkirikkiri.calendar.repository.CalendarEventRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CalendarCrawlingServiceTest {

	private final HknuAcademicCalendarCrawler crawler = mock(HknuAcademicCalendarCrawler.class);
	private final CalendarCategoryRepository categories = mock(CalendarCategoryRepository.class);
	private final CalendarEventRepository events = mock(CalendarEventRepository.class);
	private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
	private final TransactionStatus status = mock(TransactionStatus.class);
	private CalendarCrawlingService service;
	private final CrawledAcademicEvent item = new CrawledAcademicEvent("학사일정",
			LocalDate.of(2026, 3, 3), LocalDate.of(2026, 3, 20));

	@BeforeEach
	void setUp() {
		when(transactions.getTransaction(any())).thenReturn(status);
		service = new CalendarCrawlingService(crawler, categories, events, transactions);
	}

	@Test
	void repeatedCrawlsAndDuplicateHtmlRowsInsertOnlyOnceWithRequiredMetadata() {
		var school = new CalendarCategory("SCHOOL", "학교", null, true, true);
		when(categories.findSchoolForCrawling()).thenReturn(Optional.of(school));
		when(crawler.crawl()).thenReturn(List.of(item, item));
		List<CalendarEvent> saved = new ArrayList<>();
		when(events.existsBySourceTypeAndSourceNameAndTitleAndStartDateAndEndDate(
				CalendarEventSourceType.CRAWLED, HknuAcademicCalendarCrawler.SOURCE_NAME,
				item.title(), item.startDate(), item.endDate())).thenAnswer(call -> !saved.isEmpty());
		when(events.save(any(CalendarEvent.class))).thenAnswer(call -> {
			CalendarEvent event = call.getArgument(0);
			saved.add(event);
			return event;
		});
		assertEquals(1, service.crawlAcademicCalendar());
		assertEquals(0, service.crawlAcademicCalendar());
		var capture = ArgumentCaptor.forClass(CalendarEvent.class);
		verify(events).save(capture.capture());
		CalendarEvent event = capture.getValue();
		assertSame(school, event.getCategory());
		assertNull(event.getOwnerUser());
		assertEquals(CalendarEventSourceType.CRAWLED, event.getSourceType());
		assertEquals(HknuAcademicCalendarCrawler.SOURCE_NAME, event.getSourceName());
		assertEquals(HknuAcademicCalendarCrawler.SOURCE_URL, event.getSourceUrl());
		assertNotNull(event.getCollectedAt());
		assertTrue(event.isAllDay());
		assertNull(event.getStartTime());
		assertNull(event.getEndTime());
		assertEquals(item.startDate(), event.getStartDate());
		assertEquals(item.endDate(), event.getEndDate());
		verify(transactions, times(2)).commit(status);
	}

	@Test
	void missingSchoolAbortsWithoutCreatingCategory() {
		when(crawler.crawl()).thenReturn(List.of(item));
		when(categories.findSchoolForCrawling()).thenReturn(Optional.empty());
		assertThrows(IllegalStateException.class, service::crawlAcademicCalendar);
		verify(categories, never()).save(any());
		verifyNoInteractions(events);
		verify(transactions).rollback(status);
	}

	@Test
	void failedFetchDoesNotStartStorage() {
		when(crawler.crawl()).thenThrow(new IllegalStateException("network failure"));
		assertThrows(IllegalStateException.class, service::crawlAcademicCalendar);
		verifyNoInteractions(categories, events);
		verify(transactions, never()).getTransaction(any());
	}
}
