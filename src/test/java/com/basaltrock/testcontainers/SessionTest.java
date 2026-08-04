package com.basaltrock.testcontainers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import software.amazon.awssdk.services.bedrockagentruntime.model.CreateSessionRequest;
import software.amazon.awssdk.services.bedrockagentruntime.model.DeleteSessionRequest;
import software.amazon.awssdk.services.bedrockagentruntime.model.GetSessionRequest;

import static com.basaltrock.testcontainers.AwsBedrockUtils.createBedrockAgentRuntimeClient;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "RUN_DOCKER_LLM_MODEL_TEST", matches = "true")
public class SessionTest extends BaseBasaltrockTest {

    @Test
    void testCreateGetDeleteSession() {
        try (var client = createBedrockAgentRuntimeClient(container)) {
            var createResponse = client.createSession(CreateSessionRequest.builder().build());
            assertThat(createResponse.sessionId()).isNotBlank();

            var sessionId = createResponse.sessionId();

            var getResponse = client.getSession(GetSessionRequest.builder()
                    .sessionIdentifier(sessionId)
                    .build());
            assertThat(getResponse.sessionId()).isEqualTo(sessionId);

            client.deleteSession(DeleteSessionRequest.builder()
                    .sessionIdentifier(sessionId)
                    .build());
        }
    }
}