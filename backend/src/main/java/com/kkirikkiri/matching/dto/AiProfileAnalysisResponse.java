package com.kkirikkiri.matching.dto;

public record AiProfileAnalysisResponse(Long targetUserId, int matchScore, String analysis) {}
