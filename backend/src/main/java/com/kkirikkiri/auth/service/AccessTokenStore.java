package com.kkirikkiri.auth.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.kkirikkiri.global.exception.CustomException;
import com.kkirikkiri.global.exception.ErrorCode;

/** 단일 서버용 저장소. 재시작 시 토큰은 폐기되며 비밀번호 변경 후에는 재인증한다. */
@Component
public class AccessTokenStore {

	private final Map<String, Token> tokens = new ConcurrentHashMap<>();
	private final SecureRandom random = new SecureRandom();
	private final Clock clock;

	public AccessTokenStore() {
		this(Clock.systemUTC());
	}

	AccessTokenStore(Clock clock) {
		this.clock = clock;
	}

	public String issue(Long userId, String passwordHash) {
		Instant now = clock.instant();
		tokens.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
		byte[] bytes = new byte[32];
		random.nextBytes(bytes);
		String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		tokens.put(value, new Token(userId, passwordHash, now.plus(Duration.ofHours(1))));
		return value;
	}

	public Token require(String value) {
		Token token = tokens.get(value);
		if (token == null || !token.expiresAt().isAfter(clock.instant())) {
			tokens.remove(value);
			throw new CustomException(ErrorCode.UNAUTHORIZED);
		}
		return token;
	}

	public record Token(Long userId, String passwordHash, Instant expiresAt) {
	}
}
