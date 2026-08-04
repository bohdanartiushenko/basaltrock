package com.basaltrock.testcontainers;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;

import java.net.URI;

public final class AwsBedrockUtils {

    public static BedrockRuntimeAsyncClient createBedrockRuntimeAsyncClient(ComposeBasaltrockContainer container) {
        return BedrockRuntimeAsyncClient.builder()
                .endpointOverride(URI.create(container.getBaseUrl()))
                .credentialsProvider(
                        StaticCredentialsProvider.create(AwsBasicCredentials.create("dummy", "dummy")))
                .region(Region.US_EAST_1)
                .build();
    }

    public static BedrockAgentRuntimeClient createBedrockAgentRuntimeClient(ComposeBasaltrockContainer container) {
        return BedrockAgentRuntimeClient.builder()
                .endpointOverride(URI.create(container.getBaseUrl()))
                .credentialsProvider(
                        StaticCredentialsProvider.create(AwsBasicCredentials.create("dummy", "dummy")))
                .region(Region.US_EAST_1)
                .build();
    }
}