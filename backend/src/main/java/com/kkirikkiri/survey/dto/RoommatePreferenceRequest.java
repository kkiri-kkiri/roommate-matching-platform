package com.kkirikkiri.survey.dto;

import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 선호 설문의 전체 답변과 조건 설정을 저장한다.
 * 선택지, 지원되는 조건 key, 중복 및 '상관없음' 선택 조합은 서비스에서 검증한다.
 */
public record RoommatePreferenceRequest(
		@NotBlank(message = "학과 선호를 선택해 주세요.") @Size(max = 60, message = "학과 선호는 60자 이하여야 합니다.") String department,
		@NotEmpty(message = "나이 선호를 선택해 주세요.") List<@NotBlank(message = "나이 선호는 공백일 수 없습니다.") @Size(max = 60, message = "나이 선호는 60자 이하여야 합니다.") String> age,
		@NotEmpty(message = "학년 선호를 선택해 주세요.") List<@NotBlank(message = "학년 선호는 공백일 수 없습니다.") @Size(max = 60, message = "학년 선호는 60자 이하여야 합니다.") String> grade,
		@NotEmpty(message = "취침시간 선호를 선택해 주세요.") List<@NotBlank(message = "취침시간 선호는 공백일 수 없습니다.") @Size(max = 60, message = "취침시간 선호는 60자 이하여야 합니다.") String> bedtime,
		@NotEmpty(message = "기상시간 선호를 선택해 주세요.") List<@NotBlank(message = "기상시간 선호는 공백일 수 없습니다.") @Size(max = 60, message = "기상시간 선호는 60자 이하여야 합니다.") String> wakeTime,
		@NotBlank(message = "잠꼬대 허용 정도를 선택해 주세요.") @Size(max = 50, message = "잠꼬대 허용 정도는 50자 이하여야 합니다.") String sleepTalking,
		@NotBlank(message = "코골이 허용 정도를 선택해 주세요.") @Size(max = 50, message = "코골이 허용 정도는 50자 이하여야 합니다.") String snoring,
		@NotBlank(message = "이갈이 허용 정도를 선택해 주세요.") @Size(max = 50, message = "이갈이 허용 정도는 50자 이하여야 합니다.") String teethGrinding,
		@NotBlank(message = "흡연 선호를 선택해 주세요.") @Size(max = 50, message = "흡연 선호는 50자 이하여야 합니다.") String smoking,
		@NotBlank(message = "조명 환경을 선택해 주세요.") @Size(max = 30, message = "조명 환경은 30자 이하여야 합니다.") String lightingPreference,
		@NotBlank(message = "샤워 시간대를 선택해 주세요.") @Size(max = 30, message = "샤워 시간대는 30자 이하여야 합니다.") String showerTime,
		@NotBlank(message = "샤워 소요시간을 선택해 주세요.") @Size(max = 30, message = "샤워 소요시간은 30자 이하여야 합니다.") String showerDuration,
		@NotEmpty(message = "청소 주기 선호를 선택해 주세요.") List<@NotBlank(message = "청소 주기 선호는 공백일 수 없습니다.") @Size(max = 60, message = "청소 주기 선호는 60자 이하여야 합니다.") String> cleaningFrequency,
		@NotBlank(message = "청소 방식 선호를 선택해 주세요.") @Size(max = 60, message = "청소 방식 선호는 60자 이하여야 합니다.") String cleaningStyle,
		@NotBlank(message = "교류 정도 선호를 선택해 주세요.") @Size(max = 60, message = "교류 정도 선호는 60자 이하여야 합니다.") String interaction,
		@NotBlank(message = "식사 방식 선호를 선택해 주세요.") @Size(max = 60, message = "식사 방식 선호는 60자 이하여야 합니다.") String meal,
		@NotBlank(message = "공용물품 사용 방식 선호를 선택해 주세요.") @Size(max = 60, message = "공용물품 사용 방식 선호는 60자 이하여야 합니다.") String sharedItems,
		@NotBlank(message = "온도 성향 선호를 선택해 주세요.") @Size(max = 50, message = "온도 성향 선호는 50자 이하여야 합니다.") String temperature,
		@NotEmpty(message = "모든 선호 항목의 가중치를 입력해 주세요.")
		@Size(min = 18, max = 18, message = "모든 선호 항목의 가중치를 입력해 주세요.")
		Map<@NotBlank(message = "가중치 조건 key는 공백일 수 없습니다.") @Size(max = 40, message = "가중치 조건 key는 40자 이하여야 합니다.") String,
				@NotNull(message = "가중치는 필수입니다.") @Min(value = 1, message = "가중치는 1 이상이어야 합니다.") @Max(value = 5, message = "가중치는 5 이하여야 합니다.") Integer> weights,
		@NotNull(message = "필수조건 목록은 필수입니다.") @Size(max = 18, message = "필수조건은 18개 이하여야 합니다.")
		List<@NotBlank(message = "필수조건 key는 공백일 수 없습니다.") @Size(max = 40, message = "필수조건 key는 40자 이하여야 합니다.") String> requiredConditions) {
}
