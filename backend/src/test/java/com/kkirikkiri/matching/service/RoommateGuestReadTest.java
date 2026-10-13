package com.kkirikkiri.matching.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import com.kkirikkiri.matching.repository.*;
import com.kkirikkiri.survey.entity.ProfileSurvey;
import com.kkirikkiri.survey.entity.ProfileSurveyOption;
import com.kkirikkiri.survey.entity.ProfileSurveyFieldCode;
import com.kkirikkiri.survey.repository.ProfileSurveyRepository;
import com.kkirikkiri.survey.repository.ProfileSurveyOptionRepository;
import com.kkirikkiri.user.entity.UserProfile;
import com.kkirikkiri.user.repository.*;

@ExtendWith(MockitoExtension.class)
class RoommateGuestReadTest {
    @Mock RoommateMatchRepository matches;
    @Mock ProfileLikeRepository likes;
    @Mock PlatformTransactionManager transactions;
    @Mock UserProfileRepository profiles;
    @Mock ProfileSurveyRepository surveys;
    @Mock UserRepository users;
    @Mock MatchingRefreshService refresh;
    @Mock Validator validator;
    @Mock ProfileSurveyOptionRepository surveyOptions;
    RoommateMatchingService service;
    UserProfile profile;
    ProfileSurvey survey;

    @BeforeEach
    void setUp() {
        service = new RoommateMatchingService(matches, likes, transactions, profiles, surveys, users, refresh, validator, surveyOptions);
        when(transactions.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        profile = mock(UserProfile.class, RETURNS_DEEP_STUBS);
        when(profile.getUser().getUserId()).thenReturn(42L);
        when(profiles.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(profile)));
        survey = mock(ProfileSurvey.class);
        when(survey.getSmoking()).thenReturn("비흡연");
        when(survey.getSleepTime()).thenReturn("23시");
        when(survey.getCleaningFrequency()).thenReturn("주 1회");
    }

    @Test
    void guestListHasNoPersonalScoresOrLikesAndDoesNotRecalculateMatches() {
        when(profiles.findAllByUser_UserIdIn(List.of(42L))).thenReturn(List.of(profile));
        var targetUser = profile.getUser();
        when(survey.getUser()).thenReturn(targetUser);
        when(surveys.findAllByUser_UserIdIn(List.of(42L))).thenReturn(List.of(survey));
        var page = service.getRoommates(null, null, PageRequest.of(0, 9));
        assertEquals(42L, page.content().getFirst().id());
        assertNull(page.content().getFirst().matchScore());
        assertFalse(page.content().getFirst().liked());
        verifyNoInteractions(refresh, matches, likes);
    }

    @Test
    void guestDetailIncludesSurveyWithoutPersonalScoresOrLikes() {
        when(surveys.findByUser_UserId(42L)).thenReturn(Optional.of(survey));
        when(survey.getProfileSurveyId()).thenReturn(9L);
        when(surveyOptions.findAllByProfileSurvey_ProfileSurveyIdOrderByProfileSurveyOptionIdAsc(9L))
                .thenReturn(List.of(new ProfileSurveyOption(survey, ProfileSurveyFieldCode.MEAL_PLAN, "석식"),
                        new ProfileSurveyOption(survey, ProfileSurveyFieldCode.DORM_STAY_DAY, "금")));
        var detail = service.getRoommateDetail(null, 42L);
        assertEquals(42L, detail.id());
        assertEquals("23시", detail.answers().sleepTime());
        assertEquals(List.of("석식"), detail.answers().mealPlan());
        assertEquals(List.of("금"), detail.answers().dormStayDays());
        assertNull(detail.matchScore());
        assertFalse(detail.liked());
        verifyNoInteractions(refresh, matches, likes);
    }

    @Test
    void otherAccountReadsLatestAnswersAfterScoreRefresh() {
        when(surveys.findByUser_UserId(42L)).thenReturn(Optional.of(survey));
        when(survey.getSleepTime()).thenReturn("22시 이전");
        assertEquals("22시 이전", service.getRoommateDetail(8L, 42L).answers().sleepTime());
        when(survey.getSleepTime()).thenReturn("22시 ~ 23시");
        assertEquals("22시 ~ 23시", service.getRoommateDetail(8L, 42L).answers().sleepTime());
        verify(refresh, times(2)).refreshTargets(8L, List.of(42L));
    }
}
