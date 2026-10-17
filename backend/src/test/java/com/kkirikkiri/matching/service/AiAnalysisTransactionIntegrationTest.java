package com.kkirikkiri.matching.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;
import com.kkirikkiri.global.exception.*;

@SpringBootTest(properties = "ai.analysis.max-requests=1")
@ActiveProfiles("test")
class AiAnalysisTransactionIntegrationTest {
    @Autowired AiProfileAnalysisService service;
    @Autowired PlatformTransactionManager manager;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean RoommateMatchingService matching;
    @MockitoBean AiGatewayClient gateway;

    @Test void gatewaySuspendsCallerTransactionAndQuotaSurvivesCallerRollback() throws Exception {
        long id=8675309L;
        String key=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(("ai-analysis:user:"+id).getBytes(StandardCharsets.UTF_8)));
        when(matching.getAnalysisInput(id,42L)).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return new RoommateMatchCalculator.Result(75,List.of(),List.of());
        });
        when(gateway.analyze(any())).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertFalse(TransactionSynchronizationManager.hasResource(jdbc.getDataSource()));
            return "설명";
        });
        try {
            new TransactionTemplate(manager).executeWithoutResult(tx -> {
                assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                assertEquals("설명",service.analyze(id,42L).analysis());
                assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                tx.setRollbackOnly();
            });
            assertEquals(ErrorCode.AI_ANALYSIS_RATE_LIMITED,
                    assertThrows(CustomException.class,()->service.analyze(id,42L)).getErrorCode());
            verify(gateway,times(1)).analyze(any());
        } finally { jdbc.update("delete from auth_limit_bucket where bucket_key=?",key); }
    }
}
