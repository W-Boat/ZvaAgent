package com.zva.agent.data.model

import com.google.gson.annotations.SerializedName

// ── Request ──────────────────────────────────────────────────────────────────

data class ChatCompletionRequest(
    val model: String,
    val messages: List<ApiMessage>,
    val tools: List<ToolDefinition>? = null,
    @SerializedName("tool_choice")
    val toolChoice: String? = null,
    val temperature: Float = 0.7f,
    @SerializedName("max_tokens")
    val maxTokens: Int = 2048,
    val stream: Boolean = false,
)

data class ApiMessage(
    val role: String,  // "system", "user", "assistant", "tool"
    val content: String? = null,
    @SerializedName("tool_calls")
    val toolCalls: List<ToolCall>? = null,
    @SerializedName("tool_call_id")
    val toolCallId: String? = null,
    val name: String? = null,
)

data class ToolDefinition(
    val type: String = "function",
    val function: FunctionDefinition,
)

data class FunctionDefinition(
    val name: String,
    val description: String,
    val parameters: FunctionParameters,
)

data class FunctionParameters(
    val type: String = "object",
    val properties: Map<String, ParameterProperty>,
    val required: List<String> = emptyList(),
)

data class ParameterProperty(
    val type: String,
    val description: String,
    @SerializedName("enum")
    val enumValues: List<String>? = null,
)

// ── Response ─────────────────────────────────────────────────────────────────

data class ChatCompletionResponse(
    val id: String?,
    val choices: List<Choice>,
    val usage: Usage?,
)

data class Choice(
    val index: Int,
    val message: ApiMessage,
    @SerializedName("finish_reason")
    val finishReason: String?,
)

data class Usage(
    @SerializedName("prompt_tokens")
    val promptTokens: Int,
    @SerializedName("completion_tokens")
    val completionTokens: Int,
    @SerializedName("total_tokens")
    val totalTokens: Int,
)

data class ToolCall(
    val id: String,
    val type: String = "function",
    val function: ToolCallFunction,
)

data class ToolCallFunction(
    val name: String,
    val arguments: String, // JSON string
)

// ── Streaming Response ───────────────────────────────────────────────────────

data class ChatCompletionChunk(
    val id: String?,
    val choices: List<ChunkChoice>,
)

data class ChunkChoice(
    val index: Int,
    val delta: ChunkDelta,
    @SerializedName("finish_reason")
    val finishReason: String?,
)

data class ChunkDelta(
    val role: String? = null,
    val content: String? = null,
    @SerializedName("tool_calls")
    val toolCalls: List<ChunkToolCall>? = null,
)

data class ChunkToolCall(
    val index: Int,
    val id: String? = null,
    val type: String? = null,
    val function: ChunkToolCallFunction? = null,
)

data class ChunkToolCallFunction(
    val name: String? = null,
    val arguments: String? = null,
)
