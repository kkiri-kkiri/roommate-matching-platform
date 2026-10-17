package com.kkirikkiri.user.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kkirikkiri.user.entity.User;

public interface UserRepository extends JpaRepository<User, Long>, UserAccountLockRepository {

    /** 설문이 아직 없어도 사용자별 저장을 직렬화한다. 쓰기 트랜잭션에서 호출한다. */
    default Optional<User> findByIdForSurveyUpdate(Long userId) {
        // Reload stale managed users as well as locking the row shared with account writes.
        return findByIdForAccountUpdate(userId);
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.userId in :ids order by u.userId")
    List<User> lockMatchingUsers(@Param("ids") Collection<Long> ids);


    Optional<User> findBySchoolEmail(String schoolEmail);


    boolean existsBySchoolEmail(String schoolEmail);

    List<User> findAllBySchoolEmailIn(Collection<String> emails);
}
