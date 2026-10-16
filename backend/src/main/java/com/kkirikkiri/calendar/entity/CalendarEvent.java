package com.kkirikkiri.calendar.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.kkirikkiri.user.entity.User;

@Entity
@Table(name = "CALENDAR_EVENT", indexes = {
		@Index(name = "idx_calendar_crawl_identity", columnList = "source_type,source_name,title,start_date,end_date"),
		@Index(name = "idx_calendar_period", columnList = "end_date,start_date,owner_user_id,source_type")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CalendarEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "event_id")
	private Long eventId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "owner_user_id", nullable = true)
	private User ownerUser;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "category_id", nullable = false)
	private CalendarCategory category;

	@Column(name = "title", nullable = false, length = 80)
	private String title;

	@Column(name = "start_date", nullable = false, columnDefinition = "DATE")
	@JdbcTypeCode(SqlTypes.LOCAL_DATE)
	private LocalDate startDate;

	@Column(name = "end_date", nullable = false, columnDefinition = "DATE")
	@JdbcTypeCode(SqlTypes.LOCAL_DATE)
	private LocalDate endDate;

	@Column(name = "start_time", nullable = true, columnDefinition = "TIME")
	@JdbcTypeCode(SqlTypes.LOCAL_TIME)
	private LocalTime startTime;

	@Column(name = "end_time", nullable = true, columnDefinition = "TIME")
	@JdbcTypeCode(SqlTypes.LOCAL_TIME)
	private LocalTime endTime;

	@Column(name = "is_all_day", nullable = false, columnDefinition = "BOOLEAN")
	private boolean allDay;

	@Column(name = "description", nullable = true, columnDefinition = "TEXT")
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(name = "source_type", nullable = false, length = 20, columnDefinition = "VARCHAR(20)")
	private CalendarEventSourceType sourceType;

	@Column(name = "source_name", nullable = true, length = 255)
	private String sourceName;

	@Column(name = "source_url", nullable = true, length = 500)
	private String sourceUrl;

	@Column(name = "collected_at", nullable = true, columnDefinition = "DATETIME")
	@JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
	private LocalDateTime collectedAt;

	/** 카테고리 접근 권한과 입력값은 서비스에서 검증한다. 크롤링 일정의 소유자는 null이다. */
	public CalendarEvent(User ownerUser, CalendarCategory category, String title, LocalDate startDate, LocalDate endDate,
			LocalTime startTime, LocalTime endTime, boolean allDay, String description,
			CalendarEventSourceType sourceType, String sourceName, String sourceUrl, LocalDateTime collectedAt) {
		this.sourceType = Objects.requireNonNull(sourceType, "sourceType");
		this.ownerUser = sourceType == CalendarEventSourceType.USER
				? Objects.requireNonNull(ownerUser, "ownerUser") : null;
		applyDetails(category, title, startDate, endDate, startTime, endTime, allDay, description);
		this.sourceName = sourceName;
		this.sourceUrl = sourceUrl;
		this.collectedAt = collectedAt;
	}

	/**
	 * 서비스에서 소유권과 카테고리를 검증하고 PATCH의 미지정 값을 병합한 전체 값을 전달한다.
	 * nullable 필드에 null을 전달하면 기존 값을 제거한다.
	 */
	public void update(CalendarCategory category, String title, LocalDate startDate, LocalDate endDate,
			LocalTime startTime, LocalTime endTime, boolean allDay, String description) {
		if (sourceType != CalendarEventSourceType.USER) {
			throw new IllegalStateException("crawled events cannot be updated by users");
		}
		applyDetails(category, title, startDate, endDate, startTime, endTime, allDay, description);
	}

	private void applyDetails(CalendarCategory category, String title, LocalDate startDate, LocalDate endDate,
			LocalTime startTime, LocalTime endTime, boolean allDay, String description) {
		Objects.requireNonNull(category, "category");
		Objects.requireNonNull(title, "title");
		Objects.requireNonNull(startDate, "startDate");
		Objects.requireNonNull(endDate, "endDate");
		if (endDate.isBefore(startDate)) {
			throw new IllegalArgumentException("endDate cannot be before startDate");
		}
		if (sourceType == CalendarEventSourceType.USER
				&& ("SCHOOL".equals(category.getCategoryCode()) || "DORMITORY".equals(category.getCategoryCode()))) {
			throw new IllegalArgumentException("users cannot create or move events to a school or dormitory category");
		}
		this.category = category;
		this.title = title;
		this.startDate = startDate;
		this.endDate = endDate;
		this.startTime = startTime;
		this.endTime = endTime;
		this.allDay = allDay;
		this.description = description;
	}
}
