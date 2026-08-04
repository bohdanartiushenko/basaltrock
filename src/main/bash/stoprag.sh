#!/usr/bin/env bash
set -e

DOCKER_DIR="src/main/resources/opensearch/docker"

cd "$DOCKER_DIR"
docker compose down