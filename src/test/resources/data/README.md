# Basaltrock

A Gradle-based Testcontainers project for the Basaltrock RAG (Retrieval Augmented Generation) system. This project provides Java Testcontainers that make it easy to test applications against a RAG service that mimics the AWS Bedrock API.

## Overview

A local, Docker-based test container for mocking and testing AWS Bedrock API calls offline with a RAG system using your own knowledge base files.

![Basaltrock Architecture](docs/basaltrock.png)

## Features

- ✅ **Testcontainers Integration**: JUnit 5 compatible containers for easy testing
- ✅ **Docker Compose Support**: Orchestrate complex multi-container setups
- ✅ **AWS Bedrock Compatibility**: Works with AWS SDK for Bedrock clients
- ✅ **Simple REST API**: GET endpoints for curl-based testing
- ✅ **OpenSearch Backend**: k-NN vector search with OpenSearch
- ✅ **Latest Testcontainers**: Using Testcontainers 2.0.5
- ✅ **Java 21+**: Modern Java support with Gradle 8.10.2

## Quick Start

### Build the Project
```bash
./gradlew build
```

### Run RAG Service with Web UI
```bash
./gradlew runRag -PdataFolder=/path/to/your/data
```

Then open **http://localhost:80**. See [DOCKER_SETUP.md](DOCKER_SETUP.md) for details.

### Run Tests (with RAG container)
```bash
RUN_DOCKER_LLM_MODEL_TEST=true ./gradlew test
```

### Try the Simple API
```bash
# Simple chat
curl "http://localhost:80/basaltrock/chat?q=What+is+2+plus+2"

# Search knowledge base
curl "http://localhost:80/basaltrock/search?q=copyright"

# RAG with context
curl "http://localhost:80/basaltrock/search/kb?q=What+is+copyright"
```

## Project Structure

```
basaltrock/
├── src/main/java/com/basaltrock/testcontainers/
├── src/main/resources/opensearch/docker/      # OpenSearch variant
├── src/test/java/com/basaltrock/testcontainers/
├── src/test/resources/data/                   # Test knowledge base
├── examples/                                  # Example usage
├── GETTING_STARTED.md
├── TESTCONTAINERS.md
├── DOCKER_SETUP.md                            # Docker details
└── build.gradle
```

## Usage Example

```java
@Testcontainers
public class MyTest {
    
    @Container
    static TempComposeBasaltrockContainer ragContainer = 
        new TempComposeBasaltrockContainer("src/test/resources/data");
    
    @Test
    public void testRAG() {
        try (var client = BedrockRuntimeAsyncClient.builder()
                .endpointOverride(URI.create(ragContainer.getBaseUrl()))
                .credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create("dummy", "dummy")))
                .region(Region.US_EAST_1)
                .build()) {
            
            // Your test code here
        }
    }
}
```

## Key Dependencies

- **Testcontainers**: 2.0.5 (latest)
- **Docker**

## Documentation

- **[GETTING_STARTED.md](GETTING_STARTED.md)** - Quick start guide and examples
- **[TESTCONTAINERS.md](TESTCONTAINERS.md)** - Detailed Testcontainers documentation
- **[DOCKER_SETUP.md](DOCKER_SETUP.md)** - Docker setup and configuration
- **[examples/](examples/)** - Standalone examples without JUnit

## Test Data

Test knowledge base files are located in `src/test/resources/data/`. During test execution, these files are automatically copied to a temporary Docker build context. You can add your own `.txt`, `.html`, or `.md` files to this directory to test with custom knowledge bases.

## Requirements

- Java 21 or higher
- Docker (running)
- Gradle 8.10.2+ (wrapper included)

## Building and Testing

```bash
# Build
./gradlew build

# Clean build
./gradlew clean build

# Run tests with RAG container
RUN_DOCKER_LLM_MODEL_TEST=true ./gradlew test

# Run specific test
RUN_DOCKER_LLM_MODEL_TEST=true ./gradlew test --tests "BasaltrockExampleTest"

# Show available tasks
./gradlew tasks
```

## License

See [LICENSE](LICENSE).

## Copyright
Copyright (c) 2025-2026 Bohdan Artiushenko.