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

import com.kkirikkiri.user.entity.Dormitory;
import com.kkirikkiri.user.entity.User;

@Entity
@Table(name = "COMMUNITY_POST")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommunityPost {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "post_id")
	private Long postId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "author_id", nullable = false)
	private User author;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "dormitory_id", nullable = true)
	private Dormitory dormitory;

	@Column(name = "category", nullable = false, length = 30)
	private String category;

	@Column(name = "title", nullable = false, length = 100)
	private String title;

	@Column(name = "content", nullable = false, columnDefinition = "TEXT")
	private String content;

	@Column(name = "view_count", nullable = false)
	private int viewCount;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20, columnDefinition = "VARCHAR(20)")
	private CommunityContentStatus status;

	@Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME")
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME")
	private LocalDateTime updatedAt;

	/**
	 * 작성자와 생활관은 서비스에서 인증 사용자 기준으로 결정한다.
	 */
	public CommunityPost(User author, Dormitory dormitory, String category, String title, String content) {
		this.author = Objects.requireNonNull(author, "author");
		this.dormitory = dormitory;
		updateContent(category, title, content);
		this.status = CommunityContentStatus.ACTIVE;
	}

	public void updateContent(String category, String title, String content) {
		Objects.requireNonNull(category, "category");
		Objects.requireNonNull(title, "title");
		Objects.requireNonNull(content, "content");
		if (!category.equals("자유게시판") && !category.equals("나눔게시판")
				&& !category.equals("택배게시판") && !category.equals("민원게시판")) {
			throw new IllegalArgumentException("unsupported category");
		}
		this.category = category;
		this.title = title;
		this.content = content;
	}

	public void increaseViewCount() {
		this.viewCount++;
	}

	/**
	 * 신고 참조를 보존한다. 연결된 댓글/답글의 소프트 삭제는 서비스에서 함께 처리한다.
	 */
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
