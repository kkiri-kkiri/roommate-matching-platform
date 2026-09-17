package com.kkirikkiri.member.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * null 필드는 수정하지 않는다.
 */
public record MemberUpdateRequest(
		@Size(max = 20)
		@Pattern(regexp = "(?s).*\\S.*", message = "닉네임은 공백일 수 없습니다.")
		String nickname,
		@Size(max = 30)
		@Pattern(regexp = "(?s).*\\S.*", message = "생활관은 공백일 수 없습니다.")
		String dormitory,
		@Pattern(regexp = "[1-5]학년", message = "학년은 1학년부터 5학년까지 입력할 수 있습니다.")
		String grade,
		@Size(max = 100)
		@Pattern(regexp = "(?s).*\\S.*", message = "학부는 공백일 수 없습니다.")
		String faculty,
		@Size(max = 150)
		@Pattern(regexp = "(?s).*\\S.*", message = "전공은 공백일 수 없습니다.")
		String major,
		@Pattern(regexp = "[EI]", message = "MBTI 에너지는 E 또는 I여야 합니다.")
		String mbtiEnergy,
		@Pattern(regexp = "[NS]", message = "MBTI 정보는 N 또는 S여야 합니다.")
		String mbtiInformation,
		@Pattern(regexp = "[FT]", message = "MBTI 결정은 F 또는 T여야 합니다.")
		String mbtiDecision,
		@Pattern(regexp = "[PJ]", message = "MBTI 생활양식은 P 또는 J여야 합니다.")
		String mbtiLifestyle) {
}
