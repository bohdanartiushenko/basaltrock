package io.github.bohdanartiushenko.testcontainers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.model.BidirectionalInputPayloadPart;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelWithBidirectionalStreamInput;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelWithBidirectionalStreamRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelWithBidirectionalStreamResponseHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.github.bohdanartiushenko.testcontainers.AwsBedrockUtils.createBedrockRuntimeAsyncClient;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "RUN_DOCKER_LLM_MODEL_TEST", matches = "true")
public class BidirectionalStreamTest extends BaseBasaltrockTest {

    static final Logger logger = LoggerFactory.getLogger(BidirectionalStreamTest.class);
    static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void testBidirectionalStream() throws Exception {
        var textChunks = new ArrayList<String>();

        var handler = InvokeModelWithBidirectionalStreamResponseHandler.builder()
                .onEventStream(stream -> stream.subscribe(event -> event.accept(
                        InvokeModelWithBidirectionalStreamResponseHandler.Visitor.builder()
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
                    Map.of("role", "user", "content", "How much is two plus two?")));

            var inputPayload = BidirectionalInputPayloadPart.builder()
                    .bytes(SdkBytes.fromUtf8String(MAPPER.writeValueAsString(requestBody)))
                    .build();

            var request = InvokeModelWithBidirectionalStreamRequest.builder()
                    .modelId(container.getModelId())
                    .build();

            Publisher<InvokeModelWithBidirectionalStreamInput> inputStream = subscriber -> {
                subscriber.onSubscribe(new Subscription() {
                    @Override
                    public void request(long n) {
                        subscriber.onNext(inputPayload);
                        subscriber.onComplete();
                    }

                    @Override
                    public void cancel() {}
                });
            };

            bedrockClient.invokeModelWithBidirectionalStream(request, inputStream, handler).join();

            var fullText = String.join("", textChunks);
            logger.info("BidirectionalStream response: {}", fullText);
            assertThat(fullText).isNotBlank();
        }
    }
}