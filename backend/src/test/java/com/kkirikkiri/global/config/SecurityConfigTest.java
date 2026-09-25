package com.kkirikkiri.global.config;

import java.util.Base64;
import java.util.Optional;
import java.util.stream.Stream;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.ObjectMapper;

import com.kkirikkiri.global.security.JwtAuthenticationProvider;
import com.kkirikkiri.global.security.JwtTokenProvider;
import com.kkirikkiri.user.entity.User;
import com.kkirikkiri.user.entity.UserRole;
import com.kkirikkiri.user.entity.UserStatus;
import com.kkirikkiri.user.repository.UserRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(SecurityConfigTest.TestConfig.class)
@WebAppConfiguration
class SecurityConfigTest {

	@Autowired
	private FilterChainProxy securityFilterChain;

	@Autowired
	private JwtTokenProvider tokens;

	@Autowired
	private UserRepository users;

	@BeforeEach
	void setUp() {
		var user = mock(User.class);
		when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
		when(users.findById(1L)).thenReturn(Optional.of(user));
	}

	@ParameterizedTest
	@MethodSource("adminEndpoints")
	void anonymousCannotAccessAdmin(String method, String path) throws Exception {
		assertAccess(method, path, null, 401);
	}

	@ParameterizedTest
	@MethodSource("adminEndpoints")
	void userJwtCannotAccessAdmin(String method, String path) throws Exception {
		assertAccess(method, path, UserRole.USER, 403);
	}

	@ParameterizedTest
	@MethodSource("adminEndpoints")
	void adminJwtPassesSecurity(String method, String path) throws Exception {
		assertAccess(method, path, UserRole.ADMIN, 200);
	}

	@ParameterizedTest
	@CsvSource({
			"POST, /api/auth/login, , 200",
			"GET, /api/posts, , 200",
			"POST, /api/posts, , 401",
			"POST, /api/posts, USER, 200",
			"POST, /api/reports, , 401",
			"POST, /api/reports, USER, 200",
			"GET, /api/chat/rooms, , 401",
			"GET, /api/chat/rooms, USER, 200",
			"GET, /api/calendar/events, , 200",
			"POST, /api/calendar/events, , 401",
			"POST, /api/calendar/events, USER, 200",
			"GET, /api/users/me, USER, 200",
			"GET, /api/unmatched, ADMIN, 403"
	})
	void existingAccessRulesArePreserved(String method, String path, UserRole role, int expectedStatus)
			throws Exception {
		assertAccess(method, path, role, expectedStatus);
	}

	private void assertAccess(String method, String path, UserRole role, int expectedStatus) throws Exception {
		var request = new MockHttpServletRequest(method, path);
		request.setServletPath(path);
		if (role != null) {
			request.addHeader("Authorization", "Bearer " + tokens.createAccessToken(1L, role));
		}
		var response = new MockHttpServletResponse();
		var downstream = mock(FilterChain.class);

		securityFilterChain.doFilter(request, response, downstream);

		assertEquals(expectedStatus, response.getStatus());
		if (expectedStatus == 200) {
			verify(downstream).doFilter(any(), any());
		} else {
			verifyNoInteractions(downstream);
		}
	}

	static Stream<Arguments> adminEndpoints() {
		return Stream.of(
				Arguments.of("GET", "/api/admin/dashboard"),
				Arguments.of("GET", "/api/admin/members"),
				Arguments.of("GET", "/api/admin/members/1"),
				Arguments.of("PATCH", "/api/admin/members/1/status"),
				Arguments.of("GET", "/api/admin/posts"),
				Arguments.of("GET", "/api/admin/posts/1"),
				Arguments.of("DELETE", "/api/admin/posts/1"),
				Arguments.of("GET", "/api/admin/comments"),
				Arguments.of("GET", "/api/admin/comments/1"),
				Arguments.of("DELETE", "/api/admin/comments/1"),
				Arguments.of("GET", "/api/admin/reports"),
				Arguments.of("GET", "/api/admin/reports/1"),
				Arguments.of("PATCH", "/api/admin/reports/1"),
				Arguments.of("GET", "/api/admin/schedule-crawls/status"),
				Arguments.of("GET", "/api/admin/crawled-schedules"),
				Arguments.of("GET", "/api/admin/crawled-schedules/1"));
	}

	@Configuration
	@EnableWebSecurity
	@EnableWebMvc
	@Import(SecurityConfig.class)
	static class TestConfig {
		@Bean
		ObjectMapper objectMapper() {
			return new ObjectMapper();
		}

		@Bean
		JwtTokenProvider jwtTokenProvider() {
			return new JwtTokenProvider(Base64.getEncoder().encodeToString(new byte[32]), 3600);
		}

		@Bean
		UserRepository userRepository() {
			return mock(UserRepository.class);
		}

		@Bean
		JwtAuthenticationProvider jwtAuthenticationProvider(JwtTokenProvider tokens, UserRepository users) {
			return new JwtAuthenticationProvider(tokens, users);
		}
	}
}
