package com.kkirikkiri.matching.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.bind.support.WebDataBinderFactory;
import com.kkirikkiri.global.exception.*;
import com.kkirikkiri.global.security.AuthenticatedUser;
import com.kkirikkiri.matching.dto.AiProfileAnalysisResponse;
import com.kkirikkiri.matching.service.AiProfileAnalysisService;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AiProfileAnalysisControllerTest {
    private final AiProfileAnalysisService service = mock(AiProfileAnalysisService.class);
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new AiProfileAnalysisController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.getParameterType() == AuthenticatedUser.class;
                    }
                    public Object resolveArgument(MethodParameter p, ModelAndViewContainer m,
                            NativeWebRequest r, WebDataBinderFactory b) {
                        return new AuthenticatedUser(1L, "USER");
                    }
                }).build();
    }

    @Test
    void wrapsAnalysisAndUsesAuthenticatedRequester() throws Exception {
        when(service.analyze(1L, 2L)).thenReturn(new AiProfileAnalysisResponse(2L, 75, "실제 분석"));
        mvc.perform(post("/api/roommates/2/ai-analysis"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.matchScore").value(75))
                .andExpect(jsonPath("$.data.analysis").value("실제 분석"));
        verify(service).analyze(1L, 2L);
    }

    @Test
    void exposesSafeGatewayFailure() throws Exception {
        when(service.analyze(1L, 2L)).thenThrow(new CustomException(ErrorCode.AI_GATEWAY_UNAVAILABLE));
        mvc.perform(post("/api/roommates/2/ai-analysis"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("AI_GATEWAY_UNAVAILABLE"));
    }

    @Test
    void exposesInvalidResponseAndUnavailableMatch() throws Exception {
        when(service.analyze(1L, 2L)).thenThrow(new CustomException(ErrorCode.AI_GATEWAY_INVALID_RESPONSE));
        mvc.perform(post("/api/roommates/2/ai-analysis")).andExpect(status().isBadGateway());
        doThrow(new CustomException(ErrorCode.MATCH_ANALYSIS_UNAVAILABLE)).when(service).analyze(1L, 2L);
        mvc.perform(post("/api/roommates/2/ai-analysis")).andExpect(status().isConflict());
    }
}
