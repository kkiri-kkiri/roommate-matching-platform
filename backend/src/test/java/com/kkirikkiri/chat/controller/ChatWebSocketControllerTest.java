package com.kkirikkiri.chat.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import com.kkirikkiri.chat.dto.ChatMessageBroadcastResponse;
import com.kkirikkiri.chat.dto.ChatMessageSendRequest;
import com.kkirikkiri.chat.service.ChatService;
import com.kkirikkiri.global.exception.CustomException;
import com.kkirikkiri.global.exception.ErrorCode;
import com.kkirikkiri.global.security.AuthenticatedUser;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class ChatWebSocketControllerTest {

	@Test
	void sendsAuthenticatedUserToServiceAndBroadcastsReturnedDto() {
		var service = mock(ChatService.class);
		var messaging = mock(SimpMessagingTemplate.class);
		var controller = new ChatWebSocketController(service, messaging);
		var authentication = UsernamePasswordAuthenticationToken.authenticated(
				new AuthenticatedUser(1L, "USER"), null, List.of());
		var request = new ChatMessageSendRequest("hello");
		var response = new ChatMessageBroadcastResponse(1002L, 15L, 1L, "hello", LocalDateTime.now(), false);
		when(service.sendMessage(1L, 15L, request)).thenReturn(response);

		controller.sendMessage(authentication, 15L, request);

		var order = inOrder(service, messaging);
		order.verify(service).sendMessage(1L, 15L, request);
		order.verify(messaging).convertAndSend("/sub/chat/rooms/15", response);
	}

	@Test
	void rejectedRoomAccessNeverBroadcasts() {
		var service = mock(ChatService.class);
		var messaging = mock(SimpMessagingTemplate.class);
		var controller = new ChatWebSocketController(service, messaging);
		var authentication = UsernamePasswordAuthenticationToken.authenticated(
				new AuthenticatedUser(1L, "USER"), null, List.of());
		var request = new ChatMessageSendRequest("hello");
		when(service.sendMessage(1L, 15L, request)).thenThrow(new CustomException(ErrorCode.FORBIDDEN));

		assertThrows(CustomException.class, () -> controller.sendMessage(authentication, 15L, request));
		verifyNoInteractions(messaging);
	}
}
