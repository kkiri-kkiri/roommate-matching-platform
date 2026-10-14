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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import com.kkirikkiri.auth.dto.PasswordResetConfirmRequest;
import com.kkirikkiri.auth.dto.PasswordResetRequest;
import com.kkirikkiri.auth.dto.PasswordResetResponse;
import com.kkirikkiri.auth.entity.PasswordResetToken;
import com.kkirikkiri.auth.repository.PasswordResetTokenRepository;
import com.kkirikkiri.global.exception.CustomException;
import com.kkirikkiri.global.exception.ErrorCode;
import com.kkirikkiri.user.entity.User;
import com.kkirikkiri.user.entity.UserStatus;
import com.kkirikkiri.user.repository.UserRepository;

@Service
@Transactional(readOnly = true)
public class PasswordResetService {

	private final UserRepository userRepository;
	private final PasswordResetTokenRepository tokenRepository;
	private final PasswordResetMailSender mailSender;
	private final PasswordEncoder passwordEncoder;
	private final Validator validator;
	private final AuthProtectionService protection;
	private final AuthTransactions transactions;
	private final long tokenExpirationSeconds;
	private final Clock clock;
	private final SecureRandom random = new SecureRandom();

	@Autowired
	public PasswordResetService(UserRepository userRepository, PasswordResetTokenRepository tokenRepository,
			PasswordResetMailSender mailSender, PasswordEncoder passwordEncoder, Validator validator,
			AuthProtectionService protection, AuthTransactions transactions,
			@Value("${auth.password-reset.token-expiration-seconds:300}") long tokenExpirationSeconds) {
		this(userRepository, tokenRepository, mailSender, passwordEncoder, validator, protection, transactions, tokenExpirationSeconds,
				Clock.system(ZoneId.of("Asia/Seoul")));
	}

	PasswordResetService(UserRepository userRepository, PasswordResetTokenRepository tokenRepository,
			PasswordResetMailSender mailSender, PasswordEncoder passwordEncoder, Validator validator,
			AuthProtectionService protection, AuthTransactions transactions, long tokenExpirationSeconds, Clock clock) {
		if (tokenExpirationSeconds <= 0) {
			throw new IllegalArgumentException("비밀번호 재설정 토큰 만료시간은 양수여야 합니다.");
		}
		this.userRepository = userRepository;
		this.tokenRepository = tokenRepository;
		this.mailSender = mailSender;
		this.passwordEncoder = passwordEncoder;
		this.validator = validator;
		this.protection = protection;
		this.transactions = transactions;
		this.tokenExpirationSeconds = tokenExpirationSeconds;
		this.clock = clock;
	}

	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public PasswordResetResponse requestPasswordReset(PasswordResetRequest request, String ip) {
		validateRequest(request);
		protection.reset(request.email(), ip);
		if (userRepository.findBySchoolEmail(request.email())
				.filter(user -> user.getStatus() == UserStatus.ACTIVE).isEmpty()) {
			return new PasswordResetResponse(true);
		}
		protection.mail();
		return transactions.execute(() -> requestInTransaction(request));
	}

	private PasswordResetResponse requestInTransaction(PasswordResetRequest request) {
		var user = userRepository.findBySchoolEmail(request.email())
				.filter(value -> value.getStatus() == UserStatus.ACTIVE);
		// 계정 존재 여부를 응답으로 구분하지 않는다.
		if (user.isEmpty()) {
			return new PasswordResetResponse(true);
		}
		byte[] bytes = new byte[32];
		random.nextBytes(bytes);
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		tokenRepository.saveAndFlush(new PasswordResetToken(user.get(), hash(token),
				LocalDateTime.now(clock).plusSeconds(tokenExpirationSeconds)));
		// 메일 발송 실패 시 토큰 저장도 롤백한다.
		mailSender.send(user.get().getSchoolEmail(), token, tokenExpirationSeconds);
		return new PasswordResetResponse(true);
	}

	@Transactional
	public void resetPassword(PasswordResetConfirmRequest request) {
		validateRequest(request);
        String tokenHash = hash(request.resetToken());
        Long userId = tokenRepository.findUserIdByTokenHash(tokenHash)
                .orElseThrow(() -> new CustomException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID));
        User user = userRepository.findByIdForAccountUpdate(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID));
		PasswordResetToken token = tokenRepository.findByTokenHash(tokenHash)
				.orElseThrow(() -> new CustomException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID));
		LocalDateTime now = LocalDateTime.now(clock);
		if (token.isUsed()) {
			throw new CustomException(ErrorCode.PASSWORD_RESET_TOKEN_ALREADY_USED);
		}
		if (token.isExpired(now)) {
			throw new CustomException(ErrorCode.PASSWORD_RESET_TOKEN_EXPIRED);
		}
		if (user.getStatus() != UserStatus.ACTIVE) {
			throw new CustomException(ErrorCode.PASSWORD_RESET_ACCOUNT_INACTIVE);
		}
		String encodedPassword = passwordEncoder.encode(request.newPassword());
		// BCrypt 처리 중 만료된 토큰도 거절한다.
		now = LocalDateTime.now(clock);
		if (token.isExpired(now)) {
			throw new CustomException(ErrorCode.PASSWORD_RESET_TOKEN_EXPIRED);
		}
		user.changePassword(encodedPassword);
		token.markUsed(now);
	}

	private void validateRequest(Object request) {
		if (request == null || !validator.validate(request).isEmpty()) {
			throw new CustomException(ErrorCode.VALIDATION_ERROR);
		}
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
