package com.kkirikkiri.auth.service;

import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Separates business rollback from durable rate-limit reservations and failure records. */
@Component
public class AuthTransactions {
    private final TransactionTemplate tx;
    public AuthTransactions(PlatformTransactionManager manager) {
        tx = new TransactionTemplate(manager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public <T> T execute(Supplier<T> work) { return tx.execute(status -> work.get()); }
}
