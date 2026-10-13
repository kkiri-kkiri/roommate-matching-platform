package com.kkirikkiri.community.comment.entity;

import com.kkirikkiri.community.post.entity.CommunityPost;
import com.kkirikkiri.user.entity.User;
import java.util.Objects;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 댓글의 삭제 여부와 무관하게 게시글별 익명 참여 번호를 보존한다. */
@Entity
@Table(name = "COMMUNITY_ANONYMOUS_AUTHOR", uniqueConstraints = {
		@UniqueConstraint(name = "uk_anonymous_post_author", columnNames = {"post_id", "author_id"}),
		@UniqueConstraint(name = "uk_anonymous_post_number", columnNames = {"post_id", "anonymous_number"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommunityAnonymousAuthor {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "anonymous_author_id")
	private Long anonymousAuthorId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "post_id", nullable = false, updatable = false)
	private CommunityPost post;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "author_id", nullable = false, updatable = false)
	private User author;

	@Column(name = "anonymous_number", nullable = false, updatable = false)
	private Long anonymousNumber;

	public CommunityAnonymousAuthor(CommunityPost post, User author, long anonymousNumber) {
		this.post = Objects.requireNonNull(post, "post");
		this.author = Objects.requireNonNull(author, "author");
		if (anonymousNumber < 1) throw new IllegalArgumentException("anonymousNumber must be positive");
		this.anonymousNumber = anonymousNumber;
	}
}
