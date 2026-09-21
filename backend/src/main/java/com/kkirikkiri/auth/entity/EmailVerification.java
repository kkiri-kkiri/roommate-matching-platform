package com.kkirikkiri.auth.entity;

import java.time.LocalDateTime;
import java.time.ZoneId;
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

import com.kkirikkiri.user.entity.User;

@Entity
@Table(name = "EMAIL_VERIFICATION")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailVerification {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "verification_id")
	private Long verificationId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = true)
	private User user;

	@Column(name = "email", nullable = false, length = 100)
	private String email;

	@Enumerated(EnumType.STRING)
	@Column(name = "purpose", nullable = false, length = 30, columnDefinition = "VARCHAR(30)")
	private EmailVerificationPurpose purpose;

	@Column(name = "verification_code", nullable = false, length = 255)
	private String verificationCode;

	@Column(name = "expires_at", nullable = false, columnDefinition = "DATETIME")
	private LocalDateTime expiresAt;

	@Column(name = "verified_at", nullable = true, columnDefinition = "DATETIME")
	private LocalDateTime verifiedAt;

	@Column(name = "verification_token_hash", nullable = true, length = 255)
	private String verificationTokenHash;

	@Column(name = "verification_token_expires_at", nullable = true, columnDefinition = "DATETIME")
	private LocalDateTime verificationTokenExpiresAt;

	@Column(name = "consumed_at", nullable = true, columnDefinition = "DATETIME")
	private LocalDateTime consumedAt;

	@Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME")
	private LocalDateTime createdAt;

	/**
	 * 가입 전 인증의 user는 null이며, encodedVerificationCode에는 해시 처리한 인증번호를 전달한다.
	 */
	public EmailVerification(User user, String email, EmailVerificationPurpose purpose,
			String encodedVerificationCode, LocalDateTime expiresAt) {
		this.user = user;
		this.email = Objects.requireNonNull(email, "email");
		this.purpose = Objects.requireNonNull(purpose, "purpose");
		this.verificationCode = Objects.requireNonNull(encodedVerificationCode, "encodedVerificationCode");
		this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
	}

	/**
	 * 서비스에서 인증번호 일치 여부를 검증한 뒤 토큰 해시와 만료 시각을 전달한다.
	 */
	public void markVerified(LocalDateTime verifiedAt, String tokenHash, LocalDateTime tokenExpiresAt) {
		Objects.requireNonNull(verifiedAt, "verifiedAt");
		Objects.requireNonNull(tokenHash, "tokenHash");
		Objects.requireNonNull(tokenExpiresAt, "tokenExpiresAt");
		if (isVerified() || isConsumed() || isCodeExpired(verifiedAt)
				|| tokenHash.isBlank() || !tokenExpiresAt.isAfter(verifiedAt)) {
			throw new IllegalStateException("이메일 인증을 완료할 수 없습니다.");
		}
		this.verifiedAt = verifiedAt;
		this.verificationTokenHash = tokenHash;
		this.verificationTokenExpiresAt = tokenExpiresAt;
	}

	public boolean isCodeExpired(LocalDateTime now) {
		return !expiresAt.isAfter(Objects.requireNonNull(now, "now"));
	}

	public boolean isVerified() {
		return verifiedAt != null;
	}

	public boolean isConsumed() {
		return consumedAt != null;
	}

	public boolean isTokenUsable(LocalDateTime now) {
		Objects.requireNonNull(now, "now");
		return isVerified() && !isConsumed() && verificationTokenHash != null
				&& verificationTokenExpiresAt != null && verificationTokenExpiresAt.isAfter(now);
	}

	public void consume(LocalDateTime now) {
		if (!isTokenUsable(now)) {
			throw new IllegalStateException("만료되었거나 사용된 이메일 인증 토큰입니다.");
		}
		this.consumedAt = now;
	}

	@PrePersist
	protected void onCreate() {
		this.createdAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
	}
}
