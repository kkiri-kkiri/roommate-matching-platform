package com.kkirikkiri.community.post.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import com.kkirikkiri.community.post.entity.PostImageCleanup;

public interface PostImageCleanupRepository extends JpaRepository<PostImageCleanup, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from PostImageCleanup j where j.cleanupId = :id")
    Optional<PostImageCleanup> findForUpdate(@Param("id") Long id);

    @Query("select j.cleanupId from PostImageCleanup j where j.nextAttemptAt <= :now order by j.nextAttemptAt, j.cleanupId")
    List<Long> findDueIds(@Param("now") LocalDateTime now, Pageable pageable);
}
