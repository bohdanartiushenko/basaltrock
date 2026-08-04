DOCKER_DIR = src/main/resources/opensearch/docker
DATA_FOLDER ?= src/test/resources/data
MODEL_RUNNER_BASE_URL ?=
MODEL_RUNNER_LLM_CHAT ?= ai/gemma3:1B-Q4_K_M
MODEL_RUNNER_LLM_EMBEDDING ?= ai/nomic-embed-text-v2-moe

.PHONY: all build compile test clean up down restart ingest redeploy-api example-chat example-kb

all: build

compile:
	./gradlew clean build

build: compile
	cd $(DOCKER_DIR) && docker compose build

test:
	./gradlew build test

clean:
	./gradlew clean
	cd $(DOCKER_DIR) && docker compose down -v

up:
	@./src/main/bash/runrag.sh $(DATA_FOLDER)

down:
	@./src/main/bash/stoprag.sh

restart: down up

ingest:
	@./src/main/bash/ingest.sh $(DATA_FOLDER)

redeploy-api:
	cd $(DOCKER_DIR) && docker compose up -d --build api

example-chat:
	cd examples && ../gradlew runChat

example-kb:
	cd examples && ../gradlew runKb