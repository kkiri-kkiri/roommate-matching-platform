package com.kkirikkiri.chat.controller;

import java.security.Principal;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import com.kkirikkiri.chat.dto.ChatMessageSendRequest;
import com.kkirikkiri.chat.service.ChatService;
import com.kkirikkiri.global.security.AuthenticatedUser;

@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

	private final ChatService chatService;
	private final SimpMessagingTemplate messagingTemplate;

	@MessageMapping("/chat/rooms/{roomId}/messages")
	public void sendMessage(Principal principal, @DestinationVariable Long roomId,
			@Valid @Payload ChatMessageSendRequest request) {
		if (!(principal instanceof Authentication authentication) || !authentication.isAuthenticated()
				|| !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
			throw new AccessDeniedException("Authenticated chat user required");
		}
		var response = chatService.sendMessage(user.userId(), roomId, request);
		messagingTemplate.convertAndSend("/sub/chat/rooms/" + roomId, response);
	}
}
