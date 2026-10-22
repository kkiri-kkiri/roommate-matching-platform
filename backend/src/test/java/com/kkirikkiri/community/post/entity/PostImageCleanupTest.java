package com.kkirikkiri.community.post.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PostImageCleanupTest {
    @Test
    void newJobIsImmediatelyDueAtMysqlSecondPrecision() {
        LocalDateTime before = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        var job = new PostImageCleanup("/api/post-images/new.png");
        LocalDateTime after = LocalDateTime.now();

        assertEquals(0, job.getCreatedAt().getNano());
        assertEquals(job.getCreatedAt(), job.getNextAttemptAt());
        assertFalse(job.getNextAttemptAt().isBefore(before));
        assertTrue(job.isDue(after));
        assertTrue(job.isDue(job.getNextAttemptAt()));
    }

    @Test
    void exactDeadlineIsDueButEarlierInstantNeverGetsPrecisionTolerance() {
        var job = new PostImageCleanup("/api/post-images/retry.png");
        var deadline = LocalDateTime.of(2026, 10, 22, 11, 12, 13, 123456000);
        ReflectionTestUtils.setField(job, "nextAttemptAt", deadline);

        assertFalse(job.isDue(deadline.minusNanos(1)));
        assertTrue(job.isDue(deadline));
        assertTrue(job.isDue(deadline.plusNanos(1)));
        assertEquals(0, job.getAttempts());
        assertEquals(deadline, job.getNextAttemptAt());
    }
}
