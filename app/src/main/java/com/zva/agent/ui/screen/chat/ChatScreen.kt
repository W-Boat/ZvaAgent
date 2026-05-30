package com.zva.agent.ui.screen.chat

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zva.agent.domain.agent.AgentStatus
import com.zva.agent.ui.component.MarkdownText
import com.zva.agent.ui.component.MessageBubble
import com.zva.agent.ui.component.StatusIndicator
import com.zva.agent.ui.theme.ZvaPrimary
import com.zva.agent.ui.theme.DiaPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    initialSessionId: String? = null,
    onNavigateToHistory: () -> Unit = {},
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(initialSessionId) {
        if (initialSessionId != null) {
            viewModel.loadSession(initialSessionId)
        }
    }

    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.messages.size, uiState.status) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar — compact, no extra whitespace
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Zva", style = MaterialTheme.typography.titleMedium)
                    AnimatedVisibility(visible = uiState.status != AgentStatus.IDLE) {
                        Row(modifier = Modifier.padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(uiState.status.symbol, color = ZvaPrimary, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(uiState.status.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            },
            actions = {
                if (uiState.processSteps.isNotEmpty()) {
                    IconButton(onClick = { viewModel.toggleProcessPanel() }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            if (uiState.isProcessPanelExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Toggle process panel",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                IconButton(onClick = onNavigateToHistory, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.History, contentDescription = "History", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = { viewModel.clearChat() }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Clear chat", modifier = Modifier.size(18.dp))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )

        // Dia Process Panel
        AnimatedVisibility(
            visible = uiState.isProcessPanelExpanded && uiState.processSteps.isNotEmpty(),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            DiaProcessPanel(
                steps = uiState.processSteps,
                status = uiState.status,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }

        // Messages
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (uiState.messages.isEmpty() && !uiState.isLoading) {
                item { WelcomeCard() }
            }

            items(uiState.messages, key = { it.id }) { message ->
                MessageBubble(message = message)
            }

            // Streaming message (live, not yet saved)
            val streaming = uiState.streamingMessage
            if (streaming != null) {
                item(key = "streaming") {
                    MessageBubble(message = streaming)
                }
            }

            if (uiState.isLoading && streaming == null) {
                item {
                    Row(modifier = Modifier.padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = ZvaPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(uiState.status.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }

        // Input area
        Surface(tonalElevation = 3.dp) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Message Zva…") },
                    modifier = Modifier.weight(1f),
                    maxLines = 4,
                    shape = MaterialTheme.shapes.extraLarge,
                )
                Spacer(modifier = Modifier.width(8.dp))
                FilledIconButton(
                    onClick = { viewModel.sendMessage(inputText); inputText = "" },
                    enabled = inputText.isNotBlank() && !uiState.isLoading,
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            }
        }
    }
}

@Composable
private fun DiaProcessPanel(
    steps: List<ProcessStep>,
    status: AgentStatus,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = status.symbol,
                    color = DiaPrimary,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    fontSize = 16.sp,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = status.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${steps.size} steps",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            // Steps
            steps.takeLast(8).forEach { step ->
                Row(
                    modifier = Modifier.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = when (step.type) {
                            ProcessStepType.STATUS_CHANGE -> "◇"
                            ProcessStepType.TOOL_CALL -> "⚙"
                            ProcessStepType.TOOL_RESULT -> "✓"
                            ProcessStepType.THINKING -> "◇"
                            ProcessStepType.REPLY -> "◀"
                        },
                        color = when (step.type) {
                            ProcessStepType.TOOL_RESULT -> DiaPrimary
                            ProcessStepType.REPLY -> ZvaPrimary
                            else -> MaterialTheme.colorScheme.outline
                        },
                        fontSize = 12.sp,
                        modifier = Modifier.width(20.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = step.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (step.detail.isNotBlank()) {
                            Text(
                                text = step.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeCard() {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Welcome to Zva", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Your companion AI. Dia does the work, Zva stays with you.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text("◈ Zva is here. □Z", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}
