package com.kkirikkiri.chat.entity;

import java.time.LocalDateTime;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

import com.kkirikkiri.community.comment.entity.CommunityComment;
import com.kkirikkiri.community.post.entity.CommunityPost;

@Entity
@Table(name = "CHAT_ROOM")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoom {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "chat_room_id")
	private Long chatRoomId;

	@Column(name = "room_key", nullable = false, unique = true, length = 100)
	private String roomKey;

	@Enumerated(EnumType.STRING)
	@Column(name = "category", nullable = false, length = 20, columnDefinition = "VARCHAR(20)")
	private ChatRoomCategory category;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "source_post_id", nullable = true)
	private CommunityPost sourcePost;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "source_comment_id", nullable = true)
	private CommunityComment sourceComment;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20, columnDefinition = "VARCHAR(20)")
	private ChatRoomStatus status;

	@Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME")
	private LocalDateTime createdAt;

	/**
	 * 서비스에서 두 사용자의 참여자 행을 함께 생성한다.
	 * 댓글에서 시작한 경우 sourceComment만 전달하고 게시글은 댓글의 post로 조회한다.
	 */
	public ChatRoom(Long firstUserId, Long secondUserId, ChatRoomCategory category,
			CommunityPost sourcePost, CommunityComment sourceComment) {
		this.roomKey = createRoomKey(firstUserId, secondUserId, category, sourcePost, sourceComment);
		if (category == ChatRoomCategory.ROOMMATE && (sourcePost != null || sourceComment != null)) {
			throw new IllegalArgumentException("roommate chat cannot have a community source");
		}
		if (category == ChatRoomCategory.COMMUNITY && (sourcePost == null) == (sourceComment == null)) {
			throw new IllegalArgumentException("community chat requires exactly one source");
		}
		this.category = category;
		this.sourcePost = sourcePost;
		this.sourceComment = sourceComment;
		this.status = ChatRoomStatus.ACTIVE;
	}

	/**
	 * 사용자 순서와 무관한 키로 기존 방을 조회한다. 동시 생성 충돌은 서비스에서 처리한다.
	 */
	public static String createRoomKey(Long firstUserId, Long secondUserId, ChatRoomCategory category) {
		Objects.requireNonNull(firstUserId, "firstUserId");
		Objects.requireNonNull(secondUserId, "secondUserId");
		Objects.requireNonNull(category, "category");
		if (firstUserId.equals(secondUserId)) {
			throw new IllegalArgumentException("chat requires two different users");
		}
		return category.name() + ":" + Math.min(firstUserId, secondUserId)
				+ ":" + Math.max(firstUserId, secondUserId);
	}

	/** 익명 출처는 방 키에 고정하여 게시글 수정/삭제 후에도 익명성을 유지한다. */
	public static String createRoomKey(Long firstUserId, Long secondUserId, ChatRoomCategory category,
			CommunityPost post, CommunityComment comment) {
		String key = createRoomKey(firstUserId, secondUserId, category);
		if (post != null && post.isAnonymous()) return key + ":POST:" + post.getPostId();
		if (comment != null && comment.isAnonymous()) return key + ":COMMENT:" + comment.getCommentId();
		return key;
	}

	public boolean isAnonymousAuthor(Long userId) {
		return (roomKey.contains(":POST:") && sourcePost != null
				&& sourcePost.getAuthor().getUserId().equals(userId))
				|| (roomKey.contains(":COMMENT:") && sourceComment != null
				&& sourceComment.getAuthor().getUserId().equals(userId));
	}

	/** 생성 API로 일반 게시글에서 진입한 경우에만 호출한다. */
	public void updateSourcePost(CommunityPost post) {
		if (category != ChatRoomCategory.COMMUNITY || post == null || post.isAnonymous()
				|| roomKey.contains(":POST:") || roomKey.contains(":COMMENT:")) {
			throw new IllegalArgumentException("only regular community rooms can update their source");
		}
		this.sourcePost = post;
		this.sourceComment = null;
	}

	@PrePersist
	protected void onCreate() {
		this.createdAt = LocalDateTime.now();
	}
}
