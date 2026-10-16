package com.kkirikkiri.auth.service;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.sql.SQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.dao.CannotAcquireLockException;
import com.kkirikkiri.auth.repository.AuthLimitStore;
import com.kkirikkiri.global.exception.AuthRateLimitException;

@SpringBootTest
@ActiveProfiles("test")
class AuthProtectionIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @Autowired AuthTransactions transactions;
    @Autowired jakarta.persistence.EntityManagerFactory entityManagerFactory;
    @Autowired com.kkirikkiri.user.repository.UserRepository users;
    AtomicLong time;
    AuthProtectionProperties config;
    AuthProtectionService first, second;

    @BeforeEach void setup() {
        jdbc.update("DELETE FROM auth_limit_bucket");
        jdbc.update("DELETE FROM auth_email_identity");
        // H2 cannot reproduce MySQL UCA. Keep the existing case-sharing assertion explicit.
        jdbc.execute("ALTER TABLE auth_email_identity ALTER COLUMN account_email VARCHAR_IGNORECASE(100)");
        jdbc.execute("ALTER TABLE auth_email_identity ALTER COLUMN verification_email VARCHAR_IGNORECASE(100)");
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
        int identities=jdbc.queryForObject("SELECT COUNT(*) FROM auth_email_identity", Integer.class);
        for (int i=0; i<10; i++) {
            String email="invented"+i+"@hknu.ac.kr";
            assertThrows(AuthRateLimitException.class, () -> second.reset(email,"127.0.0.1"));
        }
        assertEquals(rows, jdbc.queryForObject("SELECT COUNT(*) FROM auth_limit_bucket", Integer.class));
        assertEquals(identities, jdbc.queryForObject("SELECT COUNT(*) FROM auth_email_identity", Integer.class));
        assertThrows(AuthRateLimitException.class, () -> second.reset("student@hknu.ac.kr","127.0.0.2"));
    }

    @Test void identityAndReservationsRollBackWhenWorkFails() {
        var store = new AuthLimitStore(jdbc, manager);
        assertThrows(IllegalStateException.class, () -> store.lockedEmail("rollback@hknu.ac.kr",
            AuthLimitStore.EmailTarget.VERIFICATION, subject -> List.of("rollback-test"), (subject, rows) -> {
                throw new IllegalStateException("test rollback");
            }, result -> true));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM auth_email_identity", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM auth_limit_bucket", Integer.class));
    }

    @Test void retryRollsBackWholeTransactionAndUsesNewResourceForMysqlLockFailures() {
        for (int code : List.of(1213, 1205)) {
            var retry = new AuthDatabaseRetryProperties();
            retry.setInitialDelayMillis(1); retry.setJitterMillis(0);
            var store = new AuthLimitStore(jdbc, manager, retry);
            var attempts = new AtomicInteger();
            var resources = new ArrayList<Object>();
            String key = "mysql-retry-" + code;
            store.locked(List.of(key), rows -> {
                resources.add(TransactionSynchronizationManager.getResource(jdbc.getDataSource()));
                assertEquals(0, jdbc.queryForObject("SELECT failure_count FROM auth_limit_bucket WHERE bucket_key=?", Integer.class, key));
                if (attempts.getAndIncrement() == 0) {
                    jdbc.update("UPDATE auth_limit_bucket SET failure_count=9 WHERE bucket_key=?", key);
                    throw new CannotAcquireLockException("injected MySQL lock failure", new SQLException("test", "40001", code));
                }
                rows.getFirst().requests++;
                return null;
            });
            assertEquals(2, attempts.get());
            assertNotSame(resources.get(0), resources.get(1));
            assertEquals(1, jdbc.queryForObject("SELECT request_count FROM auth_limit_bucket WHERE bucket_key=?", Integer.class, key));
            assertEquals(0, jdbc.queryForObject("SELECT failure_count FROM auth_limit_bucket WHERE bucket_key=?", Integer.class, key));
        }
    }

    @Test void retryExhaustionLeavesNoPartialRowsAndDoesNotRetryConnectionFailures() {
        var retry = new AuthDatabaseRetryProperties();
        retry.setInitialDelayMillis(1); retry.setJitterMillis(0);
        var store = new AuthLimitStore(jdbc, manager, retry);
        var attempts = new AtomicInteger();
        assertThrows(CannotAcquireLockException.class, () -> store.locked(List.of("retry-exhaust"), rows -> {
            attempts.incrementAndGet();
            throw new CannotAcquireLockException("lock", new SQLException("test", "40001", 1213));
        }));
        assertEquals(3, attempts.get());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM auth_limit_bucket WHERE bucket_key='retry-exhaust'", Integer.class));
        attempts.set(0);
        assertThrows(CannotAcquireLockException.class, () -> store.locked(List.of("connection-error"), rows -> {
            attempts.incrementAndGet();
            throw new CannotAcquireLockException("connection", new SQLException("test", "08006", 0));
        }));
        assertEquals(1, attempts.get());
    }

    @Test void jdbcLimiterSuspendsJpaWithoutCommittingOuterWork() {
        var store = new AuthLimitStore(jdbc, manager);
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            jdbc.update("INSERT INTO auth_email_identity(account_email) VALUES ('outer-rollback@hknu.ac.kr')");
            store.locked(List.of("independent-jdbc"), rows -> { rows.getFirst().requests++; return null; });
            status.setRollbackOnly();
        });
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM auth_email_identity WHERE account_email='outer-rollback@hknu.ac.kr'", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT request_count FROM auth_limit_bucket WHERE bucket_key='independent-jdbc'", Integer.class));
    }

    @Test void lookupReleasesJpaResourcesBeforeNextIndependentReservation() {
        var noBusiness = new TransactionTemplate(manager);
        noBusiness.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
        noBusiness.executeWithoutResult(status -> {
            assertFalse(transactions.readOnly(() -> users.existsBySchoolEmail("lookup-only@hknu.ac.kr")));
            assertFalse(TransactionSynchronizationManager.hasResource(entityManagerFactory));
            assertFalse(TransactionSynchronizationManager.hasResource(jdbc.getDataSource()));
            new AuthLimitStore(jdbc, manager).locked(List.of("after-lookup"), rows -> { rows.getFirst().requests++; return null; });
        });
        assertEquals(1, jdbc.queryForObject("SELECT request_count FROM auth_limit_bucket WHERE bucket_key='after-lookup'", Integer.class));
    }

    @Test void businessExceptionsAndInterruptedRetryAreNotReplayed() {
        var store = new AuthLimitStore(jdbc, manager);
        var calls = new AtomicInteger();
        assertThrows(IllegalStateException.class, () -> store.locked(List.of("no-business-retry"), rows -> {
            calls.incrementAndGet(); throw new IllegalStateException("business failure");
        }));
        assertEquals(1, calls.get());
        Thread.currentThread().interrupt();
        try {
            assertThrows(CannotAcquireLockException.class, () -> store.locked(List.of("interrupted"), rows -> {
                throw new CannotAcquireLockException("lock", new SQLException("test", "40001", 1205));
            }));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally { Thread.interrupted(); }
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM auth_limit_bucket WHERE bucket_key='interrupted'", Integer.class));
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
