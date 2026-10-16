package com.kkirikkiri.auth.controller;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kkirikkiri.auth.dto.LoginRequest;
import com.kkirikkiri.auth.dto.LoginEmailCheckRequest;
import com.kkirikkiri.auth.dto.LoginEmailCheckResponse;
import com.kkirikkiri.auth.dto.LoginResponse;
import com.kkirikkiri.auth.service.AuthService;
import com.kkirikkiri.auth.service.ClientIpResolver;
import com.kkirikkiri.global.response.ApiResponse;
import com.kkirikkiri.global.security.AuthenticatedUser;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;
	private final ClientIpResolver clientIpResolver;

	@GetMapping("/me")
	public ApiResponse<LoginResponse.LoginUser> getAuthenticatedUser(@AuthenticationPrincipal AuthenticatedUser user) {
		return ApiResponse.success(authService.getAuthenticatedUser(user.userId()));
	}

	@PostMapping("/login/email-check")
	public ApiResponse<LoginEmailCheckResponse> checkEmail(@Valid @RequestBody LoginEmailCheckRequest request, HttpServletRequest http) {
		return ApiResponse.success(authService.checkEmail(request, clientIpResolver.resolve(http)));
	}

	@PostMapping("/login")
	public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
		return ApiResponse.success(authService.login(request, clientIpResolver.resolve(http)));
	}
}
