package com.kkirikkiri.user.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kkirikkiri.global.response.ApiResponse;
import com.kkirikkiri.global.security.AuthenticatedUser;
import com.kkirikkiri.user.dto.PasswordChangeRequest;
import com.kkirikkiri.user.dto.UserResponse;
import com.kkirikkiri.user.dto.UserUpdateRequest;
import com.kkirikkiri.user.service.UserService;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class UserController {

	private final UserService userService;

	@GetMapping
	public ApiResponse<UserResponse> getMyInfo(@AuthenticationPrincipal AuthenticatedUser user) {
		return ApiResponse.success(userService.getMyInfo(user.userId()));
	}

	@PatchMapping
	public ApiResponse<UserResponse> updateMyInfo(@AuthenticationPrincipal AuthenticatedUser user,
			@Valid @RequestBody UserUpdateRequest request) {
		return ApiResponse.success(userService.updateMyInfo(user.userId(), request));
	}

	@PatchMapping("/password")
	public ApiResponse<Void> changePassword(@AuthenticationPrincipal AuthenticatedUser user,
			@Valid @RequestBody PasswordChangeRequest request) {
		userService.changePassword(user.userId(), request);
		return ApiResponse.successWithoutData();
	}
}
