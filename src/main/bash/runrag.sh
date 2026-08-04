#!/usr/bin/env bash
set -e

DOCKER_DIR="src/main/resources/opensearch/docker"
DATA_FOLDER="${1:-${DATA_FOLDER:-src/test/resources/data}}"
MODEL_RUNNER_BASE_URL="${MODEL_RUNNER_BASE_URL:-}"
MODEL_RUNNER_LLM_CHAT="${MODEL_RUNNER_LLM_CHAT:-ai/gemma3:1B-Q4_K_M}"
MODEL_RUNNER_LLM_EMBEDDING="${MODEL_RUNNER_LLM_EMBEDDING:-ai/nomic-embed-text-v2-moe}"

DATA_FOLDER_PATH=$(cd "$DATA_FOLDER" && pwd)

cat <<EOF
Starting Basaltrock RAG service...
  Data folder: $DATA_FOLDER_PATH
  Model runner: $MODEL_RUNNER_BASE_URL
  Chat model: $MODEL_RUNNER_LLM_CHAT
  Embedding model: $MODEL_RUNNER_LLM_EMBEDDING
EOF

cd "$DOCKER_DIR"
export MODEL_RUNNER_BASE_URL MODEL_RUNNER_LLM_CHAT MODEL_RUNNER_LLM_EMBEDDING DATA_FOLDER_PATH
docker compose up -d --build

echo "Waiting for API to be ready..."
until curl -sf -X POST http://localhost:80/model/health/count-tokens -H "Content-Type: application/json" -d '{"input":{}}' >/dev/null 2>&1; do
  sleep 2
done

cat <<EOF

Ready!
  Web UI: http://localhost:80
  API: http://localhost:80/retrieveAndGenerate
  OpenSearch: http://localhost:9200

Use 'make logs' to follow logs, 'make down' to stop.
EOF