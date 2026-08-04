# Docker Setup for Basaltrock RAG Service

## Overview

The Basaltrock RAG service is containerized using Docker and Docker Compose with OpenSearch backend:

- **OpenSearch service**: Runs OpenSearch with k-NN plugin
- **Ingestion service**: Uploads embeddings to OpenSearch index
- **API service**: Queries OpenSearch for vector similarity search
- **Scalable**, production-ready vector search

![Basaltrock Architecture](docs/basaltrock-docker.png)

## Files

```
src/main/resources/opensearch/docker/
├── Dockerfile              # Multi-stage with opensearch-py
├── docker-compose.yml      # 3 services: opensearch, ingestion, api
├── api.py                  # FastAPI with OpenSearch client
├── shared.py               # OpenSearch k-NN queries
├── bedrock_api.py          # AWS Bedrock endpoints
├── basaltrock_api.py       # Simple GET endpoints
└── ingestion.py                 # Uploads to OpenSearch
```

## Dockerfile

The Dockerfile uses a multi-stage build:

### Stage 1: Ingestion
```dockerfile
FROM python:3.12-slim AS ingestion
# Installs openai and beautifulsoup4
# Copies ingestion.py and data directory
# Runs ingestion.py to create embeddings cache
```

### Stage 2: API
```dockerfile
FROM python:3.13-slim AS api
# Installs fastapi, hypercorn, openai
# Copies api.py, shared.py, bedrock_api.py, basaltrock_api.py
# Exposes port 80
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

## Building Images

### Build both services:
```bash
cd src/main/resources/basaltrock/imemory/docker
docker compose build
```


## Running Services

### Production (requires model-runner):
```bash
# Set environment variables first
export MODEL_RUNNER_BASE_URL="http://your-model-runner:8080"
export MODEL_RUNNER_LLM_CHAT="ai/gemma3:1B-Q4_K_M"
export MODEL_RUNNER_LLM_EMBEDDING="ai/nomic-embed-text-v2-moe"

docker compose up -d
```

## API Endpoints

The API service provides two types of endpoints:

### AWS Bedrock-Compatible API (for SDK testing)
- **POST** `/model/{model_id}/invoke-with-response-stream` - Chat streaming with AWS event stream format
- **POST** `/knowledgebases/{knowledge_base_id}/retrieve` - Knowledge base retrieval

### Simple Basaltrock API (for curl testing)
- **GET** `/basaltrock/chat?q=question` - Simple chat (no KB context)
- **GET** `/basaltrock/search?q=query&limit=5` - Search knowledge base only
- **GET** `/basaltrock/search/kb?q=question&limit=5` - RAG: search KB + answer with context

### Health Check
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

### Example curl commands
```bash
# Simple chat
curl "http://localhost:80/basaltrock/chat?q=What+is+2+plus+2"

# Search knowledge base
curl "http://localhost:80/basaltrock/search?q=copyright&limit=3"

# RAG with context
curl "http://localhost:80/basaltrock/search/kb?q=What+is+copyright&limit=5"
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
| `MODEL_RUNNER_LLM_CHAT` | `ai/gemma3:1B-Q4_K_M` | Chat model name |
| `MODEL_RUNNER_LLM_EMBEDDING` | `ai/nomic-embed-text-v2-moe` | Embedding model name |
| `CACHE_PATH` | `/app/cache/embeddings.pkl` | Cache file path |
| `KNOWLEDGE_BASE_ID` | `basaltrock-knowledge-base-id` | Knowledge base identifier |
| `MIN_SCORE` | `0` | Min similarity score for retrieval (0.0-1.0, AWS Bedrock compatible) |
| `MIN_SCORE_FOR_ANSWER` | `0.3` | Min score required to generate answer (0.0-1.0) |
| `MAX_SIMILARITIES` | `30` | Max results to return |
| `TEMPERATURE` | `3` | Chat temperature (AWS Bedrock, see [InferenceConfiguration](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_agent_InferenceConfiguration.html)) |
| `MAX_TOKENS` | `4096` | Maximum tokens for LLM response |
| `RAG_SYSTEM_PROMPT_TEMPLATE` | See below | System prompt template for RAG queries, use `{context}` placeholder |

**Default RAG_SYSTEM_PROMPT_TEMPLATE:**
```
You must answer the question using ONLY the context provided below from the knowledge base.

STRICT RULES:
- Only use information explicitly stated in the context
- Do not use any external knowledge or make assumptions
- If the context doesn't contain enough information to answer, say "The knowledge base doesn't contain enough information to answer this question."
- Do not add information that isn't in the context

Context:
{context}
```

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
- Check API is listening on port 8080
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
    new ComposeContainer(new File("src/main/resources/basaltrock/imemory/docker/docker-compose-test.yml"))
        .withLocalCompose(true)
        .withExposedService("api", 8080, Wait.forHealthcheck())
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
