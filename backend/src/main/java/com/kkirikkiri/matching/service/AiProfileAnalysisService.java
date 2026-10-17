package com.kkirikkiri.matching.service;

import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.kkirikkiri.global.exception.CustomException;
import com.kkirikkiri.global.exception.ErrorCode;
import com.kkirikkiri.matching.dto.AiProfileAnalysisInput;
import com.kkirikkiri.matching.dto.AiProfileAnalysisResponse;

@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class AiProfileAnalysisService {
    private final RoommateMatchingService matchingService;
    private final AiGatewayClient gateway;
    private final AiAnalysisRateLimiter limiter;
    private final Semaphore capacity;

    public AiProfileAnalysisService(RoommateMatchingService matchingService, AiGatewayClient gateway,
            AiAnalysisRateLimiter limiter, @Value("${ai.analysis.max-concurrent:2}") int maxConcurrent) {
        if (maxConcurrent < 1) throw new IllegalArgumentException("Invalid AI analysis concurrency setting");
        this.matchingService = matchingService;
        this.gateway = gateway;
        this.limiter = limiter;
        this.capacity = new Semaphore(maxConcurrent);
    }

    public AiProfileAnalysisResponse analyze(Long userId, Long targetUserId) {
        limiter.reserve(userId);
        if (!capacity.tryAcquire()) throw new CustomException(ErrorCode.AI_ANALYSIS_BUSY);
        try {
            var result = matchingService.getAnalysisInput(userId, targetUserId);
            var input = new AiProfileAnalysisInput(result.score(), result.matchedItems(), result.mismatchedItems());
            // The quota and matching transactions have ended before making the remote call.
            return new AiProfileAnalysisResponse(targetUserId, result.score(), gateway.analyze(input));
        } finally {
            capacity.release();
        }
    }
}
