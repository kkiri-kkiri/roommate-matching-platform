package com.kkirikkiri.matching.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class MatchingRefreshListener {
    private final MatchingRefreshService refresh;

    @TransactionalEventListener
    public void refresh(MatchingInputsChanged event) {
        if (event.incoming()) run(event.userId(), true);
        if (event.outgoing()) run(event.userId(), false);
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
