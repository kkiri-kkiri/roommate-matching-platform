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
@Table(name = "PROFILE_SURVEY_OPTION", uniqueConstraints = @UniqueConstraint(
		columnNames = {"profile_survey_id", "field_code", "option_value"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProfileSurveyOption {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "profile_survey_option_id")
	private Long profileSurveyOptionId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "profile_survey_id", nullable = false)
	private ProfileSurvey profileSurvey;

	@Enumerated(EnumType.STRING)
	@Column(name = "field_code", nullable = false, length = 30, columnDefinition = "VARCHAR(30)")
	private ProfileSurveyFieldCode fieldCode;

	@Column(name = "option_value", nullable = false, length = 30)
	private String optionValue;

	/**
	 * 필드별 선택지, 중복 및 mealPlan의 '신청 안 함' 단독 선택 규칙은 서비스에서 검증한다.
	 */
	public ProfileSurveyOption(ProfileSurvey profileSurvey, ProfileSurveyFieldCode fieldCode,
			String optionValue) {
		this.profileSurvey = Objects.requireNonNull(profileSurvey, "profileSurvey");
		this.fieldCode = Objects.requireNonNull(fieldCode, "fieldCode");
		this.optionValue = Objects.requireNonNull(optionValue, "optionValue");
	}

	public void changeOptionValue(String optionValue) {
		this.optionValue = Objects.requireNonNull(optionValue, "optionValue");
	}
}
