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
import java.util.Locale;

import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.kkirikkiri.auth.dto.EmailVerificationVerifyRequest;
import com.kkirikkiri.auth.dto.EmailVerificationSendRequest;
import com.kkirikkiri.auth.dto.EmailVerificationSendResponse;
import com.kkirikkiri.auth.dto.EmailVerificationVerifyResponse;
import com.kkirikkiri.auth.entity.EmailVerification;
import com.kkirikkiri.auth.entity.EmailVerificationPurpose;
import com.kkirikkiri.auth.repository.EmailVerificationRepository;
import com.kkirikkiri.global.exception.CustomException;
import com.kkirikkiri.global.exception.ErrorCode;
import com.kkirikkiri.user.repository.UserRepository;

@Service
@Transactional(readOnly = true)
public class EmailVerificationService {

	private final EmailVerificationRepository repository;
	private final PasswordEncoder passwordEncoder;
	private final Validator validator;
	private final EmailVerificationMailSender mailSender;
	private final UserRepository userRepository;
	private final long codeExpirationSeconds;
	private final long resendIntervalSeconds;
	private final long tokenExpirationSeconds;
	private final Clock clock;
	private final SecureRandom random = new SecureRandom();

	@Autowired
	public EmailVerificationService(EmailVerificationRepository repository, PasswordEncoder passwordEncoder,
			Validator validator, EmailVerificationMailSender mailSender,
			UserRepository userRepository,
			@Value("${auth.email-verification.token-expiration-seconds:300}") long tokenExpirationSeconds,
			@Value("${auth.email-verification.code-expiration-seconds:300}") long codeExpirationSeconds,
			@Value("${auth.email-verification.resend-interval-seconds:60}") long resendIntervalSeconds) {
		this(repository, passwordEncoder, validator, mailSender, userRepository, tokenExpirationSeconds,
				codeExpirationSeconds, resendIntervalSeconds, Clock.system(ZoneId.of("Asia/Seoul")));
	}

	EmailVerificationService(EmailVerificationRepository repository, PasswordEncoder passwordEncoder,
			Validator validator, EmailVerificationMailSender mailSender, UserRepository userRepository,
			long tokenExpirationSeconds, long codeExpirationSeconds, long resendIntervalSeconds, Clock clock) {
		if (codeExpirationSeconds <= 0 || resendIntervalSeconds <= 0) {
			throw new IllegalArgumentException("Email verification durations must be positive");
		}
		if (tokenExpirationSeconds <= 0) {
			throw new IllegalArgumentException("이메일 인증 토큰 만료시간은 양수여야 합니다.");
		}
		this.repository = repository;
		this.passwordEncoder = passwordEncoder;
		this.validator = validator;
		this.mailSender = mailSender;
		this.userRepository = userRepository;
		this.codeExpirationSeconds = codeExpirationSeconds;
		this.resendIntervalSeconds = resendIntervalSeconds;
		this.tokenExpirationSeconds = tokenExpirationSeconds;
		this.clock = clock;
	}

	// MySQL의 범위 잠금으로 최초 요청을 포함한 동시 전송을 직렬화한다.
	@Transactional(isolation = Isolation.SERIALIZABLE)
	public EmailVerificationSendResponse send(EmailVerificationSendRequest request) {
		if (request == null || !validator.validate(request).isEmpty()) {
			throw new CustomException(ErrorCode.VALIDATION_ERROR);
		}
		EmailVerificationPurpose purpose = EmailVerificationPurpose.valueOf(request.purpose());
		if (purpose == EmailVerificationPurpose.SIGNUP
				&& userRepository.existsBySchoolEmail(request.email())) {
			throw new CustomException(ErrorCode.EMAIL_ALREADY_REGISTERED);
		}
		var latest = repository.findFirstByEmailAndPurposeOrderByCreatedAtDescVerificationIdDesc(
				request.email(), purpose);
		LocalDateTime now = LocalDateTime.now(clock);
		if (latest.isPresent() && latest.get().getCreatedAt().plusSeconds(resendIntervalSeconds).isAfter(now)) {
			throw new CustomException(ErrorCode.EMAIL_VERIFICATION_COOLDOWN);
		}
		String code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
		String codeHash = passwordEncoder.encode(code);
		repository.saveAndFlush(new EmailVerification(null, request.email(), purpose,
				codeHash, LocalDateTime.now(clock).plusSeconds(codeExpirationSeconds)));
		// 발송 실패는 예외로 전파하여 인증 요청 저장도 롤백한다.
		mailSender.send(request.email(), purpose, code, codeExpirationSeconds);
		return new EmailVerificationSendResponse(codeExpirationSeconds);
	}

	@Transactional
	public EmailVerificationVerifyResponse verify(EmailVerificationVerifyRequest request) {
		if (request == null || !validator.validate(request).isEmpty()) {
			throw new CustomException(ErrorCode.VALIDATION_ERROR);
		}
		EmailVerificationPurpose purpose = EmailVerificationPurpose.valueOf(request.purpose());
		EmailVerification verification = repository
				.findFirstByEmailAndPurposeOrderByCreatedAtDescVerificationIdDesc(request.email(), purpose)
				.orElseThrow(() -> new CustomException(ErrorCode.EMAIL_VERIFICATION_NOT_FOUND));
		LocalDateTime now = LocalDateTime.now(clock);
		if (verification.isVerified() || verification.isConsumed()) {
			throw new CustomException(ErrorCode.EMAIL_VERIFICATION_ALREADY_USED);
		}
		if (verification.isCodeExpired(now)) {
			throw new CustomException(ErrorCode.EMAIL_VERIFICATION_EXPIRED);
		}
		if (!passwordEncoder.matches(request.code(), verification.getVerificationCode())) {
			throw new CustomException(ErrorCode.EMAIL_VERIFICATION_INVALID);
		}
		// BCrypt 검증 도중 인증번호가 만료된 경우에도 발급하지 않는다.
		now = LocalDateTime.now(clock);
		if (verification.isCodeExpired(now)) {
			throw new CustomException(ErrorCode.EMAIL_VERIFICATION_EXPIRED);
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
				.orElseThrow(() -> new CustomException(ErrorCode.EMAIL_VERIFICATION_TOKEN_INVALID));
		LocalDateTime now = LocalDateTime.now(clock);
		if (!verification.isTokenUsable(now)) {
			throw new CustomException(ErrorCode.EMAIL_VERIFICATION_TOKEN_INVALID);
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
