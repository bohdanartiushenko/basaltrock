package com.basaltrock.testcontainers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.Message;

import java.util.List;

import static com.basaltrock.testcontainers.AwsBedrockUtils.createBedrockRuntimeClient;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "RUN_DOCKER_LLM_MODEL_TEST", matches = "true")
public class DeterminismTest extends BaseBasaltrockTest {

    static final Logger logger = LoggerFactory.getLogger(DeterminismTest.class);

    @Test
    void testZeroTemperatureProducesDeterministicOutput() {
        try (var bedrockClient = createBedrockRuntimeClient(container)) {
            var request = ConverseRequest.builder()
                    .modelId(container.getModelId())
                    .messages(List.of(Message.builder()
                            .role(ConversationRole.USER)
                            .content(List.of(ContentBlock.fromText("What is 2+2? Reply with just the number.")))
                            .build()))
                    .inferenceConfig(InferenceConfiguration.builder()
                            .temperature(0.0f)
                            .maxTokens(10)
                            .build())
                    .build();

            var response1 = bedrockClient.converse(request).output().message().content().get(0).text();
            var response2 = bedrockClient.converse(request).output().message().content().get(0).text();

            logger.info("Response 1: '{}', Response 2: '{}'", response1, response2);
            assertThat(response1).isEqualTo(response2);
        }
    }
}