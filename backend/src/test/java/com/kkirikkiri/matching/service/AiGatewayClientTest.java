package com.kkirikkiri.matching.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import java.net.SocketTimeoutException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;
import com.kkirikkiri.global.exception.*;
import com.kkirikkiri.matching.dto.AiProfileAnalysisInput;

class AiGatewayClientTest {
    final RestClient.Builder builder = RestClient.builder();
    final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    final AiGatewayClient client = new AiGatewayClient(builder, JsonMapper.builder().build(),
            "test-only", "http://gateway.invalid", "test-model");
    final AiProfileAnalysisInput input = new AiProfileAnalysisInput(75, List.of(), List.of());

    @Test void readsOnlyTextContent() {
        server.expect(requestTo("http://gateway.invalid/chat/completions/"))
                .andRespond(withSuccess("{\"choices\":[{\"message\":{\"content\":\" 설명 \"}}]}", MediaType.APPLICATION_JSON));
        assertEquals("설명", client.analyze(input)); server.verify();
    }
    @ParameterizedTest @ValueSource(strings = {"not-json", "{}", "{\"choices\":[]}",
            "{\"choices\":[{\"message\":{\"content\":12}}]}", "{\"choices\":[{\"message\":{\"content\":\" \"}}]}"})
    void malformedResponseKeepsGatewayError(String body) {
        server.expect(anything()).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        assertEquals(ErrorCode.AI_GATEWAY_INVALID_RESPONSE,
                assertThrows(CustomException.class, () -> client.analyze(input)).getErrorCode()); server.verify();
    }
    @Test void timeoutHasNoExternalDetails() {
        server.expect(anything()).andRespond(withException(new SocketTimeoutException("sensitive remote details")));
        var failure = assertThrows(CustomException.class, () -> client.analyze(input));
        assertEquals(ErrorCode.AI_GATEWAY_UNAVAILABLE, failure.getErrorCode());
        assertFalse(failure.getMessage().contains("sensitive")); server.verify();
    }
    @Test void remoteHttpFailureKeepsUnavailableError() {
        server.expect(anything()).andRespond(withServerError());
        assertEquals(ErrorCode.AI_GATEWAY_UNAVAILABLE,
                assertThrows(CustomException.class, () -> client.analyze(input)).getErrorCode()); server.verify();
    }
}
