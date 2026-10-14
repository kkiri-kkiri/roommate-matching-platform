package com.kkirikkiri.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import com.kkirikkiri.global.validation.PasswordByteLength;

public record LoginRequest(
        @NotBlank @Email
        @Pattern(regexp = "[^@\\s]{1,64}@hknu\\.ac\\.kr", message = "학교 이메일(@hknu.ac.kr)을 입력해 주세요.")
        String email,
        @NotBlank @PasswordByteLength String password) {
}
