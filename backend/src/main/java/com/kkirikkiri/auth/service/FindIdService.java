package com.kkirikkiri.auth.service;

import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kkirikkiri.auth.dto.FindIdRequest;
import com.kkirikkiri.auth.dto.FindIdResponse;
import com.kkirikkiri.auth.entity.EmailVerificationPurpose;
import com.kkirikkiri.global.exception.CustomException;
import com.kkirikkiri.global.exception.ErrorCode;
import com.kkirikkiri.user.entity.User;
import com.kkirikkiri.user.repository.UserRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FindIdService {

	private final UserRepository userRepository;
	private final EmailVerificationService emailVerificationService;
	private final Validator validator;

	@Transactional
	public FindIdResponse findId(FindIdRequest request) {
		if (request == null || !validator.validate(request).isEmpty()) {
			throw new CustomException(ErrorCode.VALIDATION_ERROR);
		}
		emailVerificationService.consumeToken(request.email(), EmailVerificationPurpose.FIND_ID,
				request.emailVerificationToken());
		User user = userRepository.findBySchoolEmail(request.email())
				.orElseThrow(() -> new CustomException(ErrorCode.INVALID_REQUEST));
		return new FindIdResponse(user.getLoginId());
	}
}
