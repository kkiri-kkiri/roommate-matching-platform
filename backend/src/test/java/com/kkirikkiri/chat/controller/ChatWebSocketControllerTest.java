package com.kkirikkiri.chat.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import com.kkirikkiri.chat.dto.ChatMessageBroadcastResponse;
import com.kkirikkiri.chat.dto.ChatMessageSendRequest;
import com.kkirikkiri.chat.service.ChatService;
import com.kkirikkiri.global.exception.CustomException;
import com.kkirikkiri.global.exception.ErrorCode;
import com.kkirikkiri.global.security.AuthenticatedUser;

@ExtendWith(MockitoExtension.class)
class ChatWebSocketControllerTest {
    @Mock ChatService service;
    @Mock SimpMessagingTemplate messaging;
    @InjectMocks ChatWebSocketController controller;

    @Test
    void broadcastsSavedMessageToRequestedRoomUsingAuthenticatedUser() {
        // given
        var request = new ChatMessageSendRequest("hello");
        var response = new ChatMessageBroadcastResponse(42L, 10L, 7L, "hello",
                LocalDateTime.of(2026, 1, 1, 12, 0), false);
        when(service.sendMessage(7L, 10L, request)).thenReturn(response);
        // when
        controller.sendMessage(authenticatedUser(), 10L, request);
        // then
        var order = inOrder(service, messaging);
        order.verify(service).sendMessage(7L, 10L, request);
        order.verify(messaging).convertAndSend("/sub/chat/rooms/10", response);
    }

    @Test
    void doesNotBroadcastWhenMessageCannotBeSaved() {
        // given
        var request = new ChatMessageSendRequest("hello");
        var failure = new CustomException(ErrorCode.FORBIDDEN);
        when(service.sendMessage(7L, 10L, request)).thenThrow(failure);
        // when / then
        assertSame(failure, assertThrows(CustomException.class,
                () -> controller.sendMessage(authenticatedUser(), 10L, request)));
        verifyNoInteractions(messaging);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "plain", "unauthenticated", "wrongPrincipal"})
    void rejectsInvalidPrincipalBeforeSavingOrBroadcasting(String kind) {
        // given
        Principal principal = switch (kind) {
            case "null" -> null;
            case "plain" -> () -> "7";
            case "unauthenticated" -> UsernamePasswordAuthenticationToken.unauthenticated(new AuthenticatedUser(7L, "USER"), null);
            default -> UsernamePasswordAuthenticationToken.authenticated("7", null, List.of());
        };
        // when / then
        assertThrows(AccessDeniedException.class,
                () -> controller.sendMessage(principal, 10L, new ChatMessageSendRequest("hello")));
        verifyNoInteractions(service, messaging);
    }

    private Principal authenticatedUser() {
        return UsernamePasswordAuthenticationToken.authenticated(new AuthenticatedUser(7L, "USER"), null, List.of());
    }
}
