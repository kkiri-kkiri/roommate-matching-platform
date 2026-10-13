package com.kkirikkiri.matching.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.*;

class MatchingRefreshListenerTest {
    @Configuration
    @EnableTransactionManagement
    static class Config {
        @Bean MatchingRefreshService refreshService() { return mock(MatchingRefreshService.class); }
        @Bean MatchingRefreshListener listener(MatchingRefreshService service) { return new MatchingRefreshListener(service); }
        @Bean AbstractPlatformTransactionManager transactionManager() {
            // Exercises Spring's real transaction event synchronization without a database.
            return new AbstractPlatformTransactionManager() {
                protected Object doGetTransaction() { return new Object(); }
                protected void doBegin(Object transaction, TransactionDefinition definition) {}
                protected void doCommit(DefaultTransactionStatus status) {}
                protected void doRollback(DefaultTransactionStatus status) {}
            };
        }
    }

    @Test void refreshesOnlyAfterCommitAndFailureCannotRollBackSavedInput() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var refresh = context.getBean(MatchingRefreshService.class);
            var tx = new TransactionTemplate(context.getBean(AbstractPlatformTransactionManager.class));
            tx.executeWithoutResult(status -> {
                context.publishEvent(new MatchingInputsChanged(1L, true, false));
                verifyNoInteractions(refresh);
            });
            verify(refresh).refreshIncoming(1L);
            reset(refresh);
            tx.executeWithoutResult(status -> {
                context.publishEvent(new MatchingInputsChanged(1L, true, false));
                status.setRollbackOnly();
            });
            verifyNoInteractions(refresh);
            doThrow(new IllegalStateException("calculation failed")).when(refresh).refreshIncoming(1L);
            assertDoesNotThrow(() -> tx.executeWithoutResult(status ->
                    context.publishEvent(new MatchingInputsChanged(1L, true, false))));
            verify(refresh).refreshIncoming(1L);
        }
    }
}
