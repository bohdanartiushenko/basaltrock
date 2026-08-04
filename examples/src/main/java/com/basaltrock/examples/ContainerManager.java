package com.basaltrock.examples;

import com.basaltrock.testcontainers.TempComposeBasaltrockContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;

public class ContainerManager {

    static final Logger logger = LoggerFactory.getLogger(ContainerManager.class);
    static TempComposeBasaltrockContainer container;
    static String baseUrl;

    public static void ensureContainerRunning(String dataFolder) throws Exception {
        var customUrl = System.getenv("BASALTROCK_URL");
        if (customUrl != null) {
            baseUrl = customUrl;
            if (isContainerRunning()) {
                logger.info("Container is already running at {}", baseUrl);
                return;
            }
            throw new RuntimeException("BASALTROCK_URL is set to " + customUrl + " but no container is running there");
        }

        if (container != null && isContainerRunning()) {
            logger.info("Container is already running at {}", baseUrl);
            return;
        }

        logger.info("Starting Basaltrock container...");
        var dataPath = new File(dataFolder).getAbsolutePath();
        logger.info("Using knowledge base data from: {}", dataPath);

        container = new TempComposeBasaltrockContainer(dataPath);
        container.start();
        baseUrl = container.getBaseUrl();

        logger.info("Container ready at {}", baseUrl);
    }

    public static String getBaseUrl() {
        return baseUrl;
    }

    static boolean isContainerRunning() {
        if (baseUrl == null) return false;
        try {
            var connection = (HttpURLConnection) new URL(baseUrl + "/health").openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(2000);
            connection.setReadTimeout(2000);
            return connection.getResponseCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }
}