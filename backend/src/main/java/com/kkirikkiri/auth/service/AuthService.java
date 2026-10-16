package com.kkirikkiri.auth.service;

import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import com.kkirikkiri.auth.dto.LoginRequest;
import com.kkirikkiri.auth.dto.LoginEmailCheckRequest;
import com.kkirikkiri.auth.dto.LoginEmailCheckResponse;
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
	private final AuthProtectionService protection;
	private final AuthTransactions transactions;
	// Unknown/inactive users still incur one BCrypt check; never expose credential lookup results.
	private final String dummyPasswordHash = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("auth-dummy-password");

	public LoginResponse.LoginUser getAuthenticatedUser(Long userId) {
		User user = userRepository.findById(userId)
				.filter(account -> account.getStatus() == UserStatus.ACTIVE)
				.orElseThrow(() -> new CustomException(ErrorCode.UNAUTHORIZED));
		String nickname = userProfileRepository.findByUser_UserId(userId)
				.map(UserProfile::getNickname).orElse(user.getSchoolEmail());
		return new LoginResponse.LoginUser(user.getUserId(), user.getSchoolEmail(), nickname, user.getRole().name());
	}

	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public LoginEmailCheckResponse checkEmail(LoginEmailCheckRequest request, String ip) {
		if (request == null || !validator.validate(request).isEmpty()) {
			throw new CustomException(ErrorCode.VALIDATION_ERROR);
		}
		return new LoginEmailCheckResponse(protection.emailCheck(request.email(), ip));
	}

	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public LoginResponse login(LoginRequest request, String ip) {
		if (request == null || !validator.validate(request).isEmpty()) {
			throw new CustomException(ErrorCode.VALIDATION_ERROR);
		}

		var permit = protection.login(request.email(), ip);
		boolean success = false;
		try {
			User user = transactions.readOnly(() -> userRepository.findBySchoolEmail(request.email()).orElse(null));
			boolean passwordMatches = passwordEncoder.matches(request.password(), user == null ? dummyPasswordHash : user.getPassword());
			if (user == null || user.getStatus() != UserStatus.ACTIVE || !passwordMatches) {
				throw new CustomException(ErrorCode.UNAUTHORIZED);
			}
			String nickname = transactions.readOnly(() -> userProfileRepository.findByUser_UserId(user.getUserId())
					.map(UserProfile::getNickname).orElse(user.getSchoolEmail()));
			String accessToken = jwtTokenProvider.createAccessToken(user.getUserId(), user.getRole());
			success = true;
			return new LoginResponse(accessToken, new LoginResponse.LoginUser(
					user.getUserId(), user.getSchoolEmail(), nickname, user.getRole().name()));
		} finally { protection.complete(permit, success); }
	}
}
