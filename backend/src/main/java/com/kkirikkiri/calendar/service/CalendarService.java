package com.kkirikkiri.calendar.service;

import java.time.LocalTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kkirikkiri.calendar.dto.CalendarCategoryCreateRequest;
import com.kkirikkiri.calendar.dto.CalendarCategoryResponse;
import com.kkirikkiri.calendar.dto.CalendarCategorySettingRequest;
import com.kkirikkiri.calendar.dto.CalendarCategoryUpdateRequest;
import com.kkirikkiri.calendar.dto.CalendarEventCreateRequest;
import com.kkirikkiri.calendar.dto.CalendarEventListRequest;
import com.kkirikkiri.calendar.dto.CalendarEventResponse;
import com.kkirikkiri.calendar.dto.CalendarEventUpdateRequest;
import com.kkirikkiri.calendar.entity.CalendarCategory;
import com.kkirikkiri.calendar.entity.CalendarCategorySetting;
import com.kkirikkiri.calendar.entity.CalendarEvent;
import com.kkirikkiri.calendar.entity.CalendarEventSourceType;
import com.kkirikkiri.calendar.repository.CalendarCategoryRepository;
import com.kkirikkiri.calendar.repository.CalendarCategorySettingRepository;
import com.kkirikkiri.calendar.repository.CalendarEventRepository;
import com.kkirikkiri.global.exception.CustomException;
import com.kkirikkiri.global.exception.ErrorCode;
import com.kkirikkiri.user.entity.User;
import com.kkirikkiri.user.repository.UserRepository;

/** userId에는 인증된 사용자의 ID를 전달한다. 공개 일정 조회는 비로그인 시 null을 허용한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CalendarService {

	private static final Set<String> DEFAULT_CODES = Set.of("SCHOOL", "DORMITORY", "ROOMMATE", "PERSONAL");
	// 저장된 설정이 없는 경우 기존 CalendarPageCss의 기본 색상을 사용한다.
	private static final Map<String, String> DEFAULT_COLORS = Map.of(
			"SCHOOL", "#245fc5", "DORMITORY", "#006a65", "ROOMMATE", "#7550ae", "PERSONAL", "#906016");

	private final CalendarEventRepository calendarEventRepository;
	private final CalendarCategoryRepository calendarCategoryRepository;
	private final CalendarCategorySettingRepository calendarCategorySettingRepository;
	private final UserRepository userRepository;
	private final Validator validator;

	public List<CalendarEventResponse> getEvents(Long userId, CalendarEventListRequest request) {
		if (userId != null) {
			validateUserId(userId);
		}
		validateRequest(request);
		List<CalendarEvent> events = request.categories() == null || request.categories().isEmpty()
				? calendarEventRepository.findVisibleEvents(userId, request.from(), request.to())
				: calendarEventRepository.findVisibleEventsByCategoryCodes(
						userId, request.from(), request.to(), request.categories());
		return events.stream().map(event -> CalendarEventResponse.from(event, isEditable(event, userId))).toList();
	}

	@Transactional
	public CalendarEventResponse createEvent(Long userId, CalendarEventCreateRequest request) {
		User user = findUser(userId);
		validateRequest(request);
		CalendarCategory category = findAvailableCategory(userId, request.categoryId());
		validateEventCategory(category);
		LocalTime start = parseTime(request.startTime());
		LocalTime end = parseTime(request.endTime());
		validateTimeRange(start, end);
		CalendarEvent event = new CalendarEvent(user, category, request.title(), request.date(),
				start, end, start == null && end == null, request.description(),
				CalendarEventSourceType.USER, null, null, null);
		return CalendarEventResponse.from(calendarEventRepository.save(event), true);
	}

	@Transactional
	public CalendarEventResponse updateEvent(Long userId, Long eventId, CalendarEventUpdateRequest request) {
		validateUserId(userId);
		validateRequest(request);
		CalendarEvent event = findEditableEvent(userId, eventId);
		CalendarCategory category = findAvailableCategory(userId,
				request.categoryId() == null ? event.getCategory().getCategoryId() : request.categoryId());
		validateEventCategory(category);
		LocalTime start = request.startTime() == null ? event.getStartTime() : parseTime(request.startTime());
		LocalTime end = request.endTime() == null ? event.getEndTime() : parseTime(request.endTime());
		validateTimeRange(start, end);
		event.update(category, request.title() == null ? event.getTitle() : request.title(),
				request.date() == null ? event.getEventDate() : request.date(),
				start, end, start == null && end == null,
				request.description() == null ? event.getDescription() : request.description());
		return CalendarEventResponse.from(event, true);
	}

	@Transactional
	public void deleteEvent(Long userId, Long eventId) {
		validateUserId(userId);
		calendarEventRepository.delete(findEditableEvent(userId, eventId));
	}

	/** 미저장 카테고리도 기본 표시값으로 반환하며 조회 중에는 설정을 생성하지 않는다. */
	public List<CalendarCategoryResponse> getCategories(Long userId) {
		validateUserId(userId);
		return List.copyOf(categoryResponses(userId).values());
	}

	/** 요청에 포함된 설정을 저장한다. 생략된 설정은 유지하며 최종 전체 이름의 중복을 검증한다. */
	@Transactional
	public List<CalendarCategoryResponse> saveCategorySettings(Long userId,
			List<CalendarCategorySettingRequest> requests) {
		User user = findUser(userId);
		if (requests == null) {
			throw new CustomException(ErrorCode.VALIDATION_ERROR);
		}
		Map<Long, CalendarCategoryResponse> responses = categoryResponses(userId);
		Map<Long, CalendarCategory> categories = new LinkedHashMap<>();
		Set<Long> ids = new HashSet<>();
		for (CalendarCategorySettingRequest request : requests) {
			validateRequest(request);
			if (!ids.add(request.id())) {
				throw new CustomException(ErrorCode.VALIDATION_ERROR);
			}
			CalendarCategory category = findAvailableCategory(userId, request.id());
			validateCategoryName(category, request.label());
			categories.put(request.id(), category);
			responses.put(request.id(), new CalendarCategoryResponse(request.id(), category.getCategoryCode(),
					request.label(), request.color(), category.isNameLocked(), request.enabled()));
		}
		validateUniqueLabels(responses);
		for (CalendarCategorySettingRequest request : requests) {
			CalendarCategory category = categories.get(request.id());
			CalendarCategorySetting setting = calendarCategorySettingRepository
					.findByUser_UserIdAndCategory_CategoryId(userId, request.id()).orElse(null);
			if (setting == null) {
				calendarCategorySettingRepository.save(new CalendarCategorySetting(
						user, category, request.label(), request.color(), request.enabled()));
			} else {
				setting.changeSettings(request.label(), request.color(), request.enabled());
			}
		}
		return List.copyOf(responses.values());
	}

	@Transactional
	public CalendarCategoryResponse createCategory(Long userId, CalendarCategoryCreateRequest request) {
		User user = findUser(userId);
		validateRequest(request);
		validateAvailableLabel(userId, null, request.label());
		CalendarCategory category = calendarCategoryRepository.save(new CalendarCategory(
				"CUSTOM-" + UUID.randomUUID(), request.label(), user, false, false));
		CalendarCategorySetting setting = calendarCategorySettingRepository.save(new CalendarCategorySetting(
				user, category, request.label(), request.color(), true));
		return CalendarCategoryResponse.from(setting);
	}

	@Transactional
	public CalendarCategoryResponse updateCategory(Long userId, Long categoryId,
			CalendarCategoryUpdateRequest request) {
		validateUserId(userId);
		validateRequest(request);
		CalendarCategory category = findOwnedCustomCategory(userId, categoryId);
		CalendarCategorySetting setting = calendarCategorySettingRepository
				.findByUser_UserIdAndCategory_CategoryId(userId, categoryId)
				.orElseThrow(() -> new CustomException(ErrorCode.INVALID_REQUEST));
		String label = request.label() == null ? setting.getDisplayName() : request.label();
		validateCategoryName(category, label);
		validateAvailableLabel(userId, categoryId, label);
		if (request.label() != null) {
			category.changeName(label);
		}
		setting.changeSettings(label, request.color() == null ? setting.getColor() : request.color(),
				request.enabled() == null ? setting.isEnabled() : request.enabled());
		return CalendarCategoryResponse.from(setting);
	}

	@Transactional
	public void deleteCategory(Long userId, Long categoryId) {
		validateUserId(userId);
		CalendarCategory category = findOwnedCustomCategory(userId, categoryId);
		if (calendarEventRepository.existsByCategory_CategoryId(categoryId)) {
			throw new CustomException(ErrorCode.INVALID_REQUEST);
		}
		calendarCategorySettingRepository.deleteByCategory_CategoryId(categoryId);
		calendarCategorySettingRepository.flush();
		calendarCategoryRepository.delete(category);
	}

	private Map<Long, CalendarCategoryResponse> categoryResponses(Long userId) {
		Map<Long, CalendarCategorySetting> settings = new LinkedHashMap<>();
		calendarCategorySettingRepository.findAllByUser_UserId(userId)
				.forEach(setting -> settings.put(setting.getCategory().getCategoryId(), setting));
		Map<Long, CalendarCategoryResponse> responses = new LinkedHashMap<>();
		for (CalendarCategory category : calendarCategoryRepository.findAllByOwnerUserIsNullOrOwnerUser_UserId(userId)) {
			CalendarCategorySetting setting = settings.get(category.getCategoryId());
			responses.put(category.getCategoryId(), setting == null
					? new CalendarCategoryResponse(category.getCategoryId(), category.getCategoryCode(),
							category.getDefaultName(), DEFAULT_COLORS.getOrDefault(category.getCategoryCode(), "#906016"),
							category.isNameLocked(), true)
					: CalendarCategoryResponse.from(setting));
		}
		return responses;
	}

	private void validateAvailableLabel(Long userId, Long categoryId, String label) {
		boolean duplicate = categoryResponses(userId).values().stream()
				.anyMatch(response -> !response.id().equals(categoryId) && response.label().equals(label));
		if (duplicate) {
			throw new CustomException(ErrorCode.VALIDATION_ERROR);
		}
	}

	private void validateUniqueLabels(Map<Long, CalendarCategoryResponse> responses) {
		Set<String> labels = new HashSet<>();
		for (CalendarCategoryResponse response : responses.values()) {
			if (!labels.add(response.label())) {
				throw new CustomException(ErrorCode.VALIDATION_ERROR);
			}
		}
	}

	private CalendarCategory findAvailableCategory(Long userId, Long categoryId) {
		validateId(categoryId);
		CalendarCategory category = calendarCategoryRepository.findById(categoryId)
				.orElseThrow(() -> new CustomException(ErrorCode.INVALID_REQUEST));
		if (category.getOwnerUser() != null && !userId.equals(category.getOwnerUser().getUserId())) {
			throw new CustomException(ErrorCode.FORBIDDEN);
		}
		return category;
	}

	private CalendarCategory findOwnedCustomCategory(Long userId, Long categoryId) {
		CalendarCategory category = findAvailableCategory(userId, categoryId);
		if (category.getOwnerUser() == null || category.isSystem() || category.isNameLocked()
				|| DEFAULT_CODES.contains(category.getCategoryCode()) || !category.getCategoryCode().startsWith("CUSTOM-")) {
			throw new CustomException(ErrorCode.FORBIDDEN);
		}
		return category;
	}

	private CalendarEvent findEditableEvent(Long userId, Long eventId) {
		validateId(eventId);
		CalendarEvent event = calendarEventRepository.findById(eventId)
				.orElseThrow(() -> new CustomException(ErrorCode.INVALID_REQUEST));
		if (!isEditable(event, userId)) {
			throw new CustomException(ErrorCode.FORBIDDEN);
		}
		return event;
	}

	private boolean isEditable(CalendarEvent event, Long userId) {
		return userId != null && event.getOwnerUser() != null
				&& userId.equals(event.getOwnerUser().getUserId())
				&& event.getSourceType() == CalendarEventSourceType.USER
				&& !isCollectedCategory(event.getCategory());
	}

	private boolean isCollectedCategory(CalendarCategory category) {
		return "SCHOOL".equals(category.getCategoryCode()) || "DORMITORY".equals(category.getCategoryCode());
	}

	private void validateEventCategory(CalendarCategory category) {
		if (isCollectedCategory(category)) {
			throw new CustomException(ErrorCode.FORBIDDEN);
		}
	}

	private void validateCategoryName(CalendarCategory category, String label) {
		if ((category.isNameLocked() || isCollectedCategory(category)) && !category.getDefaultName().equals(label)) {
			throw new CustomException(ErrorCode.VALIDATION_ERROR);
		}
	}

	private LocalTime parseTime(String value) {
		return value == null ? null : LocalTime.parse(value);
	}

	private void validateTimeRange(LocalTime start, LocalTime end) {
		if (start != null && end != null && !end.isAfter(start)) {
			throw new CustomException(ErrorCode.VALIDATION_ERROR);
		}
	}

	private User findUser(Long userId) {
		validateUserId(userId);
		return userRepository.findById(userId).orElseThrow(() -> new CustomException(ErrorCode.UNAUTHORIZED));
	}

	private void validateUserId(Long userId) {
		if (userId == null || userId <= 0) {
			throw new CustomException(ErrorCode.UNAUTHORIZED);
		}
	}

	private void validateId(Long id) {
		if (id == null || id <= 0) {
			throw new CustomException(ErrorCode.INVALID_REQUEST);
		}
	}

	private void validateRequest(Object request) {
		if (request == null || !validator.validate(request).isEmpty()) {
			throw new CustomException(ErrorCode.VALIDATION_ERROR);
		}
	}
}
