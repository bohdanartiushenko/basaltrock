# Docker Setup

## Architecture

![Basaltrock Docker](docs/basaltrock-docker.png)

Services:
- **OpenSearch** — k-NN vector search
- **Ingestion** — embeds data files and indexes into OpenSearch
- **API** — FastAPI serving Bedrock-compatible and simple REST endpoints

## API Endpoints

### AWS Bedrock-Compatible (for SDK testing)
- `POST /model/{model_id}/invoke-with-response-stream` — chat streaming
- `POST /knowledgebases/{knowledge_base_id}/retrieve` — knowledge base retrieval

### Simple REST (for curl)
- `GET /basaltrock/chat?q=question` — chat (no KB context)
- `GET /basaltrock/search?q=query&limit=5` — search knowledge base
- `POST /basaltrock/search/kb` — RAG: search + answer with context
- `GET /health` — health check

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `MODEL_RUNNER_BASE_URL` | - | Model runner endpoint |
| `MODEL_RUNNER_LLM_CHAT` | `ai/gemma3:1B-Q4_K_M` | Chat model |
| `MODEL_RUNNER_LLM_EMBEDDING` | `ai/nomic-embed-text-v2-moe` | Embedding model |
| `MIN_SCORE` | `0` | Min similarity score for retrieval |
| `MIN_SCORE_FOR_ANSWER` | `0.3` | Min score to generate answer |
| `MAX_SIMILARITIES` | `30` | Max results to return |
| `TEMPERATURE` | `3` | Chat temperature |
| `MAX_TOKENS` | `4096` | Max tokens for response |
| `RAG_SYSTEM_PROMPT_TEMPLATE` | (see docker-compose.yml) | System prompt, use `{context}` placeholder |

## Supported Data Formats

`*.txt`, `*.md`, `*.html`, `*.htm`, `*.pdf`, `*.doc`, `*.docx`, `*.xls`, `*.xlsx`, `*.csv`

## Ports

| Service | Internal | External |
|---------|----------|----------|
| API | 8080 | 80 |
| OpenSearch | 9200 | 9200 |