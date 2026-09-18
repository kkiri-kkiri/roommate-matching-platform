package com.kkirikkiri.auth.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kkirikkiri.auth.entity.EmailVerification;
import com.kkirikkiri.auth.entity.EmailVerificationPurpose;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {

	/**
	 * 재전송 간격 및 인증번호 검증에 사용할 가장 최근 요청을 조회한다.
	 * 만료 여부와 인증 완료 여부는 서비스에서 판단한다.
	 */
	Optional<EmailVerification> findFirstByEmailAndPurposeOrderByCreatedAtDescVerificationIdDesc(
			String email, EmailVerificationPurpose purpose);
}
