package com.basaltrock.testcontainers;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;

import java.net.URI;

public final class AwsBedrockUtils {
    /**
     * Creates a Bedrock Runtime async client configured to use this container.
     *
     * @return a configured BedrockRuntimeAsyncClient
     */
    public static BedrockRuntimeAsyncClient createBedrockRuntimeAsyncClient(ComposeBasaltrockContainer container) {
        return BedrockRuntimeAsyncClient.builder()
                .endpointOverride(URI.create(container.getBaseUrl()))
                .credentialsProvider(
                        StaticCredentialsProvider.create(AwsBasicCredentials.create("dummy", "dummy")))
                .region(Region.US_EAST_1)
                .build();
    }

    /**
     * Creates a Bedrock Agent Runtime client configured to use this container.
     *
     * @return a configured BedrockAgentRuntimeClient
     */
    public static BedrockAgentRuntimeClient createBedrockAgentRuntimeClient(ComposeBasaltrockContainer container) {
        return BedrockAgentRuntimeClient.builder()
                .endpointOverride(URI.create(container.getBaseUrl()))
                .credentialsProvider(
                        StaticCredentialsProvider.create(AwsBasicCredentials.create("dummy", "dummy")))
                .region(Region.US_EAST_1)
                .build();
    }
}
