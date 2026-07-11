#!/usr/bin/env python3
"""
Shared configuration, helpers, and data store for Basaltrock API services.
"""

import os
from openai import OpenAI
from opensearchpy import OpenSearch

# ── Config ────────────────────────────────────────────────────────────────────
BASE_URL = os.environ.get(
    "MODEL_RUNNER_BASE_URL",
    "http://model-runner.docker.internal/engines/llama.cpp/v1/",
)
CHAT_MODEL = os.environ.get("MODEL_RUNNER_LLM_CHAT", "ai/gemma3:1B-Q4_K_M")
EMBED_MODEL = os.environ.get("MODEL_RUNNER_LLM_EMBEDDING", "ai/nomic-embed-text-v2-moe")
OPENSEARCH_URL = os.environ.get("OPENSEARCH_URL", "http://opensearch:9200")
INDEX_NAME = os.environ.get("INDEX_NAME", "basaltrock-knowledge-base-default-index")
MIN_SCORE = float(os.environ.get("MIN_SCORE", "0"))
MAX_SIMS = int(os.environ.get("MAX_SIMILARITIES", "30"))
TEMPERATURE = float(os.environ.get("TEMPERATURE", "3"))
MAX_TOKENS = int(os.environ.get("MAX_TOKENS", "4096"))
EXPECTED_KB_ID = os.environ.get("KNOWLEDGE_BASE_ID", "basaltrock-knowledge-base-id")
MIN_SCORE_FOR_ANSWER = float(os.environ.get("MIN_SCORE_FOR_ANSWER", "0"))
RAG_SYSTEM_PROMPT_TEMPLATE = os.environ.get(
    "RAG_SYSTEM_PROMPT_TEMPLATE",
    "Answer the question using only the following context from the knowledge base. Context: {context}"
)

client = OpenAI(base_url=BASE_URL, api_key="dummy")
os_client = OpenSearch([OPENSEARCH_URL], use_ssl=False, verify_certs=False)

# ── Vector store ──────────────────────────────────────────────────────────────
store: list[dict] = []


# ── Helpers ───────────────────────────────────────────────────────────────────
def embed(text: str) -> list[float]:
    return client.embeddings.create(model=EMBED_MODEL, input=text).data[0].embedding


def vector_search(query: str, limit: int) -> list[tuple[float, dict]]:
    if len(query) > 1500:
        query = query[:1500]
    q_vec = embed(query)

    body = {
        "size": limit,
        "query": {
            "knn": {
                "vec": {
                    "vector": q_vec,
                    "k": limit
                }
            }
        }
    }

    if MIN_SCORE > 0:
        body["min_score"] = MIN_SCORE

    response = os_client.search(index=INDEX_NAME, body=body)

    return [
        (hit["_score"], {"file": hit["_source"]["file"], "text": hit["_source"]["text"]})
        for hit in response["hits"]["hits"]
    ]
