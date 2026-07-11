# Getting Started with Basaltrock Testcontainers

## Quick Start

This project provides Testcontainers for the Basaltrock RAG service. Here's how to get started:

### Prerequisites

1. **Docker**: Must be running (`docker ps`)
2. **Make**: Build tool
3. **Java 21+**: For tests only

### Building the Project

```bash
make build
```

### Running Tests

```bash
make test
```

### Running RAG Service

Start the RAG service with Docker:

```bash
# Start with default data
make up

# Start with custom data folder
make up DATA_FOLDER=/path/to/your/data

# Stop the service
make down
```

### Testing with curl

The service provides simple GET endpoints for quick testing:

```bash
# Simple chat
curl "http://localhost:80/basaltrock/chat?q=What+is+machine+learning"

# Search knowledge base
curl "http://localhost:80/basaltrock/search?q=testcontainers&limit=5"

# RAG: search + answer with context
curl "http://localhost:80/basaltrock/search/kb?q=How+do+I+use+testcontainers"
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
├── src/main/resources/opensearch/docker/   # Docker service files
├── build.gradle                               # Dependencies & config
└── TESTCONTAINERS.md                          # Detailed documentation
```

## Test Data

The test uses knowledge base files from `src/test/resources/data/`. During test execution:
1. A temporary Docker build context is created in `/tmp`
2. Docker files are copied from `src/main/resources/opensearch/docker/`
3. Test data files are copied from `src/test/resources/data/`
4. Docker builds and runs with this temporary context

**Supported file formats:**
- `*.txt` - Plain text files
- `*.md` - Markdown files
- `*.html`, `*.htm` - HTML files
- `*.pdf` - PDF files
- `*.docx` - Microsoft Word documents (Office 2007+)
- `*.xls`, `*.xlsx` - Microsoft Excel spreadsheets
- `*.csv` - CSV files

To customize the knowledge base, add your own files to the test data directory.

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

The tests in this project:
1. Require Docker to be running
2. Start containers, which takes time
3. Use Testcontainers to automatically manage Docker lifecycle

### Test Examples Included

1. **ChatStreamingTest**, **KnowledgeBaseRetrievalTest**: Complete example with Bedrock client demonstrating chat streaming and knowledge base retrieval
2. **AwsBedrockUtils**: Helper class for creating AWS Bedrock clients


## Troubleshooting

### Docker Issues
```bash
# Check Docker is running
docker ps

# Check Docker Compose
docker compose version

# View logs
docker compose -f src/main/resources/opensearch/docker/docker-compose.yml logs
```

### Build Issues
```bash
# Clean and rebuild
make clean
make build
```