package com.kkirikkiri.community.post.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kkirikkiri.community.post.dto.CommunityPostResponse;
import com.kkirikkiri.community.post.service.CommunityPostService;
import com.kkirikkiri.global.response.ApiResponse;
import com.kkirikkiri.global.security.AuthenticatedUser;
import com.kkirikkiri.matching.dto.RoommatePageResponse;

@RestController
@RequestMapping("/api/users/me/posts")
@RequiredArgsConstructor
public class MyCommunityPostController {
	private final CommunityPostService communityPostService;

	@GetMapping
	public ApiResponse<RoommatePageResponse<CommunityPostResponse>> getMyPosts(
			@AuthenticationPrincipal AuthenticatedUser user,
			@RequestParam(defaultValue = "0") Integer page,
			@RequestParam(defaultValue = "6") Integer size,
			@RequestParam(defaultValue = "LATEST") String sort) {
		return ApiResponse.success(communityPostService.getMyPosts(user == null ? null : user.userId(), page, size, sort));
	}
}
