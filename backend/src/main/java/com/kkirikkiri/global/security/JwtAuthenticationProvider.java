package com.kkirikkiri.global.security;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import com.kkirikkiri.global.exception.CustomException;
import com.kkirikkiri.global.exception.ErrorCode;
import com.kkirikkiri.user.entity.UserStatus;
import com.kkirikkiri.user.repository.UserRepository;

/** REST와 STOMP에서 동일한 JWT 검증, 활성 사용자 확인, Principal 생성을 사용한다. */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationProvider {

	private final JwtTokenProvider jwtTokenProvider;
	private final UserRepository userRepository;

	public UsernamePasswordAuthenticationToken authenticate(String token) {
		AuthenticatedUser principal = jwtTokenProvider.getAuthenticatedUser(token);
		var user = userRepository.findById(principal.userId())
				.orElseThrow(() -> new CustomException(ErrorCode.UNAUTHORIZED));
		if (user.getStatus() != UserStatus.ACTIVE) {
			throw new CustomException(ErrorCode.UNAUTHORIZED);
		}
		return UsernamePasswordAuthenticationToken.authenticated(principal, null,
				List.of(new SimpleGrantedAuthority("ROLE_" + principal.role())));
	}
}
