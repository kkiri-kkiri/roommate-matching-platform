package com.kkirikkiri.matching.service;

import java.util.*;
import org.springframework.stereotype.Component;
import com.kkirikkiri.survey.entity.*;
import com.kkirikkiri.user.entity.UserProfile;
import static com.kkirikkiri.survey.entity.PreferenceCriterionCode.*;

/** Pure directional calculation; unavailable inputs never become a synthetic score. */
@Component
public class RoommateMatchCalculator {
    public record Result(int score, boolean requiredConditionsMet) {}

    public Optional<Result> calculate(UserProfile requester, UserProfile target, ProfileSurvey survey,
            RoommatePreference preference, List<RoommatePreferenceOption> options, List<PreferenceCriterion> criteria) {
        if (requester == null || target == null || survey == null || !survey.isCompleted()
                || preference == null || !preference.isCompleted()) return Optional.empty();
        Map<PreferenceCriterionCode, List<String>> selections = new EnumMap<>(PreferenceCriterionCode.class);
        for (var option : options) selections.computeIfAbsent(option.getCriterionCode(), key -> new ArrayList<>())
                .add(option.getOptionValue());
        selections.put(DEPARTMENT, List.of(preference.getDepartmentPreference()));
        selections.put(SLEEP_TALKING, List.of(preference.getSleepTalkingAllowance()));
        selections.put(SNORING, List.of(preference.getSnoringAllowance()));
        selections.put(TEETH_GRINDING, List.of(preference.getTeethGrindingAllowance()));
        selections.put(SMOKING, List.of(preference.getSmokingPreference()));
        selections.put(LIGHTING, List.of(preference.getLightingPreference()));
        selections.put(SHOWER_TIME, List.of(preference.getShowerTimePreference()));
        selections.put(SHOWER_DURATION, List.of(preference.getShowerDurationPreference()));
        selections.put(CLEANING_STYLE, List.of(preference.getCleaningStylePreference()));
        selections.put(INTERACTION, List.of(preference.getInteractionPreference()));
        selections.put(MEAL, List.of(preference.getMealPreference()));
        selections.put(SHARED_ITEMS, List.of(preference.getSharedItemsPreference()));
        selections.put(TEMPERATURE, List.of(preference.getTemperaturePreference()));
        if (criteria.size() != PreferenceCriterionCode.values().length
                || criteria.stream().map(PreferenceCriterion::getCriterionCode).distinct().count() != criteria.size())
            return Optional.empty();
        int total = 0, matched = 0;
        boolean required = true;
        for (var criterion : criteria) {
            var values = selections.get(criterion.getCriterionCode());
            if (values == null || values.isEmpty()) return Optional.empty();
            if (values.contains("상관없음")) continue;
            boolean met = values.stream().anyMatch(value -> matches(criterion.getCriterionCode(), value, requester, target, survey));
            total += criterion.getWeight();
            if (met) matched += criterion.getWeight();
            if (criterion.isRequired() && !met) required = false;
        }
        return total == 0 ? Optional.empty() : Optional.of(new Result((int) Math.round(100.0 * matched / total), required));
    }

    private boolean matches(PreferenceCriterionCode code, String value, UserProfile a, UserProfile b, ProfileSurvey s) {
        return switch (code) {
            case DEPARTMENT -> switch (value) {
                case "같은 전공" -> a.getMajor() != null && b.getMajor() != null
                        && Objects.equals(a.getMajor().getMajorId(), b.getMajor().getMajorId());
                case "같은 학부의 다른 전공" -> a.getMajor() != null && b.getMajor() != null
                        && Objects.equals(a.getFaculty().getFacultyId(), b.getFaculty().getFacultyId())
                        && !Objects.equals(a.getMajor().getMajorId(), b.getMajor().getMajorId());
                case "다른 학부" -> !Objects.equals(a.getFaculty().getFacultyId(), b.getFaculty().getFacultyId());
                default -> false;
            };
            case AGE -> value.equals(b.getBirthYear() > a.getBirthYear() ? "연하"
                    : b.getBirthYear() < a.getBirthYear() ? "연상" : "동갑");
            case GRADE -> value.equals(b.getGrade());
            case BEDTIME -> value.equals(s.getSleepTime());
            case WAKE_TIME -> value.equals(s.getWakeTime());
            case SLEEP_TALKING -> withinHabit(value, s.getSleepTalking());
            case SNORING -> withinHabit(value, s.getSnoring());
            case TEETH_GRINDING -> withinHabit(value, s.getTeethGrinding());
            case SMOKING -> "비흡연".equals(s.getSmoking())
                    || "전자담배까지 허용".equals(value) && "전자담배".equals(s.getSmoking());
            case LIGHTING -> value.equals(s.getLightingPreference());
            case SHOWER_TIME -> value.equals(s.getShowerTime());
            case SHOWER_DURATION -> withinDuration(value, s.getShowerDuration());
            case CLEANING_FREQUENCY -> value.equals(s.getCleaningFrequency());
            case CLEANING_STYLE -> value.equals(switch (s.getCleaningStyle()) {
                case "같이 청소" -> "같이 청소하는 사람";
                case "각자 영역만 청소" -> "각자 영역만 청소하는 사람";
                case "상황에 따라 협의" -> "상황에 따라 협의하는 사람";
                default -> "";
            });
            case INTERACTION -> value.equals(switch (s.getRoommateInteraction()) {
                case "친하게 지내고 싶음" -> "친하게 지내고 싶은 사람";
                case "적당히 대화" -> "적당히 대화하고 싶은 사람";
                case "필요한 대화만" -> "필요한 대화만 하고 싶은 사람";
                default -> "";
            });
            case MEAL -> value.equals(s.getMeal());
            case SHARED_ITEMS -> value.equals(switch (s.getSharedItems()) {
                case "적극적으로 공유" -> "적극적으로 공유하는 사람";
                case "일부만 공유" -> "일부만 공유하는 사람";
                case "각자 사용" -> "각자 사용하는 사람";
                default -> "";
            });
            case TEMPERATURE -> value.equals(switch (s.getTemperaturePreference()) {
                case "추위 타요" -> "추위를 타는 사람";
                case "더위 타요" -> "더위를 타는 사람";
                case "보통" -> "보통인 사람";
                default -> "";
            });
        };
    }

    private boolean withinHabit(String allowance, String actual) {
        int maximum = List.of("없음만 가능", "적음까지 괜찮음", "보통까지 괜찮음").indexOf(allowance);
        int level = List.of("없음", "적음", "보통", "심함").indexOf(actual);
        return maximum >= 0 && level >= 0 && level <= maximum;
    }

    private boolean withinDuration(String allowance, String actual) {
        int maximum = List.of("10분 이하", "20분 이하", "30분 이하", "40분 이하").indexOf(allowance);
        int duration = List.of("10분", "20분", "30분", "40분", "그 이상").indexOf(actual);
        return maximum >= 0 && duration >= 0 && duration <= maximum;
    }
}
