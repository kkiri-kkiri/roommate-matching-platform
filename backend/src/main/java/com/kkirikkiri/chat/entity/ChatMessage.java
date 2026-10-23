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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

import com.kkirikkiri.user.entity.User;

@Entity
@Table(name = "CHAT_MESSAGE", uniqueConstraints = @UniqueConstraint(name = "uk_chat_message_client",
		columnNames = {"chat_room_id", "sender_id", "client_message_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@DynamicUpdate
public class ChatMessage {

	/** 메시지 최대 길이: UTF-16 코드 단위가 아닌 유니코드 코드 포인트 기준. */
	public static final int MAX_CONTENT_LENGTH = 2000;

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

	@Column(name = "content", nullable = false, length = MAX_CONTENT_LENGTH)
	private String content;

	/** Optional durable key for retries of the same logical send. Legacy sends remain null. */
	@Column(name = "client_message_id", length = 36, updatable = false)
	private String clientMessageId;

	@Column(name = "sent_at", nullable = false, updatable = false, columnDefinition = "DATETIME")
	private LocalDateTime sentAt;

	/** Receipt for the other participant in this two-person room; unrelated to timestamp ordering. */
	@Column(name = "is_read", nullable = false, columnDefinition = "BOOLEAN")
	private boolean read;

	public void markRead() {
		this.read = true;
	}

	/**
	 * 발신자의 인증 및 활성 참여 여부는 서비스에서 검증한다.
	 */
	public ChatMessage(ChatRoom chatRoom, User sender, String content) {
		this(chatRoom, sender, content, null);
	}

	public ChatMessage(ChatRoom chatRoom, User sender, String content, String clientMessageId) {
		this.chatRoom = Objects.requireNonNull(chatRoom, "chatRoom");
		this.sender = Objects.requireNonNull(sender, "sender");
		Objects.requireNonNull(content, "content");
		if (content.codePointCount(0, content.length()) > MAX_CONTENT_LENGTH) {
			throw new IllegalArgumentException("content must not exceed 2000 characters");
		}
		this.content = content;
		this.clientMessageId = clientMessageId;
	}

	@PrePersist
	protected void onCreate() {
		this.sentAt = LocalDateTime.now();
	}
}
