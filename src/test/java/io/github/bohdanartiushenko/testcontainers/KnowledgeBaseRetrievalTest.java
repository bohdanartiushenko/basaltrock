package io.github.bohdanartiushenko.testcontainers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseQuery;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseRetrievalConfiguration;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseVectorSearchConfiguration;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveRequest;

import static io.github.bohdanartiushenko.testcontainers.AwsBedrockUtils.createBedrockAgentRuntimeClient;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "RUN_DOCKER_LLM_MODEL_TEST", matches = "true")
public class KnowledgeBaseRetrievalTest extends BaseBasaltrockTest {

    static final Logger logger = LoggerFactory.getLogger(KnowledgeBaseRetrievalTest.class);

    @Test
    void testRetrievalReturnsRelevantResults() {
        try (var agentClient = createBedrockAgentRuntimeClient(container)) {
            var request = RetrieveRequest.builder()
                    .knowledgeBaseId(container.getKnowledgeBaseId())
                    .retrievalQuery(KnowledgeBaseQuery.builder().text("What is copyright?").build())
                    .retrievalConfiguration(KnowledgeBaseRetrievalConfiguration.builder()
                            .vectorSearchConfiguration(KnowledgeBaseVectorSearchConfiguration.builder()
                                    .numberOfResults(5)
                                    .build())
                            .build())
                    .build();

            var response = agentClient.retrieve(request);
            var results = response.retrievalResults();

            assertThat(results).isNotEmpty();
            assertThat(results).allSatisfy(r -> assertThat(r.score()).isGreaterThan(0.0));
            assertThat(results).anySatisfy(r -> {
                assertThat(r.content().text()).containsIgnoringCase("copyright");
                assertThat(r.score()).isGreaterThan(0.4);
            });
        }
    }

    @Test
    void testNumberOfResultsIsRespected() {
        try (var agentClient = createBedrockAgentRuntimeClient(container)) {
            var request2 = RetrieveRequest.builder()
                    .knowledgeBaseId(container.getKnowledgeBaseId())
                    .retrievalQuery(KnowledgeBaseQuery.builder().text("What is copyright?").build())
                    .retrievalConfiguration(KnowledgeBaseRetrievalConfiguration.builder()
                            .vectorSearchConfiguration(KnowledgeBaseVectorSearchConfiguration.builder()
                                    .numberOfResults(2)
                                    .build())
                            .build())
                    .build();

            var response2 = agentClient.retrieve(request2);
            assertThat(response2.retrievalResults()).hasSizeLessThanOrEqualTo(2);

            var request10 = RetrieveRequest.builder()
                    .knowledgeBaseId(container.getKnowledgeBaseId())
                    .retrievalQuery(KnowledgeBaseQuery.builder().text("What is copyright?").build())
                    .retrievalConfiguration(KnowledgeBaseRetrievalConfiguration.builder()
                            .vectorSearchConfiguration(KnowledgeBaseVectorSearchConfiguration.builder()
                                    .numberOfResults(10)
                                    .build())
                            .build())
                    .build();

            var response10 = agentClient.retrieve(request10);
            assertThat(response10.retrievalResults().size()).isGreaterThanOrEqualTo(response2.retrievalResults().size());
        }
    }

    @Test
    void testResultsAreOrderedByScoreDescending() {
        try (var agentClient = createBedrockAgentRuntimeClient(container)) {
            var request = RetrieveRequest.builder()
                    .knowledgeBaseId(container.getKnowledgeBaseId())
                    .retrievalQuery(KnowledgeBaseQuery.builder().text("docker setup").build())
                    .retrievalConfiguration(KnowledgeBaseRetrievalConfiguration.builder()
                            .vectorSearchConfiguration(KnowledgeBaseVectorSearchConfiguration.builder()
                                    .numberOfResults(5)
                                    .build())
                            .build())
                    .build();

            var response = agentClient.retrieve(request);
            var results = response.retrievalResults();

            for (int i = 1; i < results.size(); i++) {
                assertThat(results.get(i - 1).score()).isGreaterThanOrEqualTo(results.get(i).score());
            }
        }
    }
}