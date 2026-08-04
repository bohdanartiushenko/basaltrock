package com.basaltrock.testcontainers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockagentruntime.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.basaltrock.testcontainers.AwsBedrockUtils.createBedrockAgentRuntimeAsyncClient;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "RUN_DOCKER_LLM_MODEL_TEST", matches = "true")
public class InvokeFlowTest extends BaseBasaltrockTest {

    static final Logger logger = LoggerFactory.getLogger(InvokeFlowTest.class);

    @Test
    void testInvokeFlow() {
        var outputs = new ArrayList<String>();
        var completionReason = new AtomicReference<String>();

        var handler = InvokeFlowResponseHandler.builder()
                .onEventStream(stream -> stream.subscribe(event -> event.accept(
                        InvokeFlowResponseHandler.Visitor.builder()
                                .onFlowOutputEvent(e -> {
                                    var doc = e.content().document();
                                    if (doc != null) {
                                        outputs.add(doc.asString());
                                    }
                                })
                                .onFlowCompletionEvent(e -> {
                                    completionReason.set(e.completionReasonAsString());
                                })
                                .build())))
                .build();

        try (var client = createBedrockAgentRuntimeAsyncClient(container)) {
            var request = InvokeFlowRequest.builder()
                    .flowIdentifier("test-flow")
                    .flowAliasIdentifier("test-alias")
                    .inputs(List.of(FlowInput.builder()
                            .nodeName("FlowInputNode")
                            .nodeInputName("document")
                            .content(FlowInputContent.fromDocument(Document.fromString("What is 2+2?")))
                            .build()))
                    .build();

            client.invokeFlow(request, handler).join();

            logger.info("InvokeFlow output: {}", outputs);
            assertThat(outputs).isNotEmpty();
            assertThat(outputs.get(0)).containsIgnoringCase("4");
            assertThat(completionReason.get()).isEqualTo("SUCCESS");
        }
    }
}
