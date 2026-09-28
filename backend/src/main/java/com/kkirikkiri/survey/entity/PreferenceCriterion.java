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
@Table(name = "PREFERENCE_CRITERION", uniqueConstraints = @UniqueConstraint(
		columnNames = {"preference_id", "criterion_code"}))
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

	@Enumerated(EnumType.STRING)
	@Column(name = "criterion_code", nullable = false, length = 40, columnDefinition = "VARCHAR(40)")
	private PreferenceCriterionCode criterionCode;

	@Column(name = "weight", nullable = false, columnDefinition = "TINYINT")
	private byte weight;

	public PreferenceCriterion(RoommatePreference roommatePreference,
			PreferenceCriterionCode criterionCode, int weight) {
		this.roommatePreference = Objects.requireNonNull(roommatePreference, "roommatePreference");
		this.criterionCode = Objects.requireNonNull(criterionCode, "criterionCode");
		updateSettings(weight);
	}

	public void updateSettings(int weight) {
		if (weight < 1 || weight > 5) {
			throw new IllegalArgumentException("weight must be between 1 and 5");
		}
		this.weight = (byte) weight;
	}
}
