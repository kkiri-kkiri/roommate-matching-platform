package e2e;

import java.time.*;
import java.util.*;
import jakarta.persistence.EntityManager;
import com.kkirikkiri.user.entity.*;
import com.kkirikkiri.survey.entity.*;
import com.kkirikkiri.calendar.entity.*;
import com.kkirikkiri.matching.entity.ProfileLike;

/** Synthetic UI fixtures only; no stored match scores or external sources. */
final class McFixtures {
    static void seed(EntityManager em, User a, User b, Dormitory dorm, Faculty faculty) {
        E2eDatabase.check("1".equals(System.getenv("E2E_MC_P0")), "MC fixtures require the approved scope");
        survey(em, a, false); survey(em, b, false);
        preferences(em, a, false); preferences(em, b, true);
        var encoder = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
        for (int i = 1; i <= 12; i++) {
            var candidate = new User(encoder.encode(E2eDatabase.required("E2E_LOGIN_PASSWORD")),
                "mc" + i + E2eDatabase.database().substring(10) + "@hknu.ac.kr");
            candidate.verifyEmail(); em.persist(candidate);
            var profile = new UserProfile(candidate, "MC후보" + String.format("%02d", i), "남자", dorm,
                faculty, null, (short)2000, "20", "1학년", i % 2 == 0 ? "INTJ" : "INFP");
            profile.changeIntroduction("MC 합성 소개 " + i); em.persist(profile);
            survey(em, candidate, i > 10);
            if (i <= 7) em.persist(new ProfileLike(a, candidate));
            if (i == 12) em.persist(new ProfileLike(b, candidate));
        }
        var school = new CalendarCategory("SCHOOL", "학교", null, true, true);
        em.persist(school); em.persist(new CalendarCategory("DORMITORY", "기숙사", null, true, true));
        var ca = category(em, a, "CUSTOM-MC-A", "MC개인A", true);
        var cb = category(em, b, "CUSTOM-MC-B", "MC개인B", true);
        category(em, a, "CUSTOM-MC-HIDDEN", "MC숨김", false);
        category(em, a, "CUSTOM-MC-EMPTY", "MC빈분류", true);
        var today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        event(em, a, ca, "MC정상", today, "09:00", "10:00");
        event(em, a, ca, "MC종일", today, null, null);
        event(em, a, ca, "MC시작만", today, "10:00", null);
        event(em, a, ca, "MC종료만", today, null, "11:00");
        event(em, a, ca, "MC비정각", today, "09:07", "10:22");
        event(em, b, cb, "MC비공개B", today, "14:00", "15:00");
        em.persist(new CalendarEvent(null, school, "MC합성공개", today, today.plusDays(1),
            null, null, true, "실제 수집 없음", CalendarEventSourceType.CRAWLED, "E2E 합성", null, LocalDateTime.now()));
    }
    static CalendarCategory category(EntityManager em, User user, String code, String label, boolean enabled) {
        var c = new CalendarCategory(code, label, user, false, false); em.persist(c);
        em.persist(new CalendarCategorySetting(user, c, label, "#123456", enabled)); return c;
    }
    static void event(EntityManager em, User u, CalendarCategory c, String title, LocalDate date, String start, String end) {
        em.persist(new CalendarEvent(u, c, title, date, date,
            start == null ? null : LocalTime.parse(start), end == null ? null : LocalTime.parse(end),
            start == null && end == null, "MC 원본 설명", CalendarEventSourceType.USER, null, null, null));
    }
    static void survey(EntityManager em, User user, boolean second) {
        // Same valid values as the canonical SurveyTestData.profile fixture.
        var s = new ProfileSurvey(user, second ? "23시 ~ 24시" : "22시 이전", "07시 이전",
            "없음", "적음", "보통", "보통", "가능", "비흡연", "상관없음", "항상 밖에서",
            "항상 이어폰 사용해주세요", "저녁", "20분", "주 2회", "같이 청소", "같이 먹고 싶음",
            "적당히 대화", "보통", "사용 안 함", "사용하지 않아요", "일부만 공유", true);
        em.persist(s);
        for (String day : second ? List.of("토", "일") : List.of("월", "금"))
            em.persist(new ProfileSurveyOption(s, ProfileSurveyFieldCode.DORM_STAY_DAY, day));
        for (String meal : List.of("조식", "석식"))
            em.persist(new ProfileSurveyOption(s, ProfileSurveyFieldCode.MEAL_PLAN, meal));
    }
    static void preferences(EntityManager em, User user, boolean b) {
        var p = new RoommatePreference(user, "상관없음", "상관없음", "상관없음", "상관없음",
            "상관없음", "상관없음", "상관없음", "상관없음", "상관없음", "상관없음",
            "상관없음", "상관없음", "상관없음", true);
        em.persist(p);
        for (var code : PreferenceCriterionCode.values()) {
            em.persist(new PreferenceCriterion(p, code, code == (b ? PreferenceCriterionCode.BEDTIME : PreferenceCriterionCode.WAKE_TIME) ? 5 : 1));
            if (code.isMultiSelect()) em.persist(new RoommatePreferenceOption(p, code,
                code == PreferenceCriterionCode.BEDTIME ? "22시 이전" :
                code == PreferenceCriterionCode.WAKE_TIME ? (b ? "08시 ~ 09시" : "07시 이전") : "상관없음"));
        }
    }
}
