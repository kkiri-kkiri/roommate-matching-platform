package com.kkirikkiri.chat.controller;

import java.util.List;
import com.kkirikkiri.chat.dto.ChatMessageReadStatusResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kkirikkiri.chat.dto.ChatMessageListRequest;
import com.kkirikkiri.chat.dto.ChatMessageResponse;
import com.kkirikkiri.chat.dto.ChatReadRequest;
import com.kkirikkiri.chat.dto.ChatRoomCreateRequest;
import com.kkirikkiri.chat.dto.ChatRoomListRequest;
import com.kkirikkiri.chat.dto.ChatRoomResponse;
import com.kkirikkiri.chat.dto.ChatRoomPageResponse;
import com.kkirikkiri.chat.dto.ChatRoomSettingsRequest;
import com.kkirikkiri.chat.service.ChatService;
import com.kkirikkiri.global.response.ApiResponse;
import com.kkirikkiri.global.security.AuthenticatedUser;
import com.kkirikkiri.matching.dto.RoommatePageResponse;

@RestController
@RequestMapping("/api/chat/rooms")
@RequiredArgsConstructor
public class ChatController {

	private final ChatService chatService;

	@GetMapping
	public ApiResponse<ChatRoomPageResponse> getRooms(
			@AuthenticationPrincipal AuthenticatedUser user,
			@Valid @ModelAttribute ChatRoomListRequest request) {
		return ApiResponse.success(chatService.getRooms(user.userId(), request));
	}

	@GetMapping("/{roomId}")
	public ApiResponse<ChatRoomResponse> getRoom(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable Long roomId) {
		return ApiResponse.success(chatService.getRoom(user.userId(), roomId));
	}

	@PostMapping
	public ApiResponse<ChatRoomResponse> createOrGetRoom(
			@AuthenticationPrincipal AuthenticatedUser user,
			@Valid @RequestBody ChatRoomCreateRequest request) {
		return ApiResponse.success(chatService.createOrGetRoom(user.userId(), request));
	}

	@GetMapping("/{roomId}/messages")
	public ApiResponse<RoommatePageResponse<ChatMessageResponse>> getMessages(
			@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable Long roomId,
			@Valid @ModelAttribute ChatMessageListRequest request) {
		return ApiResponse.success(chatService.getMessages(user.userId(), roomId, request));
	}

	@PostMapping("/{roomId}/read")
	public ApiResponse<Void> markRead(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable Long roomId, @Valid @RequestBody ChatReadRequest request) {
		chatService.markRead(user.userId(), roomId, request);
		return ApiResponse.successWithoutData();
	}

	@PostMapping("/{roomId}/messages/read-status")
	public ApiResponse<List<ChatMessageReadStatusResponse>> getReadStatuses(
			@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long roomId,
			@Valid @RequestBody ChatReadRequest request) {
		return ApiResponse.success(chatService.getReadStatuses(user.userId(), roomId, request));
	}

	@PatchMapping("/{roomId}/settings")
	public ApiResponse<Void> updateSettings(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable Long roomId,
			@Valid @RequestBody ChatRoomSettingsRequest request) {
		chatService.updateSettings(user.userId(), roomId, request);
		return ApiResponse.successWithoutData();
	}

	@DeleteMapping("/{roomId}/members/me")
	public ApiResponse<Void> leaveRoom(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable Long roomId) {
		chatService.leaveRoom(user.userId(), roomId);
		return ApiResponse.successWithoutData();
	}
}
