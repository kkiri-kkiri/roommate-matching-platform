package com.kkirikkiri.integration.matching;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import java.net.SocketTimeoutException;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.BeanUtils;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.kkirikkiri.integration.support.ApiIntegrationTest;
import com.kkirikkiri.integration.support.TestUsers;
import com.kkirikkiri.matching.repository.*;
import com.kkirikkiri.matching.service.AiGatewayClient;
import com.kkirikkiri.survey.SurveyTestData;
import com.kkirikkiri.survey.dto.RoommatePreferenceRequest;
import com.kkirikkiri.survey.dto.ProfileSurveyRequest;
import com.kkirikkiri.survey.entity.*;
import com.kkirikkiri.user.entity.*;
import com.kkirikkiri.global.exception.ErrorCode;
import com.kkirikkiri.matching.dto.AiProfileAnalysisInput;

/** Matching refresh/likes and AI quota commit independently of a caller transaction. */
@ApiIntegrationTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = "ai.analysis.window-seconds=3600")
class MatchingApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired TestUsers users;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @Autowired JdbcTemplate jdbc;
    @Autowired ProfileLikeRepository likes;
    @Autowired RoommateMatchRepository matches;
    @Autowired AiGatewayClient ai;
    @Autowired JsonMapper json;
    @Autowired @Qualifier("matchingRefreshExecutor") ThreadPoolTaskExecutor refreshExecutor;
    @Value("${ai.analysis.max-requests:12}") int quotaLimit;

    final List<Long> owned = new ArrayList<>();
    final List<Long> dormitories = new ArrayList<>(), faculties = new ArrayList<>();
    TransactionTemplate tx;
    User requester, target;

    @BeforeEach
    void setup() {
        tx = new TransactionTemplate(manager);
        requester = member(false, false);
        target = member(true, false);
    }

    @AfterEach
    void cleanup() throws Exception {
        awaitCondition(() -> refreshExecutor.getActiveCount() == 0
                && refreshExecutor.getThreadPoolExecutor().getQueue().isEmpty(), "matching work did not finish");
        refreshExecutor.setTaskDecorator(null);
        tx.executeWithoutResult(s -> {
            for (Long id : owned) {
                jdbc.update("delete from PROFILE_LIKE where user_id=? or target_user_id=?", id, id);
                jdbc.update("delete from ROOMMATE_MATCH where requester_user_id=? or target_user_id=?", id, id);
                jdbc.update("delete from PROFILE_SURVEY_OPTION where profile_survey_id in (select profile_survey_id from PROFILE_SURVEY where user_id=?)", id);
                jdbc.update("delete from PROFILE_SURVEY where user_id=?", id);
                jdbc.update("delete from ROOMMATE_PREFERENCE_OPTION where preference_id in (select preference_id from ROOMMATE_PREFERENCE where user_id=?)", id);
                jdbc.update("delete from PREFERENCE_CRITERION where preference_id in (select preference_id from ROOMMATE_PREFERENCE where user_id=?)", id);
                jdbc.update("delete from ROOMMATE_PREFERENCE where user_id=?", id);
                jdbc.update("delete from USER_PROFILE where user_id=?", id);
                jdbc.update("delete from `USER` where user_id=?", id);
                jdbc.update("delete from auth_limit_bucket where bucket_key=?", quotaKey(id));
            }
            dormitories.forEach(id -> jdbc.update("delete from DORMITORY where dormitory_id=?", id));
            faculties.forEach(id -> jdbc.update("delete from FACULTY where faculty_id=?", id));
        });
    }

    @Test
    void likesAreIdempotentPersonalAndPersistWithoutAMatchResult() throws Exception {
        User other = member(false, false);
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/roommates/{id}/likes", target.getUserId()).with(users.authenticated(requester)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));
        }
        assertEquals(1, likes.count());
        assertTrue(matches.findByRequesterUser_UserIdAndTargetUser_UserId(requester.getUserId(), target.getUserId()).isEmpty());
        mvc.perform(get("/api/users/me/liked-roommates").with(users.authenticated(requester)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(target.getUserId()))
                .andExpect(jsonPath("$.data.content[0].liked").value(true))
                .andExpect(jsonPath("$.data.content[0].matchScore").value(org.hamcrest.Matchers.nullValue()));
        mvc.perform(delete("/api/roommates/{id}/likes", target.getUserId()).with(users.authenticated(other)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        assertTrue(likes.existsByUser_UserIdAndTargetUser_UserId(requester.getUserId(), target.getUserId()));
        mvc.perform(get("/api/roommates/{id}", target.getUserId()).with(users.authenticated(requester)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.liked").value(true));
        mvc.perform(get("/api/roommates/{id}", target.getUserId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.liked").value(false))
                .andExpect(jsonPath("$.data.matchScore").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.answers.dormStayDays", org.hamcrest.Matchers.contains("월", "금")))
                .andExpect(jsonPath("$.data.answers.mealPlan", org.hamcrest.Matchers.contains("조식", "석식")));
        mvc.perform(delete("/api/roommates/{id}/likes", target.getUserId()).with(users.authenticated(requester)))
                .andExpect(status().isOk());
        assertFalse(likes.existsByUser_UserIdAndTargetUser_UserId(requester.getUserId(), target.getUserId()));
        mvc.perform(get("/api/users/me/liked-roommates").with(users.authenticated(requester)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
        mvc.perform(delete("/api/roommates/{id}/likes", target.getUserId()).with(users.authenticated(requester)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    }

    @Test
    void filtersBindRepeatedValuesAndPageOnlyEligibleCandidates() throws Exception {
        User second = member(true, true);
        User inactive = member(true, false);
        tx.executeWithoutResult(s -> em.find(User.class, inactive.getUserId()).changeStatus(UserStatus.SUSPENDED));
        // Incomplete requester and suspended member must not inflate count.
        mvc.perform(get("/api/roommates").param("sleepTime", "22시 이전", "23시 ~ 24시"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[*].id", org.hamcrest.Matchers.contains(
                        target.getUserId().intValue(), second.getUserId().intValue())));
        mvc.perform(get("/api/roommates").param("sleepTime", "22시 이전", "23시 ~ 24시")
                        .param("dormStayDays", "금").param("mealPlan", "석식").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(target.getUserId()))
                .andExpect(jsonPath("$.data.content[0].matchScore").value(org.hamcrest.Matchers.nullValue()));
        mvc.perform(get("/api/roommates").param("size", "1").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].id").value(second.getUserId()))
                .andExpect(jsonPath("$.data.totalElements").value(2)).andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.first").value(false)).andExpect(jsonPath("$.data.last").value(true));
        mvc.perform(get("/api/roommates/{id}", inactive.getUserId()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    }

    @Test
    void recommendationsAndAiUseActualPersistedCalculationAndRefreshChangedInputs() throws Exception {
        bedtimePreference();
        mvc.perform(get("/api/roommates/recommended").with(users.authenticated(requester)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].id").value(target.getUserId()))
                .andExpect(jsonPath("$.data.content[0].matchScore").value(100));
        assertEquals(100, storedScore());
        when(ai.analyze(any())).thenReturn("취침 시간이 일치합니다.");
        mvc.perform(post("/api/roommates/{id}/ai-analysis", target.getUserId()).with(users.authenticated(requester)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.targetUserId").value(target.getUserId()))
                .andExpect(jsonPath("$.data.matchScore").value(100))
                .andExpect(jsonPath("$.data.analysis").value("취침 시간이 일치합니다."));
        verify(ai).analyze(argThat(input -> input.matchScore() == 100
                && input.matchedItems().equals(List.of(PreferenceCriterionCode.BEDTIME)) && input.mismatchedItems().isEmpty()));
        tx.executeWithoutResult(s -> jdbc.update("update PROFILE_SURVEY set sleep_time=? where user_id=?", "23시 ~ 24시", target.getUserId()));
        mvc.perform(get("/api/roommates/recommended").with(users.authenticated(requester)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content").isEmpty())
                .andExpect(jsonPath("$.data.totalElements").value(0));
        assertEquals(0, storedScore(), "recommendation must recalculate instead of returning stale score");
    }

    @Test
    void uncomputableAiReturnsConflictWithoutCallingExternalGateway() throws Exception {
        mvc.perform(post("/api/roommates/{id}/ai-analysis", target.getUserId()).with(users.authenticated(requester)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("MATCH_ANALYSIS_UNAVAILABLE"));
        verifyNoInteractions(ai);
        assertTrue(matches.findByRequesterUser_UserIdAndTargetUser_UserId(requester.getUserId(), target.getUserId()).isEmpty());
    }

    @Test
    void selfAndIncompleteTargetsCannotBeLikedAndDeactivationHidesExistingLike() throws Exception {
        for (User invalid : List.of(requester, member(false, false))) {
            mvc.perform(post("/api/roommates/{id}/likes", invalid.getUserId()).with(users.authenticated(requester)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        }
        assertEquals(0, likes.count());
        mvc.perform(post("/api/roommates/{id}/likes", target.getUserId()).with(users.authenticated(requester)))
                .andExpect(status().isOk());
        tx.executeWithoutResult(s -> em.find(User.class, target.getUserId()).changeStatus(UserStatus.WITHDRAWN));
        mvc.perform(get("/api/users/me/liked-roommates").with(users.authenticated(requester)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content").isEmpty())
                .andExpect(jsonPath("$.data.totalElements").value(0));
        assertTrue(likes.existsByUser_UserIdAndTargetUser_UserId(requester.getUserId(), target.getUserId()));
        mvc.perform(delete("/api/roommates/{id}/likes", target.getUserId()).with(users.authenticated(requester)))
                .andExpect(status().isOk());
        assertEquals(0, likes.count());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/roommates?sort=nickname,asc",
            "/api/roommates/recommended?sort=matchScore,asc",
            "/api/users/me/liked-roommates?sort=userId,asc"})
    void unsupportedSortsKeepApiErrorContract(String url) throws Exception {
        mvc.perform(get(url).with(users.authenticated(requester)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    }

    @Test
    void oversizedFilterIsRejectedByDtoValidation() throws Exception {
        mvc.perform(get("/api/roommates").param("smoking", "x".repeat(31)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void protectedEndpointsAndInactiveJwtRejectAccessBeforeMutation() throws Exception {
        mvc.perform(get("/api/roommates/recommended")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/me/liked-roommates")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/roommates/{id}/likes", target.getUserId())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/roommates/{id}/ai-analysis", target.getUserId())).andExpect(status().isUnauthorized());
        var token = users.authenticated(requester);
        tx.executeWithoutResult(s -> em.find(User.class, requester.getUserId()).changeStatus(UserStatus.SUSPENDED));
        mvc.perform(post("/api/roommates/{id}/likes", target.getUserId()).with(token))
                .andExpect(status().isUnauthorized());
        assertEquals(0, likes.count());
        verifyNoInteractions(ai);
    }

    @Test
    void surveyApiCommitsRefreshScoresAsynchronouslyAndWeightChangesAreDirectional() throws Exception {
        var choices = Map.<String, Object>of("bedtime", List.of("23시 ~ 24시", "22시 이전"),
                "wakeTime", List.of("07시 이전"));
        savePreference(preference(choices, Map.of("bedtime", 5, "wakeTime", 1)));
        awaitScore(target, 100);

        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        refreshExecutor.setTaskDecorator(task -> () -> {
            entered.countDown();
            try { assertTrue(release.await(10, TimeUnit.SECONDS), "refresh gate timed out"); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
            task.run();
        });
        try {
            ProfileSurveyRequest changed = SurveyTestData.change(SurveyTestData.profile(false),
                    "sleepTime", "22시 ~ 23시", ProfileSurveyRequest.class);
            mvc.perform(put("/api/users/me/profile-survey").with(users.authenticated(target))
                            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(changed)))
                    .andExpect(status().isOk());
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            assertEquals("22시 ~ 23시", jdbc.queryForObject(
                    "select sleep_time from PROFILE_SURVEY where user_id=?", String.class, target.getUserId()));
            assertEquals(100, storedScore(), "survey committed while its real worker is gated");
        } finally { release.countDown(); }
        // No matching GET here: polling the committed row cannot repair a broken AFTER_COMMIT path.
        awaitScore(target, 17); // only wake matches: round(100 * 1 / (5 + 1))
        awaitCondition(() -> refreshExecutor.getActiveCount() == 0, "profile refresh did not finish");
        refreshExecutor.setTaskDecorator(null);
        savePreference(preference(choices, Map.of("bedtime", 1, "wakeTime", 5)));
        awaitScore(target, 83); // weights reversed: round(100 * 5 / (1 + 5))
        assertEquals(5, jdbc.queryForObject("""
                select c.weight from PREFERENCE_CRITERION c join ROOMMATE_PREFERENCE p
                on p.preference_id=c.preference_id where p.user_id=? and c.criterion_code='WAKE_TIME'
                """, Integer.class, requester.getUserId()));
        assertTrue(matches.findByRequesterUser_UserIdAndTargetUser_UserId(target.getUserId(), requester.getUserId()).isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"3,3,14", "3,4,13", "2,3,17"})
    void partialWeightedScoresRoundBelowAtAndAboveHalfThroughApi(int bedtimeWeight, int gradeWeight, int expected) throws Exception {
        // Only wake matches (weight 1). Denominators 7, 8, 6 yield 14.285..., 12.5, 16.666....
        savePreference(preference(Map.of("bedtime", List.of("23시 ~ 24시"),
                        "wakeTime", List.of("08시 ~ 09시", "07시 이전"), "grade", List.of("4학년")),
                Map.of("bedtime", bedtimeWeight, "wakeTime", 1, "grade", gradeWeight)));
        awaitScore(target, expected);
        mvc.perform(get("/api/roommates/{id}", target.getUserId()).with(users.authenticated(requester)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.matchScore").value(expected));
        assertEquals(expected, storedScore());
    }

    @Test
    void recommendationThresholdTieOrderAndNineItemLimitUseRealScores() throws Exception {
        List<User> tied = new ArrayList<>(List.of(target));
        for (int i = 1; i < 10; i++) tied.add(member(true, false));
        User below = member(true, false);
        tx.executeWithoutResult(s -> jdbc.update("update PROFILE_SURVEY set sleep_time=? where user_id=?",
                "23시 ~ 24시", below.getUserId()));
        // Matching weights: 34 fixed + bedtime 1. Nonmatching weights: 23. Total: 58.
        savePreference(preference(Map.ofEntries(
                Map.entry("bedtime", List.of("22시 이전")), Map.entry("wakeTime", List.of("07시 이전")),
                Map.entry("sleepTalking", "없음만 가능"), Map.entry("snoring", "적음까지 괜찮음"),
                Map.entry("teethGrinding", "보통까지 괜찮음"), Map.entry("smoking", "비흡연자만"),
                Map.entry("showerTime", "저녁"), Map.entry("showerDuration", "20분 이하"),
                Map.entry("department", "같은 전공"), Map.entry("age", List.of("연하")),
                Map.entry("grade", List.of("4학년")), Map.entry("cleaningFrequency", List.of("매일")),
                Map.entry("cleaningStyle", "각자 영역만 청소하는 사람")),
                Map.of("bedtime", 1, "showerDuration", 4, "cleaningFrequency", 4, "cleaningStyle", 4)));
        awaitScore(target, 60); // round(3500 / 58)
        awaitScore(below, 59); // round(3400 / 58)
        Object[] firstNine = tied.subList(0, 9).stream().map(u -> u.getUserId().intValue()).toArray();
        for (int requestedSize : new int[] {9, 10}) {
            mvc.perform(get("/api/roommates/recommended").with(users.authenticated(requester))
                            .param("size", Integer.toString(requestedSize)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.size").value(9))
                    .andExpect(jsonPath("$.data.totalElements").value(10)).andExpect(jsonPath("$.data.totalPages").value(2))
                    .andExpect(jsonPath("$.data.content[*].id", org.hamcrest.Matchers.contains(firstNine)))
                    .andExpect(jsonPath("$.data.content[*].matchScore", org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(60))));
        }
        mvc.perform(get("/api/roommates/recommended").with(users.authenticated(requester)).param("page", "1").param("size", "10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(tied.getLast().getUserId()))
                .andExpect(jsonPath("$.data.last").value(true));
    }

    @Test
    void concurrentDuplicateLikesCommitOnePairAndDeletePreservesOtherUsersPair() throws Exception {
        User other = member(false, false);
        mvc.perform(post("/api/roommates/{id}/likes", target.getUserId()).with(users.authenticated(other)))
                .andExpect(status().isOk());
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<?>> calls = new ArrayList<>();
            for (int i = 0; i < 2; i++) calls.add(pool.submit(() -> {
                ready.countDown();
                assertTrue(start.await(10, TimeUnit.SECONDS));
                mvc.perform(post("/api/roommates/{id}/likes", target.getUserId()).with(users.authenticated(requester)))
                        .andExpect(status().isOk());
                return null;
            }));
            assertTrue(ready.await(10, TimeUnit.SECONDS)); start.countDown();
            for (var call : calls) call.get(15, TimeUnit.SECONDS);
        } finally {
            start.countDown(); pool.shutdownNow(); assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
        assertEquals(1, jdbc.queryForObject("select count(*) from PROFILE_LIKE where user_id=? and target_user_id=?",
                Integer.class, requester.getUserId(), target.getUserId()));
        mvc.perform(delete("/api/roommates/{id}/likes", target.getUserId()))
                .andExpect(status().isUnauthorized());
        assertTrue(likes.existsByUser_UserIdAndTargetUser_UserId(requester.getUserId(), target.getUserId()));
        mvc.perform(delete("/api/roommates/{id}/likes", target.getUserId()).with(users.authenticated(requester)))
                .andExpect(status().isOk());
        assertFalse(likes.existsByUser_UserIdAndTargetUser_UserId(requester.getUserId(), target.getUserId()));
        assertTrue(likes.existsByUser_UserIdAndTargetUser_UserId(other.getUserId(), target.getUserId()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"httpFailure", "timeout", "invalidResponse"})
    void gatewayFailuresMapToHttpAndPreserveCommittedMatchingData(String failure) throws Exception {
        bedtimePreference();
        mvc.perform(get("/api/roommates/{id}", target.getUserId()).with(users.authenticated(requester))).andExpect(status().isOk());
        mvc.perform(post("/api/roommates/{id}/likes", target.getUserId()).with(users.authenticated(requester))).andExpect(status().isOk());
        var before = jdbc.queryForMap("select * from ROOMMATE_MATCH where requester_user_id=? and target_user_id=?",
                requester.getUserId(), target.getUserId());
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        // Use the external adapter's existing test constructor; HTTP alone is replaced by the mock server.
        var gateway = BeanUtils.instantiateClass(AiGatewayClient.class.getDeclaredConstructor(
                RestClient.Builder.class, tools.jackson.databind.ObjectMapper.class, String.class, String.class, String.class),
                builder, json, "test-only", "http://gateway.invalid", "test-model");
        var expectation = server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.anything());
        ErrorCode expected = ErrorCode.AI_GATEWAY_UNAVAILABLE;
        switch (failure) {
            case "httpFailure" -> expectation.andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withServerError());
            case "timeout" -> expectation.andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                    .withException(new SocketTimeoutException("sensitive remote details")));
            case "invalidResponse" -> {
                expectation.andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess("{}", MediaType.APPLICATION_JSON));
                expected = ErrorCode.AI_GATEWAY_INVALID_RESPONSE;
            }
            default -> throw new AssertionError(failure);
        }
        when(ai.analyze(any())).thenAnswer(call -> gateway.analyze(call.getArgument(0, AiProfileAnalysisInput.class)));
        mvc.perform(post("/api/roommates/{id}/ai-analysis", target.getUserId()).with(users.authenticated(requester)))
                .andExpect(status().is(expected.getStatus().value())).andExpect(jsonPath("$.errorCode").value(expected.name()))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("sensitive remote details"))));
        server.verify();
        assertEquals(before, jdbc.queryForMap("select * from ROOMMATE_MATCH where requester_user_id=? and target_user_id=?",
                requester.getUserId(), target.getUserId()));
        assertTrue(likes.existsByUser_UserIdAndTargetUser_UserId(requester.getUserId(), target.getUserId()));
    }

    @Test
    void aiQuotaRejectsNextHttpRequestWithoutCallingGatewayOrChangingMatch() throws Exception {
        bedtimePreference();
        when(ai.analyze(any())).thenReturn("설명");
        for (int i = 0; i < quotaLimit; i++) mvc.perform(post("/api/roommates/{id}/ai-analysis", target.getUserId())
                        .with(users.authenticated(requester)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.matchScore").value(100));
        var before = jdbc.queryForMap("select * from ROOMMATE_MATCH where requester_user_id=? and target_user_id=?",
                requester.getUserId(), target.getUserId());
        mvc.perform(post("/api/roommates/{id}/ai-analysis", target.getUserId()).with(users.authenticated(requester)))
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.errorCode").value("AI_ANALYSIS_RATE_LIMITED"));
        verify(ai, times(quotaLimit)).analyze(any());
        assertEquals(before, jdbc.queryForMap("select * from ROOMMATE_MATCH where requester_user_id=? and target_user_id=?",
                requester.getUserId(), target.getUserId()));
    }

    private RoommatePreferenceRequest preference(Map<String, Object> selections, Map<String, Integer> overrides) {
        Map<String, Object> values = new LinkedHashMap<>(json.convertValue(SurveyTestData.preference(false), Map.class));
        values.replaceAll((key, value) -> value instanceof List<?> ? List.of("상관없음")
                : value instanceof String ? "상관없음" : value);
        values.putAll(selections);
        Map<String, Integer> weights = SurveyTestData.weights(5);
        weights.putAll(overrides);
        values.put("weights", weights);
        return json.convertValue(values, RoommatePreferenceRequest.class);
    }

    private void savePreference(RoommatePreferenceRequest request) throws Exception {
        mvc.perform(put("/api/users/me/roommate-preferences").with(users.authenticated(requester))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    private void awaitScore(User user, int expected) throws Exception {
        awaitCondition(() -> matches.findByRequesterUser_UserIdAndTargetUser_UserId(requester.getUserId(), user.getUserId())
                .map(row -> row.getMatchScore() == expected).orElse(false), "committed score did not become " + expected);
    }

    private void awaitCondition(BooleanSupplier condition, String message) throws Exception {
        var completed = new CompletableFuture<Void>();
        var polling = Executors.newSingleThreadScheduledExecutor();
        try {
            polling.scheduleWithFixedDelay(() -> {
                try { if (condition.getAsBoolean()) completed.complete(null); }
                catch (Throwable failure) { completed.completeExceptionally(failure); }
            }, 0, 20, TimeUnit.MILLISECONDS);
            try { completed.get(10, TimeUnit.SECONDS); }
            catch (TimeoutException timeout) { throw new AssertionError(message, timeout); }
        } finally { polling.shutdownNow(); assertTrue(polling.awaitTermination(10, TimeUnit.SECONDS)); }
    }

    private User member(boolean completed, boolean second) {
        return tx.execute(s -> {
            User user = users.createUser();
            owned.add(user.getUserId());
            var profile = users.createProfile(user);
            dormitories.add(profile.getDormitory().getDormitoryId());
            faculties.add(profile.getFaculty().getFacultyId());
            if (completed) {
                var a = SurveyTestData.profile(second);
                var survey = new ProfileSurvey(user, a.sleepTime(), a.wakeTime(), a.sleepTalking(), a.snoring(),
                        a.teethGrinding(), a.sleepSensitivity(), a.indoorEating(), a.smoking(), a.lightingPreference(),
                        a.phoneCall(), a.mediaSound(), a.showerTime(), a.showerDuration(), a.cleaningFrequency(),
                        a.cleaningStyle(), a.meal(), a.roommateInteraction(), a.temperaturePreference(),
                        a.scentProductUse(), a.desktopUse(), a.sharedItems(), true);
                em.persist(survey);
                a.dormStayDays().forEach(value -> em.persist(new ProfileSurveyOption(survey, ProfileSurveyFieldCode.DORM_STAY_DAY, value)));
                a.mealPlan().forEach(value -> em.persist(new ProfileSurveyOption(survey, ProfileSurveyFieldCode.MEAL_PLAN, value)));
            }
            em.flush();
            return user;
        });
    }

    /** Only bedtime affects this valid completed preference: a match is 100, a mismatch is 0. */
    private void bedtimePreference() {
        tx.executeWithoutResult(s -> {
            var preference = new RoommatePreference(em.find(User.class, requester.getUserId()),
                    "상관없음", "상관없음", "상관없음", "상관없음", "상관없음", "상관없음", "상관없음",
                    "상관없음", "상관없음", "상관없음", "상관없음", "상관없음", "상관없음", true);
            em.persist(preference);
            for (var code : PreferenceCriterionCode.values()) {
                em.persist(new PreferenceCriterion(preference, code, 1));
                if (code.isMultiSelect()) em.persist(new RoommatePreferenceOption(preference, code,
                        code == PreferenceCriterionCode.BEDTIME ? "22시 이전" : "상관없음"));
            }
        });
    }

    private int storedScore() {
        return matches.findByRequesterUser_UserIdAndTargetUser_UserId(requester.getUserId(), target.getUserId())
                .orElseThrow().getMatchScore();
    }

    private String quotaKey(Long id) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(("ai-analysis:user:" + id).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }
}
