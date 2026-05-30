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
import com.zva.agent.domain.task.NotificationHelper
import com.zva.agent.domain.tool.RecallMemoryTool
import com.zva.agent.domain.tool.RememberTool
import com.zva.agent.domain.tool.SendNotificationTool
import com.zva.agent.domain.tool.SetReminderTool
import com.zva.agent.domain.tool.ListSkillsTool
import com.zva.agent.domain.tool.CreateSubAgentTool
import com.zva.agent.domain.tool.ToolRegistry
import com.zva.agent.data.db.SubAgentDao
import com.zva.agent.data.db.SubAgentEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class ProcessStep(
    val id: Long = System.nanoTime(),
    val type: ProcessStepType,
    val label: String,
    val detail: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isComplete: Boolean = false,
)

enum class ProcessStepType {
    STATUS_CHANGE, TOOL_CALL, TOOL_RESULT, THINKING, REPLY
}

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val streamingMessage: ChatMessage? = null,  // current streaming message
    val status: AgentStatus = AgentStatus.IDLE,
    val isLoading: Boolean = false,
    val sessionId: String = UUID.randomUUID().toString(),
    val processSteps: List<ProcessStep> = emptyList(),
    val isProcessPanelExpanded: Boolean = false,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val agentEngine: AgentEngine,
    private val messageDao: MessageDao,
    private val sessionDao: SessionDao,
    private val memoryManager: MemoryManager,
    private val toolRegistry: ToolRegistry,
    private val notificationHelper: NotificationHelper,
    private val subAgentDao: SubAgentDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var messageCollectJob: Job? = null

    init {
        wireUpTools()
        loadSession(_uiState.value.sessionId)
    }

    fun loadSession(sessionId: String) {
        _uiState.update { it.copy(sessionId = sessionId, messages = emptyList(), processSteps = emptyList()) }
        messageCollectJob?.cancel()
        messageCollectJob = viewModelScope.launch {
            messageDao.getMessages(sessionId).collect { entities ->
                val messages = entities.map { it.toChatMessage() }
                _uiState.update { it.copy(messages = messages) }
            }
        }
    }

    private fun wireUpTools() {
        val rememberTool = toolRegistry.get("remember") as? RememberTool
        rememberTool?.onRemember = { content, category, importance ->
            memoryManager.remember(content, category, importance)
        }
        val recallTool = toolRegistry.get("recall_memory") as? RecallMemoryTool
        recallTool?.onRecall = { query ->
            memoryManager.recall(query)
        }
        val notifyTool = toolRegistry.get("send_notification") as? SendNotificationTool
        notifyTool?.onNotify = { title, message ->
            notificationHelper.send(title, message)
        }
        val reminderTool = toolRegistry.get("set_reminder") as? SetReminderTool
        reminderTool?.onSetReminder = { content, time ->
            notificationHelper.send("Reminder", "$content ($time)")
        }
        val listSkillsTool = toolRegistry.get("list_skills") as? ListSkillsTool
        listSkillsTool?.onListSkills = {
            val tools = toolRegistry.getAll()
            buildString {
                appendLine("Available tools (${tools.size}):")
                tools.forEach { t ->
                    appendLine("- ${t.name}: ${t.description}")
                }
            }
        }
        val createSubAgentTool = toolRegistry.get("create_sub_agent") as? CreateSubAgentTool
        createSubAgentTool?.onCreate = { name, role, prompt ->
            subAgentDao.insert(SubAgentEntity(name = name, role = role, systemPrompt = prompt))
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || _uiState.value.isLoading) return

        val sessionId = _uiState.value.sessionId

        viewModelScope.launch {
            val userEntity = MessageEntity(sessionId = sessionId, role = "user", content = text)
            messageDao.insert(userEntity)

            sessionDao.upsert(SessionEntity(sessionId = sessionId, title = text.take(50)))

            _uiState.update { it.copy(isLoading = true, processSteps = emptyList(), isProcessPanelExpanded = true) }

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

            agentEngine.processMessage(
                userMessage = text,
                conversationHistory = history,
                onStatusChange = { status ->
                    _uiState.update { st ->
                        st.copy(
                            status = status,
                            processSteps = st.processSteps + ProcessStep(
                                type = ProcessStepType.STATUS_CHANGE,
                                label = status.label,
                                detail = status.symbol,
                            )
                        )
                    }
                },
                onMessage = { chatMsg ->
                    if (chatMsg.isStreaming) {
                        // Streaming in progress — update live, don't save to DB yet
                        _uiState.update { it.copy(streamingMessage = chatMsg) }
                    } else {
                        // Final message — clear streaming, save to DB
                        _uiState.update { it.copy(streamingMessage = null) }

                        val stepType = when (chatMsg.speaker) {
                            is Speaker.Tool -> ProcessStepType.TOOL_RESULT
                            Speaker.Dia -> ProcessStepType.REPLY
                            Speaker.Zva -> ProcessStepType.REPLY
                            else -> ProcessStepType.REPLY
                        }
                        _uiState.update { st ->
                            st.copy(
                                processSteps = st.processSteps + ProcessStep(
                                    type = stepType,
                                    label = chatMsg.speaker.displayName,
                                    detail = chatMsg.content.take(200),
                                    isComplete = true,
                                )
                            )
                        }

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
                    }
                    }
                },
            )

            _uiState.update { it.copy(isLoading = false, status = AgentStatus.IDLE) }
        }
    }

    fun toggleProcessPanel() {
        _uiState.update { it.copy(isProcessPanelExpanded = !it.isProcessPanelExpanded) }
    }

    fun clearChat() {
        viewModelScope.launch {
            messageDao.deleteSession(_uiState.value.sessionId)
            val newId = UUID.randomUUID().toString()
            _uiState.update { it.copy(messages = emptyList(), sessionId = newId, processSteps = emptyList(), streamingMessage = null) }
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
    return ChatMessage(id = id, speaker = speaker, content = content, timestamp = timestamp)
}
