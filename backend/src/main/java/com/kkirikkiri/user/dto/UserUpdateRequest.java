package com.kkirikkiri.user.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * null인 필드는 변경하지 않는다. MBTI 부분 수정값은 서비스에서 기존 값과 조합한다.
 */
public record UserUpdateRequest(
		@Size(max = 20, message = "닉네임은 20자 이하여야 합니다.")
		@Pattern(regexp = "(?s).*\\S.*", message = "닉네임은 공백일 수 없습니다.")
		String nickname,
		@Size(max = 30, message = "생활관명은 30자 이하여야 합니다.")
		@Pattern(regexp = "(?s).*\\S.*", message = "생활관명은 공백일 수 없습니다.")
		String dormitory,
		@Pattern(regexp = "[1-5]학년", message = "학년은 1학년부터 5학년까지 선택할 수 있습니다.")
		String grade,
		@Size(max = 100, message = "학부명은 100자 이하여야 합니다.")
		@Pattern(regexp = "(?s).*\\S.*", message = "학부명은 공백일 수 없습니다.")
		String faculty,
		@Size(max = 150, message = "전공명은 150자 이하여야 합니다.")
		@Pattern(regexp = "(?s).*\\S.*", message = "전공명은 공백일 수 없습니다.")
		String major,
		@Pattern(regexp = "[EI]", message = "MBTI 에너지는 E 또는 I여야 합니다.")
		String mbtiEnergy,
		@Pattern(regexp = "[NS]", message = "MBTI 인식은 N 또는 S여야 합니다.")
		String mbtiInformation,
		@Pattern(regexp = "[FT]", message = "MBTI 판단은 F 또는 T여야 합니다.")
		String mbtiDecision,
		@Pattern(regexp = "[PJ]", message = "MBTI 생활양식은 P 또는 J여야 합니다.")
		String mbtiLifestyle) {
}
