package com.kkirikkiri.matching.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 프로필 목록의 선택 필터를 전달한다.
 * 각 필드는 같은 Query Parameter를 반복하거나 comma-separated 값으로 전달할 수 있다.
 */
public record RoommateFilterRequest(
		List<@NotBlank @Size(max = 50) String> sleepTime,
		List<@NotBlank @Size(max = 50) String> wakeTime,
		List<@NotBlank @Size(max = 20) String> sleepTalking,
		List<@NotBlank @Size(max = 20) String> snoring,
		List<@NotBlank @Size(max = 20) String> teethGrinding,
		List<@NotBlank @Size(max = 50) String> sleepSensitivity,
		List<@NotBlank @Size(max = 50) String> indoorEating,
		List<@NotBlank @Size(max = 30) String> smoking,
		List<@NotBlank @Size(max = 30) String> lightingPreference,
		List<@NotBlank @Size(max = 50) String> phoneCall,
		List<@NotBlank @Size(max = 100) String> mediaSound,
		List<@NotBlank @Size(max = 30) String> showerTime,
		List<@NotBlank @Size(max = 30) String> showerDuration,
		List<@NotBlank @Size(max = 30) String> cleaningFrequency,
		List<@NotBlank @Size(max = 50) String> cleaningStyle,
		List<@NotBlank @Size(max = 30) String> dormStayDays,
		List<@NotBlank @Size(max = 30) String> mealPlan,
		List<@NotBlank @Size(max = 60) String> meal,
		List<@NotBlank @Size(max = 50) String> roommateInteraction,
		List<@NotBlank @Size(max = 30) String> temperaturePreference,
		List<@NotBlank @Size(max = 50) String> scentProductUse,
		List<@NotBlank @Size(max = 50) String> desktopUse,
		List<@NotBlank @Size(max = 50) String> sharedItems) {
}
