package com.kkirikkiri.survey.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * 전체 설문 답변을 저장한다. 필드별 선택지, 중복 및 선택 조합은 서비스에서 검증한다.
 */
public record ProfileSurveyRequest(
		@NotBlank(message = "취침시간을 선택해 주세요.") @Size(max = 50, message = "취침시간은 50자 이하여야 합니다.") String sleepTime,
		@NotBlank(message = "기상시간을 선택해 주세요.") @Size(max = 50, message = "기상시간은 50자 이하여야 합니다.") String wakeTime,
		@NotBlank(message = "잠꼬대 정도를 선택해 주세요.") @Size(max = 20, message = "잠꼬대 정도는 20자 이하여야 합니다.") String sleepTalking,
		@NotBlank(message = "코골이 정도를 선택해 주세요.") @Size(max = 20, message = "코골이 정도는 20자 이하여야 합니다.") String snoring,
		@NotBlank(message = "이갈이 정도를 선택해 주세요.") @Size(max = 20, message = "이갈이 정도는 20자 이하여야 합니다.") String teethGrinding,
		@NotBlank(message = "잠귀를 선택해 주세요.") @Size(max = 50, message = "잠귀는 50자 이하여야 합니다.") String sleepSensitivity,
		@NotBlank(message = "실내 취식 방식을 선택해 주세요.") @Size(max = 50, message = "실내 취식 방식은 50자 이하여야 합니다.") String indoorEating,
		@NotBlank(message = "흡연 여부를 선택해 주세요.") @Size(max = 30, message = "흡연 여부는 30자 이하여야 합니다.") String smoking,
		@NotBlank(message = "조명 환경을 선택해 주세요.") @Size(max = 30, message = "조명 환경은 30자 이하여야 합니다.") String lightingPreference,
		@NotBlank(message = "방 안 전화 방식을 선택해 주세요.") @Size(max = 50, message = "방 안 전화 방식은 50자 이하여야 합니다.") String phoneCall,
		@NotBlank(message = "소리 사용 방식을 선택해 주세요.") @Size(max = 100, message = "소리 사용 방식은 100자 이하여야 합니다.") String mediaSound,
		@NotBlank(message = "샤워 시간대를 선택해 주세요.") @Size(max = 30, message = "샤워 시간대는 30자 이하여야 합니다.") String showerTime,
		@NotBlank(message = "샤워 소요시간을 선택해 주세요.") @Size(max = 30, message = "샤워 소요시간은 30자 이하여야 합니다.") String showerDuration,
		@NotBlank(message = "청소 주기를 선택해 주세요.") @Size(max = 30, message = "청소 주기는 30자 이하여야 합니다.") String cleaningFrequency,
		@NotBlank(message = "청소 방식을 선택해 주세요.") @Size(max = 50, message = "청소 방식은 50자 이하여야 합니다.") String cleaningStyle,
		@NotEmpty(message = "기숙사 상주 요일을 선택해 주세요.") List<@NotBlank(message = "기숙사 상주 요일은 공백일 수 없습니다.") @Size(max = 30, message = "기숙사 상주 요일은 30자 이하여야 합니다.") String> dormStayDays,
		@NotEmpty(message = "신청한 기숙사 식사를 선택해 주세요.") List<@NotBlank(message = "기숙사 식사는 공백일 수 없습니다.") @Size(max = 30, message = "기숙사 식사는 30자 이하여야 합니다.") String> mealPlan,
		@NotBlank(message = "룸메이트와의 식사 방식을 선택해 주세요.") @Size(max = 60, message = "룸메이트와의 식사 방식은 60자 이하여야 합니다.") String meal,
		@NotBlank(message = "룸메이트와의 교류 정도를 선택해 주세요.") @Size(max = 50, message = "룸메이트와의 교류 정도는 50자 이하여야 합니다.") String roommateInteraction,
		@NotBlank(message = "온도 선호를 선택해 주세요.") @Size(max = 30, message = "온도 선호는 30자 이하여야 합니다.") String temperaturePreference,
		@NotBlank(message = "향 제품 사용 여부를 선택해 주세요.") @Size(max = 50, message = "향 제품 사용 여부는 50자 이하여야 합니다.") String scentProductUse,
		@NotBlank(message = "데스크탑 사용 여부를 선택해 주세요.") @Size(max = 50, message = "데스크탑 사용 여부는 50자 이하여야 합니다.") String desktopUse,
		@NotBlank(message = "공용물품 사용 방식을 선택해 주세요.") @Size(max = 50, message = "공용물품 사용 방식은 50자 이하여야 합니다.") String sharedItems) {
}
