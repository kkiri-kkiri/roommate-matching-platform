package com.kkirikkiri.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.ObjectMapper;

import com.kkirikkiri.global.exception.ErrorCode;
import com.kkirikkiri.global.response.ApiResponse;

@Configuration
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
		return http
				.cors(Customizer.withDefaults())
				// 인증 필터는 auth Service 구현 단계에서 연결한다.
				.csrf(csrf -> csrf.disable())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.requestCache(cache -> cache.disable())
				.formLogin(form -> form.disable())
				.httpBasic(basic -> basic.disable())
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
						.requestMatchers("/api/users/me", "/api/users/me/password").authenticated()
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
