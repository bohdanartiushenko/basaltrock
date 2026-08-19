package com.basaltrock.testcontainers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.bedrockagentruntime.model.RerankDocument;
import software.amazon.awssdk.services.bedrockagentruntime.model.RerankDocumentType;
import software.amazon.awssdk.services.bedrockagentruntime.model.RerankQuery;
import software.amazon.awssdk.services.bedrockagentruntime.model.RerankQueryContentType;
import software.amazon.awssdk.services.bedrockagentruntime.model.RerankRequest;
import software.amazon.awssdk.services.bedrockagentruntime.model.RerankSource;
import software.amazon.awssdk.services.bedrockagentruntime.model.RerankSourceType;
import software.amazon.awssdk.services.bedrockagentruntime.model.RerankTextDocument;
import software.amazon.awssdk.services.bedrockagentruntime.model.RerankingConfiguration;

import java.util.List;

import static com.basaltrock.testcontainers.AwsBedrockUtils.createBedrockAgentRuntimeClient;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "RUN_DOCKER_LLM_MODEL_TEST", matches = "true")
public class RerankTest extends BaseBasaltrockTest {

    static final Logger logger = LoggerFactory.getLogger(RerankTest.class);

    @Test
    void testRerankOrdersByRelevance() {
        try (var agentClient = createBedrockAgentRuntimeClient(container)) {
            var request = RerankRequest.builder()
                    .queries(List.of(RerankQuery.builder()
                            .type(RerankQueryContentType.TEXT)
                            .textQuery(RerankTextDocument.builder().text("machine learning algorithms").build())
                            .build()))
                    .sources(List.of(
                            RerankSource.builder()
                                    .type(RerankSourceType.INLINE)
                                    .inlineDocumentSource(RerankDocument.builder()
                                            .type(RerankDocumentType.TEXT)
                                            .textDocument(RerankTextDocument.builder()
                                                    .text("The weather is sunny today with clear skies.")
                                                    .build())
                                            .build())
                                    .build(),
                            RerankSource.builder()
                                    .type(RerankSourceType.INLINE)
                                    .inlineDocumentSource(RerankDocument.builder()
                                            .type(RerankDocumentType.TEXT)
                                            .textDocument(RerankTextDocument.builder()
                                                    .text("Neural networks and deep learning are types of machine learning.")
                                                    .build())
                                            .build())
                                    .build(),
                            RerankSource.builder()
                                    .type(RerankSourceType.INLINE)
                                    .inlineDocumentSource(RerankDocument.builder()
                                            .type(RerankDocumentType.TEXT)
                                            .textDocument(RerankTextDocument.builder()
                                                    .text("Cooking pasta requires boiling water first.")
                                                    .build())
                                            .build())
                                    .build()))
                    .build();

            var response = agentClient.rerank(request);
            var results = response.results();

            logger.info("Rerank results:");
            results.forEach(r -> logger.info("  index={} score={} text={}",
                    r.index(), r.relevanceScore(), r.document().textDocument().text().substring(0, 30)));

            assertThat(results).hasSize(3);
            assertThat(results.get(0).relevanceScore()).isGreaterThan(results.get(1).relevanceScore());
            assertThat(results.get(0).document().textDocument().text()).contains("machine learning");
        }
    }
}