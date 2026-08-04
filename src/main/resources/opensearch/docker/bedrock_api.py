#!/usr/bin/env python3

import base64
import json
import logging
import os
import struct
import threading
import uuid
import zlib
from datetime import datetime, timezone
from fastapi import APIRouter, HTTPException, Request
from fastapi.responses import StreamingResponse
from typing import AsyncIterator

from shared import (client, embed, vector_search, CHAT_MODEL, EXPECTED_KB_ID, MAX_SIMS,
                     TEMPERATURE, MAX_TOKENS, RAG_SYSTEM_PROMPT_TEMPLATE, MIN_SCORE_FOR_ANSWER)

logger = logging.getLogger(__name__)
router = APIRouter()


def _encode_header(name: str, value: str) -> bytes:
    nb, vb = name.encode(), value.encode()
    return struct.pack("B", len(nb)) + nb + struct.pack("B", 7) + struct.pack(">H", len(vb)) + vb

@router.post("/optimize-prompt")
async def optimize_prompt(request: Request):
    body = await request.json()
    input_text = body.get("input", {}).get("textPrompt", {}).get("text", "")
    if not input_text:
        raise HTTPException(status_code=400, detail="Missing input text")

    response = client.chat.completions.create(
        model=CHAT_MODEL,
        messages=[
            {"role": "system", "content": "Rewrite the following prompt to be clearer, more specific, and more effective. Return only the improved prompt text, nothing else."},
            {"role": "user", "content": input_text},
        ],
        temperature=TEMPERATURE,
        max_tokens=MAX_TOKENS,
    )
    optimized = response.choices[0].message.content or input_text

    async def event_stream() -> AsyncIterator[bytes]:
        payload = json.dumps({"optimizedPrompt": {"textPrompt": {"text": optimized}}}).encode()
        yield _encode_event(payload, "optimizedPromptEvent")

    return StreamingResponse(event_stream(), media_type="application/vnd.amazon.eventstream")


def _l2_score(a, b):
    l2 = sum((x - y) ** 2 for x, y in zip(a, b)) ** 0.5
    return 1.0 / (1.0 + l2)

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


@router.post("/guardrail/{guardrail_id}/version/{guardrail_version}/apply")
async def apply_guardrail(guardrail_id: str, guardrail_version: str, request: Request):
    body = await request.json()
    content_blocks = body.get("content", [])
    text_parts = []
    for block in content_blocks:
        if "text" in block:
            text_parts.append(block["text"].get("text", ""))
    full_text = " ".join(text_parts).lower()

    blocked_words = os.environ.get("GUARDRAIL_BLOCKED_WORDS", "").lower().split(",")
    blocked_words = [w.strip() for w in blocked_words if w.strip()]

    intervened = any(w in full_text for w in blocked_words) if blocked_words else False

    if intervened:
        return {
            "usage": {"topicPolicyUnits": 1, "contentPolicyUnits": 1, "wordPolicyUnits": 1, "sensitiveInformationPolicyUnits": 0, "sensitiveInformationPolicyFreeUnits": 0, "contextualGroundingPolicyUnits": 0},
            "action": "GUARDRAIL_INTERVENED",
            "actionReason": "Content blocked by guardrail policy",
            "outputs": [{"text": "Sorry, I can't respond to this request."}],
            "assessments": [{"wordPolicy": {"customWords": [{"match": w, "action": "BLOCKED"} for w in blocked_words if w in full_text]}}],
        }
    return {
        "usage": {"topicPolicyUnits": 1, "contentPolicyUnits": 1, "wordPolicyUnits": 1, "sensitiveInformationPolicyUnits": 0, "sensitiveInformationPolicyFreeUnits": 0, "contextualGroundingPolicyUnits": 0},
        "action": "NONE",
        "outputs": [{"text": t} for t in text_parts],
        "assessments": [],
    }


@router.post("/model/{model_id:path}/count-tokens")
async def count_tokens(model_id: str, request: Request):
    body = await request.json()
    inp = body.get("input", {})
    text = ""
    if "converse" in inp:
        converse = inp["converse"]
        for s in converse.get("system", []):
            text += s.get("text", "") + " "
        for m in converse.get("messages", []):
            for c in m.get("content", []):
                text += c.get("text", "") + " "
    elif "invokeModel" in inp:
        text = inp["invokeModel"].get("body", "")
    token_count = max(1, len(text) // 4)
    return {"inputTokens": token_count}


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


@router.post("/model/{model_id:path}/invoke-with-bidirectional-stream")
async def invoke_bidirectional_stream(model_id: str, request: Request):
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
            logger.exception("LLM bidirectional streaming failed (model=%s)", CHAT_MODEL)

    return StreamingResponse(event_stream(), media_type="application/vnd.amazon.eventstream")


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


def _do_rag(query: str, num_results: int, gen_opts: dict) -> tuple[list, list, dict]:
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
                "score": score,
            }],
        })
    context = "\n\n".join(context_parts)
    tmpl = gen_opts["prompt_template"] or RAG_SYSTEM_PROMPT_TEMPLATE
    try:
        system_prompt = tmpl.format(context=context) if "{context}" in tmpl else f"{tmpl}\n\nContext:\n{context}"
    except (KeyError, ValueError):
        system_prompt = f"{tmpl}\n\nContext:\n{context}"
    messages = [
        {"role": "system", "content": system_prompt},
        {"role": "user", "content": f"{query}\n\nIMPORTANT: Answer in the same language as this question."},
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


@router.post("/rerank")
async def rerank(request: Request):
    body = await request.json()
    queries = body.get("queries", [])
    sources = body.get("sources", [])
    query_text = queries[0].get("textQuery", {}).get("text", "") if queries else ""
    if not query_text:
        raise HTTPException(status_code=400, detail="Missing query text")

    query_vec = embed(query_text)

    doc_texts = []
    for src in sources:
        inline = src.get("inlineDocumentSource", {})
        text = inline.get("textDocument", {}).get("text", "")
        doc_texts.append(text if text else " ")

    doc_vecs = [embed(t) for t in doc_texts]

    scored = [(i, _l2_score(query_vec, dv), doc_texts[i]) for i, dv in enumerate(doc_vecs)]
    scored.sort(key=lambda x: x[1], reverse=True)

    return {
        "results": [
            {
                "index": idx,
                "relevanceScore": score,
                "document": {"type": "TEXT", "textDocument": {"text": text}},
            }
            for idx, score, text in scored
        ]
    }


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


_async_invocations: dict = {}
_async_lock = threading.Lock()


def _run_async_invoke(invocation_arn: str, model_input: dict):
    try:
        oai_messages, max_tokens, temperature = _build_oai_messages(model_input)
        response = client.chat.completions.create(
            model=CHAT_MODEL, messages=oai_messages, temperature=temperature, max_tokens=max_tokens,
        )
        text = response.choices[0].message.content or "" if response.choices else ""
        output = {
            "type": "message",
            "role": "assistant",
            "content": [{"type": "text", "text": text}],
            "stop_reason": "end_turn",
        }
        with _async_lock:
            _async_invocations[invocation_arn]["status"] = "Completed"
            _async_invocations[invocation_arn]["endTime"] = datetime.now(timezone.utc).isoformat()
            _async_invocations[invocation_arn]["output"] = output
    except Exception as e:
        with _async_lock:
            _async_invocations[invocation_arn]["status"] = "Failed"
            _async_invocations[invocation_arn]["failureMessage"] = str(e)
            _async_invocations[invocation_arn]["endTime"] = datetime.now(timezone.utc).isoformat()


@router.post("/async-invoke")
async def start_async_invoke(request: Request):
    body = await request.json()
    model_id = body.get("modelId", "")
    model_input = body.get("modelInput", {})
    output_config = body.get("outputDataConfig", {})

    invocation_arn = f"arn:aws:bedrock:us-east-1:000000000000:async-invoke/{uuid.uuid4()}"
    now = datetime.now(timezone.utc).isoformat()

    with _async_lock:
        _async_invocations[invocation_arn] = {
            "invocationArn": invocation_arn,
            "modelArn": model_id,
            "status": "InProgress",
            "submitTime": now,
            "lastModifiedTime": now,
            "outputDataConfig": output_config,
        }

    thread = threading.Thread(target=_run_async_invoke, args=(invocation_arn, model_input), daemon=True)
    thread.start()

    return {"invocationArn": invocation_arn}


@router.get("/async-invoke")
async def list_async_invokes(request: Request):
    params = request.query_params
    status_filter = params.get("statusEquals")
    max_results = int(params.get("maxResults", "20"))
    with _async_lock:
        items = list(_async_invocations.values())
    if status_filter:
        items = [i for i in items if i.get("status") == status_filter]
    items = items[:max_results]
    summaries = []
    for i in items:
        summaries.append({
            "invocationArn": i["invocationArn"],
            "modelArn": i.get("modelArn", ""),
            "status": i["status"],
            "submitTime": i.get("submitTime", ""),
            "lastModifiedTime": i.get("lastModifiedTime", i.get("submitTime", "")),
            "outputDataConfig": i.get("outputDataConfig", {}),
        })
    return {"asyncInvokeSummaries": summaries}


@router.get("/async-invoke/{invocation_arn:path}")
async def get_async_invoke(invocation_arn: str):
    with _async_lock:
        invocation = _async_invocations.get(invocation_arn)
    if not invocation:
        raise HTTPException(status_code=404, detail=f"Invocation '{invocation_arn}' not found")
    return invocation


_sessions: dict = {}
_sessions_lock = threading.Lock()


@router.put("/sessions")
async def create_session(request: Request):
    body = await request.json()
    session_id = str(uuid.uuid4())
    now = datetime.now(timezone.utc).isoformat()
    session = {
        "sessionId": session_id,
        "sessionArn": f"arn:aws:bedrock:us-east-1:000000000000:session/{session_id}",
        "sessionStatus": "ACTIVE",
        "createdAt": now,
        "lastUpdatedAt": now,
        "encryptionKeyArn": body.get("encryptionKeyArn", ""),
        "sessionMetadata": body.get("sessionMetadata", {}),
    }
    with _sessions_lock:
        _sessions[session_id] = session
    return session


@router.get("/sessions/{session_id}")
async def get_session(session_id: str):
    with _sessions_lock:
        session = _sessions.get(session_id)
    if not session:
        raise HTTPException(status_code=404, detail=f"Session '{session_id}' not found")
    return session


@router.delete("/sessions/{session_id}")
async def delete_session(session_id: str):
    with _sessions_lock:
        session = _sessions.pop(session_id, None)
    if not session:
        raise HTTPException(status_code=404, detail=f"Session '{session_id}' not found")
    return {"sessionId": session_id}


@router.post("/generateQuery")
async def generate_query(request: Request):
    body = await request.json()
    transform_type = body.get("transformationType", "SQL")
    query_generation_input = body.get("queryGenerationInput", {})
    natural_language = query_generation_input.get("text", "")
    if not natural_language:
        raise HTTPException(status_code=400, detail="Missing queryGenerationInput.text")

    response = client.chat.completions.create(
        model=CHAT_MODEL,
        messages=[
            {"role": "system", "content": f"Convert the following natural language request into a {transform_type} query. Return only the query, nothing else."},
            {"role": "user", "content": natural_language},
        ],
        temperature=0.0,
        max_tokens=MAX_TOKENS,
    )
    generated = response.choices[0].message.content or ""
    return {
        "queries": [{"type": transform_type, "text": generated}],
    }


@router.post("/agents/{agent_id}/agentAliases/{agent_alias_id}/sessions/{session_id}/text")
async def invoke_agent(agent_id: str, agent_alias_id: str, session_id: str, request: Request):
    body = await request.json()
    input_text = body.get("inputText", "")

    async def event_stream() -> AsyncIterator[bytes]:
        try:
            for chunk in client.chat.completions.create(
                    model=CHAT_MODEL,
                    messages=[{"role": "user", "content": input_text}],
                    temperature=TEMPERATURE, max_tokens=MAX_TOKENS, stream=True,
            ):
                if not chunk.choices:
                    continue
                text = chunk.choices[0].delta.content or ""
                if text:
                    payload = json.dumps({"bytes": base64.b64encode(text.encode()).decode()}).encode()
                    yield _encode_event(payload, "chunk")
        except Exception:
            logger.exception("InvokeAgent failed (agent=%s)", agent_id)

    return StreamingResponse(event_stream(), media_type="application/vnd.amazon.eventstream",
                             headers={"x-amzn-bedrock-agent-session-id": session_id,
                                      "x-amz-bedrock-agent-content-type": "text/plain"})


@router.post("/flows/{flow_id}/aliases/{flow_alias_id}")
async def invoke_flow(flow_id: str, flow_alias_id: str, request: Request):
    body = await request.json()
    inputs = body.get("inputs", [])
    input_text = ""
    for inp in inputs:
        content = inp.get("content", {})
        doc = content.get("document")
        if isinstance(doc, str):
            input_text = doc
            break
        elif isinstance(doc, dict):
            input_text = json.dumps(doc)
            break

    async def event_stream() -> AsyncIterator[bytes]:
        try:
            response = client.chat.completions.create(
                model=CHAT_MODEL,
                messages=[{"role": "user", "content": input_text}],
                temperature=TEMPERATURE, max_tokens=MAX_TOKENS,
            )
            text = response.choices[0].message.content or "" if response.choices else ""
            output_payload = json.dumps({
                "nodeName": "FlowOutputNode",
                "nodeType": "FlowOutputNode",
                "content": {"document": text},
            }).encode()
            yield _encode_event(output_payload, "flowOutputEvent")
            completion_payload = json.dumps({"completionReason": "SUCCESS"}).encode()
            yield _encode_event(completion_payload, "flowCompletionEvent")
        except Exception:
            logger.exception("InvokeFlow failed (flow=%s)", flow_id)

    return StreamingResponse(event_stream(), media_type="application/vnd.amazon.eventstream")


@router.post("/agents/{session_id}")
async def invoke_inline_agent(session_id: str, request: Request):
    body = await request.json()
    input_text = body.get("inputText", "")
    instruction = body.get("instruction", "")
    messages = []
    if instruction:
        messages.append({"role": "system", "content": instruction})
    messages.append({"role": "user", "content": input_text})

    async def event_stream() -> AsyncIterator[bytes]:
        try:
            for chunk in client.chat.completions.create(
                    model=CHAT_MODEL, messages=messages,
                    temperature=TEMPERATURE, max_tokens=MAX_TOKENS, stream=True,
            ):
                if not chunk.choices:
                    continue
                text = chunk.choices[0].delta.content or ""
                if text:
                    payload = json.dumps({"bytes": base64.b64encode(text.encode()).decode()}).encode()
                    yield _encode_event(payload, "chunk")
        except Exception:
            logger.exception("InvokeInlineAgent failed (session=%s)", session_id)

    return StreamingResponse(event_stream(), media_type="application/vnd.amazon.eventstream",
                             headers={"x-amzn-bedrock-agent-session-id": session_id,
                                      "x-amz-bedrock-agent-content-type": "text/plain"})