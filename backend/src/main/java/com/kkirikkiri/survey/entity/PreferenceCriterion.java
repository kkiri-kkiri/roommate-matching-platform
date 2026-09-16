package com.kkirikkiri.survey.entity;

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
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "PREFERENCE_CRITERION", uniqueConstraints = {
		@UniqueConstraint(columnNames = {"preference_id", "criterion_code"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PreferenceCriterion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "preference_criterion_id")
	private Long preferenceCriterionId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "preference_id", nullable = false)
	private RoommatePreference roommatePreference;

	@Column(name = "criterion_code", nullable = false, length = 40)
	private String criterionCode;

	@Min(1)
	@Max(5)
	@Column(name = "weight", nullable = false, columnDefinition = "TINYINT")
	private byte weight;

	@Column(name = "is_required", nullable = false, columnDefinition = "BOOLEAN")
	private boolean required;

	public PreferenceCriterion(RoommatePreference roommatePreference, String criterionCode, int weight, boolean required) {
		this.roommatePreference = Objects.requireNonNull(roommatePreference, "roommatePreference");
		this.criterionCode = Objects.requireNonNull(criterionCode, "criterionCode");
		updateSettings(weight, required);
	}

	public void updateSettings(int weight, boolean required) {
		if (weight < 1 || weight > 5) {
			throw new IllegalArgumentException("weight must be between 1 and 5");
		}
		this.weight = (byte) weight;
		this.required = required;
	}
}
