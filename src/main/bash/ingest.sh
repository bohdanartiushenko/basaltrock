#!/usr/bin/env bash
set -e

DOCKER_DIR="src/main/resources/opensearch/docker"
DATA_FOLDER="${1:-${DATA_FOLDER:-src/test/resources/data}}"
MODEL_RUNNER_BASE_URL="${MODEL_RUNNER_BASE_URL:-}"
MODEL_RUNNER_LLM_EMBEDDING="${MODEL_RUNNER_LLM_EMBEDDING:-ai/nomic-embed-text-v2-moe}"
DOC_ANSI_CODEPAGE="${DOC_ANSI_CODEPAGE:-cp1251}"

DATA_FOLDER_PATH=$(cd "$DATA_FOLDER" && pwd)

cat <<EOF
Running ingestion job...
  Data folder: $DATA_FOLDER_PATH
  Embedding model: $MODEL_RUNNER_LLM_EMBEDDING
  .doc codepage: $DOC_ANSI_CODEPAGE
EOF

cd "$DOCKER_DIR"
export MODEL_RUNNER_BASE_URL MODEL_RUNNER_LLM_EMBEDDING DATA_FOLDER_PATH DOC_ANSI_CODEPAGE
docker compose run --rm ingestion