# Testcontainers

## Container Classes

- **ComposeBasaltrockContainer** — wraps a Docker Compose file for RAG service
- **TempComposeBasaltrockContainer** — creates a temp build context, copies your data folder, and manages cleanup

## Usage

```java
@Testcontainers
public class MyTest {
    
    @Container
    static TempComposeBasaltrockContainer ragContainer = 
        new TempComposeBasaltrockContainer("src/test/resources/data");
    
    @Test
    public void testWithBedrock() {
        try (var client = BedrockRuntimeAsyncClient.builder()
                .endpointOverride(URI.create(ragContainer.getBaseUrl()))
                .credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create("dummy", "dummy")))
                .region(Region.US_EAST_1)
                .build()) {
            
            // Use the client
        }
    }
}
```

## Running

```bash
make docker-test
```

Requires Docker running. Container startup takes up to 10 minutes depending on data size.