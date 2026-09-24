package com.kkirikkiri.calendar.crawler;

import java.time.LocalDate;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HknuAcademicCalendarCrawlerTest {

	private final HknuAcademicCalendarCrawler crawler = new HknuAcademicCalendarCrawler();

	@Test
	void parsesCapturedAnnualHtmlIncludingMonthAndYearBoundaries() throws Exception {
		try (var html = getClass().getResourceAsStream("/calendar/hknu-2026.html")) {
			assertNotNull(html);
			var events = crawler.parse(Jsoup.parse(html, "UTF-8", HknuAcademicCalendarCrawler.SOURCE_URL));
			assertEquals(62, events.size());
			assertTrue(events.contains(event("1학기 개강", "2026-03-03", "2026-03-03")));
			assertTrue(events.contains(event("2차(최종) 복학 신청", "2026-03-03", "2026-03-20")));
			assertTrue(events.contains(event("1차 수강철회 신청", "2026-03-30", "2026-04-03")));
			assertTrue(events.contains(event("1차 복학 신청", "2026-12-22", "2027-02-26")));
			assertTrue(events.contains(event("동계 계절수업", "2026-12-22", "2027-01-13")));
		}
	}

	@Test
	void rejectsChangedStructureAndEmptyCalendar() {
		assertThrows(IllegalStateException.class, () -> crawler.parse(Jsoup.parse("<p>error</p>")));
		assertThrows(IllegalStateException.class, () -> crawler.parse(Jsoup.parse(
				"<div id='timeTableList'><ul><li><p class='box-year'>2026</p></li></ul></div>")));
	}

	@Test
	void rejectsMalformedAndReversedDatesRatherThanReturningPartialResults() {
		for (String period : new String[] {"03.20 ~ 03-03", "02.30", "TBD", "03.03 ~"}) {
			assertThrows(RuntimeException.class, () -> crawler.parse(Jsoup.parse("""
					<div id="timeTableList"><ul><li><p class="box-year">2026</p>
					<div class="list-inner"><div class="list-box">
					<p class="list-date">%s</p><p class="list-content">test</p>
					</div></div></li></ul></div>
					""".formatted(period))));
		}
	}

	private CrawledAcademicEvent event(String title, String start, String end) {
		return new CrawledAcademicEvent(title, LocalDate.parse(start), LocalDate.parse(end));
	}
}
