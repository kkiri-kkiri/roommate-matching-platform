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

class ProfileSurveyServiceTest {
    ValidatorFactory validation;
    UserRepository users;
    ProfileSurveyRepository surveys;
    ProfileSurveyOptionRepository options;
    ApplicationEventPublisher events;
    ProfileSurveyService service;
    ProfileSurvey stored;
    List<ProfileSurveyOption> storedOptions;

    public static ProfileSurveyRequest answers(String sleep, String meal) {
        return new ProfileSurveyRequest(sleep, "07시 이전", "없음", "적음", "보통", "보통",
                "가능", "비흡연", "상관없음", "항상 밖에서", "항상 이어폰 사용해주세요",
                "저녁", "20분", "주 2회", "같이 청소", List.of("월", "금"), List.of("조식", "석식"),
                meal, "적당히 대화", "보통", "사용 안 함", "사용하지 않아요", "일부만 공유");
    }

    @BeforeEach void setup() {
        validation = Validation.buildDefaultValidatorFactory();
        users = mock(UserRepository.class);
        surveys = mock(ProfileSurveyRepository.class);
        options = mock(ProfileSurveyOptionRepository.class);
        events = mock(ApplicationEventPublisher.class);
        storedOptions = new ArrayList<>();
        service = new ProfileSurveyService(users, surveys, options, validation.getValidator(), events);
        when(users.findByIdForSurveyUpdate(1L)).thenReturn(Optional.of(mock(User.class)));
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
}
