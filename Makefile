# -----------------------------------------------------------------------------
# Copyright (c) 2025-2026 Bohdan Artiushenko https://github.com/bohdanartiushenko
# Home: https://github.com/bohdanartiushenko/basaltrock
# Licensed under the MIT License. See LICENSE file in the project root.
# SPDX-License-Identifier: MIT
# -----------------------------------------------------------------------------

DOCKER_DIR = src/main/resources/opensearch/docker
DATA_FOLDER ?= src/test/resources/data
MODEL_RUNNER_BASE_URL ?=
MODEL_RUNNER_LLM_CHAT ?= ai/gemma3:1B-Q4_K_M
MODEL_RUNNER_LLM_EMBEDDING ?= ai/nomic-embed-text-v2-moe

.PHONY: all build compile test docker-test clean check-docker docker-build up down restart ingest redeploy-api example-chat example-kb license logs status help

all: build

compile:
	./gradlew clean build -x test

test: compile
	./gradlew test

check-docker:
	@command -v docker >/dev/null 2>&1 || { echo "Docker is required but not found. Install from https://docker.com"; exit 1; }
	@docker info >/dev/null 2>&1 || { echo "Docker is not running. Please start Docker."; exit 1; }

docker-build: check-docker
	cd $(DOCKER_DIR) && docker compose build

build: test docker-build

docker-test: down test
	RUN_DOCKER_LLM_MODEL_TEST=true ./gradlew test --tests "com.basaltrock.testcontainers.*"

clean: down
	./gradlew clean

up: docker-build
	@./src/main/bash/runrag.sh $(DATA_FOLDER)

down: check-docker
	cd $(DOCKER_DIR) && docker compose down

restart: down up

ingest:
	@./src/main/bash/ingest.sh $(DATA_FOLDER)

redeploy-api:
	cd $(DOCKER_DIR) && docker compose up -d --build api

example-chat:
	cd examples && ../gradlew runChat

example-kb:
	cd examples && ../gradlew runKb

logs: check-docker
	cd $(DOCKER_DIR) && docker compose logs -f

status: check-docker
	cd $(DOCKER_DIR) && docker compose ps

help:
	@echo "make build          - Compile, test, and build Docker"
	@echo "make compile        - Gradle clean build (no tests)"
	@echo "make test           - Compile + run tests"
	@echo "make docker-build   - Build Docker images"
	@echo "make docker-test    - Run Docker-dependent tests"
	@echo "make up             - Build Docker + start RAG service"
	@echo "make down           - Stop RAG service"
	@echo "make restart        - Restart RAG service"
	@echo "make logs           - Follow Docker compose logs"
	@echo "make status         - Show running containers"
	@echo "make ingest         - Run ingestion job"
	@echo "make redeploy-api   - Redeploy only API service"
	@echo "make clean          - Stop containers + gradle clean"
	@echo "make example-chat   - Run chat example"
	@echo "make example-kb     - Run knowledge base example"

license:
	@echo "This project is licensed under the MIT License."
	@cat LICENSE