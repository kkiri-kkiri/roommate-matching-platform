package com.kkirikkiri.survey.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import com.kkirikkiri.global.config.SecurityConfig;
import com.kkirikkiri.global.security.*;
import com.kkirikkiri.survey.dto.*;
import com.kkirikkiri.survey.service.ProfileSurveyService;

@WebMvcTest(ProfileSurveyController.class)
@Import(SecurityConfig.class)
class ProfileSurveyControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean ProfileSurveyService service;
    @MockitoBean JwtAuthenticationProvider jwt;

    private void login() {
        when(jwt.authenticate("test-token")).thenReturn(new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(7L, "USER"), null, List.of()));
    }

    private ProfileSurveyRequest request(String meal) {
        return new ProfileSurveyRequest("22시 이전", "07시 이전", "없음", "없음", "없음", "보통",
                "가능", "비흡연", "상관없음", "항상 밖에서", "항상 이어폰 사용해주세요",
                "저녁", "20분", "주 2회", "같이 청소", List.of("월"), List.of("조식"),
                meal, "적당히 대화", "보통", "사용 안 함", "사용하지 않아요", "일부만 공유");
    }

    @Test void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/users/me/profile-survey")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/users/me/profile-survey").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(request("같이 먹고 싶음")))).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test void readsTheJwtOwnerAndReturnsIncompleteWithoutAnswers() throws Exception {
        login();
        when(service.getMyProfileSurvey(7L)).thenReturn(new ProfileSurveyResponse(false, null));
        mvc.perform(get("/api/users/me/profile-survey").header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.completed").value(false))
                .andExpect(jsonPath("$.data.answers").isEmpty());
        verify(service).getMyProfileSurvey(7L);
    }

    @Test void putReturnsSavedAnswersAndMissingMealIsRejectedBeforeService() throws Exception {
        login();
        var answers = new ProfileSurveyResponse.Answers("22시 이전", "07시 이전", "없음", "없음", "없음", "보통",
                "가능", "비흡연", "상관없음", "항상 밖에서", "항상 이어폰 사용해주세요", "저녁", "20분",
                "주 2회", "같이 청소", List.of("월"), List.of("조식"), "같이 먹고 싶음", "적당히 대화", "보통",
                "사용 안 함", "사용하지 않아요", "일부만 공유");
        when(service.saveOrUpdateMyProfileSurvey(eq(7L), any())).thenReturn(new ProfileSurveyResponse(true, answers));
        mvc.perform(put("/api/users/me/profile-survey").header("Authorization", "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request("같이 먹고 싶음"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.completed").value(true))
                .andExpect(jsonPath("$.data.answers.sleepTime").value("22시 이전"))
                .andExpect(jsonPath("$.data.answers.mealPlan[0]").value("조식"));
        mvc.perform(put("/api/users/me/profile-survey").header("Authorization", "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request(null))))
                .andExpect(status().isBadRequest());
        verify(service, times(1)).saveOrUpdateMyProfileSurvey(eq(7L), any());
    }
}
