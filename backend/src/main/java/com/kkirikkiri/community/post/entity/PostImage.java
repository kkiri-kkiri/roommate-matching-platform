package com.kkirikkiri.community.post.entity;

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

@Entity
@Table(name = "POST_IMAGE")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostImage {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "post_image_id")
	private Long postImageId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "post_id", nullable = false)
	private CommunityPost post;

	@Column(name = "image_url", nullable = false, length = 500)
	private String imageUrl;

	@Column(name = "original_name", nullable = true, length = 255)
	private String originalName;

	@Column(name = "sort_order", nullable = false)
	private int sortOrder;

	@Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME")
	private LocalDateTime createdAt;

	public PostImage(CommunityPost post, String imageUrl, String originalName, int sortOrder) {
		this.post = Objects.requireNonNull(post, "post");
		this.imageUrl = Objects.requireNonNull(imageUrl, "imageUrl");
		this.originalName = originalName;
		this.sortOrder = sortOrder;
	}

	public void changeSortOrder(int sortOrder) {
		this.sortOrder = sortOrder;
	}

	@PrePersist
	protected void onCreate() {
		this.createdAt = LocalDateTime.now();
	}
}
