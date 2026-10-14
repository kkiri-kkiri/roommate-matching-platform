package com.kkirikkiri.user.repository;

import java.util.Map;
import java.util.Optional;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import com.kkirikkiri.user.entity.User;

@RequiredArgsConstructor
public class UserAccountLockRepositoryImpl implements UserAccountLockRepository {
    private final EntityManager entityManager;

    @Override
    public Optional<User> findByIdForAccountUpdate(Long userId) {
        var hints = Map.<String, Object>of("jakarta.persistence.lock.timeout", 5000);
        User user = entityManager.find(User.class, userId, LockModeType.PESSIMISTIC_WRITE, hints);
        if (user != null) {
            // Admin/report reads may already have loaded this user before acquiring the lock.
            entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE, hints);
        }
        return Optional.ofNullable(user);
    }
}
