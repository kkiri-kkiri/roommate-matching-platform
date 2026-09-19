package com.kkirikkiri.community.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;

import com.kkirikkiri.community.dto.CommunityPostCreateRequest;
import com.kkirikkiri.community.dto.CommunityPostUpdateRequest;
import com.kkirikkiri.community.dto.PostImagesRequest;
import com.kkirikkiri.community.service.CommunityPostService;
import com.kkirikkiri.global.response.ApiResponse;
import com.kkirikkiri.global.security.AuthenticatedUser;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class CommunityPostController {

	private final CommunityPostService communityPostService;

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ApiResponse<Long> createPost(@AuthenticationPrincipal AuthenticatedUser user,
			@Valid @RequestPart("request") CommunityPostCreateRequest request,
			@Valid @ModelAttribute PostImagesRequest images) {
		return ApiResponse.success(communityPostService.createPost(user.userId(), request, images));
	}

	@PatchMapping(value = "/{postId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ApiResponse<Void> updatePost(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable Long postId,
			@Valid @RequestPart("request") CommunityPostUpdateRequest request,
			@Valid @ModelAttribute PostImagesRequest images) {
		communityPostService.updatePost(user.userId(), postId, request, images);
		return ApiResponse.successWithoutData();
	}

	@DeleteMapping("/{postId}")
	public ApiResponse<Void> deletePost(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable Long postId) {
		communityPostService.deletePost(user.userId(), postId);
		return ApiResponse.successWithoutData();
	}
}
