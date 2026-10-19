package com.kkirikkiri.community.post.entity;

import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Transactional outbox: only explicit image unlink operations create jobs. */
@Entity
@Table(name = "POST_IMAGE_CLEANUP", indexes = @Index(name = "idx_image_cleanup_due", columnList = "next_attempt_at,cleanup_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostImageCleanup {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cleanup_id", nullable = false)
    private Long cleanupId;

    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false, columnDefinition = "DATETIME")
    private LocalDateTime nextAttemptAt;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME")
    private LocalDateTime createdAt;

    public PostImageCleanup(String imageUrl) {
        this.imageUrl = java.util.Objects.requireNonNull(imageUrl);
        this.createdAt = LocalDateTime.now();
        this.nextAttemptAt = createdAt;
    }

    public void retryLater() {
        if (attempts < Integer.MAX_VALUE) attempts++;
        nextAttemptAt = LocalDateTime.now().plusMinutes(1);
    }
}
