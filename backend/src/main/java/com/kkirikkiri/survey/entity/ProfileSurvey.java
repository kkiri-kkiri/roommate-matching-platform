package com.kkirikkiri.survey.entity;

import java.time.LocalDateTime;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.kkirikkiri.user.entity.User;

@Entity
@Table(name = "PROFILE_SURVEY")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProfileSurvey {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "profile_survey_id")
	private Long profileSurveyId;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, unique = true)
	private User user;

	@Column(name = "sleep_time", nullable = false, length = 50)
	private String sleepTime;

	@Column(name = "wake_time", nullable = false, length = 50)
	private String wakeTime;

	@Column(name = "sleep_talking", nullable = false, length = 20)
	private String sleepTalking;

	@Column(name = "snoring", nullable = false, length = 20)
	private String snoring;

	@Column(name = "teeth_grinding", nullable = false, length = 20)
	private String teethGrinding;

	@Column(name = "sleep_sensitivity", nullable = false, length = 50)
	private String sleepSensitivity;

	@Column(name = "indoor_eating", nullable = false, length = 50)
	private String indoorEating;

	@Column(name = "smoking", nullable = false, length = 30)
	private String smoking;

	@Column(name = "lighting_preference", nullable = false, length = 30)
	private String lightingPreference;

	@Column(name = "phone_call", nullable = false, length = 50)
	private String phoneCall;

	@Column(name = "media_sound", nullable = false, length = 100)
	private String mediaSound;

	@Column(name = "shower_time", nullable = false, length = 30)
	private String showerTime;

	@Column(name = "shower_duration", nullable = false, length = 30)
	private String showerDuration;

	@Column(name = "cleaning_frequency", nullable = false, length = 30)
	private String cleaningFrequency;

	@Column(name = "cleaning_style", nullable = false, length = 50)
	private String cleaningStyle;

	@Column(name = "meal", nullable = false, length = 60)
	private String meal;

	@Column(name = "roommate_interaction", nullable = false, length = 50)
	private String roommateInteraction;

	@Column(name = "temperature_preference", nullable = false, length = 30)
	private String temperaturePreference;

	@Column(name = "scent_product_use", nullable = false, length = 50)
	private String scentProductUse;

	@Column(name = "desktop_use", nullable = false, length = 50)
	private String desktopUse;

	@Column(name = "shared_items", nullable = false, length = 50)
	private String sharedItems;

	@Column(name = "completed", nullable = false)
	private boolean completed;

	@Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME")
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME")
	private LocalDateTime updatedAt;

	public ProfileSurvey(User user, String sleepTime, String wakeTime, String sleepTalking,
			String snoring, String teethGrinding, String sleepSensitivity, String indoorEating,
			String smoking, String lightingPreference, String phoneCall, String mediaSound,
			String showerTime, String showerDuration, String cleaningFrequency,
			String cleaningStyle, String meal, String roommateInteraction, String temperaturePreference,
			String scentProductUse, String desktopUse, String sharedItems,
			boolean completed) {
		this.user = Objects.requireNonNull(user, "user");
		updateAnswers(sleepTime, wakeTime, sleepTalking, snoring, teethGrinding, sleepSensitivity,
				indoorEating, smoking, lightingPreference, phoneCall, mediaSound, showerTime,
				showerDuration, cleaningFrequency, cleaningStyle, meal,
				roommateInteraction, temperaturePreference, scentProductUse,
				desktopUse, sharedItems, completed);
	}

	/**
	 * 선택지와 다중선택 응답의 유효성, 작성 완료 여부는 서비스에서 검증한다.
	 * 다중선택만 변경한 경우에도 함께 호출하여 설문의 마지막 수정 일시를 갱신한다.
	 */
	public void updateAnswers(String sleepTime, String wakeTime, String sleepTalking,
			String snoring, String teethGrinding, String sleepSensitivity, String indoorEating,
			String smoking, String lightingPreference, String phoneCall, String mediaSound,
			String showerTime, String showerDuration, String cleaningFrequency,
			String cleaningStyle, String meal, String roommateInteraction, String temperaturePreference,
			String scentProductUse, String desktopUse, String sharedItems,
			boolean completed) {
		this.sleepTime = Objects.requireNonNull(sleepTime, "sleepTime");
		this.wakeTime = Objects.requireNonNull(wakeTime, "wakeTime");
		this.sleepTalking = Objects.requireNonNull(sleepTalking, "sleepTalking");
		this.snoring = Objects.requireNonNull(snoring, "snoring");
		this.teethGrinding = Objects.requireNonNull(teethGrinding, "teethGrinding");
		this.sleepSensitivity = Objects.requireNonNull(sleepSensitivity, "sleepSensitivity");
		this.indoorEating = Objects.requireNonNull(indoorEating, "indoorEating");
		this.smoking = Objects.requireNonNull(smoking, "smoking");
		this.lightingPreference = Objects.requireNonNull(lightingPreference, "lightingPreference");
		this.phoneCall = Objects.requireNonNull(phoneCall, "phoneCall");
		this.mediaSound = Objects.requireNonNull(mediaSound, "mediaSound");
		this.showerTime = Objects.requireNonNull(showerTime, "showerTime");
		this.showerDuration = Objects.requireNonNull(showerDuration, "showerDuration");
		this.cleaningFrequency = Objects.requireNonNull(cleaningFrequency, "cleaningFrequency");
		this.cleaningStyle = Objects.requireNonNull(cleaningStyle, "cleaningStyle");
		this.meal = Objects.requireNonNull(meal, "meal");
		this.roommateInteraction = Objects.requireNonNull(roommateInteraction, "roommateInteraction");
		this.temperaturePreference = Objects.requireNonNull(temperaturePreference, "temperaturePreference");
		this.scentProductUse = Objects.requireNonNull(scentProductUse, "scentProductUse");
		this.desktopUse = Objects.requireNonNull(desktopUse, "desktopUse");
		this.sharedItems = Objects.requireNonNull(sharedItems, "sharedItems");
		this.completed = completed;
		this.updatedAt = LocalDateTime.now();
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
