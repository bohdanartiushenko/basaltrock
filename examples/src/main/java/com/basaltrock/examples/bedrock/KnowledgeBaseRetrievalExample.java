package com.basaltrock.examples.bedrock;

import com.basaltrock.examples.ContainerManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeClient;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseQuery;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseRetrievalConfiguration;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseVectorSearchConfiguration;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveRequest;

import java.net.URI;

public class KnowledgeBaseRetrievalExample {

    static final Logger logger = LoggerFactory.getLogger(KnowledgeBaseRetrievalExample.class);
    static final String KNOWLEDGE_BASE_ID = "basaltrock-knowledge-base-id";

    public static void main(String[] args) throws Exception {
        var query = args.length > 0 ? String.join(" ", args) : "Basaltrock";

        ContainerManager.ensureContainerRunning("src/main/resources/data");

        logger.info("\n=== Knowledge Base Retrieval Example ===");
        logger.info("Searching for: '{}'", query);

        searchKnowledgeBase(query);
    }

    static void searchKnowledgeBase(String query) {
        try (var agentClient = BedrockAgentRuntimeClient.builder()
                .endpointOverride(URI.create(ContainerManager.getBaseUrl()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("dummy", "dummy")))
                .region(Region.US_EAST_1)
                .build()) {

            var request = RetrieveRequest.builder()
                    .knowledgeBaseId(KNOWLEDGE_BASE_ID)
                    .retrievalQuery(KnowledgeBaseQuery.builder().text(query).build())
                    .retrievalConfiguration(KnowledgeBaseRetrievalConfiguration.builder()
                            .vectorSearchConfiguration(KnowledgeBaseVectorSearchConfiguration.builder()
                                    .numberOfResults(5)
                                    .build())
                            .build())
                    .build();

            var response = agentClient.retrieve(request);

            logger.info("\nFound {} results:\n", response.retrievalResults().size());

            response.retrievalResults().forEach(r -> {
                System.out.println("──────────────────────────────────────");
                System.out.println("Source: " + r.location().s3Location().uri());
                System.out.println("Score: " + String.format("%.4f", r.score()));
                System.out.println("\nContent:");
                System.out.println(r.content().text());
                System.out.println();
            });

            if (response.retrievalResults().isEmpty()) {
                logger.warn("No results found for query: '{}'", query);
            }
        }
    }
}