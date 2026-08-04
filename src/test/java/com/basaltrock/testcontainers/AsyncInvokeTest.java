package com.basaltrock.testcontainers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockruntime.model.*;

import java.util.List;
import java.util.Map;

import static com.basaltrock.testcontainers.AwsBedrockUtils.createBedrockRuntimeClient;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "RUN_DOCKER_LLM_MODEL_TEST", matches = "true")
public class AsyncInvokeTest extends BaseBasaltrockTest {

    static final Logger logger = LoggerFactory.getLogger(AsyncInvokeTest.class);

    @Test
    void testStartAndGetAsyncInvoke() throws InterruptedException {
        try (var client = createBedrockRuntimeClient(container)) {
            var modelInput = Document.mapBuilder()
                    .putDocument("messages", Document.listBuilder()
                            .addDocument(Document.mapBuilder()
                                    .putString("role", "user")
                                    .putString("content", "Say hello")
                                    .build())
                            .build())
                    .build();

            var startResponse = client.startAsyncInvoke(StartAsyncInvokeRequest.builder()
                    .modelId(container.getModelId())
                    .modelInput(modelInput)
                    .outputDataConfig(AsyncInvokeOutputDataConfig.builder()
                            .s3OutputDataConfig(AsyncInvokeS3OutputDataConfig.builder()
                                    .s3Uri("s3://dummy-bucket/output")
                                    .build())
                            .build())
                    .build());

            assertThat(startResponse.invocationArn()).isNotBlank();
            logger.info("Started async invocation: {}", startResponse.invocationArn());

            var status = AsyncInvokeStatus.IN_PROGRESS;
            for (int i = 0; i < 30 && status == AsyncInvokeStatus.IN_PROGRESS; i++) {
                Thread.sleep(1000);
                var getResponse = client.getAsyncInvoke(GetAsyncInvokeRequest.builder()
                        .invocationArn(startResponse.invocationArn())
                        .build());
                status = getResponse.status();
                logger.info("Status: {}", status);
            }

            assertThat(status).isEqualTo(AsyncInvokeStatus.COMPLETED);
        }
    }
}