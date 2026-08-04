package com.basaltrock.examples.bedrock;

import com.basaltrock.examples.ContainerManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelWithResponseStreamRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelWithResponseStreamResponseHandler;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ChatStreamingExample {

    static final Logger logger = LoggerFactory.getLogger(ChatStreamingExample.class);
    static final ObjectMapper MAPPER = new ObjectMapper();
    static final String MODEL_ID = "anthropic.claude-3-sonnet-20240229-v1:0";

    public static void main(String[] args) throws Exception {
        var question = args.length > 0 ? String.join(" ", args) : "How much is two plus two?";

        ContainerManager.ensureContainerRunning("src/main/resources/data");

        logger.info("\n=== Chat Streaming Example ===");
        logger.info("Question: {}", question);

        streamChat(question);
    }

    static void streamChat(String question) throws Exception {
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
                                                System.out.print(text);
                                            }
                                        }
                                    } catch (Exception e) {
                                        logger.warn("Failed to parse chunk", e);
                                    }
                                })
                                .build())))
                .build();

        try (var bedrockClient = BedrockRuntimeAsyncClient.builder()
                .endpointOverride(URI.create(ContainerManager.getBaseUrl()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("dummy", "dummy")))
                .region(Region.US_EAST_1)
                .build()) {

            var requestBody = Map.of("messages", List.of(
                    Map.of("role", "user",
                            "content", question,
                            "system", "reply with text only")));

            var request = InvokeModelWithResponseStreamRequest.builder()
                    .modelId(MODEL_ID)
                    .body(SdkBytes.fromUtf8String(MAPPER.writeValueAsString(requestBody)))
                    .build();

            bedrockClient.invokeModelWithResponseStream(request, handler).join();
            System.out.println();
        }
    }
}