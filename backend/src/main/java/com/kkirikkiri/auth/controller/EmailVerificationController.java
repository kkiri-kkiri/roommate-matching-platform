package com.kkirikkiri.auth.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kkirikkiri.auth.dto.EmailVerificationSendRequest;
import com.kkirikkiri.auth.dto.EmailVerificationSendResponse;
import com.kkirikkiri.auth.dto.EmailVerificationVerifyRequest;
import com.kkirikkiri.auth.dto.EmailVerificationVerifyResponse;
import com.kkirikkiri.auth.service.EmailVerificationService;
import com.kkirikkiri.global.response.ApiResponse;

@RestController
@RequestMapping("/api/auth/email-verifications")
@RequiredArgsConstructor
public class EmailVerificationController {

	private final EmailVerificationService service;

	@PostMapping("/send")
	public ApiResponse<EmailVerificationSendResponse> send(@Valid @RequestBody EmailVerificationSendRequest request) {
		return ApiResponse.success(service.send(request));
	}

	@PostMapping("/verify")
	public ApiResponse<EmailVerificationVerifyResponse> verify(@Valid @RequestBody EmailVerificationVerifyRequest request) {
		return ApiResponse.success(service.verify(request));
	}
}
