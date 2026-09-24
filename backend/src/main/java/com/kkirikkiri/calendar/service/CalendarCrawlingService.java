package com.kkirikkiri.calendar.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashSet;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.kkirikkiri.calendar.crawler.HknuAcademicCalendarCrawler;
import com.kkirikkiri.calendar.entity.CalendarEvent;
import com.kkirikkiri.calendar.entity.CalendarEventSourceType;
import com.kkirikkiri.calendar.repository.CalendarCategoryRepository;
import com.kkirikkiri.calendar.repository.CalendarEventRepository;

@Service
public class CalendarCrawlingService {

	private final HknuAcademicCalendarCrawler crawler;
	private final CalendarCategoryRepository categories;
	private final CalendarEventRepository events;
	private final TransactionTemplate transaction;

	public CalendarCrawlingService(HknuAcademicCalendarCrawler crawler, CalendarCategoryRepository categories,
			CalendarEventRepository events, PlatformTransactionManager transactionManager) {
		this.crawler = crawler;
		this.categories = categories;
		this.events = events;
		this.transaction = new TransactionTemplate(transactionManager);
		this.transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
	}

	/** HTTP 요청과 파싱을 마친 뒤 저장 트랜잭션을 시작한다. 반환값은 신규 저장 건수다. */
	public int crawlAcademicCalendar() {
		var collected = new LinkedHashSet<>(crawler.crawl());
		if (collected.isEmpty()) {
			throw new IllegalStateException("No academic events collected");
		}
		LocalDateTime collectedAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
		return transaction.execute(status -> {
			var school = categories.findSchoolForCrawling()
					.orElseThrow(() -> new IllegalStateException("SCHOOL system category is missing; crawling aborted"));
			int inserted = 0;
			for (var item : collected) {
				if (events.existsBySourceTypeAndSourceNameAndTitleAndStartDateAndEndDate(
						CalendarEventSourceType.CRAWLED, HknuAcademicCalendarCrawler.SOURCE_NAME,
						item.title(), item.startDate(), item.endDate())) {
					continue;
				}
				events.save(new CalendarEvent(null, school, item.title(), item.startDate(), item.endDate(),
						null, null, true, null, CalendarEventSourceType.CRAWLED,
						HknuAcademicCalendarCrawler.SOURCE_NAME, HknuAcademicCalendarCrawler.SOURCE_URL, collectedAt));
				inserted++;
			}
			return inserted;
		});
	}
}
