package com.kkirikkiri.survey.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kkirikkiri.global.response.ApiResponse;
import com.kkirikkiri.global.security.AuthenticatedUser;
import com.kkirikkiri.survey.dto.RoommatePreferenceRequest;
import com.kkirikkiri.survey.dto.RoommatePreferenceResponse;
import com.kkirikkiri.survey.service.RoommatePreferenceService;

@RestController
@RequestMapping("/api/users/me/roommate-preferences")
@RequiredArgsConstructor
public class RoommatePreferenceController {

	private final RoommatePreferenceService roommatePreferenceService;

	@GetMapping
	public ApiResponse<RoommatePreferenceResponse> getMyRoommatePreference(
			@AuthenticationPrincipal AuthenticatedUser user) {
		return ApiResponse.success(roommatePreferenceService.getMyRoommatePreference(user.userId()));
	}

	@PutMapping
	public ApiResponse<Void> saveOrUpdateMyRoommatePreference(
			@AuthenticationPrincipal AuthenticatedUser user,
			@Valid @RequestBody RoommatePreferenceRequest request) {
		roommatePreferenceService.saveOrUpdateMyRoommatePreference(user.userId(), request);
		return ApiResponse.successWithoutData();
	}
}
