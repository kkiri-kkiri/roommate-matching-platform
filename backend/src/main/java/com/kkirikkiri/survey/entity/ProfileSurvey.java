package com.kkirikkiri.survey.entity;

import java.time.LocalDateTime;
import java.util.Objects;

import com.kkirikkiri.member.entity.Member;

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
	private Member member;

	@Column(name = "sleep_time", length = 50)
	private String sleepTime;

	@Column(name = "wake_time", length = 50)
	private String wakeTime;

	@Column(name = "sleep_talking", length = 20)
	private String sleepTalking;

	@Column(name = "snoring", length = 20)
	private String snoring;

	@Column(name = "teeth_grinding", length = 20)
	private String teethGrinding;

	@Column(name = "sleep_sensitivity", length = 50)
	private String sleepSensitivity;

	@Column(name = "indoor_eating", length = 50)
	private String indoorEating;

	@Column(name = "smoking", length = 30)
	private String smoking;

	@Column(name = "lighting_preference", length = 30)
	private String lightingPreference;

	@Column(name = "phone_call", length = 50)
	private String phoneCall;

	@Column(name = "media_sound", length = 100)
	private String mediaSound;

	@Column(name = "shower_time", length = 30)
	private String showerTime;

	@Column(name = "shower_duration", length = 30)
	private String showerDuration;

	@Column(name = "cleaning_frequency", length = 30)
	private String cleaningFrequency;

	@Column(name = "cleaning_style", length = 50)
	private String cleaningStyle;

	@Column(name = "roommate_interaction", length = 50)
	private String roommateInteraction;

	@Column(name = "temperature_preference", length = 30)
	private String temperaturePreference;

	@Column(name = "bug_handling", length = 30)
	private String bugHandling;

	@Column(name = "scent_product_use", length = 50)
	private String scentProductUse;

	@Column(name = "desktop_use", length = 50)
	private String desktopUse;

	@Column(name = "shared_items", length = 50)
	private String sharedItems;

	/** false: DRAFT, true: COMPLETED. */
	@Column(name = "status", nullable = false, columnDefinition = "BOOLEAN")
	private boolean status;

	@Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME")
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME")
	private LocalDateTime updatedAt;

	public ProfileSurvey(Member member) {
		this.member = Objects.requireNonNull(member, "member");
	}

	public void updateAnswers(
			String sleepTime,
			String wakeTime,
			String sleepTalking,
			String snoring,
			String teethGrinding,
			String sleepSensitivity,
			String indoorEating,
			String smoking,
			String lightingPreference,
			String phoneCall,
			String mediaSound,
			String showerTime,
			String showerDuration,
			String cleaningFrequency,
			String cleaningStyle,
			String roommateInteraction,
			String temperaturePreference,
			String bugHandling,
			String scentProductUse,
			String desktopUse,
			String sharedItems) {
		this.sleepTime = sleepTime;
		this.wakeTime = wakeTime;
		this.sleepTalking = sleepTalking;
		this.snoring = snoring;
		this.teethGrinding = teethGrinding;
		this.sleepSensitivity = sleepSensitivity;
		this.indoorEating = indoorEating;
		this.smoking = smoking;
		this.lightingPreference = lightingPreference;
		this.phoneCall = phoneCall;
		this.mediaSound = mediaSound;
		this.showerTime = showerTime;
		this.showerDuration = showerDuration;
		this.cleaningFrequency = cleaningFrequency;
		this.cleaningStyle = cleaningStyle;
		this.roommateInteraction = roommateInteraction;
		this.temperaturePreference = temperaturePreference;
		this.bugHandling = bugHandling;
		this.scentProductUse = scentProductUse;
		this.desktopUse = desktopUse;
		this.sharedItems = sharedItems;
	}

	public void complete() {
		this.status = true;
	}

	public void markAsDraft() {
		this.status = false;
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
