package com.kkirikkiri.calendar.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.kkirikkiri.calendar.crawler.CrawledAcademicEvent;
import com.kkirikkiri.calendar.crawler.HknuAcademicCalendarCrawler;
import com.kkirikkiri.calendar.entity.CalendarCategory;
import com.kkirikkiri.calendar.entity.CalendarEvent;
import com.kkirikkiri.calendar.entity.CalendarEventSourceType;
import com.kkirikkiri.calendar.repository.CalendarCategoryRepository;
import com.kkirikkiri.calendar.repository.CalendarEventRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.when;

/** 프로젝트의 테스트 DB를 사용하며 모든 테스트 데이터는 롤백한다. */
@SpringBootTest
@Transactional(isolation = Isolation.READ_COMMITTED)
class CalendarRepositoryIntegrationTest {

	@Autowired private CalendarCategoryRepository categories;
	@Autowired private CalendarEventRepository events;
	@Autowired private CalendarCrawlingService crawlingService;
	@MockitoBean private HknuAcademicCalendarCrawler crawler;

	@Test
	void queriesOverlappingPeriodsIncludingBothBoundariesAndCategoryFilter() {
		String code = "CUSTOM-" + UUID.randomUUID();
		var category = categories.saveAndFlush(new CalendarCategory(code, "조회 테스트", null, false, false));
		LocalDate start = LocalDate.of(2026, 3, 3);
		LocalDate end = LocalDate.of(2026, 3, 20);
		var event = events.saveAndFlush(new CalendarEvent(null, category, "조회 테스트", start, end,
				null, null, true, null, CalendarEventSourceType.CRAWLED, "테스트", null, null));
		for (LocalDate day : List.of(start, start.plusDays(5), end)) {
			assertTrue(events.findVisibleEvents(null, day, day).stream()
					.anyMatch(found -> found.getEventId().equals(event.getEventId())));
			assertEquals(List.of(event), events.findVisibleEventsByCategoryCodes(null, day, day, List.of(code)));
		}
		assertEquals(List.of(event), events.findVisibleEventsByCategoryCodes(
				null, start.minusDays(5), end.plusDays(5), List.of(code)));
		assertTrue(events.findVisibleEventsByCategoryCodes(null, start.minusDays(1), start.minusDays(1), List.of(code)).isEmpty());
		assertTrue(events.findVisibleEventsByCategoryCodes(null, end.plusDays(1), end.plusDays(1), List.of(code)).isEmpty());
	}

	@Test
	void crawlsTwiceWithoutDuplicateRowsWhenExistingSchoolIsAvailable() {
		var school = categories.findSchoolForCrawling();
		assumeTrue(school.isPresent(), "SCHOOL system category is missing; no category was created");
		String title = "수집 테스트 " + UUID.randomUUID();
		LocalDate start = LocalDate.of(2026, 12, 22);
		LocalDate end = LocalDate.of(2027, 2, 26);
		when(crawler.crawl()).thenReturn(List.of(new CrawledAcademicEvent(title, start, end)));
		assertEquals(1, crawlingService.crawlAcademicCalendar());
		assertEquals(0, crawlingService.crawlAcademicCalendar());
		var found = events.findVisibleEventsByCategoryCodes(null, start, end, List.of("SCHOOL")).stream()
				.filter(event -> event.getTitle().equals(title)).toList();
		assertEquals(1, found.size());
		assertEquals(school.get().getCategoryId(), found.getFirst().getCategory().getCategoryId());
		assertEquals(CalendarEventSourceType.CRAWLED, found.getFirst().getSourceType());
	}
}
