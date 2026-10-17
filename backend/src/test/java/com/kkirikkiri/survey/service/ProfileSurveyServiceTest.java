package com.kkirikkiri.survey.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.*;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import com.kkirikkiri.global.exception.CustomException;
import com.kkirikkiri.matching.service.MatchingInputsChanged;
import com.kkirikkiri.survey.dto.ProfileSurveyRequest;
import com.kkirikkiri.survey.entity.*;
import com.kkirikkiri.survey.repository.*;
import com.kkirikkiri.user.entity.User;
import com.kkirikkiri.user.repository.UserRepository;
import com.kkirikkiri.user.repository.UserProfileRepository;
import com.kkirikkiri.user.entity.UserProfile;

class ProfileSurveyServiceTest {
    ValidatorFactory validation;
    UserRepository users;
    User user;
    UserProfileRepository profiles;
    UserProfile profile;
    ProfileSurveyRepository surveys;
    ProfileSurveyOptionRepository options;
    ApplicationEventPublisher events;
    ProfileSurveyService service;
    ProfileSurvey stored;
    List<ProfileSurveyOption> storedOptions;

    public static ProfileSurveyRequest answers(String sleep, String meal) {
        return answers(sleep, meal, null);
    }

    private static ProfileSurveyRequest answers(String sleep, String meal, String introduction) {
        return new ProfileSurveyRequest(sleep, "07시 이전", "없음", "적음", "보통", "보통",
                "가능", "비흡연", "상관없음", "항상 밖에서", "항상 이어폰 사용해주세요",
                "저녁", "20분", "주 2회", "같이 청소", List.of("월", "금"), List.of("조식", "석식"),
                meal, "적당히 대화", "보통", "사용 안 함", "사용하지 않아요", "일부만 공유", introduction);
    }

    @BeforeEach void setup() {
        validation = Validation.buildDefaultValidatorFactory();
        users = mock(UserRepository.class);
        profiles = mock(UserProfileRepository.class);
        profile = mock(UserProfile.class, CALLS_REAL_METHODS);
        when(profiles.findByUser_UserId(1L)).thenReturn(Optional.of(profile));
        surveys = mock(ProfileSurveyRepository.class);
        options = mock(ProfileSurveyOptionRepository.class);
        events = mock(ApplicationEventPublisher.class);
        storedOptions = new ArrayList<>();
        service = new ProfileSurveyService(users, profiles, surveys, options, validation.getValidator(), events);
        user = mock(User.class);
        when(user.getStatus()).thenReturn(com.kkirikkiri.user.entity.UserStatus.ACTIVE);
        when(users.findByIdForSurveyUpdate(1L)).thenReturn(Optional.of(user));
        when(surveys.findByUser_UserId(1L)).thenAnswer(invocation -> Optional.ofNullable(stored));
        when(surveys.save(any())).thenAnswer(invocation -> {
            stored = invocation.getArgument(0);
            ReflectionTestUtils.setField(stored, "profileSurveyId", 10L);
            return stored;
        });
        when(options.findAllByProfileSurvey_ProfileSurveyIdOrderByProfileSurveyOptionIdAsc(10L))
                .thenAnswer(invocation -> new ArrayList<>(storedOptions));
        doAnswer(invocation -> { storedOptions.clear(); return null; }).when(options).deleteAll(any());
        when(options.saveAll(anyList())).thenAnswer(invocation -> {
            storedOptions.addAll(invocation.getArgument(0));
            return storedOptions;
        });
    }

    @AfterEach void close() { validation.close(); }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = com.kkirikkiri.user.entity.UserStatus.class,
            names = {"SUSPENDED", "WITHDRAWN"})
    void inactiveUserIsRejectedBeforeAnySurveyOrIntroductionAccess(com.kkirikkiri.user.entity.UserStatus status) {
        when(user.getStatus()).thenReturn(status);
        var failure = assertThrows(CustomException.class, () -> service.saveOrUpdateMyProfileSurvey(
                1L, answers("22시 이전", "같이 먹고 싶음", "변경 금지")));
        assertEquals(com.kkirikkiri.global.exception.ErrorCode.UNAUTHORIZED, failure.getErrorCode());
        var order = inOrder(users, user);
        order.verify(users).findByIdForSurveyUpdate(1L);
        order.verify(user).getStatus();
        verifyNoInteractions(surveys, options, profiles, profile, events);
    }

    @Test void checksActiveStatusBeforeSurveyAndIntroductionChanges() {
        service.saveOrUpdateMyProfileSurvey(1L, answers("22시 이전", "같이 먹고 싶음", "소개"));
        var order = inOrder(users, user, surveys, profiles, profile);
        order.verify(users).findByIdForSurveyUpdate(1L);
        order.verify(user).getStatus();
        order.verify(surveys).findByUser_UserId(1L);
        order.verify(profiles).findByUser_UserId(1L);
        order.verify(profile).changeIntroduction("소개");
        order.verify(surveys).save(any());
    }

    @Test void newUserIsIncompleteAndDoesNotWrite() {
        var result = service.getMyProfileSurvey(1L);
        assertFalse(result.completed());
        assertNull(result.answers());
        verify(surveys, never()).save(any());
    }

    @Test void savesCanonicalAnswersAndUpdatesSameSurveyWithoutDuplicatingOptions() {
        var result = service.saveOrUpdateMyProfileSurvey(1L, answers("22시 이전", "같이 먹고 싶음"));
        assertTrue(result.completed());
        assertEquals("22시 이전", result.answers().sleepTime());
        assertEquals("07시 이전", result.answers().wakeTime());
        assertEquals("상관없음", result.answers().lightingPreference());
        assertEquals(List.of("조식", "석식"), result.answers().mealPlan());
        assertEquals(List.of("월", "금"), result.answers().dormStayDays());
        var original = stored;
        service.saveOrUpdateMyProfileSurvey(1L, answers("22시 ~ 23시", "각자 먹고 싶음"));
        assertSame(original, stored);
        assertEquals(4, storedOptions.size());
        assertEquals("22시 ~ 23시", service.getMyProfileSurvey(1L).answers().sleepTime());
        assertEquals("각자 먹고 싶음", service.getMyProfileSurvey(1L).answers().meal());
        verify(events, times(2)).publishEvent(new MatchingInputsChanged(1L, true, false));
        var order = inOrder(users, surveys);
        order.verify(users).findByIdForSurveyUpdate(1L);
        order.verify(surveys).findByUser_UserId(1L);
        order.verify(surveys).save(original);
    }

    @Test void missingMealOrInvalidChoiceCannotOverwriteSavedAnswers() {
        service.saveOrUpdateMyProfileSurvey(1L, answers("22시 이전", "같이 먹고 싶음"));
        assertThrows(CustomException.class, () -> service.saveOrUpdateMyProfileSurvey(1L, answers("22시 이전", "")));
        assertThrows(CustomException.class, () -> service.saveOrUpdateMyProfileSurvey(1L, answers("invalid", "같이 먹고 싶음")));
        assertEquals("같이 먹고 싶음", service.getMyProfileSurvey(1L).answers().meal());
        verify(events, times(1)).publishEvent(any(MatchingInputsChanged.class));
    }

    @Test void storedCompletionFlagDoesNotHideMissingRequiredAnswers() {
        service.saveOrUpdateMyProfileSurvey(1L, answers("22시 이전", "같이 먹고 싶음"));
        storedOptions.clear();
        assertFalse(service.getMyProfileSurvey(1L).completed());
        assertNotNull(service.getMyProfileSurvey(1L).answers());
        assertTrue(stored.isCompleted()); // A read must not mutate existing data.
    }

    @Test void introductionUpdatesAndClearsWithoutChangingAnswersOrMatchingInputs() {
        var first = service.saveOrUpdateMyProfileSurvey(1L, answers("22시 이전", "같이 먹고 싶음", "안녕하세요\n배려하며 살아요"));
        assertEquals("안녕하세요\n배려하며 살아요", profile.getIntroduction());
        var original = stored;
        var updated = service.saveOrUpdateMyProfileSurvey(1L, answers("22시 이전", "같이 먹고 싶음", "새로운 한마디"));
        assertSame(original, stored);
        assertEquals(first.answers(), updated.answers());
        assertEquals("새로운 한마디", service.getMyProfileSurvey(1L).introduction());
        for (String blank : List.of("", " \n\t", "\u00a0\u3000\ufeff")) {
            var cleared = service.saveOrUpdateMyProfileSurvey(1L, answers("22시 이전", "같이 먹고 싶음", blank));
            assertEquals("", cleared.introduction());
            assertEquals("", profile.getIntroduction());
            assertTrue(cleared.completed());
        }
        verify(events, times(1)).publishEvent(any(MatchingInputsChanged.class));
    }

    @Test void validatesUtf16LimitBeforeChangingStoredData() {
        for (String text : List.of("가".repeat(200), "😀".repeat(100))) {
            assertEquals(text, service.saveOrUpdateMyProfileSurvey(1L,
                    answers("22시 이전", "같이 먹고 싶음", text)).introduction());
        }
        for (String text : List.of("가".repeat(201), "😀".repeat(101))) {
            assertThrows(CustomException.class, () -> service.saveOrUpdateMyProfileSurvey(1L,
                    answers("22시 이전", "같이 먹고 싶음", text)));
        }
        assertEquals("😀".repeat(100), profile.getIntroduction());
    }

    @Test void nullFromOlderClientsPreservesLegacyIntroductionAndCompletion() {
        profile.changeIntroduction("기".repeat(255));
        assertEquals("기".repeat(255), service.getMyProfileSurvey(1L).introduction());
        var result = service.saveOrUpdateMyProfileSurvey(1L, answers("22시 이전", "같이 먹고 싶음"));
        assertEquals("기".repeat(255), result.introduction());
        assertTrue(result.completed());
        verify(profiles, never()).save(any()); // Existing managed profile is updated, never inserted.
    }
}
