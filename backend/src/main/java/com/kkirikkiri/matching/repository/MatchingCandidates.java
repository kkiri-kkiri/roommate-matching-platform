package com.kkirikkiri.matching.repository;

import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;
import com.kkirikkiri.user.entity.*;
import com.kkirikkiri.survey.entity.*;

/** The same eligibility predicate is used before pagination and for detail/like validation. */
public final class MatchingCandidates {
    private MatchingCandidates() {}

    public static Specification<UserProfile> eligible(Long requesterId) {
        return (root, query, cb) -> eligible(root, query, cb, requesterId);
    }

    public static Predicate eligible(Root<UserProfile> profile, CommonAbstractCriteria query,
            CriteriaBuilder cb, Long requesterId) {
        Subquery<Long> surveyQuery = query.subquery(Long.class);
        Root<ProfileSurvey> survey = surveyQuery.from(ProfileSurvey.class);
        surveyQuery.select(survey.get("profileSurveyId")).where(
                cb.equal(survey.get("user"), profile.get("user")), cb.isTrue(survey.get("completed")));
        return cb.and(requesterId == null ? cb.conjunction()
                        : cb.notEqual(profile.get("user").get("userId"), requesterId),
                cb.equal(profile.get("user").get("status"), UserStatus.ACTIVE),
                cb.isNotNull(profile.get("faculty")), cb.isNotNull(profile.get("dormitory")),
                cb.isNotNull(profile.get("nickname")), cb.isNotNull(profile.get("birthYear")),
                cb.isNotNull(profile.get("grade")), cb.isNotNull(profile.get("admissionYear")),
                cb.isNotNull(profile.get("mbti")), cb.exists(surveyQuery));
    }

    public static <T> Specification<T> eligibleTarget(Long requesterId) {
        return (root, query, cb) -> {
            Subquery<Long> candidate = query.subquery(Long.class);
            Root<UserProfile> profile = candidate.from(UserProfile.class);
            candidate.select(profile.get("userProfileId")).where(
                    cb.equal(profile.get("user"), root.get("targetUser")),
                    eligible(profile, candidate, cb, requesterId));
            return cb.exists(candidate);
        };
    }
}
