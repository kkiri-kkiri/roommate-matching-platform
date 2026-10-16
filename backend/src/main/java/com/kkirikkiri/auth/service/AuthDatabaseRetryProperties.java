package com.kkirikkiri.auth.service;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/** Applies only to the rollback-safe JDBC limiter transaction, never to mail/business callbacks. */
@Component
@ConfigurationProperties(prefix = "auth.database-retry")
@Validated
@Getter
@Setter
public class AuthDatabaseRetryProperties {
    @Min(1) @Max(5) private int maxAttempts = 3;
    @Min(1) @Max(1000) private long initialDelayMillis = 10;
    @Min(1) @Max(1000) private long maxDelayMillis = 100;
    @Min(0) @Max(100) private long jitterMillis = 10;
}
