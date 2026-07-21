# Basaltrock Examples

[![Java 21+](https://img.shields.io/badge/Java-21%2B-blue)](https://openjdk.org/)
[![Gradle 9.5](https://img.shields.io/badge/Gradle-9.5-blue?logo=gradle)](https://gradle.org/)
[![Docker 4.40+](https://img.shields.io/badge/Docker-4.40%2B%20(Model%20Runner)-blue?logo=docker)](https://www.docker.com/)
[![JitPack](https://jitpack.io/v/bohdanartiushenko/basaltrock.svg)](https://jitpack.io/#bohdanartiushenko/basaltrock)

Standalone project showing how to use Basaltrock as a dependency.

## Dependency

```gradle
repositories {
    maven { url 'https://jitpack.io' }
}
dependencies {
    implementation 'com.github.bohdanartiushenko:basaltrock:v0.0.3'
}
```

## Examples

### ChatStreamingExample

Chat streaming with real-time response printing.

```bash
./gradlew runChat -Pargs="how to run with custom query?"
```

### KnowledgeBaseRetrievalExample

Knowledge base search with results display.

```bash
./gradlew runKb -Pargs="how to run with custom query?"
```

## How It Works

The examples automatically:
1. Check if a container is already running at `http://localhost:80`
2. If not, start a new container with Docker Compose
3. Use the data in `src/main/resources/data/` as the knowledge base
4. Run the example

The container stays running after completion — run multiple examples without restarting.

## Requirements

- Java 21+
- Docker (with Model Runner support)