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
    private final TransactionTemplate read;
    public AuthTransactions(PlatformTransactionManager manager) {
        tx = new TransactionTemplate(manager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        read = new TransactionTemplate(manager);
        read.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        read.setReadOnly(true);
    }
    public <T> T execute(Supplier<T> work) { return tx.execute(status -> work.get()); }
    /** Finish/close a lookup before the NOT_SUPPORTED caller reserves another independent DB transaction. */
    public <T> T readOnly(Supplier<T> work) { return read.execute(status -> work.get()); }
}
