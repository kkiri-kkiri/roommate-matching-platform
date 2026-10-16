package com.kkirikkiri.calendar.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import com.kkirikkiri.calendar.entity.ScheduleCrawlCategory;
import com.kkirikkiri.calendar.entity.ScheduleCrawlRun;
import com.kkirikkiri.calendar.entity.ScheduleCrawlStatus;

public interface ScheduleCrawlRunRepository extends JpaRepository<ScheduleCrawlRun, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from ScheduleCrawlRun r where r.crawlRunId = :id")
	Optional<ScheduleCrawlRun> findByIdForUpdate(@Param("id") Long id);

	/** 최근 실행의 상태, 시작 시각, 수집 건수를 조회한다. 동일 시각이면 ID 내림차순으로 결정한다. */
	Optional<ScheduleCrawlRun> findFirstByCategoryOrderByStartedAtDescCrawlRunIdDesc(
			ScheduleCrawlCategory category);

	/**
	 * COMPLETED는 최근 성공 완료 시각, FAILED는 최근 실패 시각과 사유 조회에 사용한다.
	 * 실행 시작 순서와 완료 순서가 다를 수 있으므로 완료 시각을 기준으로 조회한다.
	 */
	Optional<ScheduleCrawlRun> findFirstByCategoryAndStatusOrderByCompletedAtDescCrawlRunIdDesc(
			ScheduleCrawlCategory category, ScheduleCrawlStatus status);
}
