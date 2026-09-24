package com.kkirikkiri.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.ObjectMapper;

import com.kkirikkiri.global.exception.ErrorCode;
import com.kkirikkiri.global.response.ApiResponse;
import com.kkirikkiri.global.security.JwtAuthenticationFilter;
import com.kkirikkiri.global.security.JwtAuthenticationProvider;

@Configuration
public class SecurityConfig {

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper,
			JwtAuthenticationProvider jwtAuthenticationProvider,
			@Value("${app.chat.websocket.endpoint:}") String websocketEndpoint) throws Exception {
		return http
				.cors(Customizer.withDefaults())
				.csrf(csrf -> csrf.disable())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.requestCache(cache -> cache.disable())
				.formLogin(form -> form.disable())
				.httpBasic(basic -> basic.disable())
				.addFilterBefore(new JwtAuthenticationFilter(jwtAuthenticationProvider, objectMapper),
						UsernamePasswordAuthenticationFilter.class)
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/auth/email-verifications/send",
								"/api/auth/email-verifications/verify")
						.permitAll()
						.requestMatchers(HttpMethod.POST, "/api/auth/signup", "/api/auth/find-id",
								"/api/auth/password-reset/request", "/api/auth/password-reset/confirm")
						.permitAll()
						.requestMatchers(HttpMethod.GET, "/api/auth/signup/availability").permitAll()
						.requestMatchers("/api/users/me", "/api/users/me/password").authenticated()
						.requestMatchers(HttpMethod.GET, "/api/users/me/profile-survey",
								"/api/users/me/roommate-preferences")
						.authenticated()
						.requestMatchers(HttpMethod.PUT, "/api/users/me/profile-survey",
								"/api/users/me/roommate-preferences")
						.authenticated()
						.requestMatchers("/api/roommates/**", "/api/users/me/liked-roommates").authenticated()
						.requestMatchers(HttpMethod.GET, "/api/posts", "/api/posts/{postId}").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/posts", "/api/posts/{postId}/likes",
								"/api/posts/{postId}/comments", "/api/comments/{commentId}/replies").authenticated()
						.requestMatchers(HttpMethod.PATCH, "/api/posts/{postId}", "/api/comments/{commentId}").authenticated()
						.requestMatchers(HttpMethod.DELETE, "/api/posts/{postId}", "/api/posts/{postId}/likes",
								"/api/comments/{commentId}").authenticated()
						.requestMatchers(HttpMethod.POST, "/api/comments/{commentId}/likes").authenticated()
						.requestMatchers(HttpMethod.DELETE, "/api/comments/{commentId}/likes").authenticated()
						.requestMatchers(HttpMethod.GET, "/api/users/me/posts", "/api/users/me/comments").authenticated()
						.requestMatchers(HttpMethod.POST, "/api/reports").authenticated()
						.requestMatchers(HttpMethod.GET, "/api/calendar/events").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/calendar/events").authenticated()
						.requestMatchers(HttpMethod.PATCH, "/api/calendar/events/{eventId}").authenticated()
						.requestMatchers(HttpMethod.DELETE, "/api/calendar/events/{eventId}").authenticated()
						.requestMatchers(HttpMethod.GET, "/api/calendar/categories").authenticated()
						.requestMatchers(HttpMethod.PUT, "/api/calendar/categories").authenticated()
						.requestMatchers(HttpMethod.POST, "/api/calendar/categories/custom").authenticated()
						.requestMatchers(HttpMethod.PATCH, "/api/calendar/categories/{categoryId}").authenticated()
						.requestMatchers(HttpMethod.DELETE, "/api/calendar/categories/{categoryId}").authenticated()
						.requestMatchers("/api/chat/**").authenticated()
						// HTTP Upgrade만 허용하고 실제 인증은 STOMP CONNECT에서 수행한다.
						.requestMatchers(request -> !websocketEndpoint.isBlank()
								&& HttpMethod.GET.matches(request.getMethod())
								&& (request.getContextPath() + websocketEndpoint).equals(request.getRequestURI())
								&& "websocket".equalsIgnoreCase(request.getHeader("Upgrade"))).permitAll()
						.anyRequest().denyAll())
				.exceptionHandling(errors -> errors
						.authenticationEntryPoint((request, response, exception) -> {
							response.setStatus(401);
							response.setContentType("application/json;charset=UTF-8");
							objectMapper.writeValue(response.getWriter(), ApiResponse.failure(ErrorCode.UNAUTHORIZED));
						})
						.accessDeniedHandler((request, response, exception) -> {
							response.setStatus(403);
							response.setContentType("application/json;charset=UTF-8");
							objectMapper.writeValue(response.getWriter(), ApiResponse.failure(ErrorCode.FORBIDDEN));
						}))
				.build();
	}
}
