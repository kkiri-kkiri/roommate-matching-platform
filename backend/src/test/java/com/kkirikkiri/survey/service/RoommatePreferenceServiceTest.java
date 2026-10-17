package com.kkirikkiri.survey.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static com.kkirikkiri.survey.SurveyTestData.*;

import java.util.*;
import java.util.stream.Stream;
import jakarta.validation.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import com.kkirikkiri.global.exception.*;
import com.kkirikkiri.matching.service.MatchingInputsChanged;
import com.kkirikkiri.survey.dto.*;
import com.kkirikkiri.survey.entity.*;
import com.kkirikkiri.survey.repository.*;
import com.kkirikkiri.user.entity.*;
import com.kkirikkiri.user.repository.UserRepository;

class RoommatePreferenceServiceTest {
    ValidatorFactory validation;
    UserRepository users;
    User user;
    RoommatePreferenceRepository preferences;
    RoommatePreferenceOptionRepository options;
    PreferenceCriterionRepository criteria;
    ApplicationEventPublisher events;
    RoommatePreferenceService service;
    RoommatePreference stored;
    List<RoommatePreferenceOption> storedOptions;
    List<PreferenceCriterion> storedCriteria;

    @BeforeEach void setup() {
        validation = Validation.buildDefaultValidatorFactory();
        users = mock(UserRepository.class); user = mock(User.class);
        when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
        when(users.findByIdForSurveyUpdate(1L)).thenReturn(Optional.of(user));
        preferences = mock(RoommatePreferenceRepository.class);
        options = mock(RoommatePreferenceOptionRepository.class);
        criteria = mock(PreferenceCriterionRepository.class);
        events = mock(ApplicationEventPublisher.class);
        service = new RoommatePreferenceService(users, preferences, options, criteria, validation.getValidator(), events);
        storedOptions = new ArrayList<>(); storedCriteria = new ArrayList<>();
        when(preferences.findByUser_UserId(1L)).thenAnswer(c -> Optional.ofNullable(stored));
        when(preferences.save(any())).thenAnswer(c -> {
            stored = c.getArgument(0); ReflectionTestUtils.setField(stored, "preferenceId", 10L); return stored;
        });
        when(options.findAllByRoommatePreference_PreferenceIdOrderByPreferenceOptionIdAsc(10L))
                .thenAnswer(c -> new ArrayList<>(storedOptions));
        when(criteria.findAllByRoommatePreference_PreferenceId(10L)).thenAnswer(c -> new ArrayList<>(storedCriteria));
        doAnswer(c -> { storedOptions.clear(); return null; }).when(options).deleteAll(any());
        doAnswer(c -> { storedCriteria.clear(); return null; }).when(criteria).deleteAll(any());
        when(options.saveAll(anyList())).thenAnswer(c -> { storedOptions.addAll(c.getArgument(0)); return storedOptions; });
        when(criteria.saveAll(anyList())).thenAnswer(c -> { storedCriteria.addAll(c.getArgument(0)); return storedCriteria; });
    }
    @AfterEach void close() { validation.close(); }

    @Test void absentPreferenceIsIncompleteAndReadDoesNotWrite() {
        assertEquals(new RoommatePreferenceResponse(false, null), service.getMyRoommatePreference(1L));
        verify(preferences, never()).save(any()); verifyNoInteractions(events);
    }
    @Test void activeFirstSavePersistsAllOptionsAndEighteenWeightsAndPublishesEvent() {
        service.saveOrUpdateMyRoommatePreference(1L, preference(false));
        var saved = service.getMyRoommatePreference(1L);
        assertTrue(saved.completed()); assertEquals(preference(false).weights(), saved.answers().weights());
        assertEquals(7, storedOptions.size()); assertEquals(18, storedCriteria.size());
        assertEquals(preference(false).department(), saved.answers().department());
        assertEquals(preference(false).age(), saved.answers().age());
        var order = inOrder(users, user, preferences, options, criteria, events);
        order.verify(users).findByIdForSurveyUpdate(1L); order.verify(user).getStatus();
        order.verify(preferences).findByUser_UserId(1L); order.verify(preferences).save(stored);
        order.verify(options).deleteAll(any()); order.verify(options).flush(); order.verify(options).saveAll(anyList());
        order.verify(criteria).deleteAll(any()); order.verify(criteria).flush(); order.verify(criteria).saveAll(anyList());
        order.verify(events).publishEvent(new MatchingInputsChanged(1L, false, true));
    }
    @Test void repeatedSaveReusesParentAndCompletelyReplacesChildren() {
        service.saveOrUpdateMyRoommatePreference(1L, preference(false));
        var original = stored; var oldOptions = List.copyOf(storedOptions); var oldCriteria = List.copyOf(storedCriteria);
        service.saveOrUpdateMyRoommatePreference(1L, preference(true));
        assertSame(original, stored); assertEquals(6, storedOptions.size()); assertEquals(18, storedCriteria.size());
        assertTrue(Collections.disjoint(oldOptions, storedOptions)); assertTrue(Collections.disjoint(oldCriteria, storedCriteria));
        var result = service.getMyRoommatePreference(1L).answers();
        assertEquals(preference(true).department(), result.department()); assertEquals(preference(true).age(), result.age());
        assertEquals(preference(true).weights(), result.weights());
        verify(events, times(2)).publishEvent(new MatchingInputsChanged(1L, false, true));
    }
    @ParameterizedTest @EnumSource(value = UserStatus.class, names = {"SUSPENDED", "WITHDRAWN"})
    void inactiveAccountIsRejectedBeforeSurveyAccess(UserStatus status) {
        when(user.getStatus()).thenReturn(status);
        assertEquals(ErrorCode.UNAUTHORIZED, assertThrows(CustomException.class,
                () -> service.saveOrUpdateMyRoommatePreference(1L, preference(false))).getErrorCode());
        var order = inOrder(users, user);
        order.verify(users).findByIdForSurveyUpdate(1L); order.verify(user).getStatus();
        verifyNoInteractions(preferences, options, criteria, events);
    }
    static Stream<Arguments> invalidRequests() {
        var wrongKeys = weights(3); wrongKeys.remove("age"); wrongKeys.put("unknown", 3);
        var missing = weights(3); missing.remove("age");
        var nullWeight = weights(3); nullWeight.put("age", null);
        return Stream.of(
                Arguments.of("department", "지원하지 않는 값"), Arguments.of("age", List.of("invalid")),
                Arguments.of("age", List.of("동갑", "동갑")), Arguments.of("age", List.of("상관없음", "동갑")),
                Arguments.of("grade", List.of("상관없음", "1학년")),
                Arguments.of("bedtime", List.of("상관없음", "22시 이전")),
                Arguments.of("wakeTime", List.of("상관없음", "07시 이전")),
                Arguments.of("cleaningFrequency", List.of("상관없음", "매일")),
                Arguments.of("weights", null), Arguments.of("weights", Map.of()), Arguments.of("weights", missing),
                Arguments.of("weights", wrongKeys), Arguments.of("weights", weights(0)),
                Arguments.of("weights", weights(6)), Arguments.of("weights", nullWeight));
    }
    @ParameterizedTest @MethodSource("invalidRequests")
    void invalidInputPreservesExistingSurveyAndDoesNotPublish(String key, Object value) {
        service.saveOrUpdateMyRoommatePreference(1L, preference(false));
        var before = service.getMyRoommatePreference(1L);
        clearInvocations(users, user, preferences, options, criteria, events);
        var request = change(preference(true), key, value, RoommatePreferenceRequest.class);
        assertEquals(ErrorCode.VALIDATION_ERROR, assertThrows(CustomException.class,
                () -> service.saveOrUpdateMyRoommatePreference(1L, request)).getErrorCode());
        verifyNoInteractions(users, user, preferences, options, criteria, events);
        assertEquals(before, service.getMyRoommatePreference(1L));
    }
}
