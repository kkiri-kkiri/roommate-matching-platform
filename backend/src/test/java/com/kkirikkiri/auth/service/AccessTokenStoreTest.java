package com.kkirikkiri.auth.service;

import java.time.Clock;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.kkirikkiri.global.exception.CustomException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccessTokenStoreTest {

	@Test
	void tokensAreUniqueAndExpireAfterOneHour() {
		Clock clock = mock(Clock.class);
		Instant start = Instant.parse("2026-09-17T00:00:00Z");
		when(clock.instant()).thenReturn(start);
		AccessTokenStore store = new AccessTokenStore(clock);
		String token = store.issue(7L, "hash");
		assertNotEquals(token, store.issue(7L, "hash"));
		assertEquals(43, token.length());
		assertEquals(7L, store.require(token).userId());
		when(clock.instant()).thenReturn(start.plusSeconds(3600));
		assertThrows(CustomException.class, () -> store.require(token));
		assertThrows(CustomException.class, () -> store.require("forged"));
	}
}
