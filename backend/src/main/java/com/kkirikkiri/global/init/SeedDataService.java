package com.kkirikkiri.global.init;

import com.kkirikkiri.user.entity.Dormitory;
import com.kkirikkiri.user.entity.Faculty;
import com.kkirikkiri.user.entity.Major;
import com.kkirikkiri.user.entity.User;
import com.kkirikkiri.user.entity.UserProfile;
import com.kkirikkiri.user.entity.UserRole;
import com.kkirikkiri.user.repository.DormitoryRepository;
import com.kkirikkiri.user.repository.FacultyRepository;
import com.kkirikkiri.user.repository.MajorRepository;
import com.kkirikkiri.user.repository.UserProfileRepository;
import com.kkirikkiri.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class SeedDataService {

    private static final String SEED_PREFIX = "seed_user";
    private static final int SEED_USER_COUNT = 30;

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final DormitoryRepository dormitoryRepository;
    private final FacultyRepository facultyRepository;
    private final MajorRepository majorRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    private static final String[] MBTI_LIST = {
            "ISTJ", "ISFJ", "INFJ", "INTJ",
            "ISTP", "ISFP", "INFP", "INTP",
            "ESTP", "ESFP", "ENFP", "ENTP",
            "ESTJ", "ESFJ", "ENFJ", "ENTJ"
    };

    private static final String[] SLEEP_TIMES = {
            "22시 ~ 23시",
            "23시 ~ 24시",
            "24시 ~ 01시",
            "01시 ~ 02시",
            "02시 ~ 03시",
            "03시 이후"
    };

    private static final String[] WAKE_TIMES = {
            "07시 이전",
            "07시 ~ 08시",
            "08시 ~ 09시",
            "09시 ~ 10시",
            "10시 이후",
            "수업 시간에 따라 유동적"
    };

    private static final String[] SLEEP_LEVELS = {
            "없음",
            "적음",
            "보통",
            "심함"
    };

    private static final String[] SLEEP_SENSITIVITY = {
            "밝음 (소리에 잘 깨요)",
            "보통",
            "어두움 (잘 깨지 않아요)"
    };

    private static final String[] INDOOR_EATING = {
            "가능",
            "불가능",
            "냄새 안나는 음식 가능"
    };

    private static final String[] SMOKING = {
            "비흡연",
            "비흡연",
            "비흡연",
            "비흡연",
            "전자담배",
            "일반담배"
    };

    private static final String[] LIGHTING = {
            "밝음 선호",
            "어두움 선호",
            "상관 없음"
    };

    private static final String[] PHONE_CALL = {
            "항상 밖에서",
            "방에서는 짧게만",
            "상관없음"
    };

    private static final String[] MEDIA_SOUND = {
            "항상 이어폰 사용해주세요",
            "공부·과제할 때 아니면 상관없음",
            "소리만 작게 해주세요",
            "자유롭게 하세요"
    };

    private static final String[] SHOWER_TIME = {
            "아침",
            "저녁",
            "유동적"
    };

    private static final String[] SHOWER_DURATION = {
            "10분",
            "20분",
            "30분",
            "40분",
            "그 이상"
    };

    private static final String[] CLEANING_FREQUENCY = {
            "안함",
            "주 1회",
            "주 2회",
            "주 3회",
            "주 4회",
            "주 5회",
            "매일",
            "유동적"
    };

    private static final String[] CLEANING_STYLE = {
            "같이 청소",
            "각자 영역만 청소",
            "상황에 따라 협의"
    };

    private static final String[] ROOMMATE_INTERACTION = {
            "친하게 지내고 싶음",
            "적당히 대화",
            "필요한 대화만"
    };

    private static final String[] TEMPERATURE = {
            "추위 타요",
            "더위 타요",
            "보통"
    };

    private static final String[] BUG_HANDLING = {
            "잘 잡음",
            "못 잡음",
            "작은 벌레는 가능"
    };

    private static final String[] SCENT_PRODUCT = {
            "자주 사용",
            "가끔 사용",
            "사용 안 함"
    };

    private static final String[] DESKTOP_USE = {
            "사용해요",
            "사용하지 않아요"
    };

    private static final String[] SHARED_ITEMS = {
            "적극적으로 공유",
            "일부만 공유",
            "각자 사용"
    };

    private static final String[] MEAL = {
            "같이 먹고 싶음",
            "각자 먹고 싶음",
            "시간 될 때만 같이 먹고 싶음",
            "상관없음"
    };

    private static final String[] HAIR_DRYER = {
            "사용",
            "미사용"
    };

    @Transactional
    public void createSeedData() {

        Dormitory dormitory = dormitoryRepository
                .findByDormitoryName("나래")
                .orElseThrow(() ->
                        new IllegalStateException("나래 생활관 데이터가 없습니다.")
                );

        Faculty faculty = facultyRepository
                .findByFacultyName("컴퓨터응용수학부")
                .orElseThrow(() ->
                        new IllegalStateException("컴퓨터응용수학부 데이터가 없습니다.")
                );

        Major major = majorRepository
                .findByFaculty_FacultyIdAndMajorName(
                        faculty.getFacultyId(),
                        "소프트웨어융합전공"
                )
                .orElseThrow(() ->
                        new IllegalStateException("소프트웨어융합전공 데이터가 없습니다.")
                );

        int userCreatedCount = 0;
        int surveyCreatedCount = 0;

        for (int i = 1; i <= SEED_USER_COUNT; i++) {

            String number = String.format("%02d", i);
            String loginId = SEED_PREFIX + number;

            User user = userRepository
                    .findByLoginId(loginId)
                    .orElse(null);

            /*
             * 회원이 없으면 생성
             */
            if (user == null) {

                String randomPassword =
                        UUID.randomUUID() + "!Aa1";

                user = new User(
                        loginId,
                        passwordEncoder.encode(randomPassword),
                        loginId + "@hknu.ac.kr",
                        UserRole.USER
                );

                user.verifyEmail();
                userRepository.save(user);

                userCreatedCount++;
            }

            /*
             * 기본 프로필이 없으면 생성
             */
            createProfileIfNotExists(
                    user,
                    i,
                    number,
                    dormitory,
                    faculty,
                    major
            );

            /*
             * 내 프로필 설문이 없으면 생성
             */
            boolean surveyCreated =
                    createProfileSurveyIfNotExists(user, i);

            if (surveyCreated) {
                surveyCreatedCount++;
            }
        }

        log.info(
                "Seed 생성 완료 - 신규 회원: {}, 신규 설문: {}",
                userCreatedCount,
                surveyCreatedCount
        );
    }

    private void createProfileIfNotExists(
            User user,
            int index,
            String number,
            Dormitory dormitory,
            Faculty faculty,
            Major major
    ) {

        if (userProfileRepository
                .findByUser_UserId(user.getUserId())
                .isPresent()) {
            return;
        }

        short birthYear =
                (short) (2000 + ((index - 1) % 5));

        String admissionYear =
                String.format(
                        "%02d",
                        22 + ((index - 1) % 4)
                );

        String grade =
                (((index - 1) % 4) + 1) + "학년";

        String mbti =
                MBTI_LIST[(index - 1) % MBTI_LIST.length];

        UserProfile profile = new UserProfile(
                user,
                "테스트룸메" + number,
                "남자",
                dormitory,
                faculty,
                major,
                birthYear,
                admissionYear,
                grade,
                mbti
        );

        userProfileRepository.save(profile);
    }

    private boolean createProfileSurveyIfNotExists(
            User user,
            int index
    ) {

        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM profile_survey
                WHERE user_id = ?
                """,
                Integer.class,
                user.getUserId()
        );

        if (count != null && count > 0) {
            return false;
        }

        Map<String, Object> values =
                createSurveyValues(user, index);

        /*
         * 실제 DB에 존재하는 컬럼만 INSERT에 포함
         */
        Set<String> existingColumns =
                getTableColumns("profile_survey");

        LinkedHashMap<String, Object> insertValues =
                new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : values.entrySet()) {

            if (existingColumns.contains(
                    entry.getKey().toLowerCase()
            )) {
                insertValues.put(
                        entry.getKey(),
                        entry.getValue()
                );
            }
        }

        if (!insertValues.containsKey("user_id")) {
            throw new IllegalStateException(
                    "profile_survey 테이블에 user_id 컬럼이 없습니다."
            );
        }

        /*
         * 실제 DB에서 completed가 필수 컬럼이므로
         * 작성 완료 상태 true 저장
         */
        if (existingColumns.contains("completed")) {
            insertValues.put(
                    "completed",
                    true
            );
        }

        String columnSql =
                String.join(
                        ", ",
                        insertValues.keySet()
                );

        String placeholderSql =
                String.join(
                        ", ",
                        Collections.nCopies(
                                insertValues.size(),
                                "?"
                        )
                );

        String sql =
                "INSERT INTO profile_survey (" +
                        columnSql +
                        ") VALUES (" +
                        placeholderSql +
                        ")";

        jdbcTemplate.update(
                sql,
                insertValues.values().toArray()
        );

        Long profileSurveyId =
                jdbcTemplate.queryForObject(
                        """
                        SELECT profile_survey_id
                        FROM profile_survey
                        WHERE user_id = ?
                        """,
                        Long.class,
                        user.getUserId()
                );

        if (profileSurveyId == null) {
            throw new IllegalStateException(
                    "생성된 ProfileSurvey ID를 조회할 수 없습니다."
            );
        }

        createProfileSurveyOptions(
                profileSurveyId,
                index
        );

        return true;
    }

    private Map<String, Object> createSurveyValues(
            User user,
            int index
    ) {

        int i = index - 1;

        LinkedHashMap<String, Object> values =
                new LinkedHashMap<>();

        values.put(
                "user_id",
                user.getUserId()
        );

        values.put(
                "sleep_time",
                SLEEP_TIMES[i % SLEEP_TIMES.length]
        );

        values.put(
                "wake_time",
                WAKE_TIMES[i % WAKE_TIMES.length]
        );

        values.put(
                "sleep_talking",
                SLEEP_LEVELS[i % SLEEP_LEVELS.length]
        );

        values.put(
                "snoring",
                SLEEP_LEVELS[(i + 1) % SLEEP_LEVELS.length]
        );

        values.put(
                "teeth_grinding",
                SLEEP_LEVELS[(i + 2) % SLEEP_LEVELS.length]
        );

        values.put(
                "sleep_sensitivity",
                SLEEP_SENSITIVITY[
                        i % SLEEP_SENSITIVITY.length
                        ]
        );

        values.put(
                "indoor_eating",
                INDOOR_EATING[
                        i % INDOOR_EATING.length
                        ]
        );

        values.put(
                "smoking",
                SMOKING[i % SMOKING.length]
        );

        values.put(
                "lighting_preference",
                LIGHTING[i % LIGHTING.length]
        );

        values.put(
                "phone_call",
                PHONE_CALL[i % PHONE_CALL.length]
        );

        values.put(
                "media_sound",
                MEDIA_SOUND[i % MEDIA_SOUND.length]
        );

        values.put(
                "shower_time",
                SHOWER_TIME[i % SHOWER_TIME.length]
        );

        values.put(
                "shower_duration",
                SHOWER_DURATION[
                        i % SHOWER_DURATION.length
                        ]
        );

        values.put(
                "cleaning_frequency",
                CLEANING_FREQUENCY[
                        i % CLEANING_FREQUENCY.length
                        ]
        );

        values.put(
                "cleaning_style",
                CLEANING_STYLE[
                        i % CLEANING_STYLE.length
                        ]
        );

        values.put(
                "roommate_interaction",
                ROOMMATE_INTERACTION[
                        i % ROOMMATE_INTERACTION.length
                        ]
        );

        values.put(
                "temperature_preference",
                TEMPERATURE[
                        i % TEMPERATURE.length
                        ]
        );

        values.put(
                "bug_handling",
                BUG_HANDLING[
                        i % BUG_HANDLING.length
                        ]
        );

        values.put(
                "scent_product_use",
                SCENT_PRODUCT[
                        i % SCENT_PRODUCT.length
                        ]
        );

        values.put(
                "desktop_use",
                DESKTOP_USE[
                        i % DESKTOP_USE.length
                        ]
        );

        values.put(
                "shared_items",
                SHARED_ITEMS[
                        i % SHARED_ITEMS.length
                        ]
        );

        values.put(
                "hair_dryer_use",
                HAIR_DRYER[
                        i % HAIR_DRYER.length
                        ]
        );

        values.put(
                "meal",
                MEAL[i % MEAL.length]
        );

        /*
         * ★ 이번 오류 때문에 추가된 부분
         */
        values.put(
                "completed",
                true
        );

        Timestamp now =
                Timestamp.valueOf(
                        LocalDateTime.now()
                );

        values.put(
                "created_at",
                now
        );

        values.put(
                "updated_at",
                now
        );

        return values;
    }

    private void createProfileSurveyOptions(
            Long profileSurveyId,
            int index
    ) {

        List<String> stayDays;

        if (index % 3 == 0) {

            stayDays = List.of(
                    "월", "화", "수", "목",
                    "금", "토", "일"
            );

        } else if (index % 3 == 1) {

            stayDays = List.of(
                    "월", "화", "수", "목", "금"
            );

        } else {

            stayDays = List.of(
                    "월", "화", "수",
                    "목", "금", "토"
            );
        }

        for (String day : stayDays) {

            insertSurveyOption(
                    profileSurveyId,
                    "DORM_STAY_DAY",
                    day
            );
        }

        List<String> mealPlan;

        if (index % 4 == 0) {

            mealPlan = List.of(
                    "신청 안 함"
            );

        } else if (index % 4 == 1) {

            mealPlan = List.of(
                    "조식",
                    "석식"
            );

        } else if (index % 4 == 2) {

            mealPlan = List.of(
                    "중식",
                    "석식"
            );

        } else {

            mealPlan = List.of(
                    "조식",
                    "중식",
                    "석식"
            );
        }

        for (String meal : mealPlan) {

            insertSurveyOption(
                    profileSurveyId,
                    "MEAL_PLAN",
                    meal
            );
        }
    }

    private void insertSurveyOption(
            Long profileSurveyId,
            String fieldCode,
            String optionValue
    ) {

        jdbcTemplate.update(
                """
                INSERT INTO profile_survey_option
                    (
                        profile_survey_id,
                        field_code,
                        option_value
                    )
                VALUES (?, ?, ?)
                """,
                profileSurveyId,
                fieldCode,
                optionValue
        );
    }

    private Set<String> getTableColumns(
            String tableName
    ) {

        List<String> columns =
                jdbcTemplate.queryForList(
                        """
                        SELECT LOWER(column_name)
                        FROM information_schema.columns
                        WHERE table_schema = DATABASE()
                          AND LOWER(table_name) = LOWER(?)
                        """,
                        String.class,
                        tableName
                );

        return new HashSet<>(columns);
    }

    @Transactional
    public void deleteSeedData() {

        List<User> seedUsers =
                userRepository
                        .findAllByLoginIdStartingWith(
                                SEED_PREFIX
                        );

        if (seedUsers.isEmpty()) {
            log.info("삭제할 Seed 데이터가 없습니다.");
            return;
        }

        List<Long> userIds =
                seedUsers.stream()
                        .map(User::getUserId)
                        .toList();

        /*
         * 프로필 설문 다중 선택 삭제
         */
        deleteProfileSurveyOptions(userIds);

        /*
         * 프로필 설문 삭제
         */
        deleteProfileSurveys(userIds);

        /*
         * 기본 프로필 삭제
         */
        List<UserProfile> profiles =
                userProfileRepository
                        .findAllByUser_UserIdIn(
                                userIds
                        );

        if (!profiles.isEmpty()) {

            userProfileRepository
                    .deleteAll(profiles);

            userProfileRepository.flush();
        }

        /*
         * 회원 삭제
         */
        userRepository.deleteAll(seedUsers);
        userRepository.flush();

        log.info(
                "Seed 사용자 {}명 및 프로필 설문 삭제 완료",
                seedUsers.size()
        );
    }

    private void deleteProfileSurveyOptions(
            List<Long> userIds
    ) {

        if (!tableExists("profile_survey_option")) {
            return;
        }

        String placeholders =
                String.join(
                        ",",
                        Collections.nCopies(
                                userIds.size(),
                                "?"
                        )
                );

        String sql = """
                DELETE pso
                FROM profile_survey_option pso
                INNER JOIN profile_survey ps
                    ON ps.profile_survey_id =
                       pso.profile_survey_id
                WHERE ps.user_id IN (%s)
                """.formatted(placeholders);

        jdbcTemplate.update(
                sql,
                userIds.toArray()
        );
    }

    private void deleteProfileSurveys(
            List<Long> userIds
    ) {

        if (!tableExists("profile_survey")) {
            return;
        }

        String placeholders =
                String.join(
                        ",",
                        Collections.nCopies(
                                userIds.size(),
                                "?"
                        )
                );

        String sql = """
                DELETE FROM profile_survey
                WHERE user_id IN (%s)
                """.formatted(placeholders);

        jdbcTemplate.update(
                sql,
                userIds.toArray()
        );
    }

    private boolean tableExists(
            String tableName
    ) {

        Integer count =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM information_schema.tables
                        WHERE table_schema = DATABASE()
                          AND LOWER(table_name) = LOWER(?)
                        """,
                        Integer.class,
                        tableName
                );

        return count != null && count > 0;
    }
}