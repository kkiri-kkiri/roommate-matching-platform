package com.kkirikkiri.auth.controller;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
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
import com.kkirikkiri.auth.service.ClientIpResolver;
import com.kkirikkiri.global.response.ApiResponse;

@RestController
@RequestMapping("/api/auth/email-verifications")
@RequiredArgsConstructor
public class EmailVerificationController {

	private final EmailVerificationService service;
	private final ClientIpResolver clientIpResolver;

	@PostMapping("/send")
	public ApiResponse<EmailVerificationSendResponse> send(@Valid @RequestBody EmailVerificationSendRequest request, HttpServletRequest http) {
		return ApiResponse.success(service.send(request, clientIpResolver.resolve(http)));
	}

	@PostMapping("/verify")
	public ApiResponse<EmailVerificationVerifyResponse> verify(@Valid @RequestBody EmailVerificationVerifyRequest request, HttpServletRequest http) {
		return ApiResponse.success(service.verify(request, clientIpResolver.resolve(http)));
	}
}
