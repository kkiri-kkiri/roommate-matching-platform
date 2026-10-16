package com.kkirikkiri.auth.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kkirikkiri.auth.entity.PasswordResetToken;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    /** Read only the owner ID; account writes always lock USER before TOKEN. */
    @Query("select t.user.userId from PasswordResetToken t where t.tokenHash = :hash")
    Optional<Long> findUserIdByTokenHash(@Param("hash") String tokenHash);

	/**
	 * 토큰 해시로 조회하고 동일 토큰의 동시 사용을 막기 위해 비관적 쓰기 잠금을 획득한다.
	 * 호출자는 쓰기 트랜잭션 안에서 조회 후 isUsed(), isExpired(now)를 검증하고,
	 * 비밀번호 변경과 markUsed(now)를 같은 트랜잭션에서 처리해야 한다.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<PasswordResetToken> findByTokenHash(String tokenHash);
}
