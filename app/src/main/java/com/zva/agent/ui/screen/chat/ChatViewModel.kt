package com.zva.agent.ui.screen.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zva.agent.data.db.MessageDao
import com.zva.agent.data.db.MessageEntity
import com.zva.agent.data.db.SessionEntity
import com.zva.agent.data.db.SessionDao
import com.zva.agent.data.model.ApiMessage
import com.zva.agent.domain.agent.AgentEngine
import com.zva.agent.domain.agent.AgentStatus
import com.zva.agent.domain.agent.ChatMessage
import com.zva.agent.domain.agent.Speaker
import com.zva.agent.domain.memory.MemoryManager
import com.zva.agent.domain.tool.RecallMemoryTool
import com.zva.agent.domain.tool.RememberTool
import com.zva.agent.domain.tool.ToolRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val status: AgentStatus = AgentStatus.IDLE,
    val isLoading: Boolean = false,
    val sessionId: String = UUID.randomUUID().toString(),
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val agentEngine: AgentEngine,
    private val messageDao: MessageDao,
    private val sessionDao: SessionDao,
    private val memoryManager: MemoryManager,
    private val toolRegistry: ToolRegistry,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        // Wire up memory tools
        val rememberTool = toolRegistry.get("remember") as? RememberTool
        rememberTool?.onRemember = { content, category, importance ->
            memoryManager.remember(content, category, importance)
        }

        val recallTool = toolRegistry.get("recall_memory") as? RecallMemoryTool
        recallTool?.onRecall = { query ->
            memoryManager.recall(query)
        }

        // Load existing session or create new
        viewModelScope.launch {
            val sessionId = _uiState.value.sessionId
            messageDao.getMessages(sessionId).collect { entities ->
                val messages = entities.map { it.toChatMessage() }
                _uiState.update { it.copy(messages = messages) }
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || _uiState.value.isLoading) return

        val sessionId = _uiState.value.sessionId

        viewModelScope.launch {
            // Save user message
            val userEntity = MessageEntity(
                sessionId = sessionId,
                role = "user",
                content = text,
            )
            messageDao.insert(userEntity)

            // Ensure session exists
            sessionDao.upsert(
                SessionEntity(
                    sessionId = sessionId,
                    title = text.take(50),
                )
            )

            _uiState.update { it.copy(isLoading = true) }

            // Build conversation history for API
            val history = _uiState.value.messages.map { msg ->
                ApiMessage(
                    role = when (msg.speaker) {
                        Speaker.User -> "user"
                        Speaker.Zva, Speaker.Dia -> "assistant"
                        is Speaker.Tool -> "assistant"
                    },
                    content = msg.content,
                )
            }

            // Process through agent
            agentEngine.processMessage(
                userMessage = text,
                conversationHistory = history,
                onStatusChange = { status ->
                    _uiState.update { it.copy(status = status) }
                },
                onMessage = { chatMsg ->
                    // Save to DB
                    viewModelScope.launch {
                        val entity = MessageEntity(
                            sessionId = sessionId,
                            role = when (chatMsg.speaker) {
                                Speaker.Zva -> "assistant_zva"
                                Speaker.Dia -> "assistant_dia"
                                is Speaker.Tool -> "tool"
                                Speaker.User -> "user"
                            },
                            content = chatMsg.content,
                            toolName = (chatMsg.speaker as? Speaker.Tool)?.toolName,
                        )
                        messageDao.insert(entity)
                    }
                },
            )

            _uiState.update { it.copy(isLoading = false, status = AgentStatus.IDLE) }
        }
    }

    fun clearChat() {
        viewModelScope.launch {
            messageDao.deleteSession(_uiState.value.sessionId)
            _uiState.update {
                it.copy(
                    messages = emptyList(),
                    sessionId = UUID.randomUUID().toString(),
                )
            }
        }
    }
}

private fun MessageEntity.toChatMessage(): ChatMessage {
    val speaker = when (role) {
        "user" -> Speaker.User
        "assistant_zva" -> Speaker.Zva
        "assistant_dia" -> Speaker.Dia
        "tool" -> Speaker.Tool(toolName ?: "tool")
        else -> Speaker.Zva
    }
    return ChatMessage(
        id = id,
        speaker = speaker,
        content = content,
        timestamp = timestamp,
    )
}
