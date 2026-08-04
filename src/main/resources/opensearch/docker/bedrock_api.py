#!/usr/bin/env python3

import base64
import json
import logging
import struct
import zlib
from fastapi import APIRouter, HTTPException, Request
from fastapi.responses import StreamingResponse
from typing import AsyncIterator

from shared import (client, vector_search, CHAT_MODEL, EXPECTED_KB_ID, MAX_SIMS,
                     TEMPERATURE, MAX_TOKENS, RAG_SYSTEM_PROMPT_TEMPLATE, MIN_SCORE_FOR_ANSWER)

logger = logging.getLogger(__name__)
router = APIRouter()


def _encode_header(name: str, value: str) -> bytes:
    nb, vb = name.encode(), value.encode()
    return struct.pack("B", len(nb)) + nb + struct.pack("B", 7) + struct.pack(">H", len(vb)) + vb


_STATIC_HEADERS: bytes = (
        _encode_header(":event-type", "chunk")
        + _encode_header(":content-type", "application/json")
        + _encode_header(":message-type", "event")
)


def _encode_event(payload: bytes, event_type: str = "chunk") -> bytes:
    if event_type == "chunk":
        headers = _STATIC_HEADERS
    else:
        headers = (
                _encode_header(":event-type", event_type)
                + _encode_header(":content-type", "application/json")
                + _encode_header(":message-type", "event")
        )
    total = 4 + 4 + 4 + len(headers) + len(payload) + 4
    prelude = struct.pack(">I", total) + struct.pack(">I", len(headers))
    prelude_crc = zlib.crc32(prelude) & 0xFFFFFFFF
    msg = prelude + struct.pack(">I", prelude_crc) + headers + payload
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


def _build_converse_oai_messages(body: dict) -> tuple[list[dict], int, float]:
    system = body.get("system", [])
    messages = body.get("messages", [])
    inf_config = body.get("inferenceConfig", {})
    max_tokens = inf_config.get("maxTokens", MAX_TOKENS)
    temperature = inf_config.get("temperature", TEMPERATURE)

    oai_messages = []
    if system:
        oai_messages.append({"role": "system", "content": " ".join(b.get("text", "") for b in system)})
    for m in messages:
        text_parts = [c.get("text", "") for c in m.get("content", []) if "text" in c]
        oai_messages.append({"role": m["role"], "content": " ".join(text_parts)})
    return oai_messages, max_tokens, temperature


@router.post("/model/{model_id:path}/converse")
async def converse(model_id: str, request: Request):
    body = await request.json()
    oai_messages, max_tokens, temperature = _build_converse_oai_messages(body)
    response = client.chat.completions.create(
        model=CHAT_MODEL, messages=oai_messages, temperature=temperature, max_tokens=max_tokens,
    )
    text = response.choices[0].message.content or "" if response.choices else ""
    input_tokens = getattr(response.usage, "prompt_tokens", 0) or 0
    output_tokens = getattr(response.usage, "completion_tokens", 0) or 0
    return {
        "output": {"message": {"role": "assistant", "content": [{"text": text}]}},
        "stopReason": "end_turn",
        "usage": {"inputTokens": input_tokens, "outputTokens": output_tokens, "totalTokens": input_tokens + output_tokens},
        "metrics": {"latencyMs": 0},
    }


@router.post("/model/{model_id:path}/converse-stream")
async def converse_stream(model_id: str, request: Request):
    body = await request.json()
    oai_messages, max_tokens, temperature = _build_converse_oai_messages(body)

    async def event_stream() -> AsyncIterator[bytes]:
        try:
            for chunk in client.chat.completions.create(
                    model=CHAT_MODEL, messages=oai_messages, temperature=temperature,
                    max_tokens=max_tokens, stream=True,
            ):
                if not chunk.choices:
                    continue
                text = chunk.choices[0].delta.content or ""
                if text:
                    payload = json.dumps({"delta": {"text": text}, "contentBlockIndex": 0}).encode()
                    yield _encode_event(payload, "contentBlockDelta")
            stop_payload = json.dumps({"stopReason": "end_turn"}).encode()
            yield _encode_event(stop_payload, "messageStop")
        except Exception:
            logger.exception("Converse streaming failed (model=%s)", CHAT_MODEL)

    return StreamingResponse(event_stream(), media_type="application/vnd.amazon.eventstream")


@router.post("/model/{model_id:path}/invoke")
async def invoke_model(model_id: str, request: Request):
    oai_messages, max_tokens, temperature = _build_oai_messages(await request.json())
    response = client.chat.completions.create(
        model=CHAT_MODEL,
        messages=oai_messages,
        temperature=temperature,
        max_tokens=max_tokens,
    )
    text = response.choices[0].message.content or "" if response.choices else ""
    return {
        "type": "message",
        "role": "assistant",
        "content": [{"type": "text", "text": text}],
        "stop_reason": "end_turn",
    }


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


def _parse_kb_request(body: dict) -> tuple[str, str, int, dict]:
    kb_config = body.get("retrieveAndGenerateConfiguration", {}).get("knowledgeBaseConfiguration", {})
    knowledge_base_id = kb_config.get("knowledgeBaseId", "")
    query = body.get("input", {}).get("text", "")
    num_results = (
        kb_config.get("retrievalConfiguration", {})
        .get("vectorSearchConfiguration", {})
        .get("numberOfResults", MAX_SIMS)
    )
    gen_config = kb_config.get("generationConfiguration", {})
    text_inf = gen_config.get("inferenceConfig", {}).get("textInferenceConfig", {})
    temperature = text_inf.get("temperature", TEMPERATURE)
    max_tokens = text_inf.get("maxTokens", MAX_TOKENS)
    prompt_template = gen_config.get("promptTemplate", {}).get("textPromptTemplate", "")
    return knowledge_base_id, query, num_results, {
        "temperature": temperature, "max_tokens": max_tokens, "prompt_template": prompt_template,
    }


def _do_rag(query: str, num_results: int, gen_opts: dict) -> tuple[str, list, list]:
    hits = vector_search(query, num_results)
    citations = []
    context_parts = []
    for score, h in hits:
        if score < MIN_SCORE_FOR_ANSWER:
            continue
        context_parts.append(h["text"])
        citations.append({
            "generatedResponsePart": {"textResponsePart": {"text": h["text"][:200]}},
            "retrievedReferences": [{
                "content": {"text": h["text"]},
                "location": {"s3Location": {"uri": f"local://{h['file']}"}},
            }],
        })
    context = "\n\n".join(context_parts)
    tmpl = gen_opts["prompt_template"] or RAG_SYSTEM_PROMPT_TEMPLATE
    system_prompt = tmpl.format(context=context) if "{context}" in tmpl else f"{tmpl}\n\nContext:\n{context}"
    messages = [
        {"role": "system", "content": system_prompt},
        {"role": "user", "content": query},
    ]
    return messages, citations, gen_opts


@router.post("/retrieveAndGenerate")
async def retrieve_and_generate(request: Request):
    body = await request.json()
    knowledge_base_id, query, num_results, gen_opts = _parse_kb_request(body)
    if knowledge_base_id != EXPECTED_KB_ID:
        raise HTTPException(status_code=404, detail=f"Knowledge base '{knowledge_base_id}' not found.")
    if not query:
        raise HTTPException(status_code=400, detail="Missing required field: input.text")

    messages, citations, gen_opts = _do_rag(query, num_results, gen_opts)
    response = client.chat.completions.create(
        model=CHAT_MODEL, messages=messages,
        temperature=gen_opts["temperature"], max_tokens=gen_opts["max_tokens"],
    )
    output_text = response.choices[0].message.content or ""

    return {"output": {"text": output_text}, "citations": citations}


@router.post("/retrieveAndGenerateStream")
async def retrieve_and_generate_stream(request: Request):
    body = await request.json()
    knowledge_base_id, query, num_results, gen_opts = _parse_kb_request(body)
    if knowledge_base_id != EXPECTED_KB_ID:
        raise HTTPException(status_code=404, detail=f"Knowledge base '{knowledge_base_id}' not found.")
    if not query:
        raise HTTPException(status_code=400, detail="Missing required field: input.text")

    messages, citations, gen_opts = _do_rag(query, num_results, gen_opts)

    async def event_stream() -> AsyncIterator[bytes]:
        try:
            if citations:
                cite_payload = json.dumps(citations[0]).encode()
                yield _encode_event(cite_payload, "citation")
            for chunk in client.chat.completions.create(
                    model=CHAT_MODEL, messages=messages,
                    temperature=gen_opts["temperature"], max_tokens=gen_opts["max_tokens"],
                    stream=True,
            ):
                if not chunk.choices:
                    continue
                text = chunk.choices[0].delta.content or ""
                if text:
                    payload = json.dumps({"text": text}).encode()
                    yield _encode_event(payload, "output")
        except Exception:
            logger.exception("RetrieveAndGenerateStream failed")

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