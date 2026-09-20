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

import com.kkirikkiri.user.entity.User;

@Entity
@Table(name = "CHAT_ROOM_MEMBER", uniqueConstraints = {
		@UniqueConstraint(columnNames = {"chat_room_id", "user_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoomMember {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "chat_room_member_id")
	private Long chatRoomMemberId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "chat_room_id", nullable = false)
	private ChatRoom chatRoom;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(name = "is_pinned", nullable = false, columnDefinition = "BOOLEAN")
	private boolean pinned;

	@Column(name = "is_muted", nullable = false, columnDefinition = "BOOLEAN")
	private boolean muted;

	@Column(name = "is_left", nullable = false, columnDefinition = "BOOLEAN")
	private boolean left;

	@Column(name = "joined_at", nullable = false, updatable = false, columnDefinition = "DATETIME")
	private LocalDateTime joinedAt;

	@Column(name = "last_read_at", nullable = true, columnDefinition = "DATETIME")
	private LocalDateTime lastReadAt;

	@Column(name = "left_at", nullable = true, columnDefinition = "DATETIME")
	private LocalDateTime leftAt;

	/**
	 * 동일 방의 활성 참여자 수와 roomKey의 사용자 일치 여부는 서비스에서 검증한다.
	 */
	public ChatRoomMember(ChatRoom chatRoom, User user) {
		this.chatRoom = Objects.requireNonNull(chatRoom, "chatRoom");
		this.user = Objects.requireNonNull(user, "user");
	}

	/**
	 * null인 설정은 유지한다. 사용자별 고정 방 최대 2개 제한은 서비스에서 검증한다.
	 */
	public void changeSettings(Boolean pinned, Boolean muted) {
		if (pinned != null) {
			this.pinned = pinned;
		}
		if (muted != null) {
			this.muted = muted;
		}
	}

	/**
	 * 서비스에서 읽음 처리 기준 시각을 전달한다. 늦게 도착한 요청으로 시각을 되돌리지 않는다.
	 */
	public void markRead(LocalDateTime readAt) {
		Objects.requireNonNull(readAt, "readAt");
		if (this.lastReadAt == null || readAt.isAfter(this.lastReadAt)) {
			this.lastReadAt = readAt;
		}
	}

	public void leave() {
		if (!this.left) {
			this.left = true;
			this.leftAt = LocalDateTime.now();
		}
	}

	@PrePersist
	protected void onCreate() {
		this.joinedAt = LocalDateTime.now();
	}
}
