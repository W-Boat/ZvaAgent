package com.zva.agent.ui.screen.me

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zva.agent.data.db.MemoryEntity
import com.zva.agent.data.db.PersonaEntity
import com.zva.agent.data.db.SubAgentEntity
import com.zva.agent.domain.agent.AppSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeScreen(viewModel: MeViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val memories by viewModel.memories.collectAsStateWithLifecycle(initialValue = emptyList())
    val personas by viewModel.personas.collectAsStateWithLifecycle(initialValue = emptyList())
    val subAgents by viewModel.subAgents.collectAsStateWithLifecycle(initialValue = emptyList())

    var showSettings by remember { mutableStateOf(false) }
    var showAddPersona by remember { mutableStateOf(false) }
    var showAddSubAgent by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }

    val tabs = listOf("Persona", "Memory", "SubAgent", "Settings")

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        // Header with gradient
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF7C4DFF).copy(alpha = 0.15f),
                                Color(0xFFFF6D9F).copy(alpha = 0.08f),
                                Color.Transparent,
                            )
                        )
                    )
                    .padding(top = 48.dp, bottom = 16.dp, start = 20.dp, end = 20.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Avatar
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF7C4DFF), Color(0xFFFF6D9F))
                                )
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("◈", fontSize = 28.sp, color = Color.White)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = settings.personaName.ifBlank { "Zva" },
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        if (settings.userName.isNotBlank()) {
                            Text(
                                text = "Companion to ${settings.userName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                        Text(
                            text = "DIA. Do it by agent.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF7C4DFF),
                        )
                    }
                }
            }
        }

        // Tab row
        item {
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.padding(horizontal = 8.dp),
                edgePadding = 8.dp,
            ) {
                tabs.forEachIndexed { i, title ->
                    Tab(selected = selectedTab == i, onClick = { selectedTab = i }, text = { Text(title) })
                }
            }
        }

        // Tab content
        when (selectedTab) {
            0 -> {
                // Persona tab
                item { SectionHeader("Personas (${personas.size})") }
                if (personas.isEmpty()) {
                    item { EmptyHint("No custom personas. Create one to customize Zva's behavior.") }
                }
                items(personas, key = { it.id }) { persona ->
                    PersonaCard(
                        persona = persona,
                        onSetDefault = { viewModel.setDefaultPersona(persona.id) },
                        onDelete = { viewModel.deletePersona(persona) },
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    AddButton("Add Persona") { showAddPersona = true }
                }
            }
            1 -> {
                // Memory tab
                item { SectionHeader("Memories (${memories.size})") }
                if (memories.isEmpty()) {
                    item { EmptyHint("No memories yet. Zva learns from your conversations over time.") }
                }
                items(memories, key = { it.id }) { memory ->
                    MemoryCard(memory = memory, onDelete = { viewModel.deleteMemory(memory) })
                }
            }
            2 -> {
                // SubAgent tab
                item { SectionHeader("Sub-Agents (${subAgents.size})") }
                if (subAgents.isEmpty()) {
                    item { EmptyHint("No sub-agents. Dia can create child agents for complex tasks.") }
                }
                items(subAgents, key = { it.id }) { agent ->
                    SubAgentCard(agent = agent, onDeactivate = { viewModel.deactivateSubAgent(agent) })
                }
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    AddButton("Create Sub-Agent") { showAddSubAgent = true }
                }
            }
            3 -> {
                // Settings tab
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    SettingsSection(settings = settings, onSave = { viewModel.saveSettings(it) })
                }
            }
        }

        item { Spacer(modifier = Modifier.height(32.dp)) }
    }

    // Dialogs
    if (showAddPersona) {
        AddPersonaDialog(onDismiss = { showAddPersona = false }, onAdd = { name, emoji, prompt, temp ->
            viewModel.addPersona(name, emoji, prompt, temp)
            showAddPersona = false
        })
    }
    if (showAddSubAgent) {
        AddSubAgentDialog(onDismiss = { showAddSubAgent = false }, onAdd = { name, role, prompt ->
            viewModel.addSubAgent(name, role, prompt)
            showAddSubAgent = false
        })
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
    )
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
    )
}

// ── Persona Card ─────────────────────────────────────────────────────────────

@Composable
private fun PersonaCard(persona: PersonaEntity, onSetDefault: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (persona.isDefault)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape)
                    .background(Color(0xFF7C4DFF).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(persona.emoji, fontSize = 20.sp)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(persona.name, fontWeight = FontWeight.Bold)
                    if (persona.isDefault) {
                        Spacer(modifier = Modifier.width(8.dp))
                        SuggestionChip(onClick = {}, label = { Text("Active", fontSize = 10.sp) }, modifier = Modifier.height(24.dp))
                    }
                }
                Text(
                    text = persona.systemPrompt.take(80) + if (persona.systemPrompt.length > 80) "..." else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                Text("Temp: ${persona.temperature}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
            Column {
                if (!persona.isDefault) IconButton(onClick = onSetDefault, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Check, contentDescription = "Set default", modifier = Modifier.size(18.dp))
                }
                if (!persona.isDefault) IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

// ── Memory Card ──────────────────────────────────────────────────────────────

@Composable
private fun MemoryCard(memory: MemoryEntity, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier.size(32.dp).clip(CircleShape)
                    .background(
                        when (memory.category) {
                            "preference" -> Color(0xFFFF6D9F).copy(alpha = 0.2f)
                            "semantic" -> Color(0xFF7C4DFF).copy(alpha = 0.2f)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    when (memory.category) {
                        "preference" -> Icons.Default.Favorite
                        "semantic" -> Icons.Default.Lightbulb
                        else -> Icons.Default.History
                    },
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = when (memory.category) {
                        "preference" -> Color(0xFFFF6D9F)
                        "semantic" -> Color(0xFF7C4DFF)
                        else -> MaterialTheme.colorScheme.outline
                    },
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(memory.content, style = MaterialTheme.typography.bodySmall)
                Row {
                    Text(memory.category, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("★${(memory.importance * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = Color(0xFF7C4DFF))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("accessed ${memory.accessCount}x", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Delete", modifier = Modifier.size(14.dp))
            }
        }
    }
}

// ── SubAgent Card ────────────────────────────────────────────────────────────

@Composable
private fun SubAgentCard(agent: SubAgentEntity, onDeactivate: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF386A1F).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("●", color = Color(0xFF386A1F), fontSize = 18.sp)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(agent.name, fontWeight = FontWeight.Bold)
                Text(agent.role, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            IconButton(onClick = onDeactivate, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Deactivate", modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ── Settings Section ─────────────────────────────────────────────────────────

@Composable
private fun SettingsSection(settings: AppSettings, onSave: (AppSettings) -> Unit) {
    var endpoint by remember { mutableStateOf(settings.apiEndpoint) }
    var apiKey by remember { mutableStateOf(settings.apiKey) }
    var model by remember { mutableStateOf(settings.model) }
    var personaName by remember { mutableStateOf(settings.personaName) }
    var userName by remember { mutableStateOf(settings.userName) }

    LaunchedEffect(settings) {
        endpoint = settings.apiEndpoint
        apiKey = settings.apiKey
        model = settings.model
        personaName = settings.personaName
        userName = settings.userName
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("API Configuration", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            OutlinedTextField(value = endpoint, onValueChange = { endpoint = it }, label = { Text("API Endpoint") }, placeholder = { Text("https://api.openai.com") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = apiKey, onValueChange = { apiKey = it }, label = { Text("API Key") }, placeholder = { Text("sk-...") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text("Model") }, placeholder = { Text("gpt-4o-mini") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = personaName, onValueChange = { personaName = it }, label = { Text("Persona Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = userName, onValueChange = { userName = it }, label = { Text("Your Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = { onSave(settings.copy(apiEndpoint = endpoint, apiKey = apiKey, model = model, personaName = personaName, userName = userName)) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save") }
        }
    }
}

// ── Add Persona Dialog ───────────────────────────────────────────────────────

@Composable
private fun AddPersonaDialog(onDismiss: () -> Unit, onAdd: (String, String, String, Float) -> Unit) {
    var name by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("◈") }
    var prompt by remember { mutableStateOf("") }
    var temp by remember { mutableFloatStateOf(0.7f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Persona") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = emoji, onValueChange = { emoji = it }, label = { Text("Emoji") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = prompt, onValueChange = { prompt = it }, label = { Text("System Prompt") }, minLines = 3, maxLines = 6, modifier = Modifier.fillMaxWidth())
                Text("Temperature: ${"%.1f".format(temp)}", style = MaterialTheme.typography.labelMedium)
                Slider(value = temp, onValueChange = { temp = it }, valueRange = 0f..1f, steps = 10)
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(name, emoji, prompt, temp) }, enabled = name.isNotBlank() && prompt.isNotBlank()) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ── Add SubAgent Dialog ──────────────────────────────────────────────────────

@Composable
private fun AddSubAgentDialog(onDismiss: () -> Unit, onAdd: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Sub-Agent") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = role, onValueChange = { role = it }, label = { Text("Role") }, placeholder = { Text("e.g. researcher, coder, writer") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = prompt, onValueChange = { prompt = it }, label = { Text("System Prompt") }, minLines = 3, maxLines = 6, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(name, role, prompt) }, enabled = name.isNotBlank() && prompt.isNotBlank()) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AddButton(text: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text)
    }
}
