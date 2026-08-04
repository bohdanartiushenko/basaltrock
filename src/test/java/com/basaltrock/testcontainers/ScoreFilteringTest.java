package com.basaltrock.testcontainers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseQuery;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseRetrievalConfiguration;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseVectorSearchConfiguration;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveRequest;

import static com.basaltrock.testcontainers.AwsBedrockUtils.createBedrockAgentRuntimeClient;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "RUN_DOCKER_LLM_MODEL_TEST", matches = "true")
public class ScoreFilteringTest extends BaseBasaltrockTest {

    static final Logger logger = LoggerFactory.getLogger(ScoreFilteringTest.class);

    @Test
    void testIrrelevantQueryProducesLowScores() {
        try (var agentClient = createBedrockAgentRuntimeClient(container)) {
            var request = RetrieveRequest.builder()
                    .knowledgeBaseId(container.getKnowledgeBaseId())
                    .retrievalQuery(KnowledgeBaseQuery.builder()
                            .text("quantum chromodynamics and strange quark interactions")
                            .build())
                    .retrievalConfiguration(KnowledgeBaseRetrievalConfiguration.builder()
                            .vectorSearchConfiguration(KnowledgeBaseVectorSearchConfiguration.builder()
                                    .numberOfResults(5)
                                    .build())
                            .build())
                    .build();

            var response = agentClient.retrieve(request);
            var results = response.retrievalResults();

            assertThat(results).isNotEmpty();
            assertThat(results).allSatisfy(r -> {
                logger.info("Irrelevant query score: {} for {}", r.score(), r.location().s3Location().uri());
                assertThat(r.score()).isLessThan(0.8);
            });
        }
    }

    @Test
    void testRelevantQueryScoresHigherThanIrrelevant() {
        try (var agentClient = createBedrockAgentRuntimeClient(container)) {
            var relevantRequest = RetrieveRequest.builder()
                    .knowledgeBaseId(container.getKnowledgeBaseId())
                    .retrievalQuery(KnowledgeBaseQuery.builder()
                            .text("MIT License copyright permission")
                            .build())
                    .retrievalConfiguration(KnowledgeBaseRetrievalConfiguration.builder()
                            .vectorSearchConfiguration(KnowledgeBaseVectorSearchConfiguration.builder()
                                    .numberOfResults(3)
                                    .build())
                            .build())
                    .build();

            var irrelevantRequest = RetrieveRequest.builder()
                    .knowledgeBaseId(container.getKnowledgeBaseId())
                    .retrievalQuery(KnowledgeBaseQuery.builder()
                            .text("quantum chromodynamics and strange quark interactions")
                            .build())
                    .retrievalConfiguration(KnowledgeBaseRetrievalConfiguration.builder()
                            .vectorSearchConfiguration(KnowledgeBaseVectorSearchConfiguration.builder()
                                    .numberOfResults(3)
                                    .build())
                            .build())
                    .build();

            var relevantResults = agentClient.retrieve(relevantRequest).retrievalResults();
            var irrelevantResults = agentClient.retrieve(irrelevantRequest).retrievalResults();

            assertThat(relevantResults.get(0).score())
                    .isGreaterThan(irrelevantResults.get(0).score());
        }
    }

}