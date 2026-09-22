package com.kkirikkiri.global.security;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import com.kkirikkiri.global.exception.CustomException;
import com.kkirikkiri.global.exception.ErrorCode;
import com.kkirikkiri.global.response.ApiResponse;
import com.kkirikkiri.user.entity.User;
import com.kkirikkiri.user.entity.UserStatus;
import com.kkirikkiri.user.repository.UserRepository;

/**
 * SecurityFilterChain에 명시적으로 등록하여 사용한다.
 * 서블릿 필터로 자동 등록되지 않도록 Component로 선언하지 않는다.
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private final JwtTokenProvider jwtTokenProvider;
	private final ObjectMapper objectMapper;
	private final UserRepository userRepository;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
			filterChain.doFilter(request, response);
			return;
		}

		AuthenticatedUser principal;
		try {
			// 검증과 principal 추출을 한 번의 호출로 처리한다.
			principal = jwtTokenProvider.getAuthenticatedUser(authorization.substring(7).trim());
			User user = userRepository.findById(principal.userId())
					.orElseThrow(() -> new CustomException(ErrorCode.UNAUTHORIZED));
			if (user.getStatus() != UserStatus.ACTIVE) {
				throw new CustomException(ErrorCode.UNAUTHORIZED);
			}
		} catch (CustomException exception) {
			if (exception.getErrorCode() != ErrorCode.UNAUTHORIZED) {
				throw exception;
			}
			SecurityContextHolder.clearContext();
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			response.setContentType("application/json;charset=UTF-8");
			objectMapper.writeValue(response.getWriter(), ApiResponse.failure(ErrorCode.UNAUTHORIZED));
			return;
		}

		UsernamePasswordAuthenticationToken authentication =
				UsernamePasswordAuthenticationToken.authenticated(principal, null,
						List.of(new SimpleGrantedAuthority("ROLE_" + principal.role())));
		authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		SecurityContextHolder.setContext(context);

		filterChain.doFilter(request, response);
	}
}
