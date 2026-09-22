package com.kkirikkiri.matching.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kkirikkiri.global.response.ApiResponse;
import com.kkirikkiri.global.security.AuthenticatedUser;
import com.kkirikkiri.matching.dto.RoommateDetailResponse;
import com.kkirikkiri.matching.dto.RoommateFilterRequest;
import com.kkirikkiri.matching.dto.RoommatePageResponse;
import com.kkirikkiri.matching.dto.RoommateProfileResponse;
import com.kkirikkiri.matching.service.RoommateMatchingService;

@RestController
@RequiredArgsConstructor
public class RoommateMatchingController {

	private final RoommateMatchingService roommateMatchingService;

	@GetMapping("/api/roommates")
	public ApiResponse<RoommatePageResponse<RoommateProfileResponse>> getRoommates(
			@AuthenticationPrincipal AuthenticatedUser user,
			@Valid @ModelAttribute RoommateFilterRequest filter,
			@PageableDefault(size = 9) Pageable pageable) {
		return ApiResponse.success(
				roommateMatchingService.getRoommates(user.userId(), filter, pageable));
	}

	@GetMapping("/api/roommates/recommended")
	public ApiResponse<RoommatePageResponse<RoommateProfileResponse>> getRecommendedRoommates(
			@AuthenticationPrincipal AuthenticatedUser user,
			@PageableDefault(size = 9) Pageable pageable) {
		return ApiResponse.success(
				roommateMatchingService.getRecommendedRoommates(user.userId(), pageable));
	}

	@GetMapping("/api/roommates/{userId}")
	public ApiResponse<RoommateDetailResponse> getRoommateDetail(
			@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable Long userId) {
		return ApiResponse.success(
				roommateMatchingService.getRoommateDetail(user.userId(), userId));
	}

	@PostMapping("/api/roommates/{userId}/likes")
	public ApiResponse<Void> addProfileLike(
			@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable Long userId) {
		roommateMatchingService.addProfileLike(user == null ? null : user.userId(), userId);
		return ApiResponse.successWithoutData();
	}

	@DeleteMapping("/api/roommates/{userId}/likes")
	public ApiResponse<Void> cancelProfileLike(
			@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable Long userId) {
		roommateMatchingService.cancelProfileLike(user == null ? null : user.userId(), userId);
		return ApiResponse.successWithoutData();
	}

}
