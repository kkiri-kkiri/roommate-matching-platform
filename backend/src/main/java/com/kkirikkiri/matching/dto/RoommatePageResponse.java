package com.kkirikkiri.matching.dto;

import java.util.List;

import org.springframework.data.domain.Page;

public record RoommatePageResponse<T>(
		List<T> content,
		int page,
		int size,
		long totalElements,
		int totalPages,
		boolean first,
		boolean last) {

	public RoommatePageResponse {
		content = List.copyOf(content);
	}

	public static <T> RoommatePageResponse<T> from(Page<T> page) {
		return new RoommatePageResponse<>(
				page.getContent(),
				page.getNumber(),
				page.getSize(),
				page.getTotalElements(),
				page.getTotalPages(),
				page.isFirst(),
				page.isLast());
	}
}
