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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import com.kkirikkiri.auth.service.AuthService;
import com.kkirikkiri.global.exception.CustomException;
import com.kkirikkiri.global.exception.ErrorCode;
import com.kkirikkiri.global.response.ApiResponse;

@RequiredArgsConstructor
public class BearerAuthenticationFilter extends OncePerRequestFilter {

	private final AuthService authService;
	private final ObjectMapper objectMapper;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
			FilterChain chain) throws ServletException, IOException {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header != null) {
			try {
				if (!header.regionMatches(true, 0, "Bearer ", 0, 7) || header.length() <= 7) {
					throw new CustomException(ErrorCode.UNAUTHORIZED);
				}
				AuthenticatedUser user = authService.authenticate(header.substring(7));
				var authentication = UsernamePasswordAuthenticationToken.authenticated(user, null,
						List.of(new SimpleGrantedAuthority("ROLE_" + user.role())));
				var context = SecurityContextHolder.createEmptyContext();
				context.setAuthentication(authentication);
				SecurityContextHolder.setContext(context);
			} catch (CustomException exception) {
				SecurityContextHolder.clearContext();
				response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
				response.setContentType("application/json;charset=UTF-8");
				objectMapper.writeValue(response.getWriter(), ApiResponse.failure(ErrorCode.UNAUTHORIZED));
				return;
			}
		}
		chain.doFilter(request, response);
	}
}
