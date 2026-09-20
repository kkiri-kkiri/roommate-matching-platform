package com.kkirikkiri.community.comment.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kkirikkiri.community.comment.dto.CommunityCommentCreateRequest;
import com.kkirikkiri.community.comment.dto.CommunityCommentUpdateRequest;
import com.kkirikkiri.community.comment.service.CommunityCommentService;
import com.kkirikkiri.global.response.ApiResponse;
import com.kkirikkiri.global.security.AuthenticatedUser;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CommunityCommentController {

	private final CommunityCommentService communityCommentService;

	@PostMapping("/posts/{postId}/comments")
	public ApiResponse<Long> createComment(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable Long postId,
			@Valid @RequestBody CommunityCommentCreateRequest request) {
		return ApiResponse.success(communityCommentService.createComment(
				user == null ? null : user.userId(), postId, request));
	}

	@PostMapping("/comments/{commentId}/replies")
	public ApiResponse<Long> createReply(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable Long commentId,
			@Valid @RequestBody CommunityCommentCreateRequest request) {
		return ApiResponse.success(communityCommentService.createReply(
				user == null ? null : user.userId(), commentId, request));
	}

	@PatchMapping("/comments/{commentId}")
	public ApiResponse<Void> updateComment(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable Long commentId,
			@Valid @RequestBody CommunityCommentUpdateRequest request) {
		communityCommentService.updateComment(user == null ? null : user.userId(), commentId, request);
		return ApiResponse.successWithoutData();
	}

	@DeleteMapping("/comments/{commentId}")
	public ApiResponse<Void> deleteComment(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable Long commentId) {
		communityCommentService.deleteComment(user == null ? null : user.userId(), commentId);
		return ApiResponse.successWithoutData();
	}
}
