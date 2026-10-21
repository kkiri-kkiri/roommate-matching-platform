package com.kkirikkiri.integration.survey;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.type.TypeReference;

import com.kkirikkiri.integration.support.ApiIntegrationTest;
import com.kkirikkiri.integration.support.TestUsers;
import com.kkirikkiri.user.entity.User;
import com.kkirikkiri.user.entity.UserProfile;
import com.kkirikkiri.user.entity.UserStatus;

import static com.kkirikkiri.survey.SurveyTestData.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ApiIntegrationTest
class SurveyApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired TestUsers users;
    @Autowired ObjectMapper json;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    private static final TypeReference<Map<String, Object>> FIELDS = new TypeReference<>() {};
    User owner, other;
    Long profileId;

    @BeforeEach
    void setUp() {
        owner = users.createUser();
        profileId = users.createProfile(owner).getUserProfileId();
        ownerIntroduction("기존 소개");
        other = users.createUser();
        users.createProfile(other);
        em.clear();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void absentSurveyDoesNotWriteAndHttpSaveAndUpdateRemainOwnedByJwt(boolean preference) throws Exception {
        String path = path(preference);
        mvc.perform(get(path).with(users.authenticated(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.completed").value(false))
                .andExpect(jsonPath("$.data.answers").value(org.hamcrest.Matchers.nullValue()));
        assertTrue(snapshot(preference, owner).stream().allMatch(List::isEmpty));
        for (boolean second : List.of(false, true)) {
            Object request = preference ? preference(second) : profile(second);
            var fields = new LinkedHashMap<>(json.convertValue(request, FIELDS));
            fields.put("userId", other.getUserId());
            mvc.perform(put(path).with(users.authenticated(owner)).contentType("application/json")
                            .content(json.writeValueAsString(fields)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
            em.flush();
            em.clear();
            JsonNode saved = read(preference, owner);
            assertTrue(saved.path("completed").asBoolean());
            var expected = json.valueToTree(request);
            if (preference) {
                assertEquals(expected, saved.path("answers"));
            } else {
                var actual = new LinkedHashMap<>(json.convertValue(saved.path("answers"), FIELDS));
                actual.put("introduction", saved.path("introduction").asString());
                assertEquals(expected, json.valueToTree(actual));
                assertEquals(profile(second).introduction(), em.find(UserProfile.class, profileId).getIntroduction());
            }
            assertFalse(read(preference, other).path("completed").asBoolean());
            assertTrue(snapshot(preference, other).stream().allMatch(List::isEmpty));
            assertEquals(1, snapshot(preference, owner).getFirst().size());
            assertEquals(preference ? (second ? 6 : 7) : (second ? 3 : 4), snapshot(preference, owner).get(1).size());
            if (preference) assertEquals(18, snapshot(true, owner).get(2).size());
        }
    }

    static Stream<Arguments> invalidRequests() {
        var wrongKeys = new LinkedHashMap<>(weights(3));
        wrongKeys.put("unsupported", wrongKeys.remove("meal"));
        return Stream.of(
                Arguments.of(false, "meal", null),
                Arguments.of(false, "mealPlan", List.of("신청 안 함", "조식")),
                Arguments.of(false, "dormStayDays", List.of("월", "월")),
                Arguments.of(false, "introduction", "가".repeat(201)),
                Arguments.of(true, "age", List.of("상관없음", "동갑")),
                Arguments.of(true, "department", "존재하지 않는 선택지"),
                Arguments.of(true, "weights", wrongKeys));
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void rejectedUpdatePreservesAllStoredRowsAndIntroduction(boolean preference, String key, Object value) throws Exception {
        mvc.perform(put(path(preference)).with(users.authenticated(owner)).contentType("application/json")
                        .content(json.writeValueAsString(preference ? preference(false) : profile(false))))
                .andExpect(status().isOk());
        em.flush();
        em.clear();
        var before = snapshot(preference, owner);
        String introduction = em.find(UserProfile.class, profileId).getIntroduction();
        var request = new LinkedHashMap<>(json.convertValue(preference ? preference(true) : profile(true), FIELDS));
        request.put(key, value);
        mvc.perform(put(path(preference)).with(users.authenticated(owner)).contentType("application/json")
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        em.flush();
        em.clear();
        assertEquals(before, snapshot(preference, owner));
        assertEquals(introduction, em.find(UserProfile.class, profileId).getIntroduction());
    }

    @Test
    void omittedIntroductionPreservesLegacyTextAndBlankExplicitlyClearsIt() throws Exception {
        String legacy = "가".repeat(201);
        ownerIntroduction(legacy);
        mvc.perform(get(path(false)).with(users.authenticated(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.completed").value(false))
                .andExpect(jsonPath("$.data.introduction").value(legacy));
        var request = new LinkedHashMap<>(json.convertValue(profile(false), FIELDS));
        request.remove("introduction");
        mvc.perform(put(path(false)).with(users.authenticated(owner)).contentType("application/json")
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.introduction").value(legacy));
        em.flush();
        em.clear();
        assertEquals(legacy, em.find(UserProfile.class, profileId).getIntroduction());
        request.put("introduction", " \n\t\u00a0\u3000\ufeff");
        mvc.perform(put(path(false)).with(users.authenticated(owner)).contentType("application/json")
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.introduction").value(""));
        em.flush();
        em.clear();
        assertEquals("", em.find(UserProfile.class, profileId).getIntroduction());
    }

    @Test
    void incompleteStoredAnswersReturnFalseWithoutRewritingCompletionFlag() throws Exception {
        mvc.perform(put(path(false)).with(users.authenticated(owner)).contentType("application/json")
                        .content(json.writeValueAsString(profile(false))))
                .andExpect(status().isOk());
        em.flush();
        jdbc.update("update PROFILE_SURVEY set meal='' where user_id=?", owner.getUserId());
        em.clear();
        var before = snapshot(false, owner);
        mvc.perform(get(path(false)).with(users.authenticated(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.completed").value(false))
                .andExpect(jsonPath("$.data.answers.meal").value(""));
        em.flush();
        em.clear();
        assertEquals(before, snapshot(false, owner));
        assertTrue(jdbc.queryForObject("select completed from PROFILE_SURVEY where user_id=?", Boolean.class, owner.getUserId()));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void missingAndInactiveAuthenticationCannotReadOrCreateSurvey(boolean preference) throws Exception {
        String body = json.writeValueAsString(preference ? preference(false) : profile(false));
        mvc.perform(get(path(preference))).andExpect(status().isUnauthorized());
        mvc.perform(put(path(preference)).contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        for (UserStatus state : List.of(UserStatus.SUSPENDED, UserStatus.WITHDRAWN)) {
            em.find(User.class, owner.getUserId()).changeStatus(state);
            em.flush();
            em.clear();
            mvc.perform(get(path(preference)).with(users.authenticated(owner)))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
            mvc.perform(put(path(preference)).with(users.authenticated(owner)).contentType("application/json").content(body))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
        }
        assertTrue(snapshot(preference, owner).stream().allMatch(List::isEmpty));
        assertEquals("기존 소개", em.find(UserProfile.class, profileId).getIntroduction());
    }

    private String path(boolean preference) {
        return "/api/users/me/" + (preference ? "roommate-preferences" : "profile-survey");
    }

    private JsonNode read(boolean preference, User user) throws Exception {
        var result = mvc.perform(get(path(preference)).with(users.authenticated(user)))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data");
    }

    private void ownerIntroduction(String value) {
        em.find(UserProfile.class, profileId).changeIntroduction(value);
        em.flush();
    }

    private List<List<Map<String, Object>>> snapshot(boolean preference, User user) {
        Long id = user.getUserId();
        if (preference) return List.of(
                jdbc.queryForList("select * from ROOMMATE_PREFERENCE where user_id=?", id),
                jdbc.queryForList("select o.* from ROOMMATE_PREFERENCE_OPTION o join ROOMMATE_PREFERENCE p on p.preference_id=o.preference_id where p.user_id=? order by o.preference_option_id", id),
                jdbc.queryForList("select c.* from PREFERENCE_CRITERION c join ROOMMATE_PREFERENCE p on p.preference_id=c.preference_id where p.user_id=? order by c.preference_criterion_id", id));
        return List.of(jdbc.queryForList("select * from PROFILE_SURVEY where user_id=?", id),
                jdbc.queryForList("select o.* from PROFILE_SURVEY_OPTION o join PROFILE_SURVEY p on p.profile_survey_id=o.profile_survey_id where p.user_id=? order by o.profile_survey_option_id", id));
    }
}
