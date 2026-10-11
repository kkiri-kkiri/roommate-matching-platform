package com.kkirikkiri.chat.dto;

import java.time.LocalDateTime;

import com.kkirikkiri.chat.entity.ChatMessage;

/** WebSocket 구독자에게 전달하는 메시지. 읽음 여부는 서비스에서 전달한다. */
public record ChatMessageBroadcastResponse(
		Long id,
		Long roomId,
		Long senderId,
		String text,
		LocalDateTime sentAt,
		boolean read) {

	public static ChatMessageBroadcastResponse from(ChatMessage message, boolean read) {
		return new ChatMessageBroadcastResponse(message.getMessageId(),
				message.getChatRoom().getChatRoomId(), message.getChatRoom().isAnonymousAuthor(message.getSender().getUserId())
						? null : message.getSender().getUserId(),
				message.getContent(), message.getSentAt(), read);
	}
}
