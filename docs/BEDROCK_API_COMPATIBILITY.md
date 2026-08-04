# AWS Bedrock API Compatibility

Basaltrock server compatibility with AWS Bedrock SDK operations.

## BedrockRuntime (software.amazon.awssdk:bedrockruntime)

| Operation | Path | Notes |
|-----------|------|-------|
| [InvokeModel](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_InvokeModel.html) | `POST /model/{modelId}/invoke` | Anthropic Messages API format |
| [InvokeModelWithResponseStream](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_InvokeModelWithResponseStream.html) | `POST /model/{modelId}/invoke-with-response-stream` | AWS event-stream protocol |
| [Converse](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_Converse.html) | `POST /model/{modelId}/converse` | Structured messages, usage stats |
| [ConverseStream](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_ConverseStream.html) | `POST /model/{modelId}/converse-stream` | contentBlockDelta/messageStop events |
| [CountTokens](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_CountTokens.html) | `POST /model/{modelId}/count-tokens` | Approximation (len/4), not real tokenizer |
| [ApplyGuardrail](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_ApplyGuardrail.html) | `POST /guardrail/{guardrailId}/apply` | Keyword-based filtering via GUARDRAIL_BLOCKED_WORDS env |
| [InvokeModelWithBidirectionalStream](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_InvokeModelWithBidirectionalStream.html) | `POST /model/{modelId}/invoke-with-bidirectional-stream` | AWS event-stream protocol, same as response stream |
| [StartAsyncInvoke](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_StartAsyncInvoke.html) | `POST /async-invoke` | Background thread execution |
| [GetAsyncInvoke](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_GetAsyncInvoke.html) | `GET /async-invoke/{invocationArn}` | Returns status and output |
| [ListAsyncInvokes](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_ListAsyncInvokes.html) | `GET /async-invoke` | Supports statusEquals, maxResults filters |

## BedrockAgentRuntime (software.amazon.awssdk:bedrockagentruntime)

| Operation | Path | Notes |
|-----------|------|-------|
| [Retrieve](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_agent-runtime_Retrieve.html) | `POST /knowledgebases/{knowledgeBaseId}/retrieve` | Vector search with scores |
| [RetrieveAndGenerate](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_agent-runtime_RetrieveAndGenerate.html) | `POST /retrieveAndGenerate` | RAG with citations, generationConfiguration support |
| [RetrieveAndGenerateStream](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_agent-runtime_RetrieveAndGenerateStream.html) | `POST /retrieveAndGenerateStream` | Streaming RAG with citation/output events |
| [InvokeAgent](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_agent-runtime_InvokeAgent.html) | `POST /agents/{agentId}/agentAliases/{agentAliasId}/sessions/{sessionId}/text` | Streaming chunks via event-stream |
| [InvokeInlineAgent](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_agent-runtime_InvokeInlineAgent.html) | `POST /agents/{sessionId}` | Streaming chunks, supports instruction |
| [InvokeFlow](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_agent-runtime_InvokeFlow.html) | `POST /flows/{flowIdentifier}/aliases/{flowAliasIdentifier}` | flowOutputEvent + flowCompletionEvent |
| [Rerank](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_agent-runtime_Rerank.html) | `POST /rerank` | L2 distance scoring via embedding model |
| [OptimizePrompt](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_agent-runtime_OptimizePrompt.html) | `POST /optimize-prompt` | LLM-based prompt rewriting, streaming response |
| [CreateSession](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_agent-runtime_CreateSession.html) | `PUT /sessions` | In-memory session store |
| [GetSession](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_agent-runtime_GetSession.html) | `GET /sessions/{sessionId}` | Returns session metadata and status |
| [DeleteSession](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_agent-runtime_DeleteSession.html) | `DELETE /sessions/{sessionId}` | Removes session from in-memory store |
| [GenerateQuery](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_agent-runtime_GenerateQuery.html) | `POST /generateQuery` | LLM-based natural language to SQL conversion |
