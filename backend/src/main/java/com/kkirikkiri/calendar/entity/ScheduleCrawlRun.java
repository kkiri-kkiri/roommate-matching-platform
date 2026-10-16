package com.kkirikkiri.calendar.entity;

import java.time.LocalDateTime;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 관리자 수집 현황에서 사용하는 실행 이력. 수집 일정과의 FK는 ERD에 정의되어 있지 않다. */
@Entity
@Table(name = "SCHEDULE_CRAWL_RUN")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScheduleCrawlRun {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "crawl_run_id")
	private Long crawlRunId;

	@Enumerated(EnumType.STRING)
	@Column(name = "category", nullable = false, length = 20, columnDefinition = "VARCHAR(20)")
	private ScheduleCrawlCategory category;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20, columnDefinition = "VARCHAR(20)")
	private ScheduleCrawlStatus status;

	@Column(name = "collected_count", nullable = false)
	private int collectedCount;

	@Column(name = "error_message", nullable = true, columnDefinition = "TEXT")
	private String errorMessage;

	@Column(name = "started_at", nullable = false, updatable = false, columnDefinition = "DATETIME")
	@JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
	private LocalDateTime startedAt;

	@Column(name = "completed_at", nullable = true, columnDefinition = "DATETIME")
	@JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
	private LocalDateTime completedAt;

	/** 저장 시각 대신 실제 크롤링 시작 시각을 전달한다. */
	public ScheduleCrawlRun(ScheduleCrawlCategory category, LocalDateTime startedAt) {
		this.category = Objects.requireNonNull(category, "category");
		this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
		this.status = ScheduleCrawlStatus.RUNNING;
	}

	public void complete(int collectedCount, LocalDateTime completedAt) {
		validateCompletion(collectedCount, completedAt);
		this.collectedCount = collectedCount;
		this.completedAt = completedAt;
		this.status = ScheduleCrawlStatus.COMPLETED;
	}

	/** 실제 커밋된 일정 수와 실패 시각을 기록한다. 현재 수집은 전체 롤백하므로 실패 건수는 0이다. */
	public void fail(int collectedCount, String errorMessage, LocalDateTime completedAt) {
		validateCompletion(collectedCount, completedAt);
		this.collectedCount = collectedCount;
		this.errorMessage = errorMessage;
		this.completedAt = completedAt;
		this.status = ScheduleCrawlStatus.FAILED;
	}

	private void validateCompletion(int collectedCount, LocalDateTime completedAt) {
		if (status != ScheduleCrawlStatus.RUNNING) {
			throw new IllegalStateException("only running crawls can be completed");
		}
		if (collectedCount < 0) {
			throw new IllegalArgumentException("collectedCount cannot be negative");
		}
		Objects.requireNonNull(completedAt, "completedAt");
		if (completedAt.isBefore(startedAt)) {
			throw new IllegalArgumentException("completedAt cannot be before startedAt");
		}
	}
}
