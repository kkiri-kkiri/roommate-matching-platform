package com.kkirikkiri.matching.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import com.kkirikkiri.global.exception.*;
import com.kkirikkiri.matching.dto.*;
import com.kkirikkiri.matching.entity.*;
import com.kkirikkiri.matching.repository.*;
import com.kkirikkiri.survey.entity.*;
import com.kkirikkiri.survey.repository.ProfileSurveyRepository;
import com.kkirikkiri.user.entity.*;
import com.kkirikkiri.user.repository.*;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class RoommateMatchingService {
    private final RoommateMatchRepository roommateMatchRepository;
    private final ProfileLikeRepository profileLikeRepository;
    private final PlatformTransactionManager transactionManager;
    private final UserProfileRepository userProfileRepository;
    private final ProfileSurveyRepository profileSurveyRepository;
    private final UserRepository userRepository;
    private final MatchingRefreshService refreshService;
    private final Validator validator;

    public RoommatePageResponse<RoommateProfileResponse> getRoommates(
            Long userId, RoommateFilterRequest filter, Pageable pageable) {
        if (userId != null) validateUserId(userId);
        validatePageable(pageable);
        validateFilter(filter);
        Pageable sorted = ordered(pageable, "userId", "user.userId", false);
        // Choose the page in SQL before preparing only its scores.
        Page<Long> ids = read(() -> userProfileRepository.findAll(
                MatchingCandidates.eligible(userId).and(buildFilterSpecification(userId, filter)), sorted)
                .map(profile -> profile.getUser().getUserId()));
        if (userId != null) refreshService.refreshTargets(userId, ids.getContent());
        return read(() -> toIdPage(userId, ids));
    }

    public RoommatePageResponse<RoommateProfileResponse> getRecommendedRoommates(Long userId, Pageable pageable) {
        validateUserId(userId);
        validatePageable(pageable);
        Pageable sorted = recommendedPageable(pageable);
        // Ranking needs all scores for this requester, never all user pairs.
        refreshService.refreshOutgoing(userId);
        return read(() -> {
            Specification<RoommateMatch> spec = MatchingCandidates.<RoommateMatch>eligibleTarget(userId)
                    .and((root, query, cb) -> cb.and(
                            cb.equal(root.get("requesterUser").get("userId"), userId),
                            cb.ge(root.get("matchScore"), 60)));
            Page<Long> ids = roommateMatchRepository.findAll(spec, sorted)
                    .map(match -> match.getTargetUser().getUserId());
            return toIdPage(userId, ids);
        });
    }

    public RoommateDetailResponse getRoommateDetail(Long userId, Long targetUserId) {
        validateUserId(targetUserId);
        if (userId != null) validateDifferentUserIds(userId, targetUserId);
        read(() -> findCandidate(userId, targetUserId));
        if (userId != null) refreshService.refreshTargets(userId, List.of(targetUserId));
        return read(() -> {
            UserProfile profile = findCandidate(userId, targetUserId);
            ProfileSurvey survey = profileSurveyRepository.findByUser_UserId(targetUserId).orElseThrow();
            Integer score = userId == null ? null
                    : roommateMatchRepository.findByRequesterUser_UserIdAndTargetUser_UserId(userId, targetUserId)
                    .map(RoommateMatch::getMatchScore).orElse(null);
            return RoommateDetailResponse.from(profile, survey, createTags(survey), score,
                    userId != null && profileLikeRepository.existsByUser_UserIdAndTargetUser_UserId(userId, targetUserId));
        });
    }

    public RoommateMatchCalculator.Result getAnalysisInput(Long userId, Long targetUserId) {
        validateDifferentUserIds(userId, targetUserId);
        read(() -> findCandidate(userId, targetUserId));
        return refreshService.refreshAnalysis(userId, targetUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.MATCH_ANALYSIS_UNAVAILABLE));
    }

    /** A uniqueness failure is checked outside the failed transaction. */
    public void addProfileLike(Long userId, Long targetUserId) {
        validateDifferentUserIds(userId, targetUserId);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        try {
            transaction.executeWithoutResult(status -> {
                UserProfile target = findCandidate(userId, targetUserId);
                User requester = userRepository.findById(userId)
                        .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                        .orElseThrow(() -> new CustomException(ErrorCode.INVALID_REQUEST));
                if (!profileLikeRepository.existsByUser_UserIdAndTargetUser_UserId(userId, targetUserId)) {
                    profileLikeRepository.saveAndFlush(new ProfileLike(requester, target.getUser()));
                }
            });
        } catch (DataIntegrityViolationException exception) {
            Boolean exists = read(() -> profileLikeRepository.existsByUser_UserIdAndTargetUser_UserId(userId, targetUserId));
            if (!Boolean.TRUE.equals(exists)) throw exception;
        }
    }

    @Transactional
    public void cancelProfileLike(Long userId, Long targetUserId) {
        validateDifferentUserIds(userId, targetUserId);
        ProfileLike like = profileLikeRepository.findByUser_UserIdAndTargetUser_UserId(userId, targetUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_REQUEST));
        profileLikeRepository.delete(like);
    }

    public RoommatePageResponse<RoommateProfileResponse> getLikedRoommates(Long userId, Pageable pageable) {
        validateUserId(userId);
        validatePageable(pageable);
        Pageable sorted = ordered(pageable, "profileLikeId", "profileLikeId", true);
        Specification<ProfileLike> spec = MatchingCandidates.<ProfileLike>eligibleTarget(userId)
                .and((root, query, cb) -> cb.equal(root.get("user").get("userId"), userId));
        Page<Long> ids = read(() -> profileLikeRepository.findAll(spec, sorted).map(like -> like.getTargetUser().getUserId()));
        refreshService.refreshTargets(userId, ids.getContent());
        return read(() -> toIdPage(userId, ids));
    }

    private RoommatePageResponse<RoommateProfileResponse> toIdPage(Long userId, Page<Long> ids) {
        if (ids.isEmpty()) return RoommatePageResponse.from(ids.map(id -> (RoommateProfileResponse) null));
        var profiles = userProfileRepository.findAllByUser_UserIdIn(ids.getContent()).stream()
                .collect(Collectors.toMap(profile -> profile.getUser().getUserId(), profile -> profile));
        var surveys = profileSurveyRepository.findAllByUser_UserIdIn(ids.getContent()).stream()
                .collect(Collectors.toMap(survey -> survey.getUser().getUserId(), survey -> survey));
        var scores = userId == null ? Map.<Long, Integer>of()
                : roommateMatchRepository.findAllByRequesterUser_UserIdAndTargetUser_UserIdIn(userId, ids.getContent())
                .stream().collect(Collectors.toMap(match -> match.getTargetUser().getUserId(), RoommateMatch::getMatchScore));
        Set<Long> liked = userId == null ? Set.of()
                : profileLikeRepository.findAllByUser_UserIdAndTargetUser_UserIdIn(userId, ids.getContent())
                .stream().map(like -> like.getTargetUser().getUserId()).collect(Collectors.toSet());
        return RoommatePageResponse.from(ids.map(id ->
                RoommateProfileResponse.from(profiles.get(id), createTags(surveys.get(id)), scores.get(id), liked.contains(id))));
    }

    private List<String> createTags(ProfileSurvey survey) {
        return List.of(survey.getSmoking(), survey.getSleepTime(), survey.getCleaningFrequency());
    }

    private UserProfile findCandidate(Long userId, Long targetUserId) {
        return userProfileRepository.findAll(MatchingCandidates.eligible(userId)
                .and((root, query, cb) -> cb.equal(root.get("user").get("userId"), targetUserId)), PageRequest.of(0, 1))
                .stream().findFirst().orElseThrow(() -> new CustomException(ErrorCode.INVALID_REQUEST));
    }

    private <T> T read(java.util.function.Supplier<T> work) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setReadOnly(true);
        return transaction.execute(status -> work.get());
    }

    private Pageable ordered(Pageable pageable, String publicField, String entityField, boolean descending) {
        for (var order : pageable.getSort()) {
            if (!order.getProperty().equals(publicField)) throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        var order = pageable.getSort().getOrderFor(publicField);
        var direction = order == null ? (descending ? Sort.Direction.DESC : Sort.Direction.ASC) : order.getDirection();
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(direction, entityField));
    }

    private Pageable recommendedPageable(Pageable pageable) {
        for (var order : pageable.getSort()) {
            if (!order.getProperty().equals("matchScore") || !order.isDescending())
                throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        return PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), 9),
                Sort.by(Sort.Order.desc("matchScore"), Sort.Order.asc("targetUser.userId")));
    }

	private Specification<UserProfile> buildFilterSpecification(
			Long userId, RoommateFilterRequest filter) {
		Specification<UserProfile> specification = (root, query, criteriaBuilder) ->
				criteriaBuilder.conjunction();

        if (filter == null) return specification;
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
		specification = specification.and(hasSurveyValue("scentProductUse", filter.scentProductUse()));
		specification = specification.and(hasSurveyValue("desktopUse", filter.desktopUse()));
		specification = specification.and(hasSurveyValue("sharedItems", filter.sharedItems()));
		return specification;
	}

	private Specification<UserProfile> hasSurveyValue(String attribute, List<String> values) {
		if (values == null || values.isEmpty()) {
			return Specification.unrestricted();
		}
		return (root, query, criteriaBuilder) -> {
			Subquery<Long> subquery = query.subquery(Long.class);
			Root<ProfileSurvey> survey = subquery.from(ProfileSurvey.class);
			subquery.select(survey.get("profileSurveyId"));
			subquery.where(
					criteriaBuilder.equal(
							survey.get("user").get("userId"),
							root.get("user").get("userId")),
					criteriaBuilder.isTrue(survey.get("completed")),
					survey.get(attribute).in(values));
			return criteriaBuilder.exists(subquery);
		};
	}

	private Specification<UserProfile> hasSurveyOptions(
			ProfileSurveyFieldCode fieldCode, List<String> values) {
		if (values == null || values.isEmpty()) {
			return Specification.unrestricted();
		}
		return (root, query, criteriaBuilder) -> {
			Subquery<Long> subquery = query.subquery(Long.class);
			Root<ProfileSurveyOption> option = subquery.from(ProfileSurveyOption.class);
			subquery.select(option.get("profileSurveyOptionId"));
			subquery.where(
					criteriaBuilder.equal(
							option.get("profileSurvey").get("user").get("userId"),
							root.get("user").get("userId")),
					criteriaBuilder.isTrue(option.get("profileSurvey").get("completed")),
					criteriaBuilder.equal(option.get("fieldCode"), fieldCode),
					option.get("optionValue").in(values));
			return criteriaBuilder.exists(subquery);
		};
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
