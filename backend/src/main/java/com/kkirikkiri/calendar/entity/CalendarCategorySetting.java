package com.kkirikkiri.calendar.entity;

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

import com.kkirikkiri.user.entity.User;

@Entity
@Table(name = "CALENDAR_CATEGORY_SETTING", uniqueConstraints = {
		@UniqueConstraint(columnNames = {"user_id", "category_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CalendarCategorySetting {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "category_setting_id")
	private Long categorySettingId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "category_id", nullable = false)
	private CalendarCategory category;

	@Column(name = "display_name", nullable = false, length = 30)
	private String displayName;

	@Column(name = "color", nullable = false, length = 7, columnDefinition = "CHAR(7)")
	private String color;

	@Column(name = "is_enabled", nullable = false, columnDefinition = "BOOLEAN")
	private boolean enabled;

	public CalendarCategorySetting(User user, CalendarCategory category, String displayName,
			String color, boolean enabled) {
		this.user = Objects.requireNonNull(user, "user");
		this.category = Objects.requireNonNull(category, "category");
		changeSettings(displayName, color, enabled);
	}

	/** 소유권과 사용자별 이름 중복은 서비스에서 검증하고 변경 후의 전체 값을 전달한다. */
	public void changeSettings(String displayName, String color, boolean enabled) {
		Objects.requireNonNull(displayName, "displayName");
		Objects.requireNonNull(color, "color");
		if (category.isNameLocked() && !category.getDefaultName().equals(displayName)) {
			throw new IllegalArgumentException("category name is locked");
		}
		this.displayName = displayName;
		this.color = color;
		this.enabled = enabled;
	}
}
