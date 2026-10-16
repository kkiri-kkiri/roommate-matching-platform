package com.kkirikkiri.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** JDBC performs atomic updates; this mapping also makes Hibernate validate the shared table. */
@Entity
@Table(name = "AUTH_LIMIT_BUCKET", indexes = @Index(name = "idx_auth_limit_updated_at", columnList = "updated_at"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthLimitBucket {
    @Id
    @Column(name = "bucket_key", nullable = false, length = 64)
    private String bucketKey;
    @Column(name = "window_started_at", nullable = false)
    private long windowStartedAt;
    @Column(name = "generation", nullable = false)
    private long generation;
    @Column(name = "request_count", nullable = false)
    private int requestCount;
    @Column(name = "failure_count", nullable = false)
    private int failureCount;
    @Column(name = "in_flight", nullable = false)
    private int inFlight;
    @Column(name = "blocked_until", nullable = false)
    private long blockedUntil;
    @Column(name = "updated_at", nullable = false)
    private long updatedAt;
}
