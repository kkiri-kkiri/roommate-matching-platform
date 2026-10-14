package com.kkirikkiri.auth.service;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.kkirikkiri.auth.repository.AuthLimitStore;
import com.kkirikkiri.global.exception.AuthRateLimitException;

@SpringBootTest
@ActiveProfiles("test")
class AuthProtectionIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    AtomicLong time;
    AuthProtectionProperties config;
    AuthProtectionService first, second;

    @BeforeEach void setup() {
        jdbc.update("DELETE FROM auth_limit_bucket");
        time=new AtomicLong(1_000_000);
        config=new AuthProtectionProperties();
        first=instance(); second=instance();
    }
    AuthProtectionService instance() {
        var store=new AuthLimitStore(jdbc, manager) {
            @Override public long now() { return time.get(); }
        };
        return new AuthProtectionService(store, config);
    }
    @Test void failedVerificationSurvivesCallerRollbackAndRecoversWithoutExtendingBlock() {
        for (int i=0; i<5; i++) {
            var permit=first.verify("student@hknu.ac.kr", "127.0.0.1");
            new TransactionTemplate(manager).executeWithoutResult(tx -> {
                second.complete(permit, false);
                tx.setRollbackOnly();
            });
        }
        var denied=assertThrows(AuthRateLimitException.class, () -> second.verify("student@hknu.ac.kr", "127.0.0.2"));
        assertEquals(300, denied.getRetryAfterSeconds());
        time.addAndGet(100_000);
        assertEquals(200, assertThrows(AuthRateLimitException.class,
            () -> first.verify("student@hknu.ac.kr", "127.0.0.3")).getRetryAfterSeconds());
        time.addAndGet(200_000);
        second.complete(second.verify("student@hknu.ac.kr", "127.0.0.2"), true);
    }
    @Test void loginFailureLimitRecoversAndSuccessfulLoginResetsOnlyAccountFailures() {
        config.getLoginRequests().setEmailMax(100);
        for (int i=0; i<9; i++) first.complete(first.login("student@hknu.ac.kr", "127.0.0.1"), false);
        second.complete(second.login("student@hknu.ac.kr", "127.0.0.1"), true);
        for (int i=0; i<10; i++) first.complete(first.login("student@hknu.ac.kr", "127.0.0.1"), false);
        assertThrows(AuthRateLimitException.class, () -> second.login("student@hknu.ac.kr", "127.0.0.2"));
        time.addAndGet(60_000);
        first.complete(first.login("student@hknu.ac.kr", "127.0.0.2"), true);
        // Correct credentials must not wash away failures against other accounts from this IP.
        assertEquals(19, jdbc.queryForObject("SELECT MAX(failure_count) FROM auth_limit_bucket", Integer.class));
    }
    @Test void concurrentInstancesReserveAtMostFiveVerificationAttempts() throws Exception {
        config.getVerifyRequests().setEmailMax(100);
        List<AuthProtectionService.Permit> admitted=new CopyOnWriteArrayList<>();
        assertEquals(5, concurrent(24, i -> {
            var service=i%2==0 ? first : second;
            admitted.add(service.verify("student@hknu.ac.kr", "127.0.0."+(i+1)));
        }));
        admitted.forEach(p -> first.complete(p, false));
        assertThrows(AuthRateLimitException.class, () -> second.verify("student@hknu.ac.kr", "127.0.0.100"));
    }
    @Test void concurrentMailCooldownAndGlobalQuotaAreShared() throws Exception {
        assertEquals(1, concurrent(16, i -> (i%2==0?first:second).reset("student@hknu.ac.kr", "127.0.0."+(i+1))));
        config.setMailMax(3);
        assertEquals(3, concurrent(16, i -> (i%2==0?first:second).mail()));
        time.addAndGet(60_000);
        second.reset("student@hknu.ac.kr", "127.0.0.1");
        assertThrows(AuthRateLimitException.class, second::mail);
    }
    @Test void deniedIpCannotCreateUnlimitedAccountRowsAndCaseVariantsShareQuota() {
        config.getResetRequests().setIpMax(1);
        first.reset("Student@hknu.ac.kr", "127.0.0.1");
        int rows=jdbc.queryForObject("SELECT COUNT(*) FROM auth_limit_bucket", Integer.class);
        for (int i=0; i<10; i++) {
            String email="invented"+i+"@hknu.ac.kr";
            assertThrows(AuthRateLimitException.class, () -> second.reset(email,"127.0.0.1"));
        }
        assertEquals(rows, jdbc.queryForObject("SELECT COUNT(*) FROM auth_limit_bucket", Integer.class));
        assertThrows(AuthRateLimitException.class, () -> second.reset("student@hknu.ac.kr","127.0.0.2"));
    }
    @Test void crashedAndLateReservationsCannotCorruptNextWindow() {
        config.getVerifyRequests().setEmailMax(100);
        var old=first.verify("student@hknu.ac.kr", "127.0.0.1");
        time.addAndGet(300_000);
        var current=second.verify("student@hknu.ac.kr", "127.0.0.1");
        first.complete(old, false);
        assertEquals(0, jdbc.queryForObject("SELECT MAX(failure_count) FROM auth_limit_bucket",Integer.class));
        second.complete(current,true);
        assertEquals(0, jdbc.queryForObject("SELECT MAX(in_flight) FROM auth_limit_bucket",Integer.class));
    }
    @Test void ipFailureLimitAndRequestFrequencyApplyAcrossAccounts() {
        config.getLoginFailures().setIpMax(2);
        first.complete(first.login("one@hknu.ac.kr","127.0.0.1"),false);
        second.complete(second.login("two@hknu.ac.kr","127.0.0.1"),false);
        assertThrows(AuthRateLimitException.class,()->first.login("three@hknu.ac.kr","127.0.0.1"));
        config.getVerifyRequests().setIpMax(1);
        first.complete(first.verify("one@hknu.ac.kr","127.0.0.2"),true);
        assertThrows(AuthRateLimitException.class,()->second.verify("two@hknu.ac.kr","127.0.0.2"));
    }
    int concurrent(int count, java.util.function.IntConsumer action) throws Exception {
        var pool=Executors.newFixedThreadPool(8);
        var start=new CountDownLatch(1);
        try {
            var results=new ArrayList<Future<Boolean>>();
            for(int i=0;i<count;i++) { final int index=i; results.add(pool.submit(() -> {
                start.await();
                try { action.accept(index); return true; }
                catch(AuthRateLimitException limited) { return false; }
            })); }
            start.countDown();
            int accepted=0;
            for(var result:results) if(result.get(20,TimeUnit.SECONDS)) accepted++;
            return accepted;
        } finally { pool.shutdownNow(); }
    }
}
