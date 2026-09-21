package com.kkirikkiri.calendar.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kkirikkiri.calendar.entity.CalendarCategorySetting;

public interface CalendarCategorySettingRepository extends JpaRepository<CalendarCategorySetting, Long> {

	/** 일괄 저장 시 요청 내 중복과 변경 후 이름 중복은 서비스에서 검증한다. */
	List<CalendarCategorySetting> findAllByUser_UserId(Long userId);

	Optional<CalendarCategorySetting> findByUser_UserIdAndCategory_CategoryId(Long userId, Long categoryId);

	boolean existsByUser_UserIdAndDisplayName(Long userId, String displayName);

	boolean existsByUser_UserIdAndDisplayNameAndCategory_CategoryIdNot(
			Long userId, String displayName, Long categoryId);

	/** 소유권과 일정 존재 여부를 검증한 후 서비스 트랜잭션에서 카테고리보다 먼저 삭제한다. */
	void deleteByCategory_CategoryId(Long categoryId);
}
