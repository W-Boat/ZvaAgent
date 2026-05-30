package com.zva.agent.domain.agent

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.zva.agent.data.api.AgentApi
import com.zva.agent.data.model.*
import com.zva.agent.domain.memory.MemoryManager
import com.zva.agent.domain.tool.ToolRegistry
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Status indicators for the agent UI.
 */
enum class AgentStatus(val symbol: String, val label: String) {
    IDLE("□", "Zva is waiting."),
    THINKING("◇", "Dia is thinking."),
    WORKING("●", "Dia is working."),
    REPLYING("◀", "Dia is replying."),
    CALLING_DIA("●Z", "Zva is calling Dia."),
    HERE("□Z", "Zva is here."),
}

/**
 * Represents who is currently speaking.
 */
sealed class Speaker(val displayName: String) {
    data object Zva : Speaker("Zva")
    data object Dia : Speaker("Dia")
    data object User : Speaker("You")
    data class Tool(val toolName: String) : Speaker(toolName)
}

/**
 * A message in the conversation.
 */
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

/**
 * Core agent engine. Orchestrates Zva → Dia → Tools loop.
 */
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

    /**
     * Process a user message through the Zva → Dia pipeline.
     * Returns a flow of ChatMessage updates (status changes, partial results, final answer).
     */
    suspend fun processMessage(
        userMessage: String,
        conversationHistory: List<ApiMessage>,
        onStatusChange: (AgentStatus) -> Unit,
        onMessage: (ChatMessage) -> Unit,
    ) {
        onStatusChange(AgentStatus.CALLING_DIA)

        // Build memory context
        val memoryContext = memoryManager.getMemoryContext()
        val systemPrompt = zvaSystemPrompt + memoryContext

        // Build messages for API
        val messages = mutableListOf(
            ApiMessage(role = "system", content = systemPrompt)
        )
        messages.addAll(conversationHistory)
        messages.add(ApiMessage(role = "user", content = userMessage))

        onStatusChange(AgentStatus.THINKING)

        // Run the agent loop (potentially multiple tool rounds)
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

            val response = try {
                val endpoint = getStoredEndpoint().trimEnd('/')
                api.chatCompletion(
                    url = "$endpoint/v1/chat/completions",
                    request = ChatCompletionRequest(
                        model = getStoredModel(),
                        messages = messages,
                        tools = toolDefs.ifEmpty { null },
                    ),
                    auth = "Bearer ${getStoredApiKey()}",
                )
            } catch (e: Exception) {
                onMessage(
                    ChatMessage(
                        speaker = Speaker.Zva,
                        content = "Something went wrong: ${e.message}",
                    )
                )
                onStatusChange(AgentStatus.IDLE)
                return
            }

            val choice = response.choices.firstOrNull() ?: break
            val assistantMsg = choice.message

            // Check if the model wants to call tools
            if (!assistantMsg.toolCalls.isNullOrEmpty()) {
                onStatusChange(AgentStatus.WORKING)

                // Add assistant message with tool calls
                messages.add(assistantMsg)

                // Execute each tool call
                for (toolCall in assistantMsg.toolCalls) {
                    val toolName = toolCall.function.name
                    val tool = toolRegistry.get(toolName)

                    val result = if (tool != null) {
                        try {
                            val args = gson.fromJson(toolCall.function.arguments, JsonObject::class.java)
                            tool.execute(args)
                        } catch (e: Exception) {
                            "Tool error: ${e.message}"
                        }
                    } else {
                        "Unknown tool: $toolName"
                    }

                    // Notify about tool execution
                    onMessage(
                        ChatMessage(
                            speaker = Speaker.Tool(toolName),
                            content = result,
                            toolCalls = listOf(ToolCallInfo(toolName, toolCall.function.arguments, result)),
                        )
                    )

                    // Add tool result to messages
                    messages.add(
                        ApiMessage(
                            role = "tool",
                            content = result,
                            toolCallId = toolCall.id,
                            name = toolName,
                        )
                    )
                }

                onStatusChange(AgentStatus.THINKING)
                // Continue the loop — model will respond after tool results
                continue
            }

            // No tool calls — this is the final text response
            onStatusChange(AgentStatus.REPLYING)

            val replyContent = assistantMsg.content ?: "(no response)"

            // Determine if this looks like Dia or Zva speaking
            val speaker = when {
                replyContent.contains("●") || replyContent.startsWith("Result:") ||
                    replyContent.startsWith("- ") || replyContent.contains("Done:") -> Speaker.Dia
                else -> Speaker.Zva
            }

            onMessage(
                ChatMessage(
                    speaker = speaker,
                    content = replyContent,
                )
            )

            // Auto-memory: let the model decide via tools, but also save conversation summary
            if (rounds == 1 && userMessage.length > 20) {
                // First round, no tools called — save a lightweight memory
                memoryManager.remember(
                    content = "User said: ${userMessage.take(100)}",
                    category = "episodic",
                    importance = 0.3f,
                )
            }

            onStatusChange(AgentStatus.IDLE)
            return
        }

        // Exceeded max rounds
        onMessage(
            ChatMessage(
                speaker = Speaker.Zva,
                content = "I've been working on this for a while. Let me know if you'd like me to continue.",
            )
        )
        onStatusChange(AgentStatus.IDLE)
    }

    private suspend fun getStoredModel(): String {
        return personaManager.settings.first().model.ifBlank { "gpt-4o-mini" }
    }

    private suspend fun getStoredApiKey(): String {
        return personaManager.settings.first().apiKey
    }

    private suspend fun getStoredEndpoint(): String {
        return personaManager.settings.first().apiEndpoint.ifBlank { "https://api.openai.com/" }
    }
}
