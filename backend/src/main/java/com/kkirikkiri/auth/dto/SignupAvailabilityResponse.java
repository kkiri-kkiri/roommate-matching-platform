package com.kkirikkiri.auth.dto;

public record SignupAvailabilityResponse(
		boolean schoolEmailAvailable,
		boolean nicknameAvailable) {
}