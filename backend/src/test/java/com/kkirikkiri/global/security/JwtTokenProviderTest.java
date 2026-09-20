package com.kkirikkiri.global.security;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;

import org.junit.jupiter.api.Test;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import com.kkirikkiri.global.exception.CustomException;
import com.kkirikkiri.global.exception.ErrorCode;
import com.kkirikkiri.user.entity.UserRole;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

	// 테스트 전용 키이며 운영 설정에는 사용하지 않는다.
	private static final String SECRET = Base64.getEncoder().encodeToString(new byte[32]);
	private static final Instant NOW = Instant.parse("2026-09-20T00:00:00Z");
	private final JwtTokenProvider provider = providerAt(NOW);

	@Test
	void createsAndReadsBothRolesAndLongIds() throws Exception {
		for (UserRole role : UserRole.values()) {
			String token = provider.createAccessToken(Long.MAX_VALUE, role);
			provider.validateToken(token);
			assertEquals(Long.MAX_VALUE, provider.getUserId(token));
			assertEquals(role.name(), provider.getRole(token));
			assertEquals(new AuthenticatedUser(Long.MAX_VALUE, role.name()), provider.getAuthenticatedUser(token));
			JWTClaimsSet claims = SignedJWT.parse(token).getJWTClaimsSet();
			assertEquals(Date.from(NOW), claims.getIssueTime());
			assertEquals(Date.from(NOW.plusSeconds(60)), claims.getExpirationTime());
		}
		assertEquals(1L, provider.getUserId(provider.createAccessToken(1L, UserRole.USER)));
	}

	@Test
	void rejectsAtExpirationBoundaryAndAfter() {
		String token = provider.createAccessToken(1L, UserRole.USER);
		providerAt(NOW.plusSeconds(59)).validateToken(token);
		assertUnauthorized(() -> providerAt(NOW.plusSeconds(60)).getUserId(token));
		assertUnauthorized(() -> providerAt(NOW.plusSeconds(61)).getRole(token));
	}

	@Test
	void rejectsWrongSignatureAndDamagedTokens() {
		byte[] otherKey = new byte[32];
		otherKey[0] = 1;
		JwtTokenProvider other = new JwtTokenProvider(Base64.getEncoder().encodeToString(otherKey), 60,
				Clock.fixed(NOW, ZoneOffset.UTC));
		assertUnauthorized(() -> provider.validateToken(other.createAccessToken(1L, UserRole.USER)));
		for (String token : new String[] {null, "", " ", "broken", "a.b.c",
				"eyJhbGciOiJub25lIn0.eyJ1c2VySWQiOjF9."}) {
			assertUnauthorized(() -> provider.getAuthenticatedUser(token));
		}
	}

	@Test
	void rejectsModifiedPayloadWithOriginalSignature() {
		String token = provider.createAccessToken(1L, UserRole.USER);
		String[] parts = token.split("\\.");
		String payload = new String(Base64.getUrlDecoder().decode(parts[1]), java.nio.charset.StandardCharsets.UTF_8);
		parts[1] = Base64.getUrlEncoder().withoutPadding().encodeToString(
				payload.replace("USER", "ADMIN").getBytes(java.nio.charset.StandardCharsets.UTF_8));
		assertUnauthorized(() -> provider.getRole(String.join(".", parts)));
	}

	@Test
	void rejectsMissingExpirationAndInvalidClaims() throws Exception {
		assertUnauthorizedToken(new JWTClaimsSet.Builder().claim("userId", 1L).claim("role", "USER").build());
		for (Object id : new Object[] {0L, -1L, "1", 1.5, null}) {
			assertUnauthorizedToken(claims(id, "USER"));
		}
		assertUnauthorizedToken(claims(1L, "UNKNOWN"));
		assertUnauthorizedToken(claims(1L, null));
	}

	@Test
	void rejectsOtherAlgorithmsAndFutureNotBefore() throws Exception {
		byte[] key = new byte[64];
		JwtTokenProvider longKeyProvider = new JwtTokenProvider(Base64.getEncoder().encodeToString(key), 60,
				Clock.fixed(NOW, ZoneOffset.UTC));
		SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS512), claims(1L, "USER"));
		jwt.sign(new MACSigner(key));
		assertUnauthorized(() -> longKeyProvider.validateToken(jwt.serialize()));
		assertUnauthorizedToken(new JWTClaimsSet.Builder(claims(1L, "USER"))
				.notBeforeTime(Date.from(NOW.plusSeconds(1))).build());
	}

	@Test
	void rejectsBadConfigurationAndInvalidIssuance() {
		assertThrows(IllegalArgumentException.class, () -> new JwtTokenProvider("not base64!", 60));
		assertThrows(IllegalArgumentException.class, () -> new JwtTokenProvider("YQ==", 60));
		assertThrows(IllegalArgumentException.class, () -> new JwtTokenProvider(SECRET, 0));
		assertThrows(IllegalArgumentException.class, () -> new JwtTokenProvider(SECRET, -1));
		assertThrows(CustomException.class, () -> provider.createAccessToken(null, UserRole.USER));
		assertThrows(CustomException.class, () -> provider.createAccessToken(0L, UserRole.USER));
		assertThrows(CustomException.class, () -> provider.createAccessToken(1L, null));
	}

	private JWTClaimsSet claims(Object id, String role) {
		return new JWTClaimsSet.Builder().claim("userId", id).claim("role", role)
				.expirationTime(Date.from(NOW.plusSeconds(60))).build();
	}

	private void assertUnauthorizedToken(JWTClaimsSet claims) throws Exception {
		SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
		jwt.sign(new MACSigner(Base64.getDecoder().decode(SECRET)));
		assertUnauthorized(() -> provider.validateToken(jwt.serialize()));
	}

	private void assertUnauthorized(org.junit.jupiter.api.function.Executable action) {
		assertEquals(ErrorCode.UNAUTHORIZED, assertThrows(CustomException.class, action).getErrorCode());
	}

	private JwtTokenProvider providerAt(Instant instant) {
		return new JwtTokenProvider(SECRET, 60, Clock.fixed(instant, ZoneOffset.UTC));
	}
}
