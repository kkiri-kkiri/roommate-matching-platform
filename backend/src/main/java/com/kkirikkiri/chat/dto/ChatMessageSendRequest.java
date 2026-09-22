package com.kkirikkiri.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** WebSocket 메시지 전송 본문. 발신자와 채팅방은 인증 정보와 목적지에서 확인한다. */
public record ChatMessageSendRequest(
		@NotBlank @Size(max = 2000) String text) {
}
