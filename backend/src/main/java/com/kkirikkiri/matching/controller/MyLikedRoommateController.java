package com.kkirikkiri.matching.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kkirikkiri.global.response.ApiResponse;
import com.kkirikkiri.global.security.AuthenticatedUser;
import com.kkirikkiri.matching.dto.RoommatePageResponse;
import com.kkirikkiri.matching.dto.RoommateProfileResponse;
import com.kkirikkiri.matching.service.RoommateMatchingService;

@RestController
@RequestMapping("/api/users/me/liked-roommates")
@RequiredArgsConstructor
public class MyLikedRoommateController {

	private final RoommateMatchingService roommateMatchingService;

	@GetMapping
	public ApiResponse<RoommatePageResponse<RoommateProfileResponse>> getLikedRoommates(
			@AuthenticationPrincipal AuthenticatedUser user,
			@PageableDefault(page = 0, size = 6) Pageable pageable) {
		return ApiResponse.success(
				roommateMatchingService.getLikedRoommates(user == null ? null : user.userId(), pageable));
	}

}
