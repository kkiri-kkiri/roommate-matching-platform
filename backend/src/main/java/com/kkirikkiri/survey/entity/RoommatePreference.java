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
@Table(name = "ROOMMATE_PREFERENCE")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoommatePreference {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "preference_id")
	private Long preferenceId;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, unique = true)
	private Member member;

	@Column(name = "department_preference", length = 60)
	private String departmentPreference;

	@Column(name = "sleep_talking_allowance", length = 50)
	private String sleepTalkingAllowance;

	@Column(name = "snoring_allowance", length = 50)
	private String snoringAllowance;

	@Column(name = "teeth_grinding_allowance", length = 50)
	private String teethGrindingAllowance;

	@Column(name = "smoking_preference", length = 50)
	private String smokingPreference;

	@Column(name = "lighting_preference", length = 30)
	private String lightingPreference;

	@Column(name = "shower_time_preference", length = 30)
	private String showerTimePreference;

	@Column(name = "shower_duration_preference", length = 30)
	private String showerDurationPreference;

	@Column(name = "cleaning_style_preference", length = 60)
	private String cleaningStylePreference;

	@Column(name = "interaction_preference", length = 60)
	private String interactionPreference;

	@Column(name = "meal_preference", length = 60)
	private String mealPreference;

	@Column(name = "shared_items_preference", length = 60)
	private String sharedItemsPreference;

	@Column(name = "temperature_preference", length = 50)
	private String temperaturePreference;

	/** false: DRAFT, true: COMPLETED. */
	@Column(name = "status", nullable = false, columnDefinition = "BOOLEAN")
	private boolean status;

	@Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME")
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME")
	private LocalDateTime updatedAt;

	public RoommatePreference(Member member) {
		this.member = Objects.requireNonNull(member, "member");
	}

	public void updateAnswers(
			String departmentPreference,
			String sleepTalkingAllowance,
			String snoringAllowance,
			String teethGrindingAllowance,
			String smokingPreference,
			String lightingPreference,
			String showerTimePreference,
			String showerDurationPreference,
			String cleaningStylePreference,
			String interactionPreference,
			String mealPreference,
			String sharedItemsPreference,
			String temperaturePreference) {
		this.departmentPreference = departmentPreference;
		this.sleepTalkingAllowance = sleepTalkingAllowance;
		this.snoringAllowance = snoringAllowance;
		this.teethGrindingAllowance = teethGrindingAllowance;
		this.smokingPreference = smokingPreference;
		this.lightingPreference = lightingPreference;
		this.showerTimePreference = showerTimePreference;
		this.showerDurationPreference = showerDurationPreference;
		this.cleaningStylePreference = cleaningStylePreference;
		this.interactionPreference = interactionPreference;
		this.mealPreference = mealPreference;
		this.sharedItemsPreference = sharedItemsPreference;
		this.temperaturePreference = temperaturePreference;
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
