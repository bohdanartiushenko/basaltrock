# Basaltrock

A local docker RAG (Retrieval Augmented Generation) system.

## Overview

A local, Docker-based test container for knowledge based chat systems 

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
make build
```

### Run RAG Service with Web UI
```bash
make up DATA_FOLDER=/path/to/your/data
```

Then open **http://localhost:80**. See [DOCKER_SETUP.md](DOCKER_SETUP.md) for details.

### Sync Data to Knowledge Base (Ingestion)
```bash
make ingest DATA_FOLDER=/path/to/your/data
```

### Stop RAG Service
```bash
make down
```

### Run Tests
```bash
make test
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


## Documentation

- **[GETTING_STARTED.md](GETTING_STARTED.md)** - Quick start guide and examples
- **[TESTCONTAINERS.md](TESTCONTAINERS.md)** - Detailed Testcontainers documentation
- **[DOCKER_SETUP.md](DOCKER_SETUP.md)** - Docker setup and configuration
- **[examples/](examples/)** - Standalone examples without JUnit

## Test Data

Test knowledge base files are located in `src/test/resources/data/`. During test execution, these files are automatically copied to a temporary Docker build context.

**Supported file formats:**
- `*.txt` - Plain text files
- `*.md` - Markdown files
- `*.html`, `*.htm` - HTML files
- `*.pdf` - PDF files
- `*.docx` - Microsoft Word documents (Office 2007+)
- `*.xls`, `*.xlsx` - Microsoft Excel spreadsheets
- `*.csv` - CSV files

## Requirements

- Docker
- Make
- Java 21+ (for tests)

## Available Commands

```bash
make all                       # Compile + build Docker images (default)
make build                     # Compile + build Docker images
make compile                   # Gradle clean build
make up DATA_FOLDER=xyz        # Start RAG service
make down                      # Stop RAG service
make restart                   # Restart RAG service
make ingest DATA_FOLDER=xyz    # Run ingestion job
make redeploy-api              # Redeploy only API service
make test                      # Run tests
make clean                     # Clean and remove volumes
make example-chat              # Run chat example
make example-kb                # Run knowledge base example
```

## License

See [LICENSE](LICENSE).

## Copyright
Copyright (c) 2025-2026 Bohdan Artiushenko.
