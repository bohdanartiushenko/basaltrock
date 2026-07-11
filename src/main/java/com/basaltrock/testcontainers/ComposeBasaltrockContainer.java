package com.basaltrock.testcontainers;

import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.File;
import java.time.Duration;

/**
 * Testcontainer using Docker Compose for the Basaltrock RAG service.
 * Provides access to both the Bedrock Runtime (chat) and Agent Runtime (knowledge base) clients.
 */
public class ComposeBasaltrockContainer extends ComposeContainer {

    static final String SERVICE_NAME = "api";
    static final int API_PORT = 8080;
    static final String KNOWLEDGE_BASE_ID = "basaltrock-knowledge-base-id";
    static final String DEFAULT_MODEL_ID = "anthropic.basaltrock-xyz-v1:0";

    public ComposeBasaltrockContainer(File composeFile) {
        this(composeFile, null);
    }

    public ComposeBasaltrockContainer(File composeFile, String dataFolderPath) {
        super(composeFile);

        if (dataFolderPath != null) {
            withEnv("DATA_FOLDER_PATH", dataFolderPath);
        }

        withExposedService(SERVICE_NAME, API_PORT, Wait.forHealthcheck())
                .withStartupTimeout(Duration.ofMinutes(10));
    }

    /**
     * Gets the base URL for the RAG service.
     * @return the base URL (e.g., "http://localhost:80")
     */
    public String getBaseUrl() {
        return "http://" + getServiceHost(SERVICE_NAME, API_PORT) + ":"
                + getServicePort(SERVICE_NAME, API_PORT);
    }

    /**
     * Gets the port the service is exposed on.
     * @return the mapped port
     */
    public Integer getPort() {
        return getServicePort(SERVICE_NAME, API_PORT);
    }

    /**
     * Gets the knowledge base ID used by this container.
     * @return the knowledge base ID
     */
    public String getKnowledgeBaseId() {
        return KNOWLEDGE_BASE_ID;
    }

    /**
     * Gets the default model ID.
     * @return the model ID
     */
    public String getModelId() {
        return DEFAULT_MODEL_ID;
    }


}
