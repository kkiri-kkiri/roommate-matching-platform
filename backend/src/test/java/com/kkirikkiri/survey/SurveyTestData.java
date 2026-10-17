package com.kkirikkiri.survey;

import java.util.*;
import tools.jackson.databind.json.JsonMapper;
import com.kkirikkiri.survey.dto.*;

/** Canonical public API fixtures; also compiled by the opt-in MySQL verifier. */
public final class SurveyTestData {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private SurveyTestData() {}

    public static ProfileSurveyRequest profile(boolean second) {
        return new ProfileSurveyRequest(second ? "23시 ~ 24시" : "22시 이전", "07시 이전",
                "없음", "적음", "보통", "보통", "가능", "비흡연", "상관없음", "항상 밖에서",
                "항상 이어폰 사용해주세요", "저녁", "20분", "주 2회", "같이 청소",
                second ? List.of("토", "일") : List.of("월", "금"),
                second ? List.of("신청 안 함") : List.of("조식", "석식"),
                second ? "각자 먹고 싶음" : "같이 먹고 싶음", "적당히 대화", "보통",
                "사용 안 함", "사용하지 않아요", "일부만 공유", second ? "수정 소개" : "처음 소개");
    }

    public static Map<String, Integer> weights(int value) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (String key : List.of("department", "age", "grade", "bedtime", "wakeTime", "sleepTalking",
                "snoring", "teethGrinding", "smoking", "lightingPreference", "showerTime", "showerDuration",
                "cleaningFrequency", "cleaningStyle", "interaction", "meal", "sharedItems", "temperature")) {
            result.put(key, value);
        }
        return result;
    }

    public static RoommatePreferenceRequest preference(boolean second) {
        return new RoommatePreferenceRequest(second ? "다른 학부" : "같은 전공",
                second ? List.of("동갑", "연상") : List.of("연하"),
                second ? List.of("3학년") : List.of("1학년", "2학년"),
                second ? List.of("23시 ~ 24시") : List.of("22시 이전"),
                second ? List.of("08시 ~ 09시") : List.of("07시 이전"),
                "없음만 가능", "적음까지 괜찮음", "보통까지 괜찮음", "비흡연자만", "상관없음",
                "저녁", "20분 이하", second ? List.of("매일") : List.of("주 1회", "주 2회"),
                "같이 청소하는 사람", "적당히 대화하고 싶은 사람", "같이 먹고 싶음",
                "일부만 공유하는 사람", "보통인 사람", weights(second ? 5 : 2));
    }

    public static <T> T change(T original, String key, Object value, Class<T> type) {
        Map<String, Object> fields = new LinkedHashMap<>(JSON.convertValue(original, Map.class));
        fields.put(key, value);
        return JSON.convertValue(fields, type);
    }
}
