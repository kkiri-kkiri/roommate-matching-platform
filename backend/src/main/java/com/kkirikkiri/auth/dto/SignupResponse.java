package com.kkirikkiri.auth.dto;

import com.kkirikkiri.user.entity.User;
import com.kkirikkiri.user.entity.UserProfile;

public record SignupResponse(Long userId, String loginId, String nickname) {

	public static SignupResponse from(UserProfile profile) {
		User user = profile.getUser();
		return new SignupResponse(user.getUserId(), user.getLoginId(), profile.getNickname());
	}
}
