package com.kkirikkiri.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.hibernate.validator.constraints.CodePointLength;

import com.kkirikkiri.chat.entity.ChatMessage;

/** WebSocket 메시지 전송 본문. 발신자와 채팅방은 인증 정보와 목적지에서 확인한다. */
public record ChatMessageSendRequest(
		@NotBlank
		@CodePointLength(max = ChatMessage.MAX_CONTENT_LENGTH, message = "{jakarta.validation.constraints.Size.message}")
		String text,
		@Pattern(regexp = "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
		String clientMessageId) {
	public ChatMessageSendRequest(String text) {
		this(text, null);
	}
}
