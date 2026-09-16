package com.kkirikkiri.community.entity;

import java.time.LocalDateTime;
import java.util.Objects;

import com.kkirikkiri.member.entity.Member;
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
	private Member author;

	// Dormitory Entity 도입 시 연관관계로 전환한다.
	@Column(name = "dormitory_id")
	private Long dormitoryId;

	@Column(name = "category", nullable = false, length = 30)
	private String category;

	@Column(name = "title", nullable = false, length = 200)
	private String title;

	@Column(name = "content", nullable = false, columnDefinition = "TEXT")
	private String content;

	@Column(name = "view_count", nullable = false)
	private int viewCount;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20, columnDefinition = "VARCHAR(20)")
	private CommunityStatus status;

	@Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME")
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME")
	private LocalDateTime updatedAt;

	public CommunityPost(Member author, Long dormitoryId, String category, String title, String content) {
		this.author = Objects.requireNonNull(author, "author");
		this.dormitoryId = dormitoryId;
		update(category, title, content);
		this.status = CommunityStatus.ACTIVE;
	}

	public void update(String category, String title, String content) {
		Objects.requireNonNull(category, "category");
		Objects.requireNonNull(title, "title");
		Objects.requireNonNull(content, "content");
		this.category = category;
		this.title = title;
		this.content = content;
	}

	public void increaseViewCount() {
		this.viewCount++;
	}

	public void delete() {
		this.status = CommunityStatus.DELETED;
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
