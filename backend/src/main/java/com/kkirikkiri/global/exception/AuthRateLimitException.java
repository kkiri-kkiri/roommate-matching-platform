package com.kkirikkiri.global.exception;

import lombok.Getter;

@Getter
public class AuthRateLimitException extends CustomException {
    private final long retryAfterSeconds;
    public AuthRateLimitException(ErrorCode code, long retryAfterSeconds) {
        super(code);
        this.retryAfterSeconds=retryAfterSeconds;
    }
}
