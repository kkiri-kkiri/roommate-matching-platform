package com.kkirikkiri.auth.entity;

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
@Table(name = "PASSWORD_RESET_TOKEN")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PasswordResetToken {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "password_reset_token_id")
	private Long passwordResetTokenId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(name = "token_hash", nullable = false, unique = true, length = 255)
	private String tokenHash;

	@Column(name = "expires_at", nullable = false, columnDefinition = "DATETIME")
	private LocalDateTime expiresAt;

	@Column(name = "used_at", nullable = true, columnDefinition = "DATETIME")
	private LocalDateTime usedAt;

	@Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME")
	private LocalDateTime createdAt;

	/**
	 * tokenHash에는 토큰 원문이 아닌 외부에서 해시 처리한 값을 전달한다.
	 */
	public PasswordResetToken(User user, String tokenHash, LocalDateTime expiresAt) {
		this.user = Objects.requireNonNull(user, "user");
		this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash");
		this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
	}

	public boolean isExpired(LocalDateTime now) {
		return !expiresAt.isAfter(Objects.requireNonNull(now, "now"));
	}

	public boolean isUsed() {
		return usedAt != null;
	}

	/**
	 * 비밀번호 변경 성공 후 같은 트랜잭션에서 호출한다.
	 */
	public void markUsed(LocalDateTime usedAt) {
		Objects.requireNonNull(usedAt, "usedAt");
		if (isUsed() || isExpired(usedAt)) {
			throw new IllegalStateException("이미 사용되었거나 만료된 비밀번호 재설정 토큰입니다.");
		}
		this.usedAt = usedAt;
	}

	@PrePersist
	protected void onCreate() {
		this.createdAt = LocalDateTime.now();
	}
}
