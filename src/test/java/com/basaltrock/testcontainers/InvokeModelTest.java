package com.basaltrock.testcontainers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;

import java.util.List;
import java.util.Map;

import static com.basaltrock.testcontainers.AwsBedrockUtils.createBedrockRuntimeClient;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "RUN_DOCKER_LLM_MODEL_TEST", matches = "true")
public class InvokeModelTest extends BaseBasaltrockTest {

    static final Logger logger = LoggerFactory.getLogger(InvokeModelTest.class);
    static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void testInvokeModel() throws Exception {
        try (var bedrockClient = createBedrockRuntimeClient(container)) {
            var requestBody = Map.of(
                    "messages", List.of(Map.of("role", "user", "content", "How much is two plus two?")),
                    "max_tokens", 100
            );

            var request = InvokeModelRequest.builder()
                    .modelId(container.getModelId())
                    .body(SdkBytes.fromUtf8String(MAPPER.writeValueAsString(requestBody)))
                    .build();

            var response = bedrockClient.invokeModel(request);
            var responseJson = MAPPER.readTree(response.body().asUtf8String());

            logger.info("Response: {}", responseJson);
            var text = responseJson.path("content").get(0).path("text").asText();
            assertThat(text).isNotBlank();
        }
    }
}