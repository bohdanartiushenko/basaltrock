package io.github.bohdanartiushenko.testcontainers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockruntime.model.*;

import static io.github.bohdanartiushenko.testcontainers.AwsBedrockUtils.createBedrockRuntimeClient;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "RUN_DOCKER_LLM_MODEL_TEST", matches = "true")
public class ListAsyncInvokesTest extends BaseBasaltrockTest {

    @Test
    void testListAsyncInvokes() {
        try (var client = createBedrockRuntimeClient(container)) {
            var modelInput = Document.mapBuilder()
                    .putDocument("messages", Document.listBuilder()
                            .addDocument(Document.mapBuilder()
                                    .putString("role", "user")
                                    .putString("content", "Say hi")
                                    .build())
                            .build())
                    .build();

            client.startAsyncInvoke(StartAsyncInvokeRequest.builder()
                    .modelId(container.getModelId())
                    .modelInput(modelInput)
                    .outputDataConfig(AsyncInvokeOutputDataConfig.builder()
                            .s3OutputDataConfig(AsyncInvokeS3OutputDataConfig.builder()
                                    .s3Uri("s3://dummy/output")
                                    .build())
                            .build())
                    .build());

            var response = client.listAsyncInvokes(ListAsyncInvokesRequest.builder().build());

            assertThat(response.asyncInvokeSummaries()).isNotEmpty();
            var summary = response.asyncInvokeSummaries().get(0);
            assertThat(summary.invocationArn()).isNotBlank();
            assertThat(summary.status()).isNotNull();
        }
    }

    @Test
    void testListAsyncInvokesWithStatusFilter() {
        try (var client = createBedrockRuntimeClient(container)) {
            var response = client.listAsyncInvokes(ListAsyncInvokesRequest.builder()
                    .statusEquals(AsyncInvokeStatus.IN_PROGRESS)
                    .build());

            assertThat(response.asyncInvokeSummaries()).isNotNull();
        }
    }
}