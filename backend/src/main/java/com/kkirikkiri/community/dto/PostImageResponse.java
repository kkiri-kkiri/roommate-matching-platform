package com.kkirikkiri.community.dto;

import com.kkirikkiri.community.entity.PostImage;

public record PostImageResponse(Long id, String url) {

	public static PostImageResponse from(PostImage image) {
		return new PostImageResponse(image.getPostImageId(), image.getImageUrl());
	}
}
