package com.kkirikkiri.user.service;

import java.util.Objects;

import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kkirikkiri.global.exception.CustomException;
import com.kkirikkiri.global.exception.ErrorCode;
import com.kkirikkiri.user.dto.PasswordChangeRequest;
import com.kkirikkiri.user.dto.UserResponse;
import com.kkirikkiri.user.dto.UserUpdateRequest;
import com.kkirikkiri.user.entity.Dormitory;
import com.kkirikkiri.user.entity.Faculty;
import com.kkirikkiri.user.entity.Major;
import com.kkirikkiri.user.entity.User;
import com.kkirikkiri.user.entity.UserProfile;
import com.kkirikkiri.user.entity.UserStatus;
import com.kkirikkiri.user.repository.DormitoryRepository;
import com.kkirikkiri.user.repository.FacultyRepository;
import com.kkirikkiri.user.repository.MajorRepository;
import com.kkirikkiri.user.repository.UserProfileRepository;
import com.kkirikkiri.user.repository.UserRepository;

/**
 * userId에는 인증된 사용자의 ID를 전달한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

	private final UserRepository userRepository;
	private final UserProfileRepository userProfileRepository;
	private final DormitoryRepository dormitoryRepository;
	private final FacultyRepository facultyRepository;
	private final MajorRepository majorRepository;
	private final Validator validator;
	private final PasswordEncoder passwordEncoder;
    private final org.springframework.context.ApplicationEventPublisher events;

	public UserResponse getMyInfo(Long userId) {
		return UserResponse.from(findProfile(userId));
	}

	@Transactional
	public UserResponse updateMyInfo(Long userId, UserUpdateRequest request) {
		validateRequest(request);
        validateUserId(userId);
        // Match the survey lock protocol before reading comparison inputs.
        requireActive(userRepository.findByIdForAccountUpdate(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_REQUEST)));
		UserProfile profile = findProfile(userId);

		if (request.nickname() != null
				&& userProfileRepository.existsByNicknameAndUser_UserIdNot(request.nickname(), userId)) {
			throw new CustomException(ErrorCode.INVALID_REQUEST);
		}

		Dormitory dormitory = profile.getDormitory();
		if (request.dormitory() != null) {
			dormitory = dormitoryRepository.findByDormitoryName(request.dormitory())
					.filter(Dormitory::isActive)
					.orElseThrow(() -> new CustomException(ErrorCode.INVALID_REQUEST));
		}

		Faculty faculty = profile.getFaculty();
		if (request.faculty() != null) {
			faculty = facultyRepository.findByFacultyName(request.faculty())
					.filter(Faculty::isActive)
					.orElseThrow(() -> new CustomException(ErrorCode.INVALID_REQUEST));
		}

		Major major = profile.getMajor();
		if ("세부전공 없음".equals(request.major())) {
            major = null;
        } else if (request.major() != null) {
			major = majorRepository.findByFaculty_FacultyIdAndMajorName(faculty.getFacultyId(), request.major())
					.filter(Major::isActive)
					.orElseThrow(() -> new CustomException(ErrorCode.INVALID_REQUEST));
		}
		// 전공을 생략하면 기존 전공을 유지하되, 변경한 학부와의 소속 불일치는 허용하지 않는다.
		if ((request.faculty() != null || request.major() != null) && major != null
				&& !Objects.equals(faculty.getFacultyId(), major.getFaculty().getFacultyId())) {
			throw new CustomException(ErrorCode.INVALID_REQUEST);
		}

        boolean departmentChanged = !Objects.equals(profile.getFaculty().getFacultyId(), faculty.getFacultyId())
                || !Objects.equals(profile.getMajor() == null ? null : profile.getMajor().getMajorId(),
                        major == null ? null : major.getMajorId());
        boolean gradeChanged = request.grade() != null && !Objects.equals(profile.getGrade(), request.grade());

		if (request.nickname() != null) {
			profile.changeNickname(request.nickname());
		}
		if (request.dormitory() != null) {
			profile.changeDormitory(dormitory);
		}
		if (request.faculty() != null || request.major() != null) {
			profile.changeAcademicAffiliation(faculty, major);
		}
		if (request.grade() != null) {
			profile.changeGrade(request.grade());
		}
		if (request.mbtiEnergy() != null || request.mbtiInformation() != null
				|| request.mbtiDecision() != null || request.mbtiLifestyle() != null) {
			String mbti = profile.getMbti();
			profile.changeMbti(
					(request.mbtiEnergy() == null ? mbti.substring(0, 1) : request.mbtiEnergy())
					+ (request.mbtiInformation() == null ? mbti.substring(1, 2) : request.mbtiInformation())
					+ (request.mbtiDecision() == null ? mbti.substring(2, 3) : request.mbtiDecision())
					+ (request.mbtiLifestyle() == null ? mbti.substring(3, 4) : request.mbtiLifestyle()));
		}

        try {
            userProfileRepository.flush();
        } catch (DataIntegrityViolationException failure) {
            // Only nickname is changed among this entity's UNIQUE columns.
            if (request.nickname() != null && isUniqueViolation(failure)) {
                throw new CustomException(ErrorCode.INVALID_REQUEST);
            }
            throw failure;
        }
        if (departmentChanged || gradeChanged) {
            events.publishEvent(new com.kkirikkiri.matching.service.MatchingInputsChanged(
                    userId, true, departmentChanged));
        }
		// Repository로 조회한 관리 엔티티의 변경은 트랜잭션 커밋 시 반영된다.
		return UserResponse.from(profile);
	}

	@Transactional
	public void changePassword(Long userId, PasswordChangeRequest request) {
		validateRequest(request);
		validateUserId(userId);
		User user = userRepository.findByIdForAccountUpdate(userId)
				.orElseThrow(() -> new CustomException(ErrorCode.INVALID_REQUEST));
        requireActive(user);
		if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
			throw new CustomException(ErrorCode.INVALID_REQUEST);
		}
		user.changePassword(passwordEncoder.encode(request.newPassword()));
	}

	private UserProfile findProfile(Long userId) {
		validateUserId(userId);
		return userProfileRepository.findByUser_UserId(userId)
				.orElseThrow(() -> new CustomException(ErrorCode.INVALID_REQUEST));
	}

    private void requireActive(User user) {
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
    }

    private boolean isUniqueViolation(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof java.sql.SQLException sql
                    && ("23505".equals(sql.getSQLState()) || sql.getErrorCode() == 1062)) return true;
        }
        return false;
    }

	private void validateUserId(Long userId) {
		if (userId == null || userId <= 0) {
			throw new CustomException(ErrorCode.INVALID_REQUEST);
		}
	}

	private void validateRequest(Object request) {
		if (request == null || !validator.validate(request).isEmpty()) {
			throw new CustomException(ErrorCode.VALIDATION_ERROR);
		}
	}
}
