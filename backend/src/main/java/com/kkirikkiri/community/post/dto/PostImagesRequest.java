package com.kkirikkiri.community.post.dto;

import java.util.List;
import java.util.Set;

import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 작성/수정 공통 images 파일 part. Controller에서 파일 목록으로 구성한 후 검증한다.
 * 수정 시 유지할 기존 이미지까지 포함한 최대 5장 제한은 서비스에서 검증한다.
 */
public record PostImagesRequest(
		@Size(max = 5, message = "이미지는 최대 5장까지 등록할 수 있습니다.")
		List<@NotNull MultipartFile> images) {

	private static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024;
	private static final Set<String> ALLOWED_TYPES = Set.of(
			"image/jpeg", "image/png", "image/webp", "image/gif");

	public PostImagesRequest {
		images = images == null ? List.of() : images;
	}

	@JsonIgnore
	@AssertTrue(message = "이미지는 0 byte 초과, 5MB 이하의 JPEG, PNG, WebP, GIF 파일이어야 합니다.")
	public boolean isImagesValid() {
		return images.stream().allMatch(file -> file != null
				&& file.getSize() > 0 && file.getSize() <= MAX_IMAGE_SIZE
				&& file.getContentType() != null && ALLOWED_TYPES.contains(file.getContentType()));
	}
}
