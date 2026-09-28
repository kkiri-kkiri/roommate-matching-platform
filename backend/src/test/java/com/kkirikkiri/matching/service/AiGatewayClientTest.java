package com.kkirikkiri.matching.service;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import com.kkirikkiri.global.exception.CustomException;
import com.kkirikkiri.global.exception.ErrorCode;
import com.kkirikkiri.matching.dto.AiProfileAnalysisInput;
import static com.kkirikkiri.survey.entity.PreferenceCriterionCode.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class AiGatewayClientTest {
    private MockRestServiceServer server;
    private AiGatewayClient client;
    private final AiProfileAnalysisInput input = new AiProfileAnalysisInput(75, List.of(SMOKING), List.of(BEDTIME));

    @BeforeEach
    void setup() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new AiGatewayClient(builder, new ObjectMapper(), "test-only-key", "https://gateway.example/v1/", "gpt-5.6-luna");
    }

    @Test
    void sendsConfiguredModelAndOnlyComparisonResults() {
        server.expect(requestTo("https://gateway.example/v1/chat/completions/"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-only-key"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.model").value("gpt-5.6-luna"))
                .andExpect(jsonPath("$.messages[1].content").value(
                        "{\"matchScore\":75,\"matchedItems\":[\"SMOKING\"],\"mismatchedItems\":[\"BEDTIME\"]}"))
                .andRespond(withSuccess("{\"choices\":[{\"message\":{\"content\":\" 분석 결과 \"}}]}", MediaType.APPLICATION_JSON));
        assertEquals("분석 결과", client.analyze(input));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"choices\":[]}", "{\"choices\":[null]}",
            "{\"choices\":[{\"message\":{}}]}", "{\"choices\":[{\"message\":{\"content\":null}}]}",
            "{\"choices\":[{\"message\":{\"content\":\"  \"}}]}",
            "{\"choices\":[{\"message\":{\"content\":42}}]}", "not-json", ""})
    void rejectsMissingOrMalformedContent(String response) {
        server.expect(anything()).andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        assertEquals(ErrorCode.AI_GATEWAY_INVALID_RESPONSE,
                assertThrows(CustomException.class, () -> client.analyze(input)).getErrorCode());
    }

    @Test
    void hidesGatewayErrorBody() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("secret upstream details"));
        var failure = assertThrows(CustomException.class, () -> client.analyze(input));
        assertEquals(ErrorCode.AI_GATEWAY_UNAVAILABLE, failure.getErrorCode());
        assertNull(failure.getCause());
        assertFalse(failure.getMessage().contains("secret"));
    }

    @Test
    void handlesTimeout() {
        server.expect(anything()).andRespond(withException(new java.net.SocketTimeoutException("timeout")));
        assertEquals(ErrorCode.AI_GATEWAY_UNAVAILABLE,
                assertThrows(CustomException.class, () -> client.analyze(input)).getErrorCode());
    }

    @Test
    void missingConfigurationDoesNotCallGateway() {
        var unconfigured = new AiGatewayClient(RestClient.builder(), new ObjectMapper(), "", "", "");
        assertEquals(ErrorCode.AI_GATEWAY_UNAVAILABLE,
                assertThrows(CustomException.class, () -> unconfigured.analyze(input)).getErrorCode());
    }
}
