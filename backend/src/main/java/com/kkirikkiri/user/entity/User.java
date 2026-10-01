package com.kkirikkiri.user.entity;

import java.time.LocalDateTime;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "USER")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "user_id")
	private Long userId;

	@Column(name = "password", nullable = false, length = 255)
	private String password;

	@Column(name = "school_email", nullable = false, unique = true, length = 100)
	private String schoolEmail;

	@Column(name = "email_verified", nullable = false)
	private boolean emailVerified;

	@Enumerated(EnumType.STRING)
	@Column(name = "role", nullable = false, length = 20, columnDefinition = "VARCHAR(20)")
	private UserRole role;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20, columnDefinition = "VARCHAR(20)")
	private UserStatus status;

	@Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME")
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME")
	private LocalDateTime updatedAt;

	/**
	 * encodedPassword에는 외부에서 단방향 해시 처리한 비밀번호를 전달한다.
	 */
	public User(String encodedPassword, String schoolEmail) {
		this.password = Objects.requireNonNull(encodedPassword, "encodedPassword");
		this.schoolEmail = Objects.requireNonNull(schoolEmail, "schoolEmail");
		this.role = UserRole.USER;
		this.status = UserStatus.ACTIVE;
	}

	public User(String encodedPassword, String schoolEmail, UserRole role) {
    this.password = Objects.requireNonNull(encodedPassword, "encodedPassword");
    this.schoolEmail = Objects.requireNonNull(schoolEmail, "schoolEmail");
    this.role = Objects.requireNonNull(role, "role");
    this.status = UserStatus.ACTIVE;
}

	/**
	 * 현재 비밀번호 검증과 새 비밀번호 해시는 서비스에서 처리한다.
	 */
	public void changePassword(String encodedPassword) {
		this.password = Objects.requireNonNull(encodedPassword, "encodedPassword");
	}

	/**
	 * 탈퇴한 회원은 정상 또는 이용 정지 상태로 되돌릴 수 없다.
	 */
	public void changeStatus(UserStatus status) {
		Objects.requireNonNull(status, "status");
		if (this.status == UserStatus.WITHDRAWN && status != UserStatus.WITHDRAWN) {
			throw new IllegalStateException("탈퇴한 회원의 상태는 변경할 수 없습니다.");
		}
		this.status = status;
	}

	/**
	 * 서비스에서 학교 이메일 인증 완료 여부를 검증한 뒤 호출한다.
	 */
	public void verifyEmail() {
		this.emailVerified = true;
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
