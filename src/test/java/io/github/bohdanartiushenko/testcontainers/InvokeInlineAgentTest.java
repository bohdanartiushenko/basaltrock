package io.github.bohdanartiushenko.testcontainers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.bedrockagentruntime.model.InvokeInlineAgentRequest;
import software.amazon.awssdk.services.bedrockagentruntime.model.InvokeInlineAgentResponseHandler;

import java.util.ArrayList;

import static io.github.bohdanartiushenko.testcontainers.AwsBedrockUtils.createBedrockAgentRuntimeAsyncClient;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "RUN_DOCKER_LLM_MODEL_TEST", matches = "true")
public class InvokeInlineAgentTest extends BaseBasaltrockTest {

    static final Logger logger = LoggerFactory.getLogger(InvokeInlineAgentTest.class);

    @Test
    void testInvokeInlineAgent() {
        var chunks = new ArrayList<String>();

        var handler = InvokeInlineAgentResponseHandler.builder()
                .onEventStream(stream -> stream.subscribe(event -> event.accept(
                        InvokeInlineAgentResponseHandler.Visitor.builder()
                                .onChunk(chunk -> {
                                    var text = chunk.bytes().asUtf8String();
                                    if (text != null && !text.isEmpty()) {
                                        chunks.add(text);
                                    }
                                })
                                .build())))
                .build();

        try (var client = createBedrockAgentRuntimeAsyncClient(container)) {
            var request = InvokeInlineAgentRequest.builder()
                    .sessionId("test-session")
                    .foundationModel("anthropic.claude-v2")
                    .instruction("You are a calculator. Reply with ONLY the numeric result.")
                    .inputText("What is 2+2?")
                    .build();

            client.invokeInlineAgent(request, handler).join();

            var fullText = String.join("", chunks);
            logger.info("InvokeInlineAgent response: {}", fullText);
            assertThat(fullText).containsIgnoringCase("4");
        }
    }
}