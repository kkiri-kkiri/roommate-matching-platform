package com.kkirikkiri.community.comment.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kkirikkiri.community.comment.dto.MyCommunityCommentResponse;
import com.kkirikkiri.community.comment.service.CommunityCommentService;
import com.kkirikkiri.global.response.ApiResponse;
import com.kkirikkiri.global.security.AuthenticatedUser;
import com.kkirikkiri.matching.dto.RoommatePageResponse;

@RestController
@RequestMapping("/api/users/me/comments")
@RequiredArgsConstructor
public class MyCommunityCommentController {

	private final CommunityCommentService communityCommentService;

	@GetMapping
	public ApiResponse<RoommatePageResponse<MyCommunityCommentResponse>> getMyComments(
			@AuthenticationPrincipal AuthenticatedUser user,
			@RequestParam(defaultValue = "0") Integer page,
			@RequestParam(defaultValue = "6") Integer size) {
		return ApiResponse.success(communityCommentService.getMyComments(
				user == null ? null : user.userId(), page, size));
	}
}
