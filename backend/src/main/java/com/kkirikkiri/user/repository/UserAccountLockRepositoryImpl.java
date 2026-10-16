package com.kkirikkiri.user.repository;

import java.util.Map;
import java.util.Optional;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import com.kkirikkiri.user.entity.User;

@RequiredArgsConstructor
public class UserAccountLockRepositoryImpl implements UserAccountLockRepository {
    private final EntityManager entityManager;

    @Override
    public Optional<User> findByIdForAccountUpdate(Long userId) {
        var hints = Map.<String, Object>of("jakarta.persistence.lock.timeout", 5000);
        try {
            // A non-locking find would establish an old RR snapshot for later profile reads.
            User user = entityManager.getReference(User.class, userId);
            LockModeType lockMode = entityManager.getLockMode(user);
            if (lockMode != LockModeType.PESSIMISTIC_WRITE
                    && lockMode != LockModeType.PESSIMISTIC_FORCE_INCREMENT) {
                // Acquire the lock while reloading an already managed snapshot. Locking find
                // followed by refresh lets Hibernate downgrade the refresh to a snapshot read.
                entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE, hints);
            }
            // Reentrant calls retain changes made after this transaction acquired the lock.
            return Optional.of(user);
        } catch (EntityNotFoundException missing) {
            return Optional.empty();
        }
    }
}
