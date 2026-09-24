package com.kkirikkiri.admin.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.kkirikkiri.community.post.dto.CommunityAuthorResponse;
import com.kkirikkiri.report.entity.ReportStatus;
import com.kkirikkiri.report.entity.ReportType;
import com.kkirikkiri.user.entity.UserStatus;

/**
 * 관리자 화면의 최근 항목에 필요한 필드만 반환한다.
 * 서비스에서 통계 및 각 최근 항목 3개를 조회하여 전달한다.
 */
public record AdminDashboardResponse(
		Stats stats,
		List<MemberSummary> recentMembers,
		List<PostSummary> recentPosts,
		List<ReportSummary> recentReports,
		List<ScheduleSummary> recentSchoolSchedules,
		List<ScheduleSummary> recentDormitorySchedules) {

	public AdminDashboardResponse {
		recentMembers = List.copyOf(recentMembers);
		recentPosts = List.copyOf(recentPosts);
		recentReports = List.copyOf(recentReports);
		recentSchoolSchedules = List.copyOf(recentSchoolSchedules);
		recentDormitorySchedules = List.copyOf(recentDormitorySchedules);
	}

	public record Stats(long totalMembers, long totalPosts, long totalComments, long totalReports,
			long pendingReports, long schoolSchedules, long dormitorySchedules) {
	}

	public record MemberSummary(Long id, String nickname, String major, String dormitory,
			LocalDate createdAt, UserStatus status) {
	}

	public record PostSummary(Long id, String title, String category,
			CommunityAuthorResponse author, LocalDateTime createdAt) {
	}

	public record ReportSummary(Long id, ReportStatus status, ReportType type, String reason,
			LocalDateTime createdAt) {
	}

	/** 학교/기숙사 일정에 같은 요약 계약을 사용한다. date는 시작일이다. */
	public record ScheduleSummary(Long id, String title, LocalDate date, LocalDateTime collectedAt) {
	}
}
