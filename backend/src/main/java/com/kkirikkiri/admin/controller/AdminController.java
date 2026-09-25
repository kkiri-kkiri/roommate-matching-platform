package com.kkirikkiri.admin.controller;

import java.util.List;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kkirikkiri.admin.dto.AdminCommentListRequest;
import com.kkirikkiri.admin.dto.AdminCommentResponse;
import com.kkirikkiri.admin.dto.AdminCrawledScheduleListRequest;
import com.kkirikkiri.admin.dto.AdminCrawledScheduleResponse;
import com.kkirikkiri.admin.dto.AdminDashboardResponse;
import com.kkirikkiri.admin.dto.AdminMemberDetailResponse;
import com.kkirikkiri.admin.dto.AdminMemberListRequest;
import com.kkirikkiri.admin.dto.AdminMemberResponse;
import com.kkirikkiri.admin.dto.AdminMemberStatusUpdateRequest;
import com.kkirikkiri.admin.dto.AdminPostDetailResponse;
import com.kkirikkiri.admin.dto.AdminPostListRequest;
import com.kkirikkiri.admin.dto.AdminPostResponse;
import com.kkirikkiri.admin.dto.AdminReportDetailResponse;
import com.kkirikkiri.admin.dto.AdminReportListRequest;
import com.kkirikkiri.admin.dto.AdminReportProcessRequest;
import com.kkirikkiri.admin.dto.AdminReportResponse;
import com.kkirikkiri.admin.dto.AdminScheduleCrawlStatusResponse;
import com.kkirikkiri.admin.service.AdminService;
import com.kkirikkiri.global.response.ApiResponse;
import com.kkirikkiri.global.security.AuthenticatedUser;
import com.kkirikkiri.matching.dto.RoommatePageResponse;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

	private final AdminService adminService;

	@GetMapping("/dashboard")
	public ApiResponse<AdminDashboardResponse> getDashboard(@AuthenticationPrincipal AuthenticatedUser user) {
		return ApiResponse.success(adminService.getDashboard(user == null ? null : user.userId()));
	}

	@GetMapping("/members")
	public ApiResponse<RoommatePageResponse<AdminMemberResponse>> getMembers(
			@AuthenticationPrincipal AuthenticatedUser user,
			@Valid @ModelAttribute AdminMemberListRequest request) {
		return ApiResponse.success(adminService.getMembers(user == null ? null : user.userId(), request));
	}

	@GetMapping("/members/{memberId}")
	public ApiResponse<AdminMemberDetailResponse> getMember(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable Long memberId) {
		return ApiResponse.success(adminService.getMember(user == null ? null : user.userId(), memberId));
	}

	@PatchMapping("/members/{memberId}/status")
	public ApiResponse<Void> updateMemberStatus(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable Long memberId, @Valid @RequestBody AdminMemberStatusUpdateRequest request) {
		adminService.updateMemberStatus(user == null ? null : user.userId(), memberId, request);
		return ApiResponse.successWithoutData();
	}

	@GetMapping("/posts")
	public ApiResponse<RoommatePageResponse<AdminPostResponse>> getPosts(
			@AuthenticationPrincipal AuthenticatedUser user,
			@Valid @ModelAttribute AdminPostListRequest request) {
		return ApiResponse.success(adminService.getPosts(user == null ? null : user.userId(), request));
	}

	@GetMapping("/posts/{postId}")
	public ApiResponse<AdminPostDetailResponse> getPost(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable Long postId) {
		return ApiResponse.success(adminService.getPost(user == null ? null : user.userId(), postId));
	}

	@DeleteMapping("/posts/{postId}")
	public ApiResponse<Void> deletePost(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable Long postId) {
		adminService.deletePost(user == null ? null : user.userId(), postId);
		return ApiResponse.successWithoutData();
	}

	@GetMapping("/comments")
	public ApiResponse<RoommatePageResponse<AdminCommentResponse>> getComments(
			@AuthenticationPrincipal AuthenticatedUser user,
			@Valid @ModelAttribute AdminCommentListRequest request) {
		return ApiResponse.success(adminService.getComments(user == null ? null : user.userId(), request));
	}

	@GetMapping("/comments/{commentId}")
	public ApiResponse<AdminCommentResponse> getComment(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable Long commentId) {
		return ApiResponse.success(adminService.getComment(user == null ? null : user.userId(), commentId));
	}

	@DeleteMapping("/comments/{commentId}")
	public ApiResponse<Void> deleteComment(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable Long commentId) {
		adminService.deleteComment(user == null ? null : user.userId(), commentId);
		return ApiResponse.successWithoutData();
	}

	@GetMapping("/reports")
	public ApiResponse<RoommatePageResponse<AdminReportResponse>> getReports(
			@AuthenticationPrincipal AuthenticatedUser user,
			@Valid @ModelAttribute AdminReportListRequest request) {
		return ApiResponse.success(adminService.getReports(user == null ? null : user.userId(), request));
	}

	@GetMapping("/reports/{reportId}")
	public ApiResponse<AdminReportDetailResponse> getReport(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable Long reportId) {
		return ApiResponse.success(adminService.getReport(user == null ? null : user.userId(), reportId));
	}

	@PatchMapping("/reports/{reportId}")
	public ApiResponse<Void> processReport(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable Long reportId, @Valid @RequestBody AdminReportProcessRequest request) {
		adminService.processReport(user == null ? null : user.userId(), reportId, request);
		return ApiResponse.successWithoutData();
	}

	@GetMapping("/schedule-crawls/status")
	public ApiResponse<List<AdminScheduleCrawlStatusResponse>> getScheduleCrawlStatuses(
			@AuthenticationPrincipal AuthenticatedUser user) {
		return ApiResponse.success(adminService.getScheduleCrawlStatuses(user == null ? null : user.userId()));
	}

	@GetMapping("/crawled-schedules")
	public ApiResponse<RoommatePageResponse<AdminCrawledScheduleResponse>> getCrawledSchedules(
			@AuthenticationPrincipal AuthenticatedUser user,
			@Valid @ModelAttribute AdminCrawledScheduleListRequest request) {
		return ApiResponse.success(adminService.getCrawledSchedules(user == null ? null : user.userId(), request));
	}

	@GetMapping("/crawled-schedules/{scheduleId}")
	public ApiResponse<AdminCrawledScheduleResponse> getCrawledSchedule(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable Long scheduleId) {
		return ApiResponse.success(adminService.getCrawledSchedule(user == null ? null : user.userId(), scheduleId));
	}
}
