package com.kkirikkiri.matching.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.kkirikkiri.auth.repository.AuthLimitStore;
import com.kkirikkiri.global.exception.CustomException;
import com.kkirikkiri.global.exception.ErrorCode;

/** Shares the atomic MySQL bucket store, with a separate namespace from auth quotas. */
@Component
public class AiAnalysisRateLimiter {
    private final AuthLimitStore store;
    private final int maxRequests;
    private final long windowMillis;

    public AiAnalysisRateLimiter(AuthLimitStore store,
            @Value("${ai.analysis.max-requests:12}") int maxRequests,
            @Value("${ai.analysis.window-seconds:60}") long windowSeconds) {
        if (maxRequests < 1 || windowSeconds < 1 || windowSeconds > Long.MAX_VALUE / 1000)
            throw new IllegalArgumentException("Invalid AI analysis quota settings");
        this.store = store;
        this.maxRequests = maxRequests;
        this.windowMillis = windowSeconds * 1000;
    }

    public void reserve(Long userId) {
        if (userId == null || userId <= 0) throw new CustomException(ErrorCode.UNAUTHORIZED);
        boolean allowed = store.locked(List.of(key(userId)), rows -> {
            var bucket = rows.getFirst();
            long now = store.now();
            if (bucket.started == 0 || now - bucket.started >= windowMillis) {
                bucket.started = now;
                bucket.generation++;
                bucket.requests = 0;
            }
            if (bucket.requests >= maxRequests) return false;
            bucket.requests++;
            return true;
        }, result -> result);
        if (!allowed) throw new CustomException(ErrorCode.AI_ANALYSIS_RATE_LIMITED);
    }

    private String key(Long userId) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(("ai-analysis:user:" + userId).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 unavailable");
        }
    }
}
