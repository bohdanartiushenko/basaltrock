package com.basaltrock.testcontainers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseRetrievalConfiguration;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseRetrieveAndGenerateConfiguration;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseVectorSearchConfiguration;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveAndGenerateConfiguration;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveAndGenerateInput;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveAndGenerateStreamRequest;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveAndGenerateStreamResponseHandler;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveAndGenerateType;

import java.util.ArrayList;

import static com.basaltrock.testcontainers.AwsBedrockUtils.createBedrockAgentRuntimeAsyncClient;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "RUN_DOCKER_LLM_MODEL_TEST", matches = "true")
public class RetrieveAndGenerateStreamTest extends BaseBasaltrockTest {

    static final Logger logger = LoggerFactory.getLogger(RetrieveAndGenerateStreamTest.class);

    @Test
    void testRetrieveAndGenerateStream() {
        var textChunks = new ArrayList<String>();

        var handler = RetrieveAndGenerateStreamResponseHandler.builder()
                .onEventStream(stream -> stream.subscribe(event -> event.accept(
                        RetrieveAndGenerateStreamResponseHandler.Visitor.builder()
                                .onOutput(output -> {
                                    var text = output.text();
                                    if (text != null && !text.isEmpty()) {
                                        textChunks.add(text);
                                    }
                                })
                                .build())))
                .build();

        try (var agentClient = createBedrockAgentRuntimeAsyncClient(container)) {
            var request = RetrieveAndGenerateStreamRequest.builder()
                    .input(RetrieveAndGenerateInput.builder().text("What is copyright?").build())
                    .retrieveAndGenerateConfiguration(RetrieveAndGenerateConfiguration.builder()
                            .type(RetrieveAndGenerateType.KNOWLEDGE_BASE)
                            .knowledgeBaseConfiguration(KnowledgeBaseRetrieveAndGenerateConfiguration.builder()
                                    .knowledgeBaseId(container.getKnowledgeBaseId())
                                    .modelArn(container.getModelId())
                                    .retrievalConfiguration(KnowledgeBaseRetrievalConfiguration.builder()
                                            .vectorSearchConfiguration(KnowledgeBaseVectorSearchConfiguration.builder()
                                                    .numberOfResults(5)
                                                    .build())
                                            .build())
                                    .build())
                            .build())
                    .build();

            agentClient.retrieveAndGenerateStream(request, handler).join();

            var fullText = String.join("", textChunks);
            logger.info("RetrieveAndGenerateStream response: {}", fullText);
            assertThat(fullText).isNotBlank();
        }
    }
}