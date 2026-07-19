package com.basaltrock.testcontainers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseTokensRequest;
import software.amazon.awssdk.services.bedrockruntime.model.CountTokensInput;
import software.amazon.awssdk.services.bedrockruntime.model.CountTokensRequest;
import software.amazon.awssdk.services.bedrockruntime.model.Message;

import java.util.List;

import static com.basaltrock.testcontainers.AwsBedrockUtils.createBedrockRuntimeClient;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "RUN_DOCKER_LLM_MODEL_TEST", matches = "true")
public class CountTokensTest extends BaseBasaltrockTest {

    static final Logger logger = LoggerFactory.getLogger(CountTokensTest.class);

    @Test
    void testCountTokens() {
        try (var bedrockClient = createBedrockRuntimeClient(container)) {
            var request = CountTokensRequest.builder()
                    .modelId(container.getModelId())
                    .input(CountTokensInput.fromConverse(ConverseTokensRequest.builder()
                            .messages(List.of(Message.builder()
                                    .role(ConversationRole.USER)
                                    .content(List.of(ContentBlock.fromText("Hello, how are you today?")))
                                    .build()))
                            .build()))
                    .build();

            var response = bedrockClient.countTokens(request);

            logger.info("Token count: {}", response.inputTokens());
            assertThat(response.inputTokens()).isGreaterThan(0);
        }
    }

    @Test
    void testLongerTextProducesMoreTokens() {
        try (var bedrockClient = createBedrockRuntimeClient(container)) {
            var shortRequest = CountTokensRequest.builder()
                    .modelId(container.getModelId())
                    .input(CountTokensInput.fromConverse(ConverseTokensRequest.builder()
                            .messages(List.of(Message.builder()
                                    .role(ConversationRole.USER)
                                    .content(List.of(ContentBlock.fromText("Hi")))
                                    .build()))
                            .build()))
                    .build();

            var longRequest = CountTokensRequest.builder()
                    .modelId(container.getModelId())
                    .input(CountTokensInput.fromConverse(ConverseTokensRequest.builder()
                            .messages(List.of(Message.builder()
                                    .role(ConversationRole.USER)
                                    .content(List.of(ContentBlock.fromText("This is a much longer message that should produce significantly more tokens than the short one above.")))
                                    .build()))
                            .build()))
                    .build();

            var shortCount = bedrockClient.countTokens(shortRequest).inputTokens();
            var longCount = bedrockClient.countTokens(longRequest).inputTokens();

            logger.info("Short: {} tokens, Long: {} tokens", shortCount, longCount);
            assertThat(longCount).isGreaterThan(shortCount);
        }
    }
}