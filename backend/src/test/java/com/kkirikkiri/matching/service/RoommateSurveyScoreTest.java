package com.kkirikkiri.matching.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.kkirikkiri.survey.entity.*;
import com.kkirikkiri.user.entity.UserProfile;

class RoommateSurveyScoreTest {
    @Test void earlyBedtimeIsDistinctFromLegacyTimeAndRecalculationUsesChangedAnswers() {
        var preference = mock(RoommatePreference.class, invocation -> {
            if (invocation.getMethod().getReturnType() == String.class) return "상관없음";
            return RETURNS_DEFAULTS.answer(invocation);
        });
        when(preference.isCompleted()).thenReturn(true);
        var survey = mock(ProfileSurvey.class);
        when(survey.isCompleted()).thenReturn(true);
        when(survey.getSleepTime()).thenReturn("22시 이전");
        when(survey.getWakeTime()).thenReturn("07시 이전");
        var options = Arrays.stream(PreferenceCriterionCode.values()).filter(PreferenceCriterionCode::isMultiSelect)
                .map(code -> new RoommatePreferenceOption(preference, code, switch (code) {
                    case BEDTIME -> "22시 이전";
                    case WAKE_TIME -> "07시 이전";
                    default -> "상관없음";
                })).toList();
        var criteria = Arrays.stream(PreferenceCriterionCode.values())
                .map(code -> new PreferenceCriterion(preference, code, code == PreferenceCriterionCode.BEDTIME ? 5 : 1)).toList();
        var calculator = new RoommateMatchCalculator();
        var a = mock(UserProfile.class);
        var b = mock(UserProfile.class);
        assertEquals(100, calculator.calculate(a, b, survey, preference, options, criteria).orElseThrow().score());
        when(survey.getSleepTime()).thenReturn("22시 ~ 23시");
        var changed = calculator.calculate(a, b, survey, preference, options, criteria).orElseThrow();
        assertEquals(17, changed.score());
        assertTrue(changed.mismatchedItems().contains(PreferenceCriterionCode.BEDTIME));
    }
}
