package com.kkirikkiri.matching.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.ObjectMapper;
import com.kkirikkiri.matching.dto.AiProfileAnalysisInput;
import static com.kkirikkiri.survey.entity.PreferenceCriterionCode.*;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Explicit opt-in only: one real Gateway request with synthetic comparison data. */
@EnabledIfEnvironmentVariable(named = "RUN_AI_GATEWAY_LIVE_TEST", matches = "true")
class AiGatewayLiveTest {
    @Test
    void configuredSchoolGatewayReturnsAnalysis() throws Exception {
        var config = new Properties();
        if (Files.exists(Path.of(".env"))) {
            try (var reader = Files.newBufferedReader(Path.of(".env"))) { config.load(reader); }
        }
        var client = new AiGatewayClient(new ObjectMapper(), value(config, "AI_API_KEY"),
                value(config, "AI_BASE_URL"), value(config, "AI_MODEL"));
        String analysis = client.analyze(new AiProfileAnalysisInput(75, List.of(SMOKING), List.of(BEDTIME)));
        assertFalse(analysis.isBlank());
    }

    private String value(Properties config, String key) {
        return System.getenv().getOrDefault(key, config.getProperty(key, ""));
    }
}
