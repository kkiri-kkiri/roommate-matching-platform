package com.kkirikkiri.auth.service;

import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kkirikkiri.auth.dto.SignupRequest;
import com.kkirikkiri.auth.dto.SignupResponse;
import com.kkirikkiri.auth.entity.EmailVerificationPurpose;
import com.kkirikkiri.global.exception.CustomException;
import com.kkirikkiri.global.exception.ErrorCode;
import com.kkirikkiri.user.entity.Dormitory;
import com.kkirikkiri.user.entity.Faculty;
import com.kkirikkiri.user.entity.Major;
import com.kkirikkiri.user.entity.User;
import com.kkirikkiri.user.entity.UserProfile;
import com.kkirikkiri.user.repository.DormitoryRepository;
import com.kkirikkiri.user.repository.FacultyRepository;
import com.kkirikkiri.user.repository.MajorRepository;
import com.kkirikkiri.user.repository.UserProfileRepository;
import com.kkirikkiri.user.repository.UserRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SignupService {

	private final UserRepository userRepository;
	private final UserProfileRepository userProfileRepository;
	private final DormitoryRepository dormitoryRepository;
	private final FacultyRepository facultyRepository;
	private final MajorRepository majorRepository;
	private final EmailVerificationService emailVerificationService;
	private final PasswordEncoder passwordEncoder;
	private final Validator validator;

	/**
	 * SignupRequest의 admissionYear는 UserProfile의 admissionYear에 대응한다.
	 * 이메일 인증 토큰 소비와 사용자/프로필 저장은 함께 커밋하거나 롤백한다.
	 */
	@Transactional
	public SignupResponse signup(SignupRequest request) {
		if (request == null || !validator.validate(request).isEmpty()) {
			throw new CustomException(ErrorCode.VALIDATION_ERROR);
		}
		if (userRepository.existsByLoginId(request.loginId())
				|| userRepository.existsBySchoolEmail(request.schoolEmail())
				|| userProfileRepository.existsByNickname(request.nickname())) {
			throw new CustomException(ErrorCode.INVALID_REQUEST);
		}

		Dormitory dormitory = dormitoryRepository.findByDormitoryName(request.dormitory())
				.filter(Dormitory::isActive)
				.orElseThrow(() -> new CustomException(ErrorCode.INVALID_REQUEST));
		Faculty faculty = facultyRepository.findByFacultyName(request.faculty())
				.filter(Faculty::isActive)
				.orElseThrow(() -> new CustomException(ErrorCode.INVALID_REQUEST));
		Major major = majorRepository.findByFaculty_FacultyIdAndMajorName(faculty.getFacultyId(), request.major())
				.filter(Major::isActive)
				.orElseThrow(() -> new CustomException(ErrorCode.INVALID_REQUEST));

		String encodedPassword = passwordEncoder.encode(request.password());
		emailVerificationService.consumeToken(request.schoolEmail(), EmailVerificationPurpose.SIGNUP,
				request.emailVerificationToken());

		User user = new User(request.loginId(), encodedPassword, request.schoolEmail());
		user.verifyEmail();
		String mbti = request.mbtiEnergy() + request.mbtiInformation()
				+ request.mbtiDecision() + request.mbtiLifestyle();
		try {
			userRepository.saveAndFlush(user);
			userProfileRepository.saveAndFlush(new UserProfile(user, request.nickname(), request.gender(),
					dormitory, faculty, major, request.birthYear(), request.admissionYear(), request.grade(), mbti));
		} catch (DataIntegrityViolationException exception) {
			// 사전 중복 검사 이후의 동시 가입 충돌도 기존 예외 계약으로 처리한다.
			throw new CustomException(ErrorCode.INVALID_REQUEST);
		}
		return new SignupResponse(user.getUserId(), user.getLoginId(), request.nickname());
	}
}
