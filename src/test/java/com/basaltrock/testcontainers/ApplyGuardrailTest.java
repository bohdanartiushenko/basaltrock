package com.basaltrock.testcontainers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.bedrockruntime.model.ApplyGuardrailRequest;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailAction;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailContentSource;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailTextBlock;

import java.util.List;

import static com.basaltrock.testcontainers.AwsBedrockUtils.createBedrockRuntimeClient;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "RUN_DOCKER_LLM_MODEL_TEST", matches = "true")
public class ApplyGuardrailTest extends BaseBasaltrockTest {

    static final Logger logger = LoggerFactory.getLogger(ApplyGuardrailTest.class);

    @Test
    void testGuardrailAllowsSafeContent() {
        try (var bedrockClient = createBedrockRuntimeClient(container)) {
            var request = ApplyGuardrailRequest.builder()
                    .guardrailIdentifier("test-guardrail")
                    .guardrailVersion("1")
                    .source(GuardrailContentSource.INPUT)
                    .content(List.of(GuardrailContentBlock.fromText(
                            GuardrailTextBlock.builder().text("What is the weather today?").build())))
                    .build();

            var response = bedrockClient.applyGuardrail(request);

            logger.info("Action: {}", response.actionAsString());
            assertThat(response.action()).isEqualTo(GuardrailAction.NONE);
            assertThat(response.outputs()).isNotEmpty();
        }
    }
}