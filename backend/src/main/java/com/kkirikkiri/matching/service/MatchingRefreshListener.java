package com.kkirikkiri.matching.service;

import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class MatchingRefreshListener {
    private final MatchingRefreshService refresh;
    private final Executor executor;

    public MatchingRefreshListener(MatchingRefreshService refresh,
            @Qualifier("matchingRefreshExecutor") Executor executor) {
        this.refresh = refresh;
        this.executor = executor;
    }

    @TransactionalEventListener
    public void refresh(MatchingInputsChanged event) {
        try {
            executor.execute(() -> {
                if (event.incoming()) run(event.userId(), true);
                if (event.outgoing()) run(event.userId(), false);
            });
        } catch (RejectedExecutionException rejected) {
            log.warn("Matching refresh queue full: userId={}; matching reads will repair scores", event.userId());
        }
    }

    private void run(Long userId, boolean incoming) {
        try {
            if (incoming) refresh.refreshIncoming(userId);
            else refresh.refreshOutgoing(userId);
        } catch (RuntimeException failure) {
            // Input is already committed. Every matching read synchronously repairs its required scores.
            log.error("Matching refresh failed: userId={}, incoming={}; matching reads will retry", userId, incoming, failure);
        }
    }
}
