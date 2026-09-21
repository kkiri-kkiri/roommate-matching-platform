package com.kkirikkiri.auth.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kkirikkiri.auth.dto.FindIdRequest;
import com.kkirikkiri.auth.dto.FindIdResponse;
import com.kkirikkiri.auth.dto.PasswordResetConfirmRequest;
import com.kkirikkiri.auth.dto.PasswordResetRequest;
import com.kkirikkiri.auth.dto.PasswordResetResponse;
import com.kkirikkiri.auth.dto.SignupRequest;
import com.kkirikkiri.auth.dto.SignupResponse;
import com.kkirikkiri.auth.service.FindIdService;
import com.kkirikkiri.auth.service.PasswordResetService;
import com.kkirikkiri.auth.service.SignupService;
import com.kkirikkiri.global.response.ApiResponse;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AccountController {

	private final SignupService signupService;
	private final FindIdService findIdService;
	private final PasswordResetService passwordResetService;

	@PostMapping("/signup")
	public ApiResponse<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
		return ApiResponse.success(signupService.signup(request));
	}

	@PostMapping("/find-id")
	public ApiResponse<FindIdResponse> findId(@Valid @RequestBody FindIdRequest request) {
		return ApiResponse.success(findIdService.findId(request));
	}

	@PostMapping("/password-reset/request")
	public ApiResponse<PasswordResetResponse> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
		return ApiResponse.success(passwordResetService.requestPasswordReset(request));
	}

	@PostMapping("/password-reset/confirm")
	public ApiResponse<Void> resetPassword(@Valid @RequestBody PasswordResetConfirmRequest request) {
		passwordResetService.resetPassword(request);
		return ApiResponse.successWithoutData();
	}
}
