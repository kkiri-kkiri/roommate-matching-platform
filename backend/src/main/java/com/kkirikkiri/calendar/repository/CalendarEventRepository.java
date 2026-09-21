package com.kkirikkiri.calendar.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kkirikkiri.calendar.entity.CalendarEvent;

public interface CalendarEventRepository extends JpaRepository<CalendarEvent, Long> {

	/** 시작일과 종료일을 포함하여 공용 크롤링 일정과 본인 일정을 조회한다. */
	@Query("""
			select e from CalendarEvent e
			join fetch e.category c
			left join e.ownerUser owner
			where e.eventDate between :from and :to
			  and ((e.ownerUser is null
			        and e.sourceType = com.kkirikkiri.calendar.entity.CalendarEventSourceType.CRAWLED)
			       or owner.userId = :userId)
			order by e.eventDate, e.startTime, e.eventId
			""")
	List<CalendarEvent> findVisibleEvents(@Param("userId") Long userId,
			@Param("from") LocalDate from, @Param("to") LocalDate to);

	/** categoryCodes는 비어 있지 않은 실제 코드(SCHOOL, CUSTOM-* 등) 목록이다. */
	@Query("""
			select e from CalendarEvent e
			join fetch e.category c
			left join e.ownerUser owner
			where e.eventDate between :from and :to
			  and ((e.ownerUser is null
			        and e.sourceType = com.kkirikkiri.calendar.entity.CalendarEventSourceType.CRAWLED)
			       or owner.userId = :userId)
			  and c.categoryCode in :categoryCodes
			order by e.eventDate, e.startTime, e.eventId
			""")
	List<CalendarEvent> findVisibleEventsByCategoryCodes(@Param("userId") Long userId,
			@Param("from") LocalDate from, @Param("to") LocalDate to,
			@Param("categoryCodes") List<String> categoryCodes);

	/** 수정/삭제 대상의 소유권을 제한한다. 크롤링 여부 검증은 서비스에서 처리한다. */
	Optional<CalendarEvent> findByEventIdAndOwnerUser_UserId(Long eventId, Long userId);

	boolean existsByCategory_CategoryId(Long categoryId);
}
