package com.basaltrock.testcontainers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.Message;

import java.util.List;

import static com.basaltrock.testcontainers.AwsBedrockUtils.createBedrockRuntimeClient;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "RUN_DOCKER_LLM_MODEL_TEST", matches = "true")
public class ConverseTest extends BaseBasaltrockTest {

    static final Logger logger = LoggerFactory.getLogger(ConverseTest.class);

    @Test
    void testConverse() {
        try (var bedrockClient = createBedrockRuntimeClient(container)) {
            var request = ConverseRequest.builder()
                    .modelId(container.getModelId())
                    .messages(List.of(Message.builder()
                            .role(ConversationRole.USER)
                            .content(List.of(ContentBlock.fromText("How much is two plus two?")))
                            .build()))
                    .build();

            var response = bedrockClient.converse(request);

            var text = response.output().message().content().get(0).text();
            logger.info("Converse response: {}", text);
            assertThat(text).isNotBlank();
            assertThat(response.stopReason()).isNotNull();
            assertThat(response.usage()).isNotNull();
        }
    }
}