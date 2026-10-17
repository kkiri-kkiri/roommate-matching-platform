package com.kkirikkiri.global.config;

import java.util.regex.Pattern;
import java.util.concurrent.ConcurrentHashMap;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import com.kkirikkiri.chat.service.ChatService;
import com.kkirikkiri.global.security.AuthenticatedUser;
import com.kkirikkiri.global.security.JwtAuthenticationProvider;

@Component
@RequiredArgsConstructor
public class ChatStompInterceptor implements ExecutorChannelInterceptor {

	private static final Pattern SEND_DESTINATION = Pattern.compile("/pub/chat/rooms/[1-9][0-9]*/messages");
	private static final Pattern SUBSCRIBE_DESTINATION = Pattern.compile("/sub/chat/rooms/([1-9][0-9]*)");
	private final JwtAuthenticationProvider jwtAuthenticationProvider;
	private final ChatService chatService;
    private final ConcurrentHashMap<String, String> sessionTokens = new ConcurrentHashMap<>();

    @org.springframework.context.event.EventListener
    public void disconnected(org.springframework.web.socket.messaging.SessionDisconnectEvent event) {
        sessionTokens.remove(event.getSessionId());
    }

    private AuthenticatedUser reauthenticate(StompHeaderAccessor accessor) {
        String token = accessor.getSessionId() == null ? null : sessionTokens.get(accessor.getSessionId());
        if (token == null) throw new AccessDeniedException("Authenticated chat session required");
        return (AuthenticatedUser) jwtAuthenticationProvider.authenticate(token).getPrincipal();
    }

    /** Runs on the outbound executor immediately before delivery to an existing subscriber. */
    @Override
    public Message<?> beforeHandle(Message<?> message, MessageChannel channel, MessageHandler handler) {
        if (SimpMessageHeaderAccessor.getMessageType(message.getHeaders()) != SimpMessageType.MESSAGE) return message;
        String destination = SimpMessageHeaderAccessor.getDestination(message.getHeaders());
        // The same interceptor also observes inbound application SEND frames.
        if (destination != null && destination.startsWith("/pub/")) return message;
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        var matcher = SUBSCRIBE_DESTINATION.matcher(accessor.getDestination() == null ? "" : accessor.getDestination());
        if (!matcher.matches()) return null;
        try {
            AuthenticatedUser user = reauthenticate(accessor);
            chatService.validateActiveMember(user.userId(), Long.valueOf(matcher.group(1)));
            return message;
        } catch (RuntimeException failure) {
            // Fail closed; never expose session tokens or database exception text.
            return null;
        }
    }

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
		if (accessor == null) {
			throw new AccessDeniedException("STOMP headers required");
		}
		StompCommand command = accessor.getCommand();
		if (command == StompCommand.CONNECT) {
			var headers = accessor.getNativeHeader(HttpHeaders.AUTHORIZATION);
			if (headers == null || headers.size() != 1
					|| !headers.get(0).regionMatches(true, 0, "Bearer ", 0, 7)) {
				throw new AccessDeniedException("Bearer access token required");
			}
            String token = headers.get(0).substring(7).trim();
			accessor.setUser(jwtAuthenticationProvider.authenticate(token));
            if (accessor.getSessionId() == null) throw new AccessDeniedException("Chat session required");
            sessionTokens.put(accessor.getSessionId(), token);
			return message;
		}
		// 인증 전 연결 종료도 정상적으로 정리할 수 있도록 허용한다.
		if (command == StompCommand.DISCONNECT) {
            if (accessor.getSessionId() != null) sessionTokens.remove(accessor.getSessionId());
			return message;
		}
		if (!(accessor.getUser() instanceof Authentication authentication)
				|| !authentication.isAuthenticated()
				|| !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
			throw new AccessDeniedException("Authenticated chat user required");
		}
		if (command == StompCommand.SEND) {
			String destination = accessor.getDestination();
			if (destination == null || !SEND_DESTINATION.matcher(destination).matches()) {
				throw new AccessDeniedException("Invalid chat destination");
			}
			// 방 참여 권한은 ChatService.sendMessage에서 검증한다.
            AuthenticatedUser current = reauthenticate(accessor);
            if (!current.userId().equals(user.userId())) throw new AccessDeniedException("Chat user mismatch");
			return message;
		}
		if (command == StompCommand.SUBSCRIBE) {
			String destination = accessor.getDestination();
			var matcher = SUBSCRIBE_DESTINATION.matcher(destination == null ? "" : destination);
			if (!matcher.matches()) {
				throw new AccessDeniedException("Invalid chat destination");
			}
			Long roomId;
			try {
				roomId = Long.valueOf(matcher.group(1));
			} catch (NumberFormatException exception) {
				throw new AccessDeniedException("Invalid chat room ID");
			}
            AuthenticatedUser current = reauthenticate(accessor);
            if (!current.userId().equals(user.userId())) throw new AccessDeniedException("Chat user mismatch");
			chatService.validateActiveMember(current.userId(), roomId);
			return message;
		}
		if (command == StompCommand.UNSUBSCRIBE || accessor.getMessageType() == SimpMessageType.HEARTBEAT) {
			return message;
		}
		throw new AccessDeniedException("Unsupported STOMP command");
	}
}
