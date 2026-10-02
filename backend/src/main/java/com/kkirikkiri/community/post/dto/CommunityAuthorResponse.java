package com.kkirikkiri.community.post.dto;

import com.kkirikkiri.user.entity.UserProfile;

public record CommunityAuthorResponse(Long id, String nickname, String major) {

	public CommunityAuthorResponse(Long id, String nickname) {
		this(id, nickname, null);
	}

	public CommunityAuthorResponse forPost(boolean anonymous) {
		return anonymous ? new CommunityAuthorResponse(id, "익명", null) : this;
	}

	public static CommunityAuthorResponse from(UserProfile profile) {
		return new CommunityAuthorResponse(profile.getUser().getUserId(), profile.getNickname(),
				profile.getMajor() == null ? null : profile.getMajor().getMajorName());
	}
}
