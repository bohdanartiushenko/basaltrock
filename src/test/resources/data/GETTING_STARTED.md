# Getting Started with Basaltrock Testcontainers

## Quick Start

This project provides Testcontainers for the Basaltrock RAG service. Here's how to get started:

### Prerequisites

1. **Java 21+**: Check with `java -version`
2. **Docker**: Must be running (`docker ps` should work)
3. **Gradle**: Included via wrapper (`./gradlew`)

### Building the Project

```bash
# Build without running tests
./gradlew build

# Clean build
./gradlew clean build
```

### Running Tests

Tests are disabled by default. Enable them with:

```bash
# Run all tests
RUN_DOCKER_LLM_MODEL_TEST=true ./gradlew test

# Run specific test class
RUN_DOCKER_LLM_MODEL_TEST=true ./gradlew test --tests "ChatStreamingTest"

# Run with logs
RUN_DOCKER_LLM_MODEL_TEST=true ./gradlew test --info
```

### Testing with curl

The service provides AWS Bedrock-compatible endpoints (one needs to deploy docker manually):

```bash
# Retrieve from knowledge base
curl -X POST http://localhost:80/knowledgebases/basaltrock-knowledge-base-id/retrieve \
  -H "Content-Type: application/json" \
  -d '{"retrievalQuery":{"text":"testcontainers"},"retrievalConfiguration":{"vectorSearchConfiguration":{"numberOfResults":5}}}'

# Retrieve and generate (RAG)
curl -X POST http://localhost:80/retrieveAndGenerate \
  -H "Content-Type: application/json" \
  -d '{"input":{"text":"How do I use testcontainers?"},"retrieveAndGenerateConfiguration":{"type":"KNOWLEDGE_BASE","knowledgeBaseConfiguration":{"knowledgeBaseId":"basaltrock-knowledge-base-id","modelArn":"ai/gemma3:1B-Q4_K_M","retrievalConfiguration":{"vectorSearchConfiguration":{"numberOfResults":5}}}}}'
```

## Project Structure

```
basaltrock/
├── src/
│   ├── main/java/com/basaltrock/testcontainers/
│   │   ├── BasaltRockContainer.java           # Simple container
│   │   └── ComposeBasaltrockContainer.java    # Docker Compose container
│   └── test/java/
│       └── com/basaltrock/testcontainers/
│           ├── BasaltrockExampleTest.java     # Full example
│           └── AwsBedrockUtils.java           # Helper utilities
├── src/main/resources/basaltrock/           # Docker service files
├── build.gradle                               # Dependencies & config
└── TESTCONTAINERS.md                          # Detailed documentation
```

## Test Data

The test uses knowledge base files from `src/test/resources/data/`. During test execution:
1. A temporary Docker build context is created in `/tmp`
2. Docker files are copied from `src/main/resources/opensearch/docker/`
3. Test data files are copied from `src/test/resources/data/`
4. Docker builds and runs with this temporary context

To customize the knowledge base, add your own `.txt`, `.html`, or `.md` files to the test data directory.

## Key Dependencies

- **Testcontainers**
- **AWS SDK Bedrock**
- **JUnit 5**
- **Jackson**
- **Commons IO**

## Example Usage

### Minimal Example

```java
@Testcontainers
public class MyTest {
    
    @Container
    static TempComposeBasaltrockContainer ragContainer = 
        new TempComposeBasaltrockContainer("src/test/resources/data");
    
    @Test
    public void testSomething() {
        String url = ragContainer.getBaseUrl();
        // Use the URL to connect your client
    }
}
```

### With AWS Bedrock Client

```java
@Test
public void testWithBedrock() {
    try (var client = BedrockRuntimeAsyncClient.builder()
            .endpointOverride(URI.create(ragContainer.getBaseUrl()))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create("dummy", "dummy")))
            .region(Region.US_EAST_1)
            .build()) {
        
        // Use the client...
    }
}
```

## Understanding the Tests

### Understanding the Tests

The tests in this project:
1. Require Docker to be running
2. Start containers, which takes time
3. Use Testcontainers to automatically manage Docker lifecycle

### Test Examples Included

1. **ChatStreamingTest**: Chat streaming with InvokeModelWithResponseStream
2. **ConverseTest** / **ConverseStreamTest**: Converse API (sync and streaming)
3. **InvokeModelTest**: Sync model invocation
4. **KnowledgeBaseRetrievalTest**: Knowledge base vector search retrieval
5. **RetrieveAndGenerateTest**: RAG with citations and generation config
6. **RetrieveAndGenerateStreamTest**: Streaming RAG
7. **DeterminismTest**: Temperature=0 deterministic output validation
8. **ScoreFilteringTest**: Score-based relevance filtering
9. **AwsBedrockUtils**: Helper class for creating AWS Bedrock clients

## Next Steps

1. Read [TESTCONTAINERS.md](TESTCONTAINERS.md) for detailed documentation
2. Look at test examples in `src/test/java/com/basaltrock/testcontainers/`
3. Run the tests: `RUN_DOCKER_LLM_MODEL_TEST=true ./gradlew test`
4. Customize for your use case

## Troubleshooting

### Docker Issues
```bash
# Check Docker is running
docker ps

# Check Docker Compose
docker compose version
```

### Build Issues
```bash
# Clean and rebuild
./gradlew clean build --refresh-dependencies

# Show full stack traces
./gradlew build --stacktrace
```

### Test Issues
```bash
# Run with detailed logging
RUN_DOCKER_LLM_MODEL_TEST=true ./gradlew test --info

# Run single test with logs
RUN_DOCKER_LLM_MODEL_TEST=true ./gradlew test --tests "BasaltrockExampleTest" --info
```