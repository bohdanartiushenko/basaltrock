# Basaltrock - Basaltrock RAG Testcontainers

This project provides Testcontainers implementations for the Basaltrock RAG service that mimics the AWS Bedrock API.

## Overview

The project includes:
- **BasaltRockContainer**: A simple Testcontainer for running a Basaltrock Docker image
- **ComposeBasaltrockContainer**: A Testcontainer using Docker Compose for more complex setups

## Dependencies

- Java
- Docker
- Gradle
- Testcontainers
- AWS SDK for Bedrock

## Usage

### Using TempComposeBasaltrockContainer

```java
@Testcontainers
public class MyTest {
    
    @Container
    static TempComposeBasaltrockContainer ragContainer = 
        new TempComposeBasaltrockContainer("src/test/resources/data");
    
    @Test
    public void testWithRag() {
        String baseUrl = ragContainer.getBaseUrl();
        // Use the baseUrl to connect your AWS Bedrock client
    }
}
```

### Using with AWS Bedrock Client

```java
try (var bedrockClient = BedrockRuntimeAsyncClient.builder()
        .endpointOverride(URI.create(ragContainer.getBaseUrl()))
        .credentialsProvider(
                StaticCredentialsProvider.create(AwsBasicCredentials.create("dummy", "dummy")))
        .region(Region.US_EAST_1)
        .build()) {
    
    // Use the client to interact with the Basaltrock RAG service
}
```

## Building the Project

```bash
./gradlew build
```

## Running Tests

By default, tests using the RAG container are disabled. To enable them:

```bash
RUN_DOCKER_LLM_MODEL_TEST=true ./gradlew test
```

Or test a specific class:

```bash
RUN_DOCKER_LLM_MODEL_TEST=true ./gradlew test --tests "BasaltrockExampleTest"
```

## Project Structure

```
.
├── build.gradle                          # Gradle build configuration
├── settings.gradle                       # Gradle settings
├── gradle/wrapper/                       # Gradle wrapper files
├── src/
│   ├── main/java/
│   │   └── com/basaltrock/testcontainers/
│   │       ├── BasaltRockContainer.java           # Single container implementation
│   │       └── ComposeBasaltrockContainer.java    # Docker Compose implementation
│   └── test/java/
│       └── com/basaltrock/testcontainers/
│           ├── BasaltrockExampleTest.java         # Example test
│           └── AwsBedrockUtils.java               # Helper utilities
└── src/main/resources/basaltrock/      # Basaltrock RAG service files
    ├── docker-compose.yml
    ├── Dockerfile
    └── ...
```

## Configuration

The container expects:
- A health check endpoint at `/health` returning HTTP 200
- Default port: 80
- Startup timeout: 5 minutes
- Overall timeout: 10 minutes for compose setup

## Features

- **Automatic lifecycle management**: Containers start before tests and stop after
- **Port mapping**: Automatically maps container ports to available host ports
- **Health checks**: Waits for service to be ready before running tests
- **JUnit 5 integration**: Works seamlessly with JUnit Jupiter
- **Docker Compose support**: Can orchestrate multiple containers if needed

## Troubleshooting

### Container won't start
- Check Docker is running: `docker ps`
- Check the docker-compose.yml file exists at `src/main/resources/opensearch/docker/docker-compose.yml`
- Increase timeout in the container configuration if needed

### Tests are skipped
- Make sure the `RUN_DOCKER_LLM_MODEL_TEST=true` environment variable is set
- Check JUnit test logs for details

### Port conflicts
- Testcontainers automatically assigns random ports, avoiding conflicts
- Use `ragContainer.getPort()` or `ragContainer.getBaseUrl()` to get the actual endpoint

## License

See [LICENSE](LICENSE).
