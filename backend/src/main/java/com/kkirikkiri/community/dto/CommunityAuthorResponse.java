package com.kkirikkiri.community.dto;

import com.kkirikkiri.user.entity.UserProfile;

public record CommunityAuthorResponse(Long id, String nickname) {

	public static CommunityAuthorResponse from(UserProfile profile) {
		return new CommunityAuthorResponse(profile.getUser().getUserId(), profile.getNickname());
	}
}
