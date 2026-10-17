package com.kkirikkiri.matching.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import com.kkirikkiri.global.exception.*;

class AiProfileAnalysisServiceTest {
    final RoommateMatchingService matching = mock(RoommateMatchingService.class);
    final AiGatewayClient gateway = mock(AiGatewayClient.class);
    final AiAnalysisRateLimiter limiter = mock(AiAnalysisRateLimiter.class);
    final AiProfileAnalysisService service = new AiProfileAnalysisService(matching, gateway, limiter, 2);

    void input() {
        when(matching.getAnalysisInput(anyLong(), anyLong()))
                .thenReturn(new RoommateMatchCalculator.Result(75, List.of(), List.of()));
    }

    @Test void normalCallKeepsBackendScoreAndGatewayResponse() {
        input(); when(gateway.analyze(any())).thenReturn("서로 배려하세요.");
        var result = service.analyze(1L, 2L);
        assertEquals(2L, result.targetUserId()); assertEquals(75, result.matchScore());
        assertEquals("서로 배려하세요.", result.analysis()); verify(limiter).reserve(1L);
    }

    @ParameterizedTest
    @EnumSource(value = ErrorCode.class, names = {"AI_GATEWAY_UNAVAILABLE", "AI_GATEWAY_INVALID_RESPONSE"})
    void gatewayFailureReturnsPermitAndKeepsError(ErrorCode error) {
        input(); when(gateway.analyze(any())).thenThrow(new CustomException(error));
        for (int i = 0; i < 4; i++)
            assertEquals(error, assertThrows(CustomException.class, () -> service.analyze(1L, 2L)).getErrorCode());
        doReturn("복구됨").when(gateway).analyze(any());
        assertEquals("복구됨", service.analyze(1L, 2L).analysis());
    }

    @Test void uncomputableMatchDoesNotCallGatewayAndReturnsPermit() {
        when(matching.getAnalysisInput(1L, 2L)).thenThrow(new CustomException(ErrorCode.MATCH_ANALYSIS_UNAVAILABLE));
        for (int i = 0; i < 4; i++)
            assertEquals(ErrorCode.MATCH_ANALYSIS_UNAVAILABLE,
                    assertThrows(CustomException.class, () -> service.analyze(1L, 2L)).getErrorCode());
        verifyNoInteractions(gateway);
    }

    @Test void quotaRejectionDoesNotComputeOrCallGateway() {
        doThrow(new CustomException(ErrorCode.AI_ANALYSIS_RATE_LIMITED)).when(limiter).reserve(1L);
        assertEquals(ErrorCode.AI_ANALYSIS_RATE_LIMITED,
                assertThrows(CustomException.class, () -> service.analyze(1L, 2L)).getErrorCode());
        verifyNoInteractions(matching, gateway);
    }

    @Test void parallelCallsNeverExceedCapacityAndOverflowDoesNotWait() throws Exception {
        input(); var entered = new CountDownLatch(2); var release = new CountDownLatch(1);
        var active = new AtomicInteger(); var peak = new AtomicInteger();
        when(gateway.analyze(any())).thenAnswer(call -> {
            peak.accumulateAndGet(active.incrementAndGet(), Math::max); entered.countDown();
            try { assertTrue(release.await(10, TimeUnit.SECONDS)); return "완료"; }
            finally { active.decrementAndGet(); }
        });
        try (var pool = Executors.newFixedThreadPool(3)) {
            var a = pool.submit(() -> service.analyze(1L, 3L));
            var b = pool.submit(() -> service.analyze(2L, 3L));
            try {
                assertTrue(entered.await(10, TimeUnit.SECONDS));
                var rejected = pool.submit(() -> assertThrows(CustomException.class, () -> service.analyze(4L, 3L)));
                assertEquals(ErrorCode.AI_ANALYSIS_BUSY, rejected.get(2, TimeUnit.SECONDS).getErrorCode());
            } finally { release.countDown(); }
            assertEquals("완료", a.get(10, TimeUnit.SECONDS).analysis());
            assertEquals("완료", b.get(10, TimeUnit.SECONDS).analysis());
        }
        assertEquals(2, peak.get());
        assertEquals("완료", service.analyze(4L, 3L).analysis());
    }
}
