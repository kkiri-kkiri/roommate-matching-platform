package com.kkirikkiri.calendar.controller;

import java.util.List;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kkirikkiri.calendar.dto.CalendarCategoryCreateRequest;
import com.kkirikkiri.calendar.dto.CalendarCategoryResponse;
import com.kkirikkiri.calendar.dto.CalendarCategorySettingRequest;
import com.kkirikkiri.calendar.dto.CalendarCategoryUpdateRequest;
import com.kkirikkiri.calendar.dto.CalendarEventCreateRequest;
import com.kkirikkiri.calendar.dto.CalendarEventListRequest;
import com.kkirikkiri.calendar.dto.CalendarEventResponse;
import com.kkirikkiri.calendar.dto.CalendarEventUpdateRequest;
import com.kkirikkiri.calendar.service.CalendarService;
import com.kkirikkiri.global.response.ApiResponse;
import com.kkirikkiri.global.security.AuthenticatedUser;

@RestController
@RequestMapping("/api/calendar")
@RequiredArgsConstructor
@Validated
public class CalendarController {

	private final CalendarService calendarService;

	// 공개 일정 조회는 비로그인 사용자도 접근할 수 있다.
	@GetMapping("/events")
	public ApiResponse<List<CalendarEventResponse>> getEvents(
			@AuthenticationPrincipal AuthenticatedUser user,
			@Valid @ModelAttribute CalendarEventListRequest request) {
		return ApiResponse.success(calendarService.getEvents(user == null ? null : user.userId(), request));
	}

	@PostMapping("/events")
	public ApiResponse<CalendarEventResponse> createEvent(
			@AuthenticationPrincipal AuthenticatedUser user,
			@Valid @RequestBody CalendarEventCreateRequest request) {
		return ApiResponse.success(calendarService.createEvent(user.userId(), request));
	}

	@PatchMapping("/events/{eventId}")
	public ApiResponse<CalendarEventResponse> updateEvent(
			@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable("eventId") Long eventId,
			@Valid @RequestBody CalendarEventUpdateRequest request) {
		return ApiResponse.success(calendarService.updateEvent(user.userId(), eventId, request));
	}

	@DeleteMapping("/events/{eventId}")
	public ApiResponse<Void> deleteEvent(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable("eventId") Long eventId) {
		calendarService.deleteEvent(user.userId(), eventId);
		return ApiResponse.successWithoutData();
	}

	@GetMapping("/categories")
	public ApiResponse<List<CalendarCategoryResponse>> getCategories(
			@AuthenticationPrincipal AuthenticatedUser user) {
		return ApiResponse.success(calendarService.getCategories(user.userId()));
	}

	@PutMapping("/categories")
	public ApiResponse<List<CalendarCategoryResponse>> saveCategorySettings(
			@AuthenticationPrincipal AuthenticatedUser user,
			@Valid @RequestBody List<@Valid CalendarCategorySettingRequest> requests) {
		return ApiResponse.success(calendarService.saveCategorySettings(user.userId(), requests));
	}

	@PostMapping("/categories/custom")
	public ApiResponse<CalendarCategoryResponse> createCategory(
			@AuthenticationPrincipal AuthenticatedUser user,
			@Valid @RequestBody CalendarCategoryCreateRequest request) {
		return ApiResponse.success(calendarService.createCategory(user.userId(), request));
	}

	@PatchMapping("/categories/{categoryId}")
	public ApiResponse<CalendarCategoryResponse> updateCategory(
			@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable("categoryId") Long categoryId,
			@Valid @RequestBody CalendarCategoryUpdateRequest request) {
		return ApiResponse.success(calendarService.updateCategory(user.userId(), categoryId, request));
	}

	@DeleteMapping("/categories/{categoryId}")
	public ApiResponse<Void> deleteCategory(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable("categoryId") Long categoryId) {
		calendarService.deleteCategory(user.userId(), categoryId);
		return ApiResponse.successWithoutData();
	}
}
