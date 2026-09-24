package com.kkirikkiri.admin.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.kkirikkiri.admin.entity.ScheduleCrawlCategory;
import com.kkirikkiri.calendar.entity.CalendarEvent;

@Repository
@Transactional(readOnly = true)
public class AdminCrawledScheduleQueryRepository extends AdminQuerySupport {
	public AdminCrawledScheduleQueryRepository(EntityManager entityManager) {
		super(entityManager);
	}

	private static final String FROM = " from CalendarEvent e join e.category c ";
	private static final String SELECT = "select e from CalendarEvent e join fetch e.category c ";
	private static final String CRAWLED = """
			where e.sourceType = com.kkirikkiri.calendar.entity.CalendarEventSourceType.CRAWLED
			  and e.ownerUser is null and c.categoryCode in ('SCHOOL', 'DORMITORY')
			""";
	private static final String CATEGORY = " and (:category is null or c.categoryCode = :category) ";
	private static final String FILTER = CRAWLED + CATEGORY + """
			and (:q is null or locate(:q, e.title) > 0 or locate(:q, e.description) > 0
			     or locate(:q, e.sourceName) > 0)
			""";

	public Page<CalendarEvent> searchSchedules(String q, ScheduleCrawlCategory category, Pageable pageable) {
		return page(SELECT + FILTER + "order by e.collectedAt desc, e.eventId desc",
				"select count(e)" + FROM + FILTER, CalendarEvent.class,
				parameters("q", keyword(q), "category", category == null ? null : category.name()), pageable);
	}

	/** SCHOOL 또는 DORMITORY별 집계. null이면 두 카테고리의 합계를 반환한다. */
	public long countSchedules(ScheduleCrawlCategory category) {
		return count("select count(e)" + FROM + CRAWLED + CATEGORY,
				parameters("category", category == null ? null : category.name()));
	}

	/** 수집 시각 기준 최근 3건. category가 null이면 두 카테고리를 함께 조회한다. */
	public List<CalendarEvent> findRecentSchedules(ScheduleCrawlCategory category) {
		return recent(SELECT + CRAWLED + CATEGORY + "order by e.collectedAt desc, e.eventId desc",
				CalendarEvent.class, parameters("category", category == null ? null : category.name()));
	}

	public Optional<CalendarEvent> findScheduleById(Long scheduleId) {
		return one(SELECT + CRAWLED + "and e.eventId = :id", CalendarEvent.class, parameters("id", scheduleId));
	}
}
