package com.basaltrock.testcontainers;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;

public abstract class BaseBasaltrockTest {

    static final Logger logger = LoggerFactory.getLogger(BaseBasaltrockTest.class);
    protected static TempComposeBasaltrockContainer container;

    @BeforeAll
    static void prepareDockerContext() throws IOException, URISyntaxException {
        container = new TempComposeBasaltrockContainer("src/test/resources/data");
        container.start();
        logger.info("Container started at: {}", container.getBaseUrl());
    }

    @AfterAll
    static void stopContainer() {
        container.stop();
    }
}