#!/usr/bin/env python3

from contextlib import asynccontextmanager
from fastapi import FastAPI, Request
from fastapi.templating import Jinja2Templates

from bedrock_api import router as bedrock_router
from shared import os_client, INDEX_NAME, RAG_SYSTEM_PROMPT_TEMPLATE, TEMPERATURE, MAX_TOKENS, EXPECTED_KB_ID, CHAT_MODEL


@asynccontextmanager
async def lifespan(app: FastAPI):
    if not os_client.indices.exists(INDEX_NAME):
        raise RuntimeError(f"OpenSearch index '{INDEX_NAME}' not found — run the ingestion service first.")
    count = os_client.count(index=INDEX_NAME, body={"query": {"match_all": {}}})["count"]
    print(f"✓ OpenSearch ready with {count} documents — listening on :8080")
    yield


app = FastAPI(lifespan=lifespan)
templates = Jinja2Templates(directory="/app")

app.include_router(bedrock_router)


@app.get("/")
def index(request: Request):
    return templates.TemplateResponse(
        request=request,
        name="index.html",
        context={"system_prompt": RAG_SYSTEM_PROMPT_TEMPLATE, "temperature": TEMPERATURE, "max_tokens": MAX_TOKENS, "kb_id": EXPECTED_KB_ID, "model_id": CHAT_MODEL},
    )


@app.get("/health")
def health():
    count = os_client.count(index=INDEX_NAME, body={"query": {"match_all": {}}})["count"]
    return {"status": "ok", "chunks": count}