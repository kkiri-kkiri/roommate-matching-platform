package com.kkirikkiri.global.security;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.transaction.TransactionException;
import tools.jackson.databind.ObjectMapper;

import com.kkirikkiri.global.exception.CustomException;
import com.kkirikkiri.global.exception.ErrorCode;
import com.kkirikkiri.global.response.ApiResponse;

/**
 * SecurityFilterChain에 명시적으로 등록하여 사용한다.
 * 서블릿 필터로 자동 등록되지 않도록 Component로 선언하지 않는다.
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

	private final JwtAuthenticationProvider jwtAuthenticationProvider;
	private final ObjectMapper objectMapper;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
			filterChain.doFilter(request, response);
			return;
		}

		UsernamePasswordAuthenticationToken authentication;
		try {
			// 검증과 principal 추출을 한 번의 호출로 처리한다.
			authentication = jwtAuthenticationProvider.authenticate(authorization.substring(7).trim());
		} catch (CustomException exception) {
			if (exception.getErrorCode() != ErrorCode.UNAUTHORIZED) {
				throw exception;
			}
			SecurityContextHolder.clearContext();
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			response.setContentType("application/json;charset=UTF-8");
			objectMapper.writeValue(response.getWriter(), ApiResponse.failure(ErrorCode.UNAUTHORIZED));
			return;
		} catch (DataAccessException | TransactionException exception) {
			// Authentication's database check must fail closed, without treating an outage as a bad JWT.
			SecurityContextHolder.clearContext();
			String traceId = UUID.randomUUID().toString();
			Throwable cause = exception;
			for (int depth = 0; depth < 16 && cause.getCause() != null && cause.getCause() != cause; depth++) {
				cause = cause.getCause();
			}
			// Exception messages/stack traces may contain JDBC URLs, credentials or token values.
			log.error("JWT account lookup failed: traceId={} exceptionType={} rootCauseType={}",
					traceId, exception.getClass().getName(), cause.getClass().getName());
			response.setHeader("X-Request-ID", traceId);
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			response.setContentType("application/json;charset=UTF-8");
			objectMapper.writeValue(response.getWriter(), ApiResponse.failure(ErrorCode.INTERNAL_SERVER_ERROR));
			return;
		}

		authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		SecurityContextHolder.setContext(context);

		filterChain.doFilter(request, response);
	}
}
