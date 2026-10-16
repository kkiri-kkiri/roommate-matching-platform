package com.kkirikkiri.calendar.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.Objects;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.kkirikkiri.calendar.crawler.HknuAcademicCalendarCrawler;
import com.kkirikkiri.calendar.entity.CalendarEvent;
import com.kkirikkiri.calendar.entity.CalendarEventSourceType;
import com.kkirikkiri.calendar.entity.ScheduleCrawlCategory;
import com.kkirikkiri.calendar.entity.ScheduleCrawlRun;
import com.kkirikkiri.calendar.entity.ScheduleCrawlStatus;
import com.kkirikkiri.calendar.repository.CalendarCategoryRepository;
import com.kkirikkiri.calendar.repository.CalendarEventRepository;
import com.kkirikkiri.calendar.repository.ScheduleCrawlRunRepository;

@Slf4j
@Service
public class CalendarCrawlingService {

	private final HknuAcademicCalendarCrawler crawler;
	private final CalendarCategoryRepository categories;
	private final CalendarEventRepository events;
	private final ScheduleCrawlRunRepository runs;
	private final TransactionTemplate transaction;
	private final TransactionTemplate history;

	public CalendarCrawlingService(HknuAcademicCalendarCrawler crawler, CalendarCategoryRepository categories,
			CalendarEventRepository events, ScheduleCrawlRunRepository runs, PlatformTransactionManager transactionManager) {
		this.crawler = crawler;
		this.categories = categories;
		this.events = events;
		this.runs = runs;
		this.transaction = new TransactionTemplate(transactionManager);
		this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
		this.history = new TransactionTemplate(transactionManager);
		this.history.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	/** 시작/실패는 독립 커밋, 일정과 성공 이력은 하나의 커밋으로 확정한다. */
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public int crawlAcademicCalendar() {
		Long runId = Objects.requireNonNull(history.execute(status -> runs.save(
				new ScheduleCrawlRun(ScheduleCrawlCategory.SCHOOL, now())).getCrawlRunId()));
		String phase = "ACADEMIC_CALENDAR_FETCH_FAILED";
		try {
			var collected = new LinkedHashSet<>(crawler.crawl());
			if (collected.isEmpty()) {
				throw new IllegalStateException("No academic events collected");
			}
			phase = "ACADEMIC_CALENDAR_STORE_FAILED";
			LocalDateTime collectedAt = now();
			return Objects.requireNonNull(transaction.execute(status -> {
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
				var run = runs.findByIdForUpdate(runId).orElseThrow();
				run.complete(inserted, completionTime(run));
				// Neither the events nor COMPLETED can be observed unless this transaction commits.
				return inserted;
			}));
		} catch (RuntimeException failure) {
			String reason = phase;
			try {
				history.executeWithoutResult(status -> {
					var run = runs.findByIdForUpdate(runId).orElseThrow();
					// A lost commit acknowledgement must not overwrite an already committed success.
					if (run.getStatus() == ScheduleCrawlStatus.RUNNING) {
						run.fail(0, reason, completionTime(run));
					}
				});
			} catch (RuntimeException historyFailure) {
				log.error("Calendar crawl history unavailable: runId={} exceptionType={}",
						runId, historyFailure.getClass().getSimpleName());
				failure.addSuppressed(historyFailure);
			}
			log.error("Calendar crawl failed: runId={} reason={} exceptionType={}",
					runId, reason, failure.getClass().getSimpleName());
			throw failure;
		}
	}

	private LocalDateTime now() {
		return LocalDateTime.now(ZoneId.of("Asia/Seoul"));
	}

	private LocalDateTime completionTime(ScheduleCrawlRun run) {
		LocalDateTime completed = now();
		return completed.isBefore(run.getStartedAt()) ? run.getStartedAt() : completed;
	}
}
