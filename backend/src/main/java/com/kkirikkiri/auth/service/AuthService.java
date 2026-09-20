package com.kkirikkiri.auth.service;

import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kkirikkiri.auth.dto.LoginRequest;
import com.kkirikkiri.auth.dto.LoginResponse;
import com.kkirikkiri.global.exception.CustomException;
import com.kkirikkiri.global.exception.ErrorCode;
import com.kkirikkiri.global.security.JwtTokenProvider;
import com.kkirikkiri.user.entity.User;
import com.kkirikkiri.user.entity.UserProfile;
import com.kkirikkiri.user.entity.UserStatus;
import com.kkirikkiri.user.repository.UserProfileRepository;
import com.kkirikkiri.user.repository.UserRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

	private final UserRepository userRepository;
	private final UserProfileRepository userProfileRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtTokenProvider jwtTokenProvider;
	private final Validator validator;

	public LoginResponse login(LoginRequest request) {
		if (request == null || !validator.validate(request).isEmpty()) {
			throw new CustomException(ErrorCode.VALIDATION_ERROR);
		}

		User user = userRepository.findByLoginId(request.loginId())
				.orElseThrow(() -> new CustomException(ErrorCode.UNAUTHORIZED));
		if (user.getStatus() != UserStatus.ACTIVE
				|| !passwordEncoder.matches(request.password(), user.getPassword())) {
			throw new CustomException(ErrorCode.UNAUTHORIZED);
		}

		UserProfile profile = userProfileRepository.findByUser_UserId(user.getUserId())
				.orElseThrow(() -> new CustomException(ErrorCode.INTERNAL_SERVER_ERROR));
		String accessToken = jwtTokenProvider.createAccessToken(user.getUserId(), user.getRole());
		return new LoginResponse(accessToken, new LoginResponse.LoginUser(
				user.getUserId(), user.getLoginId(), profile.getNickname(), user.getRole().name()));
	}
}
