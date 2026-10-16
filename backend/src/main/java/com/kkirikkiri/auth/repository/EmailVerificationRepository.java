package com.kkirikkiri.auth.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kkirikkiri.auth.entity.EmailVerification;
import com.kkirikkiri.auth.entity.EmailVerificationPurpose;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {

    /** Reservation has committed this existing UNIQUE row; point lock avoids empty-range GAP locks. */
    @Query(value = "SELECT identity_id FROM auth_email_identity WHERE verification_email = :email FOR UPDATE", nativeQuery = true)
    Optional<Long> lockVerificationEmailIdentity(@Param("email") String email);

    /** Only for send, after the stable identity mutex and before any consistent read in that transaction. */
    Optional<EmailVerification> findTopByEmailAndPurposeOrderByCreatedAtDescVerificationIdDesc(
            String email, EmailVerificationPurpose purpose);

	/**
	 * 재전송 간격 및 인증번호 검증에 사용할 가장 최근 요청을 조회한다.
	 * 만료 여부와 인증 완료 여부는 서비스에서 판단한다.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<EmailVerification> findFirstByEmailAndPurposeOrderByCreatedAtDescVerificationIdDesc(
			String email, EmailVerificationPurpose purpose);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<EmailVerification> findByEmailAndPurposeAndVerificationTokenHash(
			String email, EmailVerificationPurpose purpose, String verificationTokenHash);
}
