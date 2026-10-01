package com.kkirikkiri.auth.dto;

public record LoginResponse(String accessToken, LoginUser user) {

	public record LoginUser(Long id, String schoolEmail, String nickname, String role) {
	}
}
