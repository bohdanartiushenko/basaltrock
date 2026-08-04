package com.basaltrock.testcontainers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.bedrockagentruntime.model.InvokeAgentRequest;
import software.amazon.awssdk.services.bedrockagentruntime.model.InvokeAgentResponseHandler;

import java.util.ArrayList;

import static com.basaltrock.testcontainers.AwsBedrockUtils.createBedrockAgentRuntimeAsyncClient;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "RUN_DOCKER_LLM_MODEL_TEST", matches = "true")
public class InvokeAgentTest extends BaseBasaltrockTest {

    static final Logger logger = LoggerFactory.getLogger(InvokeAgentTest.class);

    @Test
    void testInvokeAgent() {
        var chunks = new ArrayList<String>();

        var handler = InvokeAgentResponseHandler.builder()
                .onEventStream(stream -> stream.subscribe(event -> event.accept(
                        InvokeAgentResponseHandler.Visitor.builder()
                                .onChunk(chunk -> {
                                    var text = chunk.bytes().asUtf8String();
                                    if (text != null && !text.isEmpty()) {
                                        chunks.add(text);
                                    }
                                })
                                .build())))
                .build();

        try (var client = createBedrockAgentRuntimeAsyncClient(container)) {
            var request = InvokeAgentRequest.builder()
                    .agentId("test-agent")
                    .agentAliasId("test-alias")
                    .sessionId("test-session")
                    .inputText("What is 2+2?")
                    .build();

            client.invokeAgent(request, handler).join();

            var fullText = String.join("", chunks);
            logger.info("InvokeAgent response: {}", fullText);
            assertThat(fullText).containsIgnoringCase("4");
        }
    }
}