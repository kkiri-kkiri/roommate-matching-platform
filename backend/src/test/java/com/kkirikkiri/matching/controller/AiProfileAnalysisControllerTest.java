package com.kkirikkiri.matching.controller;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.kkirikkiri.global.config.SecurityConfig;
import com.kkirikkiri.global.exception.*;
import com.kkirikkiri.global.security.*;
import com.kkirikkiri.matching.dto.AiProfileAnalysisResponse;
import com.kkirikkiri.matching.service.AiProfileAnalysisService;

@WebMvcTest(AiProfileAnalysisController.class)
@Import(SecurityConfig.class)
class AiProfileAnalysisControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean AiProfileAnalysisService service;
    @MockitoBean JwtAuthenticationProvider jwtAuthenticationProvider;
    final UsernamePasswordAuthenticationToken principal = UsernamePasswordAuthenticationToken.authenticated(
            new AuthenticatedUser(7L, "USER"), null, List.of(new SimpleGrantedAuthority("ROLE_USER")));

    @Test void requiresAuthentication() throws Exception {
        mvc.perform(post("/api/roommates/42/ai-analysis")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
    @Test void requesterAlwaysComesFromPrincipal() throws Exception {
        when(service.analyze(7L, 42L)).thenReturn(new AiProfileAnalysisResponse(42L, 75, "설명"));
        mvc.perform(post("/api/roommates/42/ai-analysis").param("userId", "999").with(authentication(principal)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.targetUserId").value(42))
                .andExpect(jsonPath("$.data.matchScore").value(75));
        verify(service).analyze(7L, 42L);
    }
    @ParameterizedTest @EnumSource(value = ErrorCode.class, names = {"AI_ANALYSIS_RATE_LIMITED", "AI_ANALYSIS_BUSY",
            "AI_GATEWAY_UNAVAILABLE", "AI_GATEWAY_INVALID_RESPONSE", "MATCH_ANALYSIS_UNAVAILABLE"})
    void errorsKeepCommonHttpContract(ErrorCode code) throws Exception {
        when(service.analyze(7L, 42L)).thenThrow(new CustomException(code));
        mvc.perform(post("/api/roommates/42/ai-analysis").with(authentication(principal)))
                .andExpect(status().is(code.getStatus().value())).andExpect(jsonPath("$.errorCode").value(code.name()));
    }
}
