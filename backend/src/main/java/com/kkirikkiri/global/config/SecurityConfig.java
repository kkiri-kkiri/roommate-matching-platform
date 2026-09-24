package com.kkirikkiri.global.config;

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
import com.kkirikkiri.global.security.JwtTokenProvider;
import com.kkirikkiri.user.repository.UserRepository;

@Configuration
public class SecurityConfig {

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper,
			JwtTokenProvider jwtTokenProvider, UserRepository userRepository) throws Exception {
		return http
				.cors(Customizer.withDefaults())
				.csrf(csrf -> csrf.disable())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.requestCache(cache -> cache.disable())
				.formLogin(form -> form.disable())
				.httpBasic(basic -> basic.disable())
				.addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider, objectMapper, userRepository),
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
						.requestMatchers(HttpMethod.POST, "/api/comments/{commentId}/likes").authenticated()
						.requestMatchers(HttpMethod.DELETE, "/api/comments/{commentId}/likes").authenticated()
						.requestMatchers(HttpMethod.GET, "/api/calendar/events").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/calendar/events").authenticated()
						.requestMatchers(HttpMethod.PATCH, "/api/calendar/events/{eventId}").authenticated()
						.requestMatchers(HttpMethod.DELETE, "/api/calendar/events/{eventId}").authenticated()
						.requestMatchers(HttpMethod.GET, "/api/calendar/categories").authenticated()
						.requestMatchers(HttpMethod.PUT, "/api/calendar/categories").authenticated()
						.requestMatchers(HttpMethod.POST, "/api/calendar/categories/custom").authenticated()
						.requestMatchers(HttpMethod.PATCH, "/api/calendar/categories/{categoryId}").authenticated()
						.requestMatchers(HttpMethod.DELETE, "/api/calendar/categories/{categoryId}").authenticated()
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
