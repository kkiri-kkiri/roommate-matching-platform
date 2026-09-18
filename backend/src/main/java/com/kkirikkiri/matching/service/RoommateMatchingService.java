package com.kkirikkiri.matching.service;

import java.util.List;

import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kkirikkiri.global.exception.CustomException;
import com.kkirikkiri.global.exception.ErrorCode;
import com.kkirikkiri.matching.dto.RoommateDetailResponse;
import com.kkirikkiri.matching.dto.RoommateFilterRequest;
import com.kkirikkiri.matching.dto.RoommatePageResponse;
import com.kkirikkiri.matching.dto.RoommateProfileResponse;
import com.kkirikkiri.matching.entity.ProfileLike;
import com.kkirikkiri.matching.entity.RoommateMatch;
import com.kkirikkiri.matching.repository.ProfileLikeRepository;
import com.kkirikkiri.matching.repository.RoommateMatchRepository;
import com.kkirikkiri.survey.entity.ProfileSurvey;
import com.kkirikkiri.survey.entity.ProfileSurveyFieldCode;
import com.kkirikkiri.survey.entity.ProfileSurveyOption;
import com.kkirikkiri.survey.repository.ProfileSurveyRepository;
import com.kkirikkiri.user.entity.User;
import com.kkirikkiri.user.entity.UserProfile;
import com.kkirikkiri.user.repository.UserProfileRepository;
import com.kkirikkiri.user.repository.UserRepository;

/**
 * userId에는 인증된 사용자의 ID를 전달한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoommateMatchingService {

	private static final int RECOMMENDED_MINIMUM_SCORE = 60;
	private static final int RECOMMENDED_MAX_PAGE_SIZE = 9;

	private final RoommateMatchRepository roommateMatchRepository;
	private final ProfileLikeRepository profileLikeRepository;
	private final UserRepository userRepository;
	private final UserProfileRepository userProfileRepository;
	private final ProfileSurveyRepository profileSurveyRepository;
	private final Validator validator;

	public RoommatePageResponse<RoommateProfileResponse> getRoommates(
			Long userId, RoommateFilterRequest filter, Pageable pageable) {
		validateUserId(userId);
		validatePageable(pageable);
		validateFilter(filter);

		Page<RoommateMatch> matches;
		if (hasAnyFilter(filter)) {
			matches = roommateMatchRepository.findAll(buildFilterSpecification(userId, filter), pageable);
		} else {
			matches = roommateMatchRepository.findAllByRequesterUser_UserId(userId, pageable);
		}

		return RoommatePageResponse.from(matches.map(match -> toProfileResponse(userId, match)));
	}

	public RoommatePageResponse<RoommateProfileResponse> getRecommendedRoommates(
			Long userId, Pageable pageable) {
		validateUserId(userId);
		validatePageable(pageable);
		Pageable limitedPageable = limitRecommendedPageSize(pageable);

		Page<RoommateProfileResponse> profiles = roommateMatchRepository
				.findAllByRequesterUser_UserIdAndMatchScoreGreaterThanEqualAndRequiredConditionsMetTrueOrderByMatchScoreDesc(
						userId, RECOMMENDED_MINIMUM_SCORE, limitedPageable)
				.map(match -> toProfileResponse(userId, match));
		return RoommatePageResponse.from(profiles);
	}

	public RoommateDetailResponse getRoommateDetail(Long userId, Long targetUserId) {
		validateDifferentUserIds(userId, targetUserId);

		RoommateMatch match = findMatch(userId, targetUserId);
		UserProfile profile = findProfile(targetUserId);
		ProfileSurvey survey = findSurvey(targetUserId);
		boolean liked = profileLikeRepository
				.existsByUser_UserIdAndTargetUser_UserId(userId, targetUserId);

		return RoommateDetailResponse.from(
				profile, survey, createTags(survey), match.getMatchScore(), liked);
	}

	@Transactional
	public void addProfileLike(Long userId, Long targetUserId) {
		validateDifferentUserIds(userId, targetUserId);
		if (profileLikeRepository.existsByUser_UserIdAndTargetUser_UserId(userId, targetUserId)) {
			return;
		}

		User user = findUser(userId);
		User targetUser = findUser(targetUserId);
		profileLikeRepository.save(new ProfileLike(user, targetUser));
	}

	@Transactional
	public void cancelProfileLike(Long userId, Long targetUserId) {
		validateDifferentUserIds(userId, targetUserId);
		profileLikeRepository.deleteByUser_UserIdAndTargetUser_UserId(userId, targetUserId);
	}

	public RoommatePageResponse<RoommateProfileResponse> getLikedRoommates(
			Long userId, Pageable pageable) {
		validateUserId(userId);
		validatePageable(pageable);

		Page<RoommateProfileResponse> profiles = profileLikeRepository
				.findAllByUser_UserId(userId, pageable)
				.map(profileLike -> toLikedProfileResponse(userId, profileLike));
		return RoommatePageResponse.from(profiles);
	}

	private RoommateProfileResponse toProfileResponse(Long userId, RoommateMatch match) {
		Long targetUserId = match.getTargetUser().getUserId();
		UserProfile profile = findProfile(targetUserId);
		ProfileSurvey survey = findSurvey(targetUserId);
		boolean liked = profileLikeRepository
				.existsByUser_UserIdAndTargetUser_UserId(userId, targetUserId);
		return RoommateProfileResponse.from(
				profile, createTags(survey), match.getMatchScore(), liked);
	}

	private RoommateProfileResponse toLikedProfileResponse(Long userId, ProfileLike profileLike) {
		Long targetUserId = profileLike.getTargetUser().getUserId();
		RoommateMatch match = findMatch(userId, targetUserId);
		UserProfile profile = findProfile(targetUserId);
		ProfileSurvey survey = findSurvey(targetUserId);
		return RoommateProfileResponse.from(
				profile, createTags(survey), match.getMatchScore(), true);
	}

	private List<String> createTags(ProfileSurvey survey) {
		return List.of(survey.getSmoking(), survey.getSleepTime(), survey.getCleaningFrequency());
	}

	private Specification<RoommateMatch> buildFilterSpecification(
			Long userId, RoommateFilterRequest filter) {
		Specification<RoommateMatch> specification = (root, query, criteriaBuilder) ->
				criteriaBuilder.equal(root.get("requesterUser").get("userId"), userId);

		specification = specification.and(hasSurveyValue("sleepTime", filter.sleepTime()));
		specification = specification.and(hasSurveyValue("wakeTime", filter.wakeTime()));
		specification = specification.and(hasSurveyValue("sleepTalking", filter.sleepTalking()));
		specification = specification.and(hasSurveyValue("snoring", filter.snoring()));
		specification = specification.and(hasSurveyValue("teethGrinding", filter.teethGrinding()));
		specification = specification.and(hasSurveyValue("sleepSensitivity", filter.sleepSensitivity()));
		specification = specification.and(hasSurveyValue("indoorEating", filter.indoorEating()));
		specification = specification.and(hasSurveyValue("smoking", filter.smoking()));
		specification = specification.and(hasSurveyValue("lightingPreference", filter.lightingPreference()));
		specification = specification.and(hasSurveyValue("phoneCall", filter.phoneCall()));
		specification = specification.and(hasSurveyValue("mediaSound", filter.mediaSound()));
		specification = specification.and(hasSurveyValue("showerTime", filter.showerTime()));
		specification = specification.and(hasSurveyValue("showerDuration", filter.showerDuration()));
		specification = specification.and(hasSurveyValue("cleaningFrequency", filter.cleaningFrequency()));
		specification = specification.and(hasSurveyValue("cleaningStyle", filter.cleaningStyle()));
		specification = specification.and(hasSurveyOptions(
				ProfileSurveyFieldCode.DORM_STAY_DAY, filter.dormStayDays()));
		specification = specification.and(hasSurveyOptions(
				ProfileSurveyFieldCode.MEAL_PLAN, filter.mealPlan()));
		specification = specification.and(hasSurveyValue("meal", filter.meal()));
		specification = specification.and(hasSurveyValue(
				"roommateInteraction", filter.roommateInteraction()));
		specification = specification.and(hasSurveyValue(
				"temperaturePreference", filter.temperaturePreference()));
		specification = specification.and(hasSurveyValue("hairDryerUse", filter.hairDryer()));
		specification = specification.and(hasSurveyValue("bugHandling", filter.bugHandling()));
		specification = specification.and(hasSurveyValue("scentProductUse", filter.scentProductUse()));
		specification = specification.and(hasSurveyValue("desktopUse", filter.desktopUse()));
		specification = specification.and(hasSurveyValue("sharedItems", filter.sharedItems()));
		return specification;
	}

	private Specification<RoommateMatch> hasSurveyValue(String attribute, List<String> values) {
		if (values == null || values.isEmpty()) {
			return null;
		}
		return (root, query, criteriaBuilder) -> {
			Subquery<Long> subquery = query.subquery(Long.class);
			Root<ProfileSurvey> survey = subquery.from(ProfileSurvey.class);
			subquery.select(survey.get("profileSurveyId"));
			subquery.where(
					criteriaBuilder.equal(
							survey.get("user").get("userId"),
							root.get("targetUser").get("userId")),
					criteriaBuilder.isTrue(survey.get("completed")),
					survey.get(attribute).in(values));
			return criteriaBuilder.exists(subquery);
		};
	}

	private Specification<RoommateMatch> hasSurveyOptions(
			ProfileSurveyFieldCode fieldCode, List<String> values) {
		if (values == null || values.isEmpty()) {
			return null;
		}
		return (root, query, criteriaBuilder) -> {
			Subquery<Long> subquery = query.subquery(Long.class);
			Root<ProfileSurveyOption> option = subquery.from(ProfileSurveyOption.class);
			subquery.select(option.get("profileSurveyOptionId"));
			subquery.where(
					criteriaBuilder.equal(
							option.get("profileSurvey").get("user").get("userId"),
							root.get("targetUser").get("userId")),
					criteriaBuilder.isTrue(option.get("profileSurvey").get("completed")),
					criteriaBuilder.equal(option.get("fieldCode"), fieldCode),
					option.get("optionValue").in(values));
			return criteriaBuilder.exists(subquery);
		};
	}

	private boolean hasAnyFilter(RoommateFilterRequest filter) {
		return filter != null && (
				hasValues(filter.sleepTime()) || hasValues(filter.wakeTime())
						|| hasValues(filter.sleepTalking()) || hasValues(filter.snoring())
						|| hasValues(filter.teethGrinding()) || hasValues(filter.sleepSensitivity())
						|| hasValues(filter.indoorEating()) || hasValues(filter.smoking())
						|| hasValues(filter.lightingPreference()) || hasValues(filter.phoneCall())
						|| hasValues(filter.mediaSound()) || hasValues(filter.showerTime())
						|| hasValues(filter.showerDuration()) || hasValues(filter.cleaningFrequency())
						|| hasValues(filter.cleaningStyle()) || hasValues(filter.dormStayDays())
						|| hasValues(filter.mealPlan()) || hasValues(filter.meal())
						|| hasValues(filter.roommateInteraction())
						|| hasValues(filter.temperaturePreference()) || hasValues(filter.hairDryer())
						|| hasValues(filter.bugHandling()) || hasValues(filter.scentProductUse())
						|| hasValues(filter.desktopUse()) || hasValues(filter.sharedItems()));
	}

	private boolean hasValues(List<String> values) {
		return values != null && !values.isEmpty();
	}

	private Pageable limitRecommendedPageSize(Pageable pageable) {
		if (pageable.getPageSize() <= RECOMMENDED_MAX_PAGE_SIZE) {
			return pageable;
		}
		return PageRequest.of(
				pageable.getPageNumber(), RECOMMENDED_MAX_PAGE_SIZE, pageable.getSort());
	}

	private User findUser(Long userId) {
		return userRepository.findById(userId)
				.orElseThrow(() -> new CustomException(ErrorCode.INVALID_REQUEST));
	}

	private UserProfile findProfile(Long userId) {
		return userProfileRepository.findByUser_UserId(userId)
				.orElseThrow(() -> new CustomException(ErrorCode.INVALID_REQUEST));
	}

	private ProfileSurvey findSurvey(Long userId) {
		return profileSurveyRepository.findByUser_UserId(userId)
				.filter(ProfileSurvey::isCompleted)
				.orElseThrow(() -> new CustomException(ErrorCode.INVALID_REQUEST));
	}

	private RoommateMatch findMatch(Long userId, Long targetUserId) {
		return roommateMatchRepository
				.findByRequesterUser_UserIdAndTargetUser_UserId(userId, targetUserId)
				.orElseThrow(() -> new CustomException(ErrorCode.INVALID_REQUEST));
	}

	private void validateDifferentUserIds(Long userId, Long targetUserId) {
		validateUserId(userId);
		validateUserId(targetUserId);
		if (userId.equals(targetUserId)) {
			throw new CustomException(ErrorCode.INVALID_REQUEST);
		}
	}

	private void validateUserId(Long userId) {
		if (userId == null || userId <= 0) {
			throw new CustomException(ErrorCode.INVALID_REQUEST);
		}
	}

	private void validatePageable(Pageable pageable) {
		if (pageable == null || pageable.isUnpaged()
				|| pageable.getPageNumber() < 0 || pageable.getPageSize() <= 0) {
			throw new CustomException(ErrorCode.INVALID_REQUEST);
		}
	}

	private void validateFilter(RoommateFilterRequest filter) {
		if (filter != null && !validator.validate(filter).isEmpty()) {
			throw new CustomException(ErrorCode.VALIDATION_ERROR);
		}
	}
}
