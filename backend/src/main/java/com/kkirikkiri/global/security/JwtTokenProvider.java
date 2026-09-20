package com.kkirikkiri.global.security;

import java.text.ParseException;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import com.kkirikkiri.global.exception.CustomException;
import com.kkirikkiri.global.exception.ErrorCode;
import com.kkirikkiri.user.entity.UserRole;

/**
 * Bearer 접두사를 제외한 Access Token을 처리한다. 모든 추출 메서드는 먼저 토큰을 검증한다.
 */
@Component
public class JwtTokenProvider {

	private final byte[] secret;
	private final long expirationSeconds;
	private final Clock clock;

	@Autowired
	public JwtTokenProvider(@Value("${jwt.secret}") String secret,
			@Value("${jwt.access-token-expiration-seconds}") long expirationSeconds) {
		this(secret, expirationSeconds, Clock.systemUTC());
	}

	JwtTokenProvider(String secret, long expirationSeconds, Clock clock) {
		try {
			this.secret = Base64.getDecoder().decode(Objects.requireNonNull(secret, "jwt.secret"));
		} catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException("jwt.secret must be Base64 encoded");
		}
		if (this.secret.length < 32 || expirationSeconds <= 0) {
			throw new IllegalArgumentException("JWT requires at least 32 key bytes and a positive expiration");
		}
		this.expirationSeconds = expirationSeconds;
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	public String createAccessToken(Long userId, UserRole role) {
		if (userId == null || userId <= 0 || role == null) {
			throw new CustomException(ErrorCode.INVALID_REQUEST);
		}
		Instant now = clock.instant();
		JWTClaimsSet claims = new JWTClaimsSet.Builder()
				.claim("userId", userId)
				.claim("role", role.name())
				.issueTime(Date.from(now))
				.expirationTime(Date.from(now.plusSeconds(expirationSeconds)))
				.build();
		SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256)
				.type(JOSEObjectType.JWT).build(), claims);
		try {
			jwt.sign(new MACSigner(secret));
			return jwt.serialize();
		} catch (JOSEException exception) {
			throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
		}
	}

	/** 유효하지 않으면 기존 UNAUTHORIZED 예외를 발생시킨다. */
	public void validateToken(String token) {
		getAuthenticatedUser(token);
	}

	public Long getUserId(String token) {
		return getAuthenticatedUser(token).userId();
	}

	public String getRole(String token) {
		return getAuthenticatedUser(token).role();
	}

	public AuthenticatedUser getAuthenticatedUser(String token) {
		if (token == null || token.isBlank()) {
			throw new CustomException(ErrorCode.UNAUTHORIZED);
		}
		try {
			SignedJWT jwt = SignedJWT.parse(token);
			if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm())
					|| !jwt.verify(new MACVerifier(secret))) {
				throw new CustomException(ErrorCode.UNAUTHORIZED);
			}
			JWTClaimsSet claims = jwt.getJWTClaimsSet();
			Instant now = clock.instant();
			Date expiresAt = claims.getExpirationTime();
			Date notBefore = claims.getNotBeforeTime();
			Object id = claims.getClaim("userId");
			String role = claims.getStringClaim("role");
			if (expiresAt == null || !expiresAt.toInstant().isAfter(now)
					|| (notBefore != null && notBefore.toInstant().isAfter(now))
					|| (!(id instanceof Long) && !(id instanceof Integer))
					|| ((Number) id).longValue() <= 0 || role == null) {
				throw new CustomException(ErrorCode.UNAUTHORIZED);
			}
			return new AuthenticatedUser(((Number) id).longValue(), UserRole.valueOf(role).name());
		} catch (ParseException | JOSEException | IllegalArgumentException exception) {
			throw new CustomException(ErrorCode.UNAUTHORIZED);
		}
	}
}
