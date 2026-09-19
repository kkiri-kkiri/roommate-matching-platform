package com.kkirikkiri.community.entity;

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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.kkirikkiri.user.entity.User;

@Entity
@Table(name = "COMMUNITY_COMMENT")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommunityComment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "comment_id")
	private Long commentId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "post_id", nullable = false)
	private CommunityPost post;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "author_id", nullable = false)
	private User author;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "parent_comment_id", nullable = true)
	private CommunityComment parentComment;

	@Column(name = "content", nullable = false, columnDefinition = "TEXT")
	private String content;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20, columnDefinition = "VARCHAR(20)")
	private CommunityContentStatus status;

	@Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME")
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME")
	private LocalDateTime updatedAt;

	public CommunityComment(CommunityPost post, User author, CommunityComment parentComment, String content) {
		this.post = Objects.requireNonNull(post, "post");
		this.author = Objects.requireNonNull(author, "author");
		if (parentComment != null) {
			if (parentComment.getParentComment() != null) {
				throw new IllegalArgumentException("replies cannot have replies");
			}
			CommunityPost parentPost = parentComment.getPost();
			if (post != parentPost && (post.getPostId() == null
					|| !post.getPostId().equals(parentPost.getPostId()))) {
				throw new IllegalArgumentException("parentComment must belong to the same post");
			}
		}
		this.parentComment = parentComment;
		this.content = Objects.requireNonNull(content, "content");
		this.status = CommunityContentStatus.ACTIVE;
	}

	public void delete() {
		this.status = CommunityContentStatus.DELETED;
	}

	@PrePersist
	protected void onCreate() {
		LocalDateTime now = LocalDateTime.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	protected void onUpdate() {
		this.updatedAt = LocalDateTime.now();
	}
}
