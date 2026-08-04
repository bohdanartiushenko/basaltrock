# AWS Bedrock API Compatibility

Basaltrock server compatibility with AWS Bedrock SDK operations.

## BedrockRuntime (software.amazon.awssdk:bedrockruntime)

| Operation | Path | Status          | Notes                                                     |
|-----------|------|-----------------|-----------------------------------------------------------|
| [InvokeModel](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_InvokeModel.html) | `POST /model/{modelId}/invoke` | ✅ Full          | Anthropic Messages API format                             |
| [InvokeModelWithResponseStream](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_InvokeModelWithResponseStream.html) | `POST /model/{modelId}/invoke-with-response-stream` | ✅ Full          | AWS event-stream protocol                                 |
| [Converse](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_Converse.html) | `POST /model/{modelId}/converse` | ✅ Full          | Structured messages, usage stats                          |
| [ConverseStream](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_ConverseStream.html) | `POST /model/{modelId}/converse-stream` | ✅ Full          | contentBlockDelta/messageStop events                      |
| [CountTokens](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_CountTokens.html) | `POST /model/{modelId}/count-tokens` | ✅ Full          | Approximation (len/4), not real tokenizer                 |
| [ApplyGuardrail](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_ApplyGuardrail.html) | `POST /guardrail/{guardrailId}/apply` | ✅ Full          | Keyword-based filtering via GUARDRAIL_BLOCKED_WORDS env |
| [InvokeModelWithBidirectionalStream](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_InvokeModelWithBidirectionalStream.html) | `POST /model/{modelId}/invoke-with-bidirectional-stream` | ✅ Full          | AWS event-stream protocol, same as response stream        |
| [StartAsyncInvoke](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_StartAsyncInvoke.html) | | ❌ Not supported |                                                           |
| [GetAsyncInvoke](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_GetAsyncInvoke.html) | | ❌ Not supported |                                                           |
| [ListAsyncInvokes](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_ListAsyncInvokes.html) | | ❌ Not supported |                                                           |

## BedrockAgentRuntime (software.amazon.awssdk:bedrockagentruntime)

| Operation | Path | Status | Notes |
|-----------|------|--------|-------|
| [Retrieve](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_agent-runtime_Retrieve.html) | `POST /knowledgebases/{knowledgeBaseId}/retrieve` | ✅ Full | Vector search with scores |
| [RetrieveAndGenerate](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_agent-runtime_RetrieveAndGenerate.html) | `POST /retrieveAndGenerate` | ✅ Full | RAG with citations, generationConfiguration (temperature, maxTokens, promptTemplate) |
| [RetrieveAndGenerateStream](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_agent-runtime_RetrieveAndGenerateStream.html) | `POST /retrieveAndGenerateStream` | ✅ Full | Streaming RAG with citation/output events |
| [InvokeAgent](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_agent-runtime_InvokeAgent.html) | | ❌ Not supported | |
| [InvokeInlineAgent](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_agent-runtime_InvokeInlineAgent.html) | | ❌ Not supported | |
| [InvokeFlow](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_agent-runtime_InvokeFlow.html) | | ❌ Not supported | |
| [Rerank](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_agent-runtime_Rerank.html) | `POST /rerank` | ✅ Full | L2 distance scoring via embedding model |
| [OptimizePrompt](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_agent-runtime_OptimizePrompt.html) | `POST /optimize-prompt` | ✅ Full | LLM-based prompt rewriting, streaming response |
| [CreateSession](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_agent-runtime_CreateSession.html) / [GetSession](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_agent-runtime_GetSession.html) / [DeleteSession](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_agent-runtime_DeleteSession.html) | | ❌ Not supported | |
| [GenerateQuery](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_agent-runtime_GenerateQuery.html) | | ❌ Not supported | |

## Legend

- ✅ **Full** — compatible with AWS SDK, tested with Testcontainers
- ⚠️ **Partial** — works but with approximations or missing features
- ❌ **Not supported** — not implemented