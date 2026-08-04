#!/usr/bin/env python3

from fastapi import APIRouter, Request

from shared import client, vector_search, CHAT_MODEL, TEMPERATURE, MAX_TOKENS, RAG_SYSTEM_PROMPT_TEMPLATE, \
    MIN_SCORE_FOR_ANSWER

router = APIRouter()


def format_sources(hits):
    return [{"file": h["file"], "text": h["text"], "score": s} for s, h in hits]


@router.get("/basaltrock/chat")
def chat_simple(q: str):
    messages = [{"role": "user", "content": q}]
    response = client.chat.completions.create(
        model=CHAT_MODEL,
        messages=messages,
        temperature=TEMPERATURE,
        max_tokens=MAX_TOKENS,
    )
    if not response.choices:
        return {"answer": ""}
    return {"answer": response.choices[0].message.content}


@router.get("/basaltrock/search")
def search_simple(q: str, limit: int = 5):
    hits = vector_search(q, limit)
    return {"results": format_sources(hits)}


@router.post("/basaltrock/search/kb")
async def search_kb_with_chat(request: Request):
    body = await request.json()
    q = body.get("retrievalQuery", {}).get("text")
    limit = body.get("retrievalConfiguration", {}).get("vectorSearchConfiguration", {}).get("numberOfResults", 5)
    maxTokens = body.get("maxTokens", MAX_TOKENS)
    system_prompt_override = body.get("system")
    temperature = body.get("temperature", TEMPERATURE)

    hits = vector_search(q, limit)

    if not hits:
        return {"answer": "No relevant information found in the knowledge base.", "sources": []}

    high_scored_hits = [(s, h) for s, h in hits if s >= MIN_SCORE_FOR_ANSWER]

    if not high_scored_hits:
        return {
            "answer": f"Found {len(hits)} results, but none met the minimum relevance score of {MIN_SCORE_FOR_ANSWER:.2f}. Try rephrasing your question.",
            "sources": format_sources(hits)
        }

    context_parts = [f"From {h['file']}: {h['text']}" for s, h in high_scored_hits]
    context = "\n\n".join(context_parts)

    if system_prompt_override:
        system_prompt = system_prompt_override.format(
            context=context) if "{context}" in system_prompt_override else f"{system_prompt_override}\n\nContext:\n{context}"
    else:
        system_prompt = RAG_SYSTEM_PROMPT_TEMPLATE.format(context=context)

    messages = [
        {"role": "system", "content": system_prompt},
        {"role": "user", "content": f"Question: {q}\n\nIMPORTANT: Answer in the same language as this question."}
    ]

    response = client.chat.completions.create(
        model=CHAT_MODEL,
        messages=messages,
        temperature=temperature,
        max_tokens=maxTokens,
    )

    if not response.choices:
        return {"answer": "", "sources": format_sources(high_scored_hits)}
    return {
        "answer": response.choices[0].message.content,
        "sources": format_sources(high_scored_hits)
    }