package com.kkirikkiri.community.post.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

@Component
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.community.image-cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class PostImageCleanupScheduler {
    private final PostImageCleanupService cleanup;

    @Scheduled(initialDelayString = "${app.community.image-cleanup.delay-ms:60000}",
            fixedDelayString = "${app.community.image-cleanup.delay-ms:60000}")
    public void retryPending() {
        try {
            cleanup.retryPending();
        } catch (RuntimeException failure) {
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("Image cleanup batch deferred");
        }
    }
}
