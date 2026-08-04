#!/usr/bin/env python3

from contextlib import asynccontextmanager
from fastapi import FastAPI, Request
from fastapi.responses import FileResponse
from fastapi.templating import Jinja2Templates

from basaltrock_api import router as basaltrock_router
from bedrock_api import router as bedrock_router
from shared import os_client, INDEX_NAME, RAG_SYSTEM_PROMPT_TEMPLATE, TEMPERATURE, MAX_TOKENS


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
app.include_router(basaltrock_router)


@app.get("/")
def index(request: Request):
    return templates.TemplateResponse(
        request=request,
        name="index.html",
        context={"system_prompt": RAG_SYSTEM_PROMPT_TEMPLATE, "temperature": TEMPERATURE, "max_tokens": MAX_TOKENS},
    )


@app.get("/health")
def health():
    count = os_client.count(index=INDEX_NAME, body={"query": {"match_all": {}}})["count"]
    return {"status": "ok", "chunks": count}