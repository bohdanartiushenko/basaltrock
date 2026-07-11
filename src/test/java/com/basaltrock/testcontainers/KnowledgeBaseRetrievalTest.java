/*
 * Copyright (c) 2026 Two Sigma Investments, LP
 * All Rights Reserved
 *
 * THIS IS UNPUBLISHED PROPRIETARY SOURCE CODE OF
 * Two Sigma Investments, LLC.
 *
 * The copyright notice above does not evidence any
 * actual or intended publication of such source code.
 */
package com.basaltrock.testcontainers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseQuery;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseRetrievalConfiguration;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseVectorSearchConfiguration;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveRequest;

import java.util.stream.Collectors;

import static com.basaltrock.testcontainers.AwsBedrockUtils.createBedrockAgentRuntimeClient;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the Basaltrock RAG service knowledge base retrieval endpoint:
 * - POST /knowledgebases/{knowledge_base_id}/retrieve
 */
@EnabledIfSystemProperty(named = "RUN_DOCKER_LLM_MODEL_TEST", matches = "true")
public class KnowledgeBaseRetrievalTest extends BaseBasaltrockTest {

    static final Logger logger = LoggerFactory.getLogger(KnowledgeBaseRetrievalTest.class);

    @Test
    public void testKnowledgeBaseRetrieval() throws Exception {
        logger.info("Testing knowledge base retrieval endpoint");
        logger.info("Connecting to RAG service at: {}", container.getBaseUrl());

        try (var agentClient = createBedrockAgentRuntimeClient(container)) {

            // Build retrieval request
            var request = RetrieveRequest.builder()
                    .knowledgeBaseId(container.getKnowledgeBaseId())
                    .retrievalQuery(KnowledgeBaseQuery.builder().text("What is copyright?").build())
                    .retrievalConfiguration(KnowledgeBaseRetrievalConfiguration.builder()
                            .vectorSearchConfiguration(KnowledgeBaseVectorSearchConfiguration.builder()
                                    .numberOfResults(5)
                                    .build())
                            .build())
                    .build();

            // when
            var response = agentClient.retrieve(request);

            // then
            assertThat(response.retrievalResults().stream()
                    .filter(r -> r.location().s3Location().uri().contains("README.md"))
                    .filter(r -> r.content().text().contains("opyright"))
                    .filter(r -> r.score() > 0.4)
                    .collect(Collectors.toSet()))
                    .isNotEmpty();
        }
    }
}