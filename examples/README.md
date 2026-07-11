# Basaltrock Examples

Standalone examples showing how to call Basaltrock RAG API.

## Examples

### 1. ChatStreamingExample

Demonstrates chat streaming with real-time response printing.

**Run with custom question:**
```bash
./gradlew runChat -Pargs="how to run with custom query?"
```

### 2. KnowledgeBaseRetrievalExample

Demonstrates searching the knowledge base and displaying results.


**Run with custom query:**
```bash
./gradlew runKb -Pargs="how to run with custom query?"
```

## How It Works

The examples automatically:
1. Check if a container is already running at `http://localhost:80`
2. If not, start a new container with Docker Compose
3. Use this README.md file as the knowledge base data
4. Run the example

The container **stays running** after the example completes, so you can:
- Run multiple examples without restarting
- Connect your own applications to `http://localhost:80`

**To stop the container:**
```bash
docker compose -f /tmp/basaltrock-example-*/docker-compose.yml down
# or find it with:
docker ps | grep basaltrock
docker stop <container-id>
```

No manual setup required!

## Requirements

- Java 21+
- Running Basaltrock container (see Prerequisites above)
