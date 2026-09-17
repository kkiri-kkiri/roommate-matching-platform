package com.kkirikkiri.auth.dto;

public record LoginResponse(String accessToken, LoginUser user) {

	public record LoginUser(Long id, String loginId, String nickname, String role) {
	}
}
