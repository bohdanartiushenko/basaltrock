package com.basaltrock.testcontainers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamResponseHandler;
import software.amazon.awssdk.services.bedrockruntime.model.Message;

import java.util.ArrayList;
import java.util.List;

import static com.basaltrock.testcontainers.AwsBedrockUtils.createBedrockRuntimeAsyncClient;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "RUN_DOCKER_LLM_MODEL_TEST", matches = "true")
public class ConverseStreamTest extends BaseBasaltrockTest {

    static final Logger logger = LoggerFactory.getLogger(ConverseStreamTest.class);

    @Test
    void testConverseStream() {
        var textChunks = new ArrayList<String>();

        var handler = ConverseStreamResponseHandler.builder()
                .onEventStream(stream -> stream.subscribe(event -> event.accept(
                        ConverseStreamResponseHandler.Visitor.builder()
                                .onContentBlockDelta(delta -> {
                                    var text = delta.delta().text();
                                    if (text != null && !text.isEmpty()) {
                                        textChunks.add(text);
                                    }
                                })
                                .build())))
                .build();

        try (var bedrockClient = createBedrockRuntimeAsyncClient(container)) {
            var request = ConverseStreamRequest.builder()
                    .modelId(container.getModelId())
                    .messages(List.of(Message.builder()
                            .role(ConversationRole.USER)
                            .content(List.of(ContentBlock.fromText("How much is two plus two?")))
                            .build()))
                    .build();

            bedrockClient.converseStream(request, handler).join();

            var fullText = String.join("", textChunks);
            logger.info("ConverseStream response: {}", fullText);
            assertThat(fullText).isNotBlank();
            assertThat(textChunks.size()).isGreaterThan(1);
        }
    }
}