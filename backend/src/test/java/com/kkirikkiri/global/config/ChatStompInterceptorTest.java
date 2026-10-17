package com.kkirikkiri.global.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.security.Principal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import com.kkirikkiri.chat.service.ChatService;
import com.kkirikkiri.global.exception.CustomException;
import com.kkirikkiri.global.exception.ErrorCode;
import com.kkirikkiri.global.security.AuthenticatedUser;
import com.kkirikkiri.global.security.JwtAuthenticationProvider;

@ExtendWith(MockitoExtension.class)
class ChatStompInterceptorTest {
    @Mock JwtAuthenticationProvider authenticationProvider;
    @Mock ChatService service;
    @InjectMocks ChatStompInterceptor interceptor;

    @Test
    void authenticatesConnectWithCaseInsensitiveBearerAndTrimmedToken() {
        // given
        var accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader(HttpHeaders.AUTHORIZATION, "bEaReR   token  ");
        accessor.setSessionId("connect");
        var authentication = authenticatedUser();
        when(authenticationProvider.authenticate("token")).thenReturn(authentication);
        Message<?> message = message(accessor);
        // when / then
        assertSame(message, interceptor.preSend(message, null));
        assertSame(authentication, accessor.getUser());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"Basic token", "Bearer", " token"})
    void rejectsConnectWithoutBearerHeader(String header) {
        // given
        var accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        if (header != null) accessor.setNativeHeader(HttpHeaders.AUTHORIZATION, header);
        // when / then
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message(accessor), null));
        verifyNoInteractions(authenticationProvider, service);
    }

    @Test
    void rejectsAmbiguousAuthorizationHeaders() {
        // given
        var accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader(HttpHeaders.AUTHORIZATION, "Bearer first");
        accessor.addNativeHeader(HttpHeaders.AUTHORIZATION, "Bearer second");
        // when / then
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message(accessor), null));
        verifyNoInteractions(authenticationProvider);
    }

    @Test
    void propagatesTokenAuthenticationFailureWithoutSettingUser() {
        // given
        var accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader(HttpHeaders.AUTHORIZATION, "Bearer expired");
        var failure = new CustomException(ErrorCode.UNAUTHORIZED);
        when(authenticationProvider.authenticate("expired")).thenThrow(failure);
        // when / then
        assertSame(failure, assertThrows(CustomException.class, () -> interceptor.preSend(message(accessor), null)));
        assertNull(accessor.getUser());
    }

    @Test
    void checksRoomMembershipBeforeAllowingSubscription() {
        // given
        var accessor = authenticated(StompCommand.SUBSCRIBE, "/sub/chat/rooms/10");
        Message<?> message = message(accessor);
        // when / then
        assertSame(message, interceptor.preSend(message, null));
        verify(service).validateActiveMember(7L, 10L);
    }

    @Test
    void propagatesForbiddenSubscription() {
        // given
        var failure = new CustomException(ErrorCode.FORBIDDEN);
        doThrow(failure).when(service).validateActiveMember(7L, 10L);
        var accessor = authenticated(StompCommand.SUBSCRIBE, "/sub/chat/rooms/10");
        // when / then
        assertSame(failure, assertThrows(CustomException.class, () -> interceptor.preSend(message(accessor), null)));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"/sub/chat/rooms/0", "/sub/chat/rooms/-1", "/sub/chat/rooms/01",
            "/sub/chat/rooms/10/messages", "/sub/chat/rooms/9223372036854775808", "/sub/chat/rooms/*"})
    void rejectsInvalidSubscriptionDestinationBeforeMembershipLookup(String destination) {
        // given
        var accessor = authenticated(StompCommand.SUBSCRIBE, destination);
        // when / then
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message(accessor), null));
        verifyNoInteractions(service);
    }

    @Test
    void allowsSendToChatMessageDestination() {
        // given
        Message<?> message = message(authenticated(StompCommand.SEND, "/pub/chat/rooms/10/messages"));
        // when / then
        assertSame(message, interceptor.preSend(message, null));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"/sub/chat/rooms/10", "/pub/chat/rooms/0/messages",
            "/pub/chat/rooms/01/messages", "/pub/chat/rooms/10", "/pub/chat/rooms/10/messages/extra"})
    void rejectsSendOutsideAllowedDestination(String destination) {
        // given
        var accessor = authenticated(StompCommand.SEND, destination);
        // when / then
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message(accessor), null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "plain", "unauthenticated", "wrongPrincipal"})
    void rejectsUnauthenticatedSubscription(String kind) {
        // given
        var accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/sub/chat/rooms/10");
        Principal principal = switch (kind) {
            case "null" -> null;
            case "plain" -> () -> "7";
            case "unauthenticated" -> UsernamePasswordAuthenticationToken.unauthenticated(new AuthenticatedUser(7L, "USER"), null);
            default -> UsernamePasswordAuthenticationToken.authenticated("7", null, List.of());
        };
        accessor.setUser(principal);
        // when / then
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message(accessor), null));
        verifyNoInteractions(service);
    }

    @Test
    void permitsDisconnectBeforeAuthentication() {
        // given
        Message<?> message = message(StompHeaderAccessor.create(StompCommand.DISCONNECT));
        // when / then
        assertSame(message, interceptor.preSend(message, null));
    }

    @Test
    void permitsAuthenticatedUnsubscribeAndHeartbeat() {
        // given
        Message<?> unsubscribe = message(authenticated(StompCommand.UNSUBSCRIBE, null));
        var heartbeatAccessor = StompHeaderAccessor.createForHeartbeat();
        heartbeatAccessor.setUser(authenticatedUser());
        Message<?> heartbeat = message(heartbeatAccessor);
        // when / then
        assertEquals(SimpMessageType.HEARTBEAT, heartbeatAccessor.getMessageType());
        assertSame(unsubscribe, interceptor.preSend(unsubscribe, null));
        assertSame(heartbeat, interceptor.preSend(heartbeat, null));
    }

    @Test
    void rejectsUnsupportedCommandAndMissingStompHeaders() {
        // when / then
        assertThrows(AccessDeniedException.class,
                () -> interceptor.preSend(message(authenticated(StompCommand.ACK, null)), null));
        assertThrows(AccessDeniedException.class,
                () -> interceptor.preSend(MessageBuilder.withPayload(new byte[0]).build(), null));
    }

    private UsernamePasswordAuthenticationToken authenticatedUser() {
        return UsernamePasswordAuthenticationToken.authenticated(new AuthenticatedUser(7L, "USER"), null, List.of());
    }

    @Test
    void rejectsSendWhenConnectedTokenExpiresOrAccountIsSuspended() {
        var send = message(authenticated(StompCommand.SEND, "/pub/chat/rooms/10/messages"));
        when(authenticationProvider.authenticate("token")).thenThrow(new CustomException(ErrorCode.UNAUTHORIZED));
        assertThrows(CustomException.class, () -> interceptor.preSend(send, null));
        verifyNoInteractions(service);
    }

    @Test
    void checksActualBrokerMessageBeforeDeliveryToExistingSubscriber() {
        authenticated(StompCommand.SUBSCRIBE, "/sub/chat/rooms/10");
        var delivery = brokerDelivery("session");
        assertSame(delivery, interceptor.beforeHandle(delivery, null, ignored -> {}));
        verify(service).validateActiveMember(7L, 10L);
    }

    @Test
    void dropsQueuedDeliveryAfterMembershipIsRevoked() {
        authenticated(StompCommand.SUBSCRIBE, "/sub/chat/rooms/10");
        var delivery = brokerDelivery("session");
        doThrow(new CustomException(ErrorCode.FORBIDDEN)).when(service).validateActiveMember(7L, 10L);
        assertNull(interceptor.beforeHandle(delivery, null, ignored -> {}));
    }

    @Test
    void dropsDeliveryAfterTokenExpiryOrAccountDeactivation() {
        authenticated(StompCommand.SUBSCRIBE, "/sub/chat/rooms/10");
        when(authenticationProvider.authenticate("token")).thenThrow(new CustomException(ErrorCode.UNAUTHORIZED));
        assertNull(interceptor.beforeHandle(brokerDelivery("session"), null, ignored -> {}));
        verifyNoInteractions(service);
    }

    @Test
    void dropsDeliveryOnDatabaseFailureAndAfterDisconnect() {
        authenticated(StompCommand.SUBSCRIBE, "/sub/chat/rooms/10");
        when(authenticationProvider.authenticate("token")).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("offline"));
        assertNull(interceptor.beforeHandle(brokerDelivery("session"), null, ignored -> {}));
        var disconnect = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        disconnect.setSessionId("session");
        interceptor.preSend(message(disconnect), null);
        assertNull(interceptor.beforeHandle(brokerDelivery("session"), null, ignored -> {}));
        assertNull(interceptor.beforeHandle(brokerDelivery("unknown"), null, ignored -> {}));
    }

    @Test
    void doesNotDropValidatedInboundApplicationSendOnExecutor() {
        var send = message(authenticated(StompCommand.SEND, "/pub/chat/rooms/10/messages"));
        assertSame(send, interceptor.preSend(send, null));
        assertSame(send, interceptor.beforeHandle(send, null, ignored -> {}));
    }

    private Message<?> brokerDelivery(String session) {
        var headers = org.springframework.messaging.simp.SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headers.setSessionId(session);
        headers.setDestination("/sub/chat/rooms/10");
        return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }

    private StompHeaderAccessor authenticated(StompCommand command, String destination) {
        var accessor = StompHeaderAccessor.create(command);
        var connect = StompHeaderAccessor.create(StompCommand.CONNECT);
        connect.setSessionId("session");
        connect.setNativeHeader(HttpHeaders.AUTHORIZATION, "Bearer token");
        lenient().when(authenticationProvider.authenticate("token")).thenReturn(authenticatedUser());
        interceptor.preSend(message(connect), null);
        accessor.setSessionId("session");
        accessor.setUser(authenticatedUser());
        accessor.setDestination(destination);
        return accessor;
    }

    private Message<?> message(StompHeaderAccessor accessor) {
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
