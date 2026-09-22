package com.kkirikkiri.auth.dto;

public record SignupAvailabilityResponse(
		boolean loginIdAvailable,
		boolean schoolEmailAvailable,
		boolean nicknameAvailable) {
}