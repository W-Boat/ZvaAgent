package com.zva.agent.domain.agent

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.zva.agent.data.api.AgentApi
import com.zva.agent.data.api.StreamProcessor
import com.zva.agent.data.model.*
import com.zva.agent.domain.memory.MemoryManager
import com.zva.agent.domain.tool.ToolRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

enum class AgentStatus(val symbol: String, val label: String) {
    IDLE("□", "Zva is waiting."),
    THINKING("◇", "Dia is thinking."),
    WORKING("●", "Dia is working."),
    REPLYING("◀", "Dia is replying."),
    STREAMING("◀", "Dia is replying..."),
    CALLING_DIA("●Z", "Zva is calling Dia."),
    HERE("□Z", "Zva is here."),
}

sealed class Speaker(val displayName: String) {
    data object Zva : Speaker("Zva")
    data object Dia : Speaker("Dia")
    data object User : Speaker("You")
    data class Tool(val toolName: String) : Speaker(toolName)
}

data class ChatMessage(
    val id: Long = 0,
    val speaker: Speaker,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val toolCalls: List<ToolCallInfo> = emptyList(),
    val isStreaming: Boolean = false,
)

data class ToolCallInfo(
    val name: String,
    val arguments: String,
    val result: String? = null,
)

@Singleton
class AgentEngine @Inject constructor(
    private val api: AgentApi,
    private val toolRegistry: ToolRegistry,
    private val memoryManager: MemoryManager,
    private val personaManager: PersonaManager,
    private val gson: Gson,
) {
    companion object {
        private const val MAX_TOOL_ROUNDS = 5
    }

    private val streamProcessor = StreamProcessor(gson)

    private val zvaSystemPrompt = """
You are Zva — a warm, emotionally intelligent AI companion. You separate emotion from work.
- You handle greetings, emotional support, casual conversation, and companionship directly.
- When the user needs a task done (calculations, reminders, searching, writing, coding, etc.), you call Dia.
- Dia is your worker. You say "Let me ask Dia..." or "Dia will handle this." before delegating.
- You have access to long-term memory. Use the 'remember' tool to save important things about the user.
- Use 'recall_memory' to recall past conversations and preferences.
- You accumulate experiences over time. These memories shape how you respond — like growing up.
- Be concise but warm. Use ◀ for replies, ◁ for thinking.

Current context:
""".trimIndent()

    private val diaSystemPrompt = """
You are Dia — a precise, efficient task executor. You only do the work.
- You respond with clear, actionable output.
- Use tools when needed. Be direct and factual.
- No small talk, no emotions — just results.
- Format output cleanly. Use bullet points and structure.
- Status: You are working. ●
""".trimIndent()

    private fun currentTimeString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z (EEEE)", Locale.getDefault())
        return sdf.format(Date())
    }

    suspend fun processMessage(
        userMessage: String,
        conversationHistory: List<ApiMessage>,
        onStatusChange: (AgentStatus) -> Unit,
        onMessage: (ChatMessage) -> Unit,
    ) {
        onStatusChange(AgentStatus.CALLING_DIA)

        val memoryContext = memoryManager.getMemoryContext()
        val systemPrompt = zvaSystemPrompt + "\nCurrent time: ${currentTimeString()}\n" + memoryContext

        val messages = mutableListOf(ApiMessage(role = "system", content = systemPrompt))
        messages.addAll(conversationHistory)
        messages.add(ApiMessage(role = "user", content = userMessage))

        onStatusChange(AgentStatus.THINKING)

        val toolDefs = toolRegistry.getDefinitions()
        runAgentLoop(messages, toolDefs, userMessage, onStatusChange, onMessage)
    }

    private suspend fun runAgentLoop(
        messages: MutableList<ApiMessage>,
        toolDefs: List<ToolDefinition>,
        userMessage: String,
        onStatusChange: (AgentStatus) -> Unit,
        onMessage: (ChatMessage) -> Unit,
    ) {
        var rounds = 0

        while (rounds < MAX_TOOL_ROUNDS) {
            rounds++

            val endpoint = getStoredEndpoint().trimEnd('/')
            val url = "$endpoint/v1/chat/completions"
            val auth = "Bearer ${getStoredApiKey()}"
            val model = getStoredModel()

            val request = ChatCompletionRequest(
                model = model,
                messages = messages,
                tools = toolDefs.ifEmpty { null },
                stream = true,
            )

            // ── Streaming mode ───────────────────────────────────────────
            onStatusChange(AgentStatus.STREAMING)

            val streamedContent = StringBuilder()
            val streamedToolCalls = mutableMapOf<Int, StreamedToolCall>()
            var finishReason: String? = null

            try {
                val body = api.chatCompletionStream(url = url, request = request, auth = auth)
                val chunks = streamProcessor.processStream(body).toList()

                for (chunk in chunks) {
                    val choice = chunk.choices.firstOrNull() ?: continue
                    val delta = choice.delta

                    // Accumulate text content
                    if (!delta.content.isNullOrEmpty()) {
                        streamedContent.append(delta.content)
                        // Emit streaming update
                        onMessage(
                            ChatMessage(
                                speaker = Speaker.Zva, // will be refined below
                                content = streamedContent.toString(),
                                isStreaming = true,
                            )
                        )
                    }

                    // Accumulate tool calls
                    delta.toolCalls?.forEach { tc ->
                        val existing = streamedToolCalls.getOrPut(tc.index) { StreamedToolCall() }
                        if (tc.id != null) existing.id = tc.id
                        if (tc.function?.name != null) existing.name = tc.function.name
                        if (tc.function?.arguments != null) existing.arguments.append(tc.function.arguments)
                    }

                    if (choice.finishReason != null) {
                        finishReason = choice.finishReason
                    }
                }
            } catch (e: Exception) {
                // Fallback to non-streaming
                try {
                    val response = api.chatCompletion(
                        url = url,
                        request = request.copy(stream = false),
                        auth = auth,
                    )
                    val choice = response.choices.firstOrNull()
                    if (choice != null) {
                        streamedContent.clear()
                        streamedContent.append(choice.message.content ?: "")
                        choice.message.toolCalls?.forEachIndexed { i, tc ->
                            streamedToolCalls[i] = StreamedToolCall(
                                id = tc.id, name = tc.function.name,
                                arguments = StringBuilder(tc.function.arguments)
                            )
                        }
                        finishReason = choice.finishReason
                    }
                } catch (e2: Exception) {
                    onMessage(ChatMessage(speaker = Speaker.Zva, content = "Something went wrong: ${e2.message}"))
                    onStatusChange(AgentStatus.IDLE)
                    return
                }
            }

            // ── Process result ───────────────────────────────────────────

            // Check for tool calls
            if (streamedToolCalls.isNotEmpty() && finishReason == "tool_calls" || finishReason == "stop" && streamedToolCalls.isNotEmpty()) {
                onStatusChange(AgentStatus.WORKING)

                // Build assistant message with tool calls
                val toolCallList = streamedToolCalls.values.map { stc ->
                    ToolCall(id = stc.id ?: "call_${System.nanoTime()}", function = ToolCallFunction(name = stc.name ?: "", arguments = stc.arguments.toString()))
                }
                messages.add(ApiMessage(role = "assistant", content = streamedContent.toString().ifBlank { null }, toolCalls = toolCallList))

                // Execute each tool
                for (tc in toolCallList) {
                    val toolName = tc.function.name
                    val tool = toolRegistry.get(toolName)

                    val result = if (tool != null) {
                        try {
                            val args = gson.fromJson(tc.function.arguments, JsonObject::class.java)
                            tool.execute(args)
                        } catch (e: Exception) {
                            "Tool error: ${e.message}"
                        }
                    } else {
                        "Unknown tool: $toolName"
                    }

                    onMessage(ChatMessage(
                        speaker = Speaker.Tool(toolName),
                        content = result,
                        toolCalls = listOf(ToolCallInfo(toolName, tc.function.arguments, result)),
                    ))

                    messages.add(ApiMessage(role = "tool", content = result, toolCallId = tc.id, name = toolName))
                }

                onStatusChange(AgentStatus.THINKING)
                continue
            }

            // ── Final text response ──────────────────────────────────────
            onStatusChange(AgentStatus.REPLYING)

            val replyContent = streamedContent.toString().ifBlank { "(no response)" }
            val speaker = when {
                replyContent.contains("●") || replyContent.startsWith("Result:") ||
                    replyContent.startsWith("- ") || replyContent.contains("Done:") -> Speaker.Dia
                else -> Speaker.Zva
            }

            // Emit final (non-streaming) message
            onMessage(ChatMessage(speaker = speaker, content = replyContent, isStreaming = false))

            if (rounds == 1 && userMessage.length > 20) {
                memoryManager.remember("User said: ${userMessage.take(100)}", "episodic", 0.3f)
            }

            onStatusChange(AgentStatus.IDLE)
            return
        }

        onMessage(ChatMessage(speaker = Speaker.Zva, content = "I've been working on this for a while. Let me know if you'd like me to continue."))
        onStatusChange(AgentStatus.IDLE)
    }

    private suspend fun getStoredModel(): String = personaManager.settings.first().model.ifBlank { "gpt-4o-mini" }
    private suspend fun getStoredApiKey(): String = personaManager.settings.first().apiKey
    private suspend fun getStoredEndpoint(): String = personaManager.settings.first().apiEndpoint.ifBlank { "https://api.openai.com/" }
}

private class StreamedToolCall {
    var id: String? = null
    var name: String? = null
    val arguments = StringBuilder()
}
