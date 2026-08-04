package com.basaltrock.examples;

import com.basaltrock.testcontainers.TempComposeBasaltrockContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Manages the Basaltrock container lifecycle for examples.
 */
public class ContainerManager {

    static final Logger logger = LoggerFactory.getLogger(ContainerManager.class);
    static TempComposeBasaltrockContainer container;
    static String baseUrl;

    /**
     * Ensures the container is running. Starts it if needed.
     * @param dataFolder path to folder containing knowledge base files (txt, html, md), or null for default test data
     */
    public static void ensureContainerRunning(String dataFolder) throws Exception {
        // Check if custom URL is set
        String customUrl = System.getenv("BASALTROCK_URL");
        if (customUrl != null) {
            baseUrl = customUrl;
            if (isContainerRunning()) {
                logger.info("✓ Container is already running at {}", baseUrl);
                return;
            }
            throw new RuntimeException("BASALTROCK_URL is set to " + customUrl + " but no container is running there");
        }

        // Check if we already started a container
        if (container != null && isContainerRunning()) {
            logger.info("✓ Container is already running at {}", baseUrl);
            return;
        }

        logger.info("Starting Basaltrock container...");
        startContainer(dataFolder);
    }

    /**
     * Gets the base URL of the container.
     */
    public static String getBaseUrl() {
        return baseUrl;
    }

    static boolean isContainerRunning() {
        if (baseUrl == null) {
            return false;
        }
        try {
            URL url = new URL(baseUrl + "/health");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(2000);
            connection.setReadTimeout(2000);
            int responseCode = connection.getResponseCode();
            return responseCode == 200;
        } catch (Exception e) {
            return false;
        }
    }

    static void startContainer(String dataFolder) throws Exception {
        var dataPath = new File(dataFolder).getAbsolutePath();

        logger.info("Using knowledge base data from: {}", dataPath);

        container = new TempComposeBasaltrockContainer(dataPath);
        container.start();

        baseUrl = container.getBaseUrl();

        logger.info("Container started at {}, waiting for API to be ready...", baseUrl);

        waitForApiReady();

        logger.info("✓ API is ready at {}", baseUrl);
    }

    static void waitForApiReady() {
        int maxAttempts = 60;  // 60 seconds
        int attempt = 0;

        while (attempt < maxAttempts) {
            if (isContainerRunning()) {
                logger.info("✓ API responded successfully");
                return;
            }

            attempt++;
            if (attempt % 10 == 0) {
                logger.info("Still waiting for API... ({}/{})", attempt, maxAttempts);
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for API", e);
            }
        }

        throw new RuntimeException("API did not become ready after " + maxAttempts + " seconds");
    }
}
