package com.basaltrock.testcontainers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.bedrockagentruntime.model.InputPrompt;
import software.amazon.awssdk.services.bedrockagentruntime.model.OptimizePromptRequest;
import software.amazon.awssdk.services.bedrockagentruntime.model.OptimizePromptResponseHandler;
import software.amazon.awssdk.services.bedrockagentruntime.model.TextPrompt;

import java.util.ArrayList;

import static com.basaltrock.testcontainers.AwsBedrockUtils.createBedrockAgentRuntimeAsyncClient;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "RUN_DOCKER_LLM_MODEL_TEST", matches = "true")
public class OptimizePromptTest extends BaseBasaltrockTest {

    static final Logger logger = LoggerFactory.getLogger(OptimizePromptTest.class);

    @Test
    void testOptimizePrompt() {
        var optimizedTexts = new ArrayList<String>();

        var handler = OptimizePromptResponseHandler.builder()
                .onEventStream(stream -> stream.subscribe(event -> event.accept(
                        OptimizePromptResponseHandler.Visitor.builder()
                                .onOptimizedPromptEvent(e -> {
                                    var text = e.optimizedPrompt().textPrompt().text();
                                    if (text != null && !text.isEmpty()) {
                                        optimizedTexts.add(text);
                                    }
                                })
                                .build())))
                .build();

        try (var agentClient = createBedrockAgentRuntimeAsyncClient(container)) {
            var request = OptimizePromptRequest.builder()
                    .input(InputPrompt.fromTextPrompt(TextPrompt.builder()
                            .text("tell me about dogs")
                            .build()))
                    .targetModelId(container.getModelId())
                    .build();

            agentClient.optimizePrompt(request, handler).join();

            assertThat(optimizedTexts).isNotEmpty();
            var result = optimizedTexts.get(0);
            logger.info("Optimized prompt: {}", result);
            assertThat(result).isNotBlank();
            assertThat(result.length()).isGreaterThan("tell me about dogs".length());
        }
    }
}