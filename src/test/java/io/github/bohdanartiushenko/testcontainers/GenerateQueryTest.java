package io.github.bohdanartiushenko.testcontainers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.bedrockagentruntime.model.GenerateQueryRequest;
import software.amazon.awssdk.services.bedrockagentruntime.model.QueryGenerationInput;
import software.amazon.awssdk.services.bedrockagentruntime.model.QueryTransformationMode;
import software.amazon.awssdk.services.bedrockagentruntime.model.TransformationConfiguration;

import static io.github.bohdanartiushenko.testcontainers.AwsBedrockUtils.createBedrockAgentRuntimeClient;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "RUN_DOCKER_LLM_MODEL_TEST", matches = "true")
public class GenerateQueryTest extends BaseBasaltrockTest {

    static final Logger logger = LoggerFactory.getLogger(GenerateQueryTest.class);

    @Test
    void testGenerateQuery() {
        try (var client = createBedrockAgentRuntimeClient(container)) {
            var response = client.generateQuery(GenerateQueryRequest.builder()
                    .queryGenerationInput(QueryGenerationInput.builder()
                            .text("Show me all users who signed up in the last 30 days")
                            .type("TEXT")
                            .build())
                    .transformationConfiguration(TransformationConfiguration.builder()
                            .mode(QueryTransformationMode.TEXT_TO_SQL)
                            .build())
                    .build());

            assertThat(response.queries()).isNotEmpty();
            var query = response.queries().get(0).sql();
            logger.info("Generated query: {}", query);
            assertThat(query).isNotBlank();
        }
    }
}