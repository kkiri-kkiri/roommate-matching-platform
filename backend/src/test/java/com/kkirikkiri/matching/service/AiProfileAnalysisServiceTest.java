package com.kkirikkiri.matching.service;

import java.util.List;
import org.junit.jupiter.api.Test;
import com.kkirikkiri.global.exception.CustomException;
import com.kkirikkiri.global.exception.ErrorCode;
import com.kkirikkiri.matching.dto.AiProfileAnalysisInput;
import static com.kkirikkiri.survey.entity.PreferenceCriterionCode.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiProfileAnalysisServiceTest {
    private final RoommateMatchingService matching = mock(RoommateMatchingService.class);
    private final AiGatewayClient gateway = mock(AiGatewayClient.class);
    private final AiProfileAnalysisService service = new AiProfileAnalysisService(matching, gateway);

    @Test
    void preservesBackendScoreEvenIfAiTextMentionsAnotherScore() {
        when(matching.getAnalysisInput(1L, 2L)).thenReturn(
                new RoommateMatchCalculator.Result(75, List.of(SMOKING), List.of(BEDTIME)));
        when(gateway.analyze(new AiProfileAnalysisInput(75, List.of(SMOKING), List.of(BEDTIME))))
                .thenReturn("100점이라는 잘못된 모델 문구");
        var result = service.analyze(1L, 2L);
        assertEquals(75, result.matchScore());
        assertEquals(2L, result.targetUserId());
        verify(gateway).analyze(new AiProfileAnalysisInput(75, List.of(SMOKING), List.of(BEDTIME)));
    }

    @Test
    void unavailableInputsNeverReachGateway() {
        when(matching.getAnalysisInput(1L, 2L)).thenThrow(new CustomException(ErrorCode.MATCH_ANALYSIS_UNAVAILABLE));
        assertThrows(CustomException.class, () -> service.analyze(1L, 2L));
        verifyNoInteractions(gateway);
    }
}
