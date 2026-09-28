package com.kkirikkiri.matching.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.kkirikkiri.matching.dto.AiProfileAnalysisInput;
import com.kkirikkiri.matching.dto.AiProfileAnalysisResponse;

@Service
@RequiredArgsConstructor
public class AiProfileAnalysisService {
    private final RoommateMatchingService matchingService;
    private final AiGatewayClient gateway;

    public AiProfileAnalysisResponse analyze(Long userId, Long targetUserId) {
        var result = matchingService.getAnalysisInput(userId, targetUserId);
        var input = new AiProfileAnalysisInput(result.score(), result.matchedItems(), result.mismatchedItems());
        // The matching transaction is already committed before making the remote call.
        return new AiProfileAnalysisResponse(targetUserId, result.score(), gateway.analyze(input));
    }
}
