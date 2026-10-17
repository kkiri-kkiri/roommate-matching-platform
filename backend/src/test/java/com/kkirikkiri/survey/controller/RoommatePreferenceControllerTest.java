package com.kkirikkiri.survey.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.kkirikkiri.survey.SurveyTestData.*;

import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import com.kkirikkiri.global.config.SecurityConfig;
import com.kkirikkiri.global.exception.*;
import com.kkirikkiri.global.security.*;
import com.kkirikkiri.survey.dto.*;
import com.kkirikkiri.survey.service.RoommatePreferenceService;

@WebMvcTest(RoommatePreferenceController.class)
@Import(SecurityConfig.class)
class RoommatePreferenceControllerTest {
    static final String URL = "/api/users/me/roommate-preferences";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean RoommatePreferenceService service;
    @MockitoBean JwtAuthenticationProvider jwt;

    void login() {
        when(jwt.authenticate("test-token")).thenReturn(new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(7L, "USER"), null, List.of()));
    }
    @Test void getAndPutRequireAuthentication() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
        mvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(preference(false))))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
    @Test void getUsesJwtPrincipalAndKeepsIncompleteResponseContract() throws Exception {
        login(); when(service.getMyRoommatePreference(7L)).thenReturn(new RoommatePreferenceResponse(false, null));
        mvc.perform(get(URL).header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.completed").value(false)).andExpect(jsonPath("$.data.answers").isEmpty());
        verify(service).getMyRoommatePreference(7L);
    }
    @Test void getKeepsSavedAnswersAndWeightContract() throws Exception {
        login(); var r = preference(false);
        var answers = new RoommatePreferenceResponse.Answers(r.department(), r.age(), r.grade(), r.bedtime(), r.wakeTime(),
                r.sleepTalking(), r.snoring(), r.teethGrinding(), r.smoking(), r.lightingPreference(), r.showerTime(),
                r.showerDuration(), r.cleaningFrequency(), r.cleaningStyle(), r.interaction(), r.meal(), r.sharedItems(),
                r.temperature(), r.weights());
        when(service.getMyRoommatePreference(7L)).thenReturn(new RoommatePreferenceResponse(true, answers));
        mvc.perform(get(URL).header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.completed").value(true))
                .andExpect(jsonPath("$.data.answers.department").value(r.department()))
                .andExpect(jsonPath("$.data.answers.age[0]").value("연하"))
                .andExpect(jsonPath("$.data.answers.weights.age").value(2));
    }
    @Test void putIgnoresForgedUserIdAndUsesJwtOwner() throws Exception {
        login(); Map<String, Object> payload = new LinkedHashMap<>(json.convertValue(preference(false), Map.class));
        payload.put("userId", 99L);
        mvc.perform(put(URL).header("Authorization", "Bearer test-token").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(payload))).andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        verify(service).saveOrUpdateMyRoommatePreference(7L, preference(false));
        verifyNoMoreInteractions(service);
    }
    static Stream<Arguments> invalidDtos() {
        var missing = weights(2); missing.remove("age");
        var nullValue = weights(2); nullValue.put("age", null);
        return Stream.of(Arguments.of("department", ""), Arguments.of("age", List.of()),
                Arguments.of("weights", null), Arguments.of("weights", Map.of()), Arguments.of("weights", missing),
                Arguments.of("weights", weights(0)), Arguments.of("weights", weights(6)), Arguments.of("weights", nullValue));
    }
    @ParameterizedTest @MethodSource("invalidDtos")
    void invalidDtoIsRejectedBeforeService(String key, Object value) throws Exception {
        login(); var request = change(preference(false), key, value, RoommatePreferenceRequest.class);
        mvc.perform(put(URL).header("Authorization", "Bearer test-token").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(request))).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void serviceChoiceValidationRemainsBadRequest() throws Exception {
        login(); doThrow(new CustomException(ErrorCode.VALIDATION_ERROR)).when(service)
                .saveOrUpdateMyRoommatePreference(eq(7L), any());
        mvc.perform(put(URL).header("Authorization", "Bearer test-token").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(change(preference(false), "department", "invalid", RoommatePreferenceRequest.class))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }
    @Test void inactiveExceptionFromServiceIsUnauthorized() throws Exception {
        login(); doThrow(new CustomException(ErrorCode.UNAUTHORIZED)).when(service)
                .saveOrUpdateMyRoommatePreference(eq(7L), any());
        mvc.perform(put(URL).header("Authorization", "Bearer test-token").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(preference(false))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }
    @Test void inactiveJwtIsRejectedBeforeController() throws Exception {
        when(jwt.authenticate("test-token")).thenThrow(new CustomException(ErrorCode.UNAUTHORIZED));
        mvc.perform(get(URL).header("Authorization", "Bearer test-token")).andExpect(status().isUnauthorized());
        mvc.perform(put(URL).header("Authorization", "Bearer test-token").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(preference(false)))).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
}
