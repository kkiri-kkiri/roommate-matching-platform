package com.kkirikkiri.global.config;

import java.util.Base64;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

import com.kkirikkiri.global.exception.CustomException;
import com.kkirikkiri.global.exception.ErrorCode;
import com.kkirikkiri.chat.service.ChatService;
import com.kkirikkiri.global.security.AuthenticatedUser;
import com.kkirikkiri.global.security.JwtAuthenticationProvider;
import com.kkirikkiri.global.security.JwtTokenProvider;
import com.kkirikkiri.user.entity.User;
import com.kkirikkiri.user.entity.UserRole;
import com.kkirikkiri.user.entity.UserStatus;
import com.kkirikkiri.user.repository.UserRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatStompInterceptorTest {

	private JwtTokenProvider tokens;
	private ChatStompInterceptor interceptor;
	private User user;
	private ChatService chatService;

	@BeforeEach
	void setUp() {
		tokens = new JwtTokenProvider(Base64.getEncoder().encodeToString(new byte[32]), 3600);
		UserRepository users = mock(UserRepository.class);
		user = mock(User.class);
		when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
		when(users.findById(1L)).thenReturn(Optional.of(user));
		chatService = mock(ChatService.class);
		interceptor = new ChatStompInterceptor(new JwtAuthenticationProvider(tokens, users), chatService);
	}

	@Test
	void connectUsesExistingJwtPrincipal() {
		var headers = connect();
		interceptor.preSend(message(headers), null);
		var authentication = (Authentication) headers.getUser();
		assertNotNull(authentication);
		assertTrue(authentication.isAuthenticated());
		assertEquals(1L, ((AuthenticatedUser) authentication.getPrincipal()).userId());
	}

	@Test
	void rejectsMissingInvalidAndInactiveCredentials() {
		assertThrows(AccessDeniedException.class, () -> interceptor.preSend(
				message(StompHeaderAccessor.create(StompCommand.CONNECT)), null));
		var invalid = connect();
		invalid.setNativeHeader(HttpHeaders.AUTHORIZATION, "Bearer invalid");
		assertThrows(CustomException.class, () -> interceptor.preSend(message(invalid), null));
		when(user.getStatus()).thenReturn(UserStatus.WITHDRAWN);
		assertThrows(CustomException.class, () -> interceptor.preSend(message(connect()), null));
	}

	@Test
	void rejectsAnonymousSendAndSubscribe() {
		for (var command : new StompCommand[] {StompCommand.SEND, StompCommand.SUBSCRIBE}) {
			assertThrows(AccessDeniedException.class, () -> interceptor.preSend(
					message(StompHeaderAccessor.create(command)), null));
		}
	}

	@Test
	void allowsOnlyApplicationSendWithoutDuplicateMembershipCheck() {
		var connect = connect();
		interceptor.preSend(message(connect), null);
		var send = StompHeaderAccessor.create(StompCommand.SEND);
		send.setUser(connect.getUser());
		send.setDestination("/pub/chat/rooms/15/messages");
		assertDoesNotThrow(() -> interceptor.preSend(message(send), null));
		send.setDestination("/sub/chat/rooms/15");
		assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message(send), null));
		verifyNoInteractions(chatService);
	}

	@Test
	void subscriptionUsesConnectedUserAndPropagatesMembershipDenial() {
		var connect = connect();
		interceptor.preSend(message(connect), null);
		var subscribe = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
		subscribe.setUser(connect.getUser());
		subscribe.setDestination("/sub/chat/rooms/15");
		assertDoesNotThrow(() -> interceptor.preSend(message(subscribe), null));
		verify(chatService).validateActiveMember(1L, 15L);
		doThrow(new CustomException(ErrorCode.FORBIDDEN)).when(chatService).validateActiveMember(1L, 16L);
		subscribe.setDestination("/sub/chat/rooms/16");
		var exception = assertThrows(CustomException.class, () -> interceptor.preSend(message(subscribe), null));
		assertEquals(ErrorCode.FORBIDDEN, exception.getErrorCode());
	}

	@Test
	void rejectsMalformedSubscriptionDestinationsBeforeCallingService() {
		var connect = connect();
		interceptor.preSend(message(connect), null);
		for (String destination : new String[] {null, "/sub/chat/rooms/**", "/sub/chat/rooms/0",
				"/sub/chat/rooms/-1", "/sub/chat/rooms/01", "/sub/chat/rooms/15/",
				"/sub/chat/rooms/15/messages", "/pub/chat/rooms/15/messages",
				"/sub/chat/rooms/9223372036854775808"}) {
			var subscribe = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
			subscribe.setUser(connect.getUser());
			subscribe.setDestination(destination);
			assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message(subscribe), null));
		}
		verifyNoInteractions(chatService);
	}

	private StompHeaderAccessor connect() {
		var headers = StompHeaderAccessor.create(StompCommand.CONNECT);
		headers.setNativeHeader(HttpHeaders.AUTHORIZATION,
				"Bearer " + tokens.createAccessToken(1L, UserRole.values()[0]));
		return headers;
	}

	private Message<byte[]> message(StompHeaderAccessor headers) {
		headers.setLeaveMutable(true);
		return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
	}
}
