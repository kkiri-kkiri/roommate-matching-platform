package com.kkirikkiri.auth.entity;

import java.time.LocalDateTime;
import java.util.Objects;

import com.kkirikkiri.user.entity.User;

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

	@Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME")
	private LocalDateTime createdAt;

	/**
	 * 회원가입 전에는 user가 null일 수 있다. encodedCode에는 해시 처리된 인증번호를 전달한다.
	 */
	public EmailVerification(User user, String email, EmailVerificationPurpose purpose,
			String encodedCode, LocalDateTime expiresAt) {
		this.user = user;
		this.email = Objects.requireNonNull(email, "email");
		this.purpose = Objects.requireNonNull(purpose, "purpose");
		this.verificationCode = Objects.requireNonNull(encodedCode, "encodedCode");
		this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
	}

	public boolean isExpired(LocalDateTime now) {
		return !Objects.requireNonNull(now, "now").isBefore(expiresAt);
	}

	/**
	 * 인증번호 해시의 일치 여부는 서비스에서 검증한 뒤 호출한다.
	 */
	public void verify(LocalDateTime now) {
		Objects.requireNonNull(now, "now");
		if (verifiedAt != null) {
			throw new IllegalStateException("이미 확인된 이메일 인증입니다.");
		}
		if (isExpired(now)) {
			throw new IllegalStateException("만료된 이메일 인증입니다.");
		}
		this.verifiedAt = now;
	}

	@PrePersist
	protected void onCreate() {
		this.createdAt = LocalDateTime.now();
	}
}
