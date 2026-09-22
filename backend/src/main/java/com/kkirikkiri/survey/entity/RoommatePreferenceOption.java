package com.kkirikkiri.survey.entity;

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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ROOMMATE_PREFERENCE_OPTION", uniqueConstraints = @UniqueConstraint(
		columnNames = {"preference_id", "criterion_code", "option_value"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoommatePreferenceOption {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "preference_option_id")
	private Long preferenceOptionId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "preference_id", nullable = false)
	private RoommatePreference roommatePreference;

	@Enumerated(EnumType.STRING)
	@Column(name = "criterion_code", nullable = false, length = 40, columnDefinition = "VARCHAR(40)")
	private PreferenceCriterionCode criterionCode;

	@Column(name = "option_value", nullable = false, length = 60)
	private String optionValue;

	/**
	 * 필드별 선택지, 중복 및 '상관없음' 단독 선택 규칙은 서비스에서 검증한다.
	 */
	public RoommatePreferenceOption(RoommatePreference roommatePreference,
			PreferenceCriterionCode criterionCode, String optionValue) {
		this.roommatePreference = Objects.requireNonNull(roommatePreference, "roommatePreference");
		this.criterionCode = Objects.requireNonNull(criterionCode, "criterionCode");
		if (!criterionCode.isMultiSelect()) {
			throw new IllegalArgumentException("criterionCode must support multiple selections");
		}
		this.optionValue = Objects.requireNonNull(optionValue, "optionValue");
	}

	public void changeOptionValue(String optionValue) {
		this.optionValue = Objects.requireNonNull(optionValue, "optionValue");
	}
}
