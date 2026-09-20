package com.kkirikkiri.chat.entity;

import java.time.LocalDateTime;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.kkirikkiri.user.entity.User;

@Entity
@Table(name = "CHAT_MESSAGE")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "message_id")
	private Long messageId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "chat_room_id", nullable = false)
	private ChatRoom chatRoom;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "sender_id", nullable = false)
	private User sender;

	@Column(name = "content", nullable = false, length = 2000)
	private String content;

	@Column(name = "sent_at", nullable = false, updatable = false, columnDefinition = "DATETIME")
	private LocalDateTime sentAt;

	/**
	 * 발신자의 인증 및 활성 참여 여부는 서비스에서 검증한다.
	 */
	public ChatMessage(ChatRoom chatRoom, User sender, String content) {
		this.chatRoom = Objects.requireNonNull(chatRoom, "chatRoom");
		this.sender = Objects.requireNonNull(sender, "sender");
		Objects.requireNonNull(content, "content");
		if (content.codePointCount(0, content.length()) > 2000) {
			throw new IllegalArgumentException("content must not exceed 2000 characters");
		}
		this.content = content;
	}

	@PrePersist
	protected void onCreate() {
		this.sentAt = LocalDateTime.now();
	}
}
