package com.kkirikkiri.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kkirikkiri.auth.dto.LoginRequest;
import com.kkirikkiri.auth.dto.LoginResponse;
import com.kkirikkiri.global.exception.CustomException;
import com.kkirikkiri.global.exception.ErrorCode;
import com.kkirikkiri.global.security.AuthenticatedUser;
import com.kkirikkiri.user.entity.User;
import com.kkirikkiri.user.entity.UserStatus;
import com.kkirikkiri.user.repository.UserProfileRepository;
import com.kkirikkiri.user.repository.UserRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

	private final UserRepository userRepository;
	private final UserProfileRepository userProfileRepository;
	private final AccessTokenStore tokenStore;
	private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
	private final String dummyPasswordHash = passwordEncoder.encode("unused-login-password");

	public LoginResponse login(LoginRequest request) {
		User user = userRepository.findByLoginId(request.loginId()).orElse(null);
		boolean matches = passwordEncoder.matches(request.password(),
				user == null ? dummyPasswordHash : user.getPassword());
		if (!matches || user == null || user.getStatus() != UserStatus.ACTIVE) {
			throw new CustomException(ErrorCode.UNAUTHORIZED);
		}
		var profile = userProfileRepository.findByUser_UserId(user.getUserId())
				.orElseThrow(() -> new CustomException(ErrorCode.UNAUTHORIZED));
		return new LoginResponse(tokenStore.issue(user.getUserId(), user.getPassword()),
				new LoginResponse.LoginUser(user.getUserId(), user.getLoginId(),
						profile.getNickname(), user.getRole().name()));
	}

	public AuthenticatedUser authenticate(String accessToken) {
		var token = tokenStore.require(accessToken);
		User user = userRepository.findById(token.userId())
				.orElseThrow(() -> new CustomException(ErrorCode.UNAUTHORIZED));
		if (user.getStatus() != UserStatus.ACTIVE || !user.getPassword().equals(token.passwordHash())) {
			throw new CustomException(ErrorCode.UNAUTHORIZED);
		}
		return new AuthenticatedUser(user.getUserId(), user.getRole().name());
	}
}
