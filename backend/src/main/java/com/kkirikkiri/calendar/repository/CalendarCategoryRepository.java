package com.kkirikkiri.calendar.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kkirikkiri.calendar.entity.CalendarCategory;

public interface CalendarCategoryRepository extends JpaRepository<CalendarCategory, Long> {

	/** 사용자 설정이 아직 없는 기본 카테고리도 포함한다. */
	List<CalendarCategory> findAllByOwnerUserIsNullOrOwnerUser_UserId(Long userId);

	/** 사용자 카테고리 수정/삭제용 조회이며 기본 카테고리 제한은 서비스에서 검증한다. */
	Optional<CalendarCategory> findByCategoryIdAndOwnerUser_UserId(Long categoryId, Long userId);
}
