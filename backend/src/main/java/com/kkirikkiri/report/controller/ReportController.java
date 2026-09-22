package com.kkirikkiri.report.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kkirikkiri.global.response.ApiResponse;
import com.kkirikkiri.global.security.AuthenticatedUser;
import com.kkirikkiri.report.dto.ReportCreateRequest;
import com.kkirikkiri.report.service.ReportService;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

	private final ReportService reportService;

	@PostMapping
	public ApiResponse<Long> createReport(@AuthenticationPrincipal AuthenticatedUser user,
			@Valid @RequestBody ReportCreateRequest request) {
		return ApiResponse.success(reportService.createReport(
				user == null ? null : user.userId(), request));
	}
}
