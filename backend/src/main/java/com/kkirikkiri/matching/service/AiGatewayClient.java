package com.kkirikkiri.matching.service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.kkirikkiri.global.exception.CustomException;
import com.kkirikkiri.global.exception.ErrorCode;
import com.kkirikkiri.matching.dto.AiProfileAnalysisInput;

@Component
public class AiGatewayClient {
    private static final String INSTRUCTIONS = """
            당신은 룸메이트 매칭 계산 결과를 설명하는 도우미입니다.
            입력은 요청자의 선호와 상대방의 특성을 백엔드에서 비교한 방향성 결과입니다.
            matchScore는 확정된 점수입니다. 점수를 계산, 변경, 추정하거나 새로운 점수/확률을 만들지 마세요.
            matchedItems는 선호와 일치한 항목, mismatchedItems는 일치하지 않은 항목입니다.
            상관없음 항목은 제외되어 있습니다. 원래 답변의 구체적인 값이나 두 사람의 성격을 추측하지 마세요.
            일치 항목의 장점과 불일치 항목에서 대화로 확인할 점만 한국어 2~3문장, 250자 이내로 설명하세요.
            결과를 추천 또는 비추천으로 새로 판정하지 마세요. 마크다운 없이 자연어만 반환하세요.
            항목 의미: DEPARTMENT 학부/전공, AGE 나이, GRADE 학년, BEDTIME 취침 시간,
            WAKE_TIME 기상 시간, SLEEP_TALKING 잠꼬대, SNORING 코골이, TEETH_GRINDING 이갈이,
            SMOKING 흡연, LIGHTING 조명, SHOWER_TIME 샤워 시간, SHOWER_DURATION 샤워 소요 시간,
            CLEANING_FREQUENCY 청소 빈도, CLEANING_STYLE 청소 방식, INTERACTION 교류,
            MEAL 식사, SHARED_ITEMS 물건 공유, TEMPERATURE 온도 선호.
            """;
    private final RestClient client;
    private final ObjectMapper mapper;
    private final String apiKey;
    private final String baseUrl;
    private final String model;

    @Autowired
    public AiGatewayClient(ObjectMapper mapper, @Value("${ai.api-key:}") String apiKey,
            @Value("${ai.base-url:}") String baseUrl, @Value("${ai.model:}") String model) {
        this(RestClient.builder().requestFactory(requestFactory()), mapper, apiKey, baseUrl, model);
    }

    AiGatewayClient(RestClient.Builder builder, ObjectMapper mapper, String apiKey, String baseUrl, String model) {
        this.client = builder.build();
        this.mapper = mapper;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.model = model;
    }

    private static SimpleClientHttpRequestFactory requestFactory() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(45));
        return factory;
    }

    public String analyze(AiProfileAnalysisInput input) {
        if (apiKey.isBlank() || baseUrl.isBlank() || model.isBlank())
            throw new CustomException(ErrorCode.AI_GATEWAY_UNAVAILABLE);
        try {
            JsonNode response = client.post().uri(baseUrl + "/chat/completions/")
                    .headers(headers -> headers.setBearerAuth(apiKey))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("model", model, "messages", List.of(
                            Map.of("role", "system", "content", INSTRUCTIONS),
                            Map.of("role", "user", "content", mapper.writeValueAsString(input)))))
                    .retrieve().body(JsonNode.class);
            JsonNode content = response == null ? null : response.at("/choices/0/message/content");
            if (content == null || !content.isString() || content.asString().isBlank())
                throw new CustomException(ErrorCode.AI_GATEWAY_INVALID_RESPONSE);
            return content.asString().strip();
        } catch (ResourceAccessException | RestClientResponseException exception) {
            // Never log or propagate Gateway headers, request data, or error bodies.
            throw new CustomException(ErrorCode.AI_GATEWAY_UNAVAILABLE);
        } catch (RestClientException exception) {
            throw new CustomException(ErrorCode.AI_GATEWAY_INVALID_RESPONSE);
        } catch (IllegalArgumentException exception) {
            throw new CustomException(ErrorCode.AI_GATEWAY_UNAVAILABLE);
        }
    }
}
