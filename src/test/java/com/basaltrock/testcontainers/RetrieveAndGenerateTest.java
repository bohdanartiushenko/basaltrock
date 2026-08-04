package com.basaltrock.testcontainers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.bedrockagentruntime.model.GenerationConfiguration;
import software.amazon.awssdk.services.bedrockagentruntime.model.InferenceConfig;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseRetrievalConfiguration;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseRetrieveAndGenerateConfiguration;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseVectorSearchConfiguration;
import software.amazon.awssdk.services.bedrockagentruntime.model.PromptTemplate;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveAndGenerateConfiguration;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveAndGenerateInput;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveAndGenerateRequest;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveAndGenerateType;
import software.amazon.awssdk.services.bedrockagentruntime.model.TextInferenceConfig;

import static com.basaltrock.testcontainers.AwsBedrockUtils.createBedrockAgentRuntimeClient;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "RUN_DOCKER_LLM_MODEL_TEST", matches = "true")
public class RetrieveAndGenerateTest extends BaseBasaltrockTest {

    static final Logger logger = LoggerFactory.getLogger(RetrieveAndGenerateTest.class);

    @Test
    void testRetrieveAndGenerate() {
        try (var agentClient = createBedrockAgentRuntimeClient(container)) {
            var request = RetrieveAndGenerateRequest.builder()
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

            var response = agentClient.retrieveAndGenerate(request);

            assertThat(response.output().text()).isNotBlank();
            assertThat(response.citations()).isNotEmpty();
            assertThat(response.citations()).allSatisfy(c -> {
                assertThat(c.retrievedReferences()).isNotEmpty();
                assertThat(c.retrievedReferences().get(0).content().text()).isNotBlank();
            });
            logger.info("Generated answer: {}", response.output().text());
        }
    }

    @Test
    void testRetrieveAndGenerateWithGenerationConfig() {
        try (var agentClient = createBedrockAgentRuntimeClient(container)) {
            var request = RetrieveAndGenerateRequest.builder()
                    .input(RetrieveAndGenerateInput.builder().text("What is copyright?").build())
                    .retrieveAndGenerateConfiguration(RetrieveAndGenerateConfiguration.builder()
                            .type(RetrieveAndGenerateType.KNOWLEDGE_BASE)
                            .knowledgeBaseConfiguration(KnowledgeBaseRetrieveAndGenerateConfiguration.builder()
                                    .knowledgeBaseId(container.getKnowledgeBaseId())
                                    .modelArn(container.getModelId())
                                    .retrievalConfiguration(KnowledgeBaseRetrievalConfiguration.builder()
                                            .vectorSearchConfiguration(KnowledgeBaseVectorSearchConfiguration.builder()
                                                    .numberOfResults(2)
                                                    .build())
                                            .build())
                                    .generationConfiguration(GenerationConfiguration.builder()
                                            .inferenceConfig(InferenceConfig.builder()
                                                    .textInferenceConfig(TextInferenceConfig.builder()
                                                            .temperature(0.1f)
                                                            .maxTokens(100)
                                                            .build())
                                                    .build())
                                            .promptTemplate(PromptTemplate.builder()
                                                    .textPromptTemplate("Answer only in German. Context:\n{context}")
                                                    .build())
                                            .build())
                                    .build())
                            .build())
                    .build();

            var response = agentClient.retrieveAndGenerate(request);

            assertThat(response.output().text()).isNotBlank();
            assertThat(response.citations()).hasSizeLessThanOrEqualTo(2);
            logger.info("Generated answer with config: {}", response.output().text());
        }
    }

    @Test
    void testNumberOfResultsLimitsCitations() {
        try (var agentClient = createBedrockAgentRuntimeClient(container)) {
            var request1 = RetrieveAndGenerateRequest.builder()
                    .input(RetrieveAndGenerateInput.builder().text("What is copyright?").build())
                    .retrieveAndGenerateConfiguration(RetrieveAndGenerateConfiguration.builder()
                            .type(RetrieveAndGenerateType.KNOWLEDGE_BASE)
                            .knowledgeBaseConfiguration(KnowledgeBaseRetrieveAndGenerateConfiguration.builder()
                                    .knowledgeBaseId(container.getKnowledgeBaseId())
                                    .modelArn(container.getModelId())
                                    .retrievalConfiguration(KnowledgeBaseRetrievalConfiguration.builder()
                                            .vectorSearchConfiguration(KnowledgeBaseVectorSearchConfiguration.builder()
                                                    .numberOfResults(1)
                                                    .build())
                                            .build())
                                    .build())
                            .build())
                    .build();

            var response1 = agentClient.retrieveAndGenerate(request1);
            assertThat(response1.citations()).hasSizeLessThanOrEqualTo(1);

            var request5 = RetrieveAndGenerateRequest.builder()
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

            var response5 = agentClient.retrieveAndGenerate(request5);
            assertThat(response5.citations().size()).isGreaterThanOrEqualTo(response1.citations().size());
        }
    }
}