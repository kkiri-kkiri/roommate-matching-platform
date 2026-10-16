package com.kkirikkiri.calendar.entity;

import java.time.LocalDateTime;
import java.time.ZoneId;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.kkirikkiri.user.entity.User;

@Entity
@Table(name = "CALENDAR_CATEGORY")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CalendarCategory {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "category_id")
	private Long categoryId;

	@Column(name = "category_code", nullable = false, unique = true, length = 50)
	private String categoryCode;

	@Column(name = "default_name", nullable = false, length = 50)
	private String defaultName;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "owner_user_id", nullable = true)
	private User ownerUser;

	@Column(name = "name_locked", nullable = false, columnDefinition = "BOOLEAN")
	private boolean nameLocked;

	@Column(name = "is_system", nullable = false, columnDefinition = "BOOLEAN")
	private boolean system;

	@Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME")
	@JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
	private LocalDateTime createdAt;

	/**
	 * 사용자 카테고리는 고유한 CUSTOM-* 코드와 소유자를 전달하고 사용자 설정도 함께 생성한다.
	 */
	public CalendarCategory(String categoryCode, String defaultName, User ownerUser,
			boolean nameLocked, boolean system) {
		this.categoryCode = Objects.requireNonNull(categoryCode, "categoryCode");
		this.defaultName = Objects.requireNonNull(defaultName, "defaultName");
		this.ownerUser = ownerUser;
		this.nameLocked = nameLocked;
		this.system = system;
	}

	/** 서비스에서 소유자를 확인한 뒤 사용자 카테고리의 기본 이름을 변경한다. */
	public void changeName(String defaultName) {
		Objects.requireNonNull(defaultName, "defaultName");
		if (system || nameLocked || ownerUser == null) {
			throw new IllegalStateException("only custom category names can be changed");
		}
		this.defaultName = defaultName;
	}

	@PrePersist
	protected void onCreate() {
		this.createdAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
	}
}
