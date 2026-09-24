package com.kkirikkiri.calendar.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

import com.kkirikkiri.calendar.entity.CalendarCategory;

public interface CalendarCategoryRepository extends JpaRepository<CalendarCategory, Long> {

	/** 수집 작업 간 중복 저장을 막기 위해 기존 SCHOOL 시스템 행을 잠근다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from CalendarCategory c where c.categoryCode = 'SCHOOL' and c.ownerUser is null and c.system = true")
	Optional<CalendarCategory> findSchoolForCrawling();

	/** 사용자 설정이 아직 없는 기본 카테고리도 포함한다. */
	List<CalendarCategory> findAllByOwnerUserIsNullOrOwnerUser_UserId(Long userId);

	/** 사용자 카테고리 수정/삭제용 조회이며 기본 카테고리 제한은 서비스에서 검증한다. */
	Optional<CalendarCategory> findByCategoryIdAndOwnerUser_UserId(Long categoryId, Long userId);
}
