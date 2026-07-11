# Docker Setup for Basaltrock RAG Service

## Overview

The Basaltrock RAG service is containerized using Docker and Docker Compose. The setup includes:
- **Ingestion service**: Processes data files and creates an embeddings cache
- **API service**: Exposes Bedrock-compatible REST endpoints

## Files

```
src/main/resources/basaltrock/docker/
├── Dockerfile                  # Multi-stage build (ingestion + api)
├── docker-compose.yml          # Production compose file (requires model-runner)
├── .dockerignore              # Excludes unnecessary files
├── api.py                     # FastAPI service
├── ingestion.py                    # Embedding ingestion
└── data/                      # Knowledge base data
    └── sample_knowledge.txt   # Sample data file
```

## Dockerfile

The Dockerfile uses a multi-stage build:

### Stage 1: Ingestion
```dockerfile
FROM python:3.12-slim AS ingestion
```

### Stage 2: API
```dockerfile
FROM python:3.12-slim AS api
```

## Docker Compose Files

### docker-compose.yml (Production)

For production use with an external model-runner service:

```yaml
services:
  ingestion:
    # Builds embeddings from data/
    # Requires MODEL_RUNNER_BASE_URL for embedding model
    
  api:
    # Exposes port 80 (maps to 8080 internally)
    # Requires MODEL_RUNNER_BASE_URL for chat and embedding
    # Health check on /health endpoint
```

**Required environment variables:**
- `MODEL_RUNNER_BASE_URL` - URL to model-runner service
- `MODEL_RUNNER_LLM_CHAT` - Chat model name
- `MODEL_RUNNER_LLM_EMBEDDING` - Embedding model name


## Data Directory

The `data/` directory contains knowledge base source files:

**Supported formats:**
- `*.txt` - Plain text files
- `*.html` - HTML files (text extracted with BeautifulSoup)
- `*.md` - Markdown files

Please feel free to replace it with your own knowledge base source files.

**Processing:**
- Files are split into chunks (~800 characters max)
- Each chunk is embedded using the embedding model
- Results are cached in `/app/cache/embeddings.pkl`

**Current data:**
- `sample_knowledge.txt` - Sample knowledge about factor analysis and portfolio management

## Building Images

### Build both services:
```bash
cd src/main/resources/basaltrock/docker
docker compose build
```


## Running Services

### Production (requires model-runner):
```bash
# Set environment variables first
export MODEL_RUNNER_BASE_URL="http://your-model-runner:8080"
export MODEL_RUNNER_LLM_CHAT="ai/llama3.2:1B-Q4_0"
export MODEL_RUNNER_LLM_EMBEDDING="ai/nomic-embed-text-v1.5"

docker compose up -d
```

## Health Check

The API service includes a health check endpoint:

```bash
curl http://localhost:80/health
```

**Response:**
```json
{
  "status": "ok",
  "chunks": 10
}
```

## Volumes

**rag_cache** volume:
- Stores embeddings cache
- Shared between ingestion and api services
- Persists across container restarts
- Path: `/app/cache/embeddings.pkl`

## Environment Variables

### Ingestion Service
| Variable | Default | Description |
|----------|---------|-------------|
| `MODEL_RUNNER_BASE_URL` | - | Model runner endpoint |
| `MODEL_RUNNER_LLM_EMBEDDING` | - | Embedding model name |
| `DATA_DIR` | `/app/data` | Data directory path |
| `CACHE_PATH` | `/app/cache/embeddings.pkl` | Cache file path |

### API Service
| Variable | Default | Description |
|----------|---------|-------------|
| `MODEL_RUNNER_BASE_URL` | - | Model runner endpoint |
| `MODEL_RUNNER_LLM_CHAT` | `ai/llama3.2:1B-Q4_0` | Chat model name |
| `MODEL_RUNNER_LLM_EMBEDDING` | `ai/nomic-embed-text-v1.5` | Embedding model name |
| `CACHE_PATH` | `/app/cache/embeddings.pkl` | Cache file path |
| `KNOWLEDGE_BASE_ID` | `basaltrock-knowledge-base-id` | Knowledge base identifier |
| `MIN_SCORE` | `0` | Min similarity score (0.0-1.0, AWS Bedrock compatible) |
| `MAX_SIMILARITIES` | `30` | Max results to return |
| `TEMPERATURE` | `3` | Chat temperature (AWS Bedrock, see [InferenceConfiguration](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_agent_InferenceConfiguration.html)) |

## Ports

| Service | Internal Port | External Port |
|---------|--------------|---------------|
| API | 8080 | 80 |

## .dockerignore

Excludes from build context:
- `.DS_Store` - macOS metadata
- `README.md` - Documentation
- `__pycache__/` - Python cache
- `*.pyc` - Compiled Python
- `.pytest_cache/` - Test cache
- `data/*.md` - Documentation in data dir
- `data/LICENSE` - License file

## Troubleshooting

### Ingestion fails with "No .txt or .html files found"
- Ensure the `data/` directory contains `.txt` or `.html` files
- Check that `.dockerignore` isn't excluding data files
- Verify the COPY command in Dockerfile: `COPY data /app/data`

### API fails to start
- Check ingestion completed successfully
- Verify embeddings cache exists: `docker compose exec api ls -la /app/cache/`
- Check logs: `docker compose logs api`

### Health check failing
- Check API is listening
- Verify Python and dependencies installed correctly
- Check logs for startup errors

### Cache not persisting
- Verify volume is created: `docker volume ls | grep rag_cache`
- Check volume mount in compose file
- Ensure cache directory has write permissions

## Testing with Testcontainers

The Java tests use Testcontainers to automatically manage Docker Compose:

```java
@Container
private static final ComposeContainer compose =
    new ComposeContainer(new File("src/main/resources/basaltrock/docker/docker-compose-test.yml"))
        .withLocalCompose(true)
        .withExposedService("api", PORT, Wait.forHealthcheck())
        .withStartupTimeout(Duration.ofMinutes(10));
```

**To run tests:**
```bash
RUN_DOCKER_LLM_MODEL_TEST=true ./gradlew test --tests "BasaltrockExampleTest"
```

## Adding New Data

1. Add `.txt` or `.html` files to `data/` directory
2. Rebuild ingestion image: `docker compose build ingestion`
3. Restart services: `docker compose up -d`
4. Ingestion will regenerate embeddings cache automatically

## Performance Considerations

- **Embeddings cache**: Cached after first run, only regenerated when data changes
- **Startup time**: Ingestion must complete before API starts (~1-5 minutes depending on data size)
- **Memory**: API service needs enough memory for embeddings cache
- **Health check interval**: Adjust based on expected query load

## Security Notes

- API uses dummy credentials in examples (for local development only)
- No authentication or authorization implemented
- Not recommended for production without additional security layers
- The model runner endpoint should be secured in production environments
