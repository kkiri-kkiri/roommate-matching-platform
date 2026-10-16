package com.kkirikkiri.auth.controller;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kkirikkiri.auth.dto.PasswordResetConfirmRequest;
import com.kkirikkiri.auth.dto.PasswordResetRequest;
import com.kkirikkiri.auth.dto.PasswordResetResponse;
import com.kkirikkiri.auth.dto.SignupRequest;
import com.kkirikkiri.auth.dto.SignupResponse;
import com.kkirikkiri.auth.dto.SignupAvailabilityResponse;
import com.kkirikkiri.auth.service.PasswordResetService;
import com.kkirikkiri.auth.service.SignupService;
import com.kkirikkiri.auth.service.ClientIpResolver;
import com.kkirikkiri.global.response.ApiResponse;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AccountController {

	private final SignupService signupService;
	private final PasswordResetService passwordResetService;
	private final ClientIpResolver clientIpResolver;

	@PostMapping("/signup")
	public ApiResponse<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
		return ApiResponse.success(signupService.signup(request));
	}

	@GetMapping("/signup/availability")
	public ApiResponse<SignupAvailabilityResponse> checkSignupAvailability(
			@RequestParam(required = false, defaultValue = "") String schoolEmail,
			@RequestParam(required = false, defaultValue = "") String nickname) {
		return ApiResponse.success(signupService.checkAvailability(schoolEmail, nickname));
	}

	@PostMapping("/password-reset/request")
	public ApiResponse<PasswordResetResponse> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request, HttpServletRequest http) {
		return ApiResponse.success(passwordResetService.requestPasswordReset(request, clientIpResolver.resolve(http)));
	}

	@PostMapping("/password-reset/confirm")
	public ApiResponse<Void> resetPassword(@Valid @RequestBody PasswordResetConfirmRequest request) {
		passwordResetService.resetPassword(request);
		return ApiResponse.successWithoutData();
	}
}
