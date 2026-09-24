package com.kkirikkiri.admin.dto;

import java.time.LocalDateTime;

import com.kkirikkiri.community.post.dto.CommunityAuthorResponse;
import com.kkirikkiri.report.entity.ReportStatus;
import com.kkirikkiri.report.entity.ReportType;

/** 신고 목록 항목. 대상 내용과 사용자 요약은 서비스에서 조립한다. */
public record AdminReportResponse(
		Long id,
		ReportType type,
		String targetContent,
		CommunityAuthorResponse reporter,
		CommunityAuthorResponse targetUser,
		String reason,
		LocalDateTime createdAt,
		ReportStatus status) {
}
