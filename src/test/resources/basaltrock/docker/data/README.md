# Basaltrock - Basaltrock RAG Testcontainers

A Gradle-based Testcontainers project for the Basaltrock RAG (Retrieval Augmented Generation) system. This project provides Java Testcontainers that make it easy to test applications against a RAG service that mimics the AWS Bedrock API.

## Overview

A local, Docker-based test container for mocking and testing AWS Bedrock API calls offline with a RAG system using your own knowledge base files.

## Features

- ✅ **Testcontainers Integration**: JUnit 5 compatible containers for easy testing
- ✅ **Docker Compose Support**: Orchestrate complex multi-container setups
- ✅ **AWS Bedrock Compatibility**: Works with AWS SDK for Bedrock clients
- ✅ **Latest Testcontainers**: Using Testcontainers 1.20.1
- ✅ **Java 21+**: Modern Java support with Gradle 8.10.2

## Quick Start

### Build the Project
```bash
./gradlew build
```

### Run Tests (with RAG container)
```bash
RUN_DOCKER_LLM_MODEL_TEST=true ./gradlew test
```

### Start RAG Service Manually
```bash
docker compose -f src/main/resources/basaltrock/docker/docker-compose.yml up
```

## Project Structure

```
basaltrock/
├── src/main/java/
│   └── com/basaltrock/testcontainers/
│       ├── BasaltRockContainer.java           # Single Docker container
│       └── ComposeBasaltrockContainer.java    # Docker Compose support
├── src/main/resources/basaltrock/docker/
│   ├── Dockerfile                             # Docker build configuration
│   ├── docker-compose.yml                     # Docker Compose configuration
│   ├── api.py                                 # RAG API service
│   └── ingestion.py                                # Embedding ingestion
├── src/test/java/
│   └── com/basaltrock/testcontainers/
│       ├── BasaltrockExampleTest.java         # Example test
│       └── AwsBedrockUtils.java               # Helper utilities
├── src/test/resources/basaltrock/docker/
│   └── data/                                  # Test knowledge base files
├── GETTING_STARTED.md                         # Quick start guide
├── TESTCONTAINERS.md                          # Detailed documentation
└── build.gradle                               # Gradle configuration
```

## Usage Example

```java
@Testcontainers
public class MyTest {
    
    @Container
    static ComposeBasaltrockContainer ragContainer = 
        new ComposeBasaltrockContainer(
            new File("src/main/resources/basaltrock/docker/docker-compose.yml"));
    
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

- **Testcontainers**: 1.20.1 (latest)
- **AWS SDK Bedrock**: 2.25.67
- **JUnit 5**: 5.10.3
- **Jackson**: 2.17.1
- **AssertJ**: 3.26.0
- **Logback**: 1.5.6

## Documentation

- **[GETTING_STARTED.md](GETTING_STARTED.md)** - Quick start guide and examples
- **[TESTCONTAINERS.md](TESTCONTAINERS.md)** - Detailed Testcontainers documentation
- **[DOCKER_SETUP.md](DOCKER_SETUP.md)** - Docker setup and configuration
- **[examples/](examples/)** - Standalone examples without JUnit

## Test Data

Test knowledge base files are located in `src/test/resources/basaltrock/docker/data/`. During test execution, these files are automatically copied to a temporary Docker build context. You can add your own `.txt`, `.html`, or `.md` files to this directory to test with custom knowledge bases.

## Requirements

- Java 21 (or higher)
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