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
curl "http://localhost:80/basaltrock/chat?q=What+is+machine+learning"
curl "http://localhost:80/basaltrock/search?q=testcontainers&limit=5"
curl "http://localhost:80/basaltrock/search/kb?q=How+do+I+use+testcontainers"
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