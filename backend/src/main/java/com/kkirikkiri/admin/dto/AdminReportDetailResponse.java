package com.kkirikkiri.admin.dto;

import java.time.LocalDateTime;

import com.kkirikkiri.report.entity.ReportStatus;
import com.kkirikkiri.report.entity.ReportType;
import com.kkirikkiri.user.entity.UserStatus;

public record AdminReportDetailResponse(
		Long id,
		ReportType type,
		Long targetId,
		TargetUser targetUser,
		String targetContent,
		Reporter reporter,
		String reason,
		String description,
		LocalDateTime createdAt,
		ReportStatus status) {

	/** 신고자 정보 화면에서 사용하는 필드. 프로필이 없으면 nickname은 null이다. */
	public record Reporter(Long id, String nickname, String schoolEmail) {
	}

	/** 대상자 상태는 화면 표시 및 이용 정지 버튼에 사용한다. */
	public record TargetUser(Long id, String nickname, String schoolEmail,
			UserStatus status) {
	}
}
