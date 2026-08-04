# Getting Started

## Prerequisites

- Docker running (`docker ps`)
- Make
- Java 21+

## Build & Run

```bash
make build                              # compile, test, build docker
make up DATA_FOLDER=/path/to/data       # start RAG service
make down                               # stop
make help                               # all commands
```

## Testing with curl

```bash
# Retrieve from knowledge base
curl -X POST http://localhost:80/knowledgebases/basaltrock-knowledge-base-id/retrieve \
  -H "Content-Type: application/json" \
  -d '{"retrievalQuery":{"text":"testcontainers"},"retrievalConfiguration":{"vectorSearchConfiguration":{"numberOfResults":5}}}'

# RAG: retrieve and generate
curl -X POST http://localhost:80/retrieveAndGenerate \
  -H "Content-Type: application/json" \
  -d '{"input":{"text":"How do I use testcontainers?"},"retrieveAndGenerateConfiguration":{"type":"KNOWLEDGE_BASE","knowledgeBaseConfiguration":{"knowledgeBaseId":"basaltrock-knowledge-base-id","modelArn":"ai/gemma3:1B-Q4_K_M","retrievalConfiguration":{"vectorSearchConfiguration":{"numberOfResults":5}}}}}'
```

## Test Data

Files in `src/test/resources/data/` are used as the knowledge base during tests.

Supported formats: `*.txt`, `*.md`, `*.html`, `*.htm`, `*.pdf`, `*.docx`, `*.xls`, `*.xlsx`, `*.csv`

## Troubleshooting

```bash
make logs                               # view docker logs
make status                             # check running containers
make clean && make build                # full rebuild
```