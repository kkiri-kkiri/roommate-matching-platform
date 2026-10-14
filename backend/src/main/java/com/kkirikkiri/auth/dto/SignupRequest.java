package com.kkirikkiri.auth.dto;

import java.time.Year;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.kkirikkiri.global.validation.PasswordByteLength;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 이메일 인증, 중복 여부, 학부와 전공의 조합은 서비스에서 검증한다.
 * admissionYear는 입학연도이며 MBTI 네 축은 저장 시 하나의 문자열로 조합한다.
 */
public record SignupRequest(
		@NotBlank(message = "비밀번호를 입력해 주세요.")
		@Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
		@Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s])\\S{8,}$",
				message = "비밀번호는 영문, 숫자, 특수문자를 포함해 8자 이상이어야 합니다.")
        @PasswordByteLength String password,
		@NotBlank(message = "비밀번호 확인을 입력해 주세요.")
        @PasswordByteLength String passwordConfirm,
		@NotBlank(message = "닉네임을 입력해 주세요.")
		@Size(min = 2, max = 8, message = "닉네임은 2자 이상 8자 이하여야 합니다.")
		@Pattern(regexp = "[가-힣A-Za-z0-9]+", message = "닉네임은 한글, 영문, 숫자만 사용할 수 있습니다.")
		String nickname,
		@NotBlank(message = "이메일을 입력해 주세요.")
		@Email(message = "올바른 이메일 형식이어야 합니다.")
		@Pattern(regexp = "[^@\\s]{1,64}@hknu\\.ac\\.kr", message = "학교 이메일(@hknu.ac.kr)을 입력해 주세요. 이메일 아이디는 최대 64자입니다.")
		String schoolEmail,
		@NotBlank(message = "학교 이메일 인증 토큰을 입력해 주세요.")
		String emailVerificationToken,
		@NotBlank(message = "성별을 선택해 주세요.")
		@Pattern(regexp = "남자|여자", message = "성별은 남자 또는 여자여야 합니다.")
		String gender,
		@NotBlank(message = "생활관을 선택해 주세요.")
		@Size(max = 30, message = "생활관명은 30자 이하여야 합니다.")
		String dormitory,
		@NotBlank(message = "학부를 선택해 주세요.")
		@Size(max = 100, message = "학부명은 100자 이하여야 합니다.")
		String faculty,
		@NotBlank(message = "전공을 선택해 주세요.")
		@Size(max = 150, message = "전공명은 150자 이하여야 합니다.")
		String major,
		@NotNull(message = "출생연도를 입력해 주세요.")
		@Min(value = 1900, message = "출생연도는 1900년 이상이어야 합니다.")
		Short birthYear,
		@NotBlank(message = "학번을 입력해 주세요.")
		@Pattern(regexp = "[0-9]{2}", message = "학번은 숫자 2자리여야 합니다.")
		String admissionYear,
		@NotBlank(message = "학년을 선택해 주세요.")
		@Pattern(regexp = "[1-5]학년", message = "학년은 1학년부터 5학년까지 선택할 수 있습니다.")
		String grade,
		@NotBlank(message = "MBTI 에너지를 선택해 주세요.")
		@Pattern(regexp = "[EI]", message = "MBTI 에너지는 E 또는 I여야 합니다.")
		String mbtiEnergy,
		@NotBlank(message = "MBTI 인식을 선택해 주세요.")
		@Pattern(regexp = "[NS]", message = "MBTI 인식은 N 또는 S여야 합니다.")
		String mbtiInformation,
		@NotBlank(message = "MBTI 판단을 선택해 주세요.")
		@Pattern(regexp = "[FT]", message = "MBTI 판단은 F 또는 T여야 합니다.")
		String mbtiDecision,
		@NotBlank(message = "MBTI 생활양식을 선택해 주세요.")
		@Pattern(regexp = "[PJ]", message = "MBTI 생활양식은 P 또는 J여야 합니다.")
		String mbtiLifestyle) {

	@JsonIgnore
	@AssertTrue(message = "비밀번호와 비밀번호 확인이 일치해야 합니다.")
	public boolean isPasswordMatching() {
		return password == null || passwordConfirm == null || password.equals(passwordConfirm);
	}

	@JsonIgnore
	@AssertTrue(message = "출생연도는 현재 연도 이하여야 합니다.")
	public boolean isBirthYearValid() {
		return birthYear == null || birthYear <= Year.now().getValue();
	}
}
