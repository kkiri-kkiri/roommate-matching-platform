package com.kkirikkiri.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.HexFormat;

import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.kkirikkiri.auth.dto.EmailVerificationVerifyRequest;
import com.kkirikkiri.auth.dto.EmailVerificationVerifyResponse;
import com.kkirikkiri.auth.entity.EmailVerification;
import com.kkirikkiri.auth.entity.EmailVerificationPurpose;
import com.kkirikkiri.auth.repository.EmailVerificationRepository;
import com.kkirikkiri.global.exception.CustomException;
import com.kkirikkiri.global.exception.ErrorCode;

@Service
@Transactional(readOnly = true)
public class EmailVerificationService {

	private final EmailVerificationRepository repository;
	private final PasswordEncoder passwordEncoder;
	private final Validator validator;
	private final long tokenExpirationSeconds;
	private final Clock clock;
	private final SecureRandom random = new SecureRandom();

	@Autowired
	public EmailVerificationService(EmailVerificationRepository repository, PasswordEncoder passwordEncoder,
			Validator validator,
			@Value("${auth.email-verification.token-expiration-seconds:300}") long tokenExpirationSeconds) {
		this(repository, passwordEncoder, validator, tokenExpirationSeconds, Clock.system(ZoneId.of("Asia/Seoul")));
	}

	EmailVerificationService(EmailVerificationRepository repository, PasswordEncoder passwordEncoder,
			Validator validator, long tokenExpirationSeconds, Clock clock) {
		if (tokenExpirationSeconds <= 0) {
			throw new IllegalArgumentException("이메일 인증 토큰 만료시간은 양수여야 합니다.");
		}
		this.repository = repository;
		this.passwordEncoder = passwordEncoder;
		this.validator = validator;
		this.tokenExpirationSeconds = tokenExpirationSeconds;
		this.clock = clock;
	}

	@Transactional
	public EmailVerificationVerifyResponse verify(EmailVerificationVerifyRequest request) {
		if (request == null || !validator.validate(request).isEmpty()) {
			throw new CustomException(ErrorCode.VALIDATION_ERROR);
		}
		EmailVerificationPurpose purpose = EmailVerificationPurpose.valueOf(request.purpose());
		EmailVerification verification = repository
				.findFirstByEmailAndPurposeOrderByCreatedAtDescVerificationIdDesc(request.email(), purpose)
				.orElseThrow(() -> new CustomException(ErrorCode.INVALID_REQUEST));
		LocalDateTime now = LocalDateTime.now(clock);
		if (verification.isVerified() || verification.isConsumed() || verification.isCodeExpired(now)
				|| !passwordEncoder.matches(request.code(), verification.getVerificationCode())) {
			throw new CustomException(ErrorCode.INVALID_REQUEST);
		}
		// BCrypt 검증 도중 인증번호가 만료된 경우에도 발급하지 않는다.
		now = LocalDateTime.now(clock);
		if (verification.isCodeExpired(now)) {
			throw new CustomException(ErrorCode.INVALID_REQUEST);
		}
		byte[] bytes = new byte[32];
		random.nextBytes(bytes);
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		verification.markVerified(now, hash(token), now.plusSeconds(tokenExpirationSeconds));
		return new EmailVerificationVerifyResponse(true, token);
	}

	/**
	 * 토큰 사용과 후속 작업이 함께 커밋/롤백되도록 호출자의 쓰기 트랜잭션 안에서만 사용한다.
	 * 회원가입/아이디 찾기 자체는 이 서비스에서 처리하지 않는다.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void consumeToken(String email, EmailVerificationPurpose purpose, String token) {
		if (email == null || email.isBlank() || token == null || token.isBlank()
				|| (purpose != EmailVerificationPurpose.SIGNUP && purpose != EmailVerificationPurpose.FIND_ID)) {
			throw new CustomException(ErrorCode.VALIDATION_ERROR);
		}
		EmailVerification verification = repository
				.findByEmailAndPurposeAndVerificationTokenHash(email, purpose, hash(token))
				.orElseThrow(() -> new CustomException(ErrorCode.INVALID_REQUEST));
		LocalDateTime now = LocalDateTime.now(clock);
		if (!verification.isTokenUsable(now)) {
			throw new CustomException(ErrorCode.INVALID_REQUEST);
		}
		verification.consume(now);
	}

	private String hash(String token) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(token.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
		}
	}
}
