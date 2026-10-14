package com.kkirikkiri.user.repository;

import java.util.Optional;
import com.kkirikkiri.user.entity.User;

public interface UserAccountLockRepository {
    /** Account writes lock USER first, then any reset token. Call before changing managed state. */
    Optional<User> findByIdForAccountUpdate(Long userId);
}
