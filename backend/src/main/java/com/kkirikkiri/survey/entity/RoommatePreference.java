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
	private User user;

	@Column(name = "department_preference", nullable = false, length = 60)
	private String departmentPreference;

	@Column(name = "sleep_talking_allowance", nullable = false, length = 50)
	private String sleepTalkingAllowance;

	@Column(name = "snoring_allowance", nullable = false, length = 50)
	private String snoringAllowance;

	@Column(name = "teeth_grinding_allowance", nullable = false, length = 50)
	private String teethGrindingAllowance;

	@Column(name = "smoking_preference", nullable = false, length = 50)
	private String smokingPreference;

	@Column(name = "lighting_preference", nullable = false, length = 30)
	private String lightingPreference;

	@Column(name = "shower_time_preference", nullable = false, length = 30)
	private String showerTimePreference;

	@Column(name = "shower_duration_preference", nullable = false, length = 30)
	private String showerDurationPreference;

	@Column(name = "cleaning_style_preference", nullable = false, length = 60)
	private String cleaningStylePreference;

	@Column(name = "interaction_preference", nullable = false, length = 60)
	private String interactionPreference;

	@Column(name = "meal_preference", nullable = false, length = 60)
	private String mealPreference;

	@Column(name = "shared_items_preference", nullable = false, length = 60)
	private String sharedItemsPreference;

	@Column(name = "temperature_preference", nullable = false, length = 50)
	private String temperaturePreference;

	@Column(name = "completed", nullable = false)
	private boolean completed;

	@Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME")
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME")
	private LocalDateTime updatedAt;

	public RoommatePreference(User user, String departmentPreference, String sleepTalkingAllowance,
			String snoringAllowance, String teethGrindingAllowance, String smokingPreference,
			String lightingPreference, String showerTimePreference, String showerDurationPreference,
			String cleaningStylePreference, String interactionPreference, String mealPreference,
			String sharedItemsPreference, String temperaturePreference, boolean completed) {
		this.user = Objects.requireNonNull(user, "user");
		updateAnswers(departmentPreference, sleepTalkingAllowance, snoringAllowance, teethGrindingAllowance,
				smokingPreference, lightingPreference, showerTimePreference, showerDurationPreference,
				cleaningStylePreference, interactionPreference, mealPreference, sharedItemsPreference,
				temperaturePreference, completed);
	}

	/**
	 * 선택지와 다중선택 응답, 전체 항목의 가중치 및 작성 완료 여부는 서비스에서 검증한다.
	 * 다중선택이나 조건 설정만 변경한 경우에도 함께 호출하여 마지막 수정 일시를 갱신한다.
	 */
	public void updateAnswers(String departmentPreference, String sleepTalkingAllowance,
			String snoringAllowance, String teethGrindingAllowance, String smokingPreference,
			String lightingPreference, String showerTimePreference, String showerDurationPreference,
			String cleaningStylePreference, String interactionPreference, String mealPreference,
			String sharedItemsPreference, String temperaturePreference, boolean completed) {
		this.departmentPreference = Objects.requireNonNull(departmentPreference, "departmentPreference");
		this.sleepTalkingAllowance = Objects.requireNonNull(sleepTalkingAllowance, "sleepTalkingAllowance");
		this.snoringAllowance = Objects.requireNonNull(snoringAllowance, "snoringAllowance");
		this.teethGrindingAllowance = Objects.requireNonNull(teethGrindingAllowance, "teethGrindingAllowance");
		this.smokingPreference = Objects.requireNonNull(smokingPreference, "smokingPreference");
		this.lightingPreference = Objects.requireNonNull(lightingPreference, "lightingPreference");
		this.showerTimePreference = Objects.requireNonNull(showerTimePreference, "showerTimePreference");
		this.showerDurationPreference = Objects.requireNonNull(showerDurationPreference, "showerDurationPreference");
		this.cleaningStylePreference = Objects.requireNonNull(cleaningStylePreference, "cleaningStylePreference");
		this.interactionPreference = Objects.requireNonNull(interactionPreference, "interactionPreference");
		this.mealPreference = Objects.requireNonNull(mealPreference, "mealPreference");
		this.sharedItemsPreference = Objects.requireNonNull(sharedItemsPreference, "sharedItemsPreference");
		this.temperaturePreference = Objects.requireNonNull(temperaturePreference, "temperaturePreference");
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
