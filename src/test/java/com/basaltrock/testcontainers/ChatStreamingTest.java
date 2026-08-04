package com.basaltrock.testcontainers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelWithResponseStreamRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelWithResponseStreamResponseHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.basaltrock.testcontainers.AwsBedrockUtils.createBedrockRuntimeAsyncClient;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "RUN_DOCKER_LLM_MODEL_TEST", matches = "true")
public class ChatStreamingTest extends BaseBasaltrockTest {

    static final Logger logger = LoggerFactory.getLogger(ChatStreamingTest.class);
    static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    public void testChatStreaming() throws Exception {
        logger.info("Testing chat streaming endpoint");
        logger.info("Connecting to RAG service at: {}", container.getBaseUrl());

        var textChunks = new ArrayList<String>();
        var handler = InvokeModelWithResponseStreamResponseHandler.builder()
                .onEventStream(stream -> stream.subscribe(event -> event.accept(
                        InvokeModelWithResponseStreamResponseHandler.Visitor.builder()
                                .onChunk(chunk -> {
                                    var chunkData = chunk.bytes().asUtf8String();
                                    try {
                                        var json = MAPPER.readTree(chunkData);
                                        if ("content_block_delta".equals(json.path("type").asText())) {
                                            var text = json.path("delta").path("text").asText();
                                            if (!text.isEmpty()) {
                                                textChunks.add(text);
                                            }
                                        }
                                    } catch (Exception e) {
                                        logger.warn("Failed to parse chunk", e);
                                    }
                                })
                                .build())))
                .build();

        try (var bedrockClient = createBedrockRuntimeAsyncClient(container)) {
            var requestBody = Map.of("messages", List.of(
                    Map.of("role", "user",
                            "content", "How much is two plus two?",
                            "system", "reply with text only, no numbers or digits")));

            var request = InvokeModelWithResponseStreamRequest.builder()
                    .modelId(container.getModelId())
                    .body(SdkBytes.fromUtf8String(MAPPER.writeValueAsString(requestBody)))
                    .build();

            bedrockClient.invokeModelWithResponseStream(request, handler).join();

            assertThat(String.join("", textChunks)).containsIgnoringCase("four");
        }
    }
}