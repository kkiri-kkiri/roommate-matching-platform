package com.kkirikkiri.chat.dto;

import java.time.LocalDateTime;

import com.kkirikkiri.chat.entity.ChatMessage;

/** 메시지 목록 항목. senderType과 read는 서비스에서 판단하여 전달한다. */
public record ChatMessageResponse(
		Long id,
		Long senderId,
		String senderType,
		String text,
		LocalDateTime sentAt,
		boolean read) {

	public static ChatMessageResponse from(ChatMessage message, String senderType, boolean read) {
		return new ChatMessageResponse(message.getMessageId(), message.getSender().getUserId(),
				senderType, message.getContent(), message.getSentAt(), read);
	}
}
