package com.kkirikkiri.report.entity;

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
import com.kkirikkiri.user.entity.User;

@Entity
@Table(name = "REPORT")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Report {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "report_id")
	private Long reportId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "reporter_id", nullable = false)
	private User reporter;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "target_user_id", nullable = false)
	private User targetUser;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "post_id", nullable = true)
	private CommunityPost post;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "comment_id", nullable = true)
	private CommunityComment comment;

	@Enumerated(EnumType.STRING)
	@Column(name = "report_type", nullable = false, length = 20, columnDefinition = "VARCHAR(20)")
	private ReportType reportType;

	@Column(name = "reason", nullable = false, length = 100)
	private String reason;

	@Column(name = "description", nullable = true, columnDefinition = "TEXT")
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20, columnDefinition = "VARCHAR(20)")
	private ReportStatus status;

	@Column(name = "resolution_action", nullable = true, length = 30)
	private String resolutionAction;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "processed_by", nullable = true)
	private User processedBy;

	@Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME")
	private LocalDateTime createdAt;

	@Column(name = "processed_at", nullable = true, columnDefinition = "DATETIME")
	private LocalDateTime processedAt;

	private Report(User reporter, User targetUser, CommunityPost post, CommunityComment comment,
			ReportType reportType, String reason, String description) {
		this.reporter = Objects.requireNonNull(reporter, "reporter");
		this.targetUser = Objects.requireNonNull(targetUser, "targetUser");
		this.post = post;
		this.comment = comment;
		this.reportType = reportType;
		Objects.requireNonNull(reason, "reason");
		switch (reason) {
			case "욕설·비방", "광고·홍보", "음란성 콘텐츠", "개인정보 노출", "도배·스팸", "기타" -> {
			}
			default -> throw new IllegalArgumentException("unsupported reason");
		}
		if (reason.equals("기타") && (description == null || description.isBlank())) {
			throw new IllegalArgumentException("description is required for 기타");
		}
		this.reason = reason;
		this.description = description;
		this.status = ReportStatus.PENDING;
	}

	/**
	 * 신고자는 서비스에서 인증 사용자 기준으로 결정하고 중복 신고 여부를 확인한다.
	 */
	public static Report forUser(User reporter, User targetUser, String reason, String description) {
		return new Report(reporter, targetUser, null, null, ReportType.USER, reason, description);
	}

	public static Report forPost(User reporter, CommunityPost post, String reason, String description) {
		Objects.requireNonNull(post, "post");
		return new Report(reporter, post.getAuthor(), post, null, ReportType.POST, reason, description);
	}

	public static Report forComment(User reporter, CommunityComment comment, String reason, String description) {
		Objects.requireNonNull(comment, "comment");
		return new Report(reporter, comment.getAuthor(), null, comment, ReportType.COMMENT, reason, description);
	}

	@PrePersist
	protected void onCreate() {
		this.createdAt = LocalDateTime.now();
	}
}
