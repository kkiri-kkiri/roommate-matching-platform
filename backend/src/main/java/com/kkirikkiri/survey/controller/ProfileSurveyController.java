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
import com.kkirikkiri.survey.dto.ProfileSurveyRequest;
import com.kkirikkiri.survey.dto.ProfileSurveyResponse;
import com.kkirikkiri.survey.service.ProfileSurveyService;

@RestController
@RequestMapping("/api/users/me/profile-survey")
@RequiredArgsConstructor
public class ProfileSurveyController {

	private final ProfileSurveyService profileSurveyService;

	@GetMapping
	public ApiResponse<ProfileSurveyResponse> getMyProfileSurvey(
			@AuthenticationPrincipal AuthenticatedUser user) {
		return ApiResponse.success(profileSurveyService.getMyProfileSurvey(user.userId()));
	}

	@PutMapping
	public ApiResponse<Void> saveOrUpdateMyProfileSurvey(
			@AuthenticationPrincipal AuthenticatedUser user,
			@Valid @RequestBody ProfileSurveyRequest request) {
		profileSurveyService.saveOrUpdateMyProfileSurvey(user.userId(), request);
		return ApiResponse.successWithoutData();
	}
}
