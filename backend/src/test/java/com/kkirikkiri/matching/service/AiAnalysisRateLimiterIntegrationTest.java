package com.kkirikkiri.matching.service;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.kkirikkiri.auth.repository.AuthLimitStore;
import com.kkirikkiri.auth.service.*;
import com.kkirikkiri.global.exception.*;

@SpringBootTest
@ActiveProfiles("test")
class AiAnalysisRateLimiterIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    final AtomicLong time = new AtomicLong(1_000_000);
    final Set<String> keys = ConcurrentHashMap.newKeySet();
    AiAnalysisRateLimiter first, second;

    AuthLimitStore store() {
        return new AuthLimitStore(jdbc, manager) {
            @Override public long now() { return time.get(); }
            @Override public <T> T locked(List<String> selected, Function<List<Bucket>, T> action, Predicate<T> commit) {
                keys.addAll(selected); return super.locked(selected, action, commit);
            }
        };
    }
    @BeforeEach void setup() {
        first = new AiAnalysisRateLimiter(store(), 3, 60);
        second = new AiAnalysisRateLimiter(store(), 3, 60);
    }
    @AfterEach void cleanup() { keys.forEach(key -> jdbc.update("delete from auth_limit_bucket where bucket_key=?", key)); }

    @Test void limitsAreSharedAcrossObjectsButIndependentAcrossUsersAndAuth() {
        first.reserve(101L); second.reserve(101L); first.reserve(101L);
        assertEquals(ErrorCode.AI_ANALYSIS_RATE_LIMITED,
                assertThrows(CustomException.class, () -> second.reserve(101L)).getErrorCode());
        second.reserve(102L);
        var props = new AuthProtectionProperties(); props.setMailMax(1);
        var auth = new AuthProtectionService(store(), props); auth.mail();
        assertThrows(CustomException.class, auth::mail);
        first.reserve(102L);
        time.addAndGet(60_000); second.reserve(101L);
    }
    @Test void acceptedQuotaSurvivesBusinessRollbackAndDenialsDoNotExtendWindow() {
        for (int i = 0; i < 3; i++) new TransactionTemplate(manager).executeWithoutResult(tx -> {
            first.reserve(103L); tx.setRollbackOnly();
        });
        time.addAndGet(59_999);
        assertThrows(CustomException.class, () -> second.reserve(103L));
        time.incrementAndGet(); second.reserve(103L);
    }
    @Test void parallelIndependentLimitersReserveExactlyTheConfiguredMaximum() throws Exception {
        var start = new CountDownLatch(1); List<Future<Boolean>> futures = new ArrayList<>();
        try (var pool = Executors.newFixedThreadPool(8)) {
            for (int i = 0; i < 16; i++) {
                var limiter = i % 2 == 0 ? first : second;
                futures.add(pool.submit(() -> {
                    assertTrue(start.await(10, TimeUnit.SECONDS));
                    try { limiter.reserve(104L); return true; }
                    catch (CustomException error) { assertEquals(ErrorCode.AI_ANALYSIS_RATE_LIMITED, error.getErrorCode()); return false; }
                }));
            }
            start.countDown(); int allowed = 0;
            for (var future : futures) if (future.get(15, TimeUnit.SECONDS)) allowed++;
            assertEquals(3, allowed);
        }
    }
    @Test void unauthenticatedIdentityNeverUsesQuotaStorage() {
        assertEquals(ErrorCode.UNAUTHORIZED, assertThrows(CustomException.class, () -> first.reserve(null)).getErrorCode());
        assertTrue(keys.isEmpty());
    }
    @Test void storeFailureCannotBypassQuota() {
        var broken = org.mockito.Mockito.mock(AuthLimitStore.class);
        org.mockito.Mockito.when(broken.locked(org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("unavailable"));
        assertThrows(org.springframework.dao.DataAccessException.class,
                () -> new AiAnalysisRateLimiter(broken, 3, 60).reserve(105L));
    }
}
