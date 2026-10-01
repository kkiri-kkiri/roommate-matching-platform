package com.kkirikkiri.auth.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kkirikkiri.auth.dto.LoginRequest;
import com.kkirikkiri.auth.dto.LoginEmailCheckRequest;
import com.kkirikkiri.auth.dto.LoginEmailCheckResponse;
import com.kkirikkiri.auth.dto.LoginResponse;
import com.kkirikkiri.auth.service.AuthService;
import com.kkirikkiri.global.response.ApiResponse;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;

	@PostMapping("/login/email-check")
	public ApiResponse<LoginEmailCheckResponse> checkEmail(@Valid @RequestBody LoginEmailCheckRequest request) {
		return ApiResponse.success(authService.checkEmail(request));
	}

	@PostMapping("/login")
	public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
		return ApiResponse.success(authService.login(request));
	}
}
