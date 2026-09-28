package com.kkirikkiri.matching.service;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.kkirikkiri.survey.entity.*;
import com.kkirikkiri.user.entity.*;
import static com.kkirikkiri.survey.entity.PreferenceCriterionCode.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoommateMatchCalculatorTest {
    private final RoommateMatchCalculator calculator = new RoommateMatchCalculator();
    private final UserProfile requester = mock(UserProfile.class);
    private final UserProfile target = mock(UserProfile.class);
    private final ProfileSurvey survey = mock(ProfileSurvey.class);

    private RoommatePreference preference(String smoking) {
        return new RoommatePreference(mock(User.class), "상관없음", "상관없음", "상관없음", "상관없음",
                smoking, "상관없음", "상관없음", "상관없음", "상관없음", "상관없음", "상관없음",
                "상관없음", "상관없음", true);
    }

    private List<RoommatePreferenceOption> options(RoommatePreference preference, String bedtime) {
        return Arrays.stream(PreferenceCriterionCode.values()).filter(PreferenceCriterionCode::isMultiSelect)
                .map(code -> new RoommatePreferenceOption(preference, code, code == BEDTIME ? bedtime : "상관없음")).toList();
    }

    @Test
    void preservesWeightedScoreAndOmitsIndifferentItemsFromExplanation() {
        var preference = preference("비흡연만 가능");
        var criteria = Arrays.stream(PreferenceCriterionCode.values())
                .map(code -> new PreferenceCriterion(preference, code, code == SMOKING ? 3 : 2)).toList();
        when(survey.isCompleted()).thenReturn(true);
        when(survey.getSmoking()).thenReturn("비흡연");
        when(survey.getSleepTime()).thenReturn("01시 ~ 02시");
        var result = calculator.calculate(requester, target, survey, preference,
                options(preference, "23시 ~ 24시"), criteria).orElseThrow();
        assertEquals(60, result.score()); // existing weighted formula: round(100 * 3 / 5)
        assertEquals(List.of(SMOKING), result.matchedItems());
        assertEquals(List.of(BEDTIME), result.mismatchedItems());
    }

    @Test
    void allIndifferentOrIncompleteSurveyStillHasNoScore() {
        var preference = preference("상관없음");
        var criteria = Arrays.stream(PreferenceCriterionCode.values())
                .map(code -> new PreferenceCriterion(preference, code, 1)).toList();
        when(survey.isCompleted()).thenReturn(true);
        assertTrue(calculator.calculate(requester, target, survey, preference,
                options(preference, "상관없음"), criteria).isEmpty());
        when(survey.isCompleted()).thenReturn(false);
        assertTrue(calculator.calculate(requester, target, survey, preference,
                options(preference, "23시 ~ 24시"), criteria).isEmpty());
    }
}
