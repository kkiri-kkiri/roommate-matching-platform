package com.kkirikkiri.global.security;

import java.util.Base64;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.ObjectMapper;

import com.kkirikkiri.user.entity.User;
import com.kkirikkiri.user.entity.UserRole;
import com.kkirikkiri.user.entity.UserStatus;
import com.kkirikkiri.user.repository.UserRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {

	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void restStillBuildsSecurityContextAndRejectsInvalidOrInactiveUser() throws Exception {
		var tokens = new JwtTokenProvider(Base64.getEncoder().encodeToString(new byte[32]), 3600);
		var users = mock(UserRepository.class);
		var user = mock(User.class);
		when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
		when(users.findById(1L)).thenReturn(Optional.of(user));
		var filter = new JwtAuthenticationFilter(new JwtAuthenticationProvider(tokens, users), new ObjectMapper());
		var request = new MockHttpServletRequest("GET", "/api/chat/rooms");
		request.addHeader("Authorization", "Bearer " + tokens.createAccessToken(1L, UserRole.values()[0]));
		var chain = mock(jakarta.servlet.FilterChain.class);
		var response = new MockHttpServletResponse();

		filter.doFilter(request, response, chain);

		verify(chain).doFilter(request, response);
		var authentication = SecurityContextHolder.getContext().getAuthentication();
		assertTrue(authentication.isAuthenticated());
		assertEquals(1L, ((AuthenticatedUser) authentication.getPrincipal()).userId());

		when(user.getStatus()).thenReturn(UserStatus.WITHDRAWN);
		var deniedResponse = new MockHttpServletResponse();
		var deniedChain = mock(jakarta.servlet.FilterChain.class);
		filter.doFilter(request, deniedResponse, deniedChain);
		assertEquals(401, deniedResponse.getStatus());
		assertNull(SecurityContextHolder.getContext().getAuthentication());
		verifyNoInteractions(deniedChain);

		request.removeHeader("Authorization");
		request.addHeader("Authorization", "Bearer invalid");
		var invalidResponse = new MockHttpServletResponse();
		filter.doFilter(request, invalidResponse, deniedChain);
		assertEquals(401, invalidResponse.getStatus());
		verifyNoInteractions(deniedChain);
	}
}
