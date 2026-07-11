#!/usr/bin/env python3
"""
AWS Bedrock API-compatible endpoints for testing applications built for AWS Bedrock.
Implements:
- POST /model/{model_id}/invoke-with-response-stream (chat streaming)
- POST /knowledgebases/{knowledge_base_id}/retrieve (knowledge base retrieval)
"""

import base64
import json
import logging
import struct
import zlib
from fastapi import APIRouter, HTTPException, Request
from fastapi.responses import StreamingResponse
from typing import AsyncIterator

from shared import client, vector_search, CHAT_MODEL, EXPECTED_KB_ID, MAX_SIMS, TEMPERATURE, MAX_TOKENS

logger = logging.getLogger(__name__)
router = APIRouter()


# ── AWS Event Stream encoding ─────────────────────────────────────────────────
def _encode_header(name: str, value: str) -> bytes:
    nb, vb = name.encode(), value.encode()
    return struct.pack("B", len(nb)) + nb + struct.pack("B", 7) + struct.pack(">H", len(vb)) + vb


_STATIC_HEADERS: bytes = (
        _encode_header(":event-type", "chunk")
        + _encode_header(":content-type", "application/json")
        + _encode_header(":message-type", "event")
)


def _encode_event(payload: bytes) -> bytes:
    total = 4 + 4 + 4 + len(_STATIC_HEADERS) + len(payload) + 4
    prelude = struct.pack(">I", total) + struct.pack(">I", len(_STATIC_HEADERS))
    prelude_crc = zlib.crc32(prelude) & 0xFFFFFFFF
    msg = prelude + struct.pack(">I", prelude_crc) + _STATIC_HEADERS + payload
    return msg + struct.pack(">I", zlib.crc32(msg) & 0xFFFFFFFF)


def _build_oai_messages(body: dict) -> tuple[list[dict], int, float]:
    messages: list[dict] = body.get("messages", [])
    system_prompt: str = body.get("system", "")
    max_tokens: int = body.get("max_tokens", MAX_TOKENS)
    temperature: float = body.get("temperature", TEMPERATURE)

    oai_messages: list[dict] = []
    if system_prompt:
        oai_messages.append({"role": "system", "content": system_prompt})
    oai_messages.extend({"role": m["role"], "content": m["content"]} for m in messages)
    return oai_messages, max_tokens, temperature


# ── Bedrock endpoints ─────────────────────────────────────────────────────────
@router.post("/model/{model_id:path}/invoke-with-response-stream")
async def invoke_stream(model_id: str, request: Request):
    oai_messages, max_tokens, temperature = _build_oai_messages(await request.json())

    async def event_stream() -> AsyncIterator[bytes]:
        try:
            for chunk in client.chat.completions.create(
                    model=CHAT_MODEL,
                    messages=oai_messages,
                    temperature=temperature,
                    max_tokens=max_tokens,
                    stream=True,
            ):
                if not chunk.choices:
                    continue
                text = chunk.choices[0].delta.content or ""
                if text:
                    inner = json.dumps(
                        {
                            "type": "content_block_delta",
                            "index": 0,
                            "delta": {"type": "text_delta", "text": text},
                        }
                    ).encode()
                    payload = json.dumps(
                        {"bytes": base64.b64encode(inner).decode()}
                    ).encode()
                    yield _encode_event(payload)
        except Exception:
            logger.exception("LLM streaming failed (model=%s)", CHAT_MODEL)

    return StreamingResponse(event_stream(), media_type="application/vnd.amazon.eventstream")


@router.post("/knowledgebases/{knowledge_base_id}/retrieve")
async def retrieve(knowledge_base_id: str, request: Request):
    if knowledge_base_id != EXPECTED_KB_ID:
        raise HTTPException(
            status_code=404,
            detail=f"Knowledge base '{knowledge_base_id}' not found. Expected: '{EXPECTED_KB_ID}'"
        )
    body = await request.json()
    try:
        query = body["retrievalQuery"]["text"]
    except KeyError as e:
        raise HTTPException(
            status_code=400,
            detail=f"Missing required field in request body: {e}"
        )
    num_results = (
        body.get("retrievalConfiguration", {})
        .get("vectorSearchConfiguration", {})
        .get("numberOfResults", MAX_SIMS)
    )
    hits = vector_search(query, num_results)
    return {
        "retrievalResults": [
            {
                "content": {"text": h["text"]},
                "location": {"s3Location": {"uri": f"local://{h['file']}"}},
                "score": s,
            }
            for s, h in hits
        ]
    }
