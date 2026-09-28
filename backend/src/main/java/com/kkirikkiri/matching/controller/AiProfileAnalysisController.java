package com.kkirikkiri.matching.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import com.kkirikkiri.global.response.ApiResponse;
import com.kkirikkiri.global.security.AuthenticatedUser;
import com.kkirikkiri.matching.dto.AiProfileAnalysisResponse;
import com.kkirikkiri.matching.service.AiProfileAnalysisService;

@RestController
@RequiredArgsConstructor
public class AiProfileAnalysisController {
    private final AiProfileAnalysisService service;

    @PostMapping("/api/roommates/{userId}/ai-analysis")
    public ApiResponse<AiProfileAnalysisResponse> analyze(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long userId) {
        return ApiResponse.success(service.analyze(user == null ? null : user.userId(), userId));
    }
}
