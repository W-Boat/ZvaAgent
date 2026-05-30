package com.zva.agent.ui.screen.me

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zva.agent.domain.agent.AppSettings
import com.zva.agent.ui.theme.ZvaPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeScreen(viewModel: MeViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val memories by viewModel.memories.collectAsStateWithLifecycle(initialValue = emptyList())
    var showSettings by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        TopAppBar(
            title = { Text("Me") },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        )

        // Persona Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "◈ ${settings.personaName}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "DIA. Do it by agent.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                )
                if (settings.userName.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Companion to ${settings.userName}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        // Status indicators legend
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Status Indicators", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                StatusRow("◀", "Dia is replying.")
                StatusRow("◇", "Dia is thinking.")
                StatusRow("●", "Dia is working.")
                StatusRow("○", "Dia is running.")
                StatusRow("□", "Zva is waiting.")
                StatusRow("●Z", "Zva is calling Dia.")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Memory section
        Text(
            text = "Memories (${memories.size})",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (memories.isEmpty()) {
            Text(
                text = "No memories yet. Zva learns from your conversations.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        } else {
            memories.take(10).forEach { memory ->
                ListItem(
                    headlineContent = { Text(memory.content, maxLines = 2) },
                    supportingContent = {
                        Text("${memory.category} · importance: ${(memory.importance * 100).toInt()}%")
                    },
                    leadingContent = {
                        Icon(Icons.Default.Memory, contentDescription = null)
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Settings button
        OutlinedButton(
            onClick = { showSettings = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Icon(Icons.Default.Settings, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("API Settings")
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    // Settings dialog
    if (showSettings) {
        SettingsDialog(
            settings = settings,
            onDismiss = { showSettings = false },
            onSave = { newSettings ->
                viewModel.saveSettings(newSettings)
                showSettings = false
            },
        )
    }
}

@Composable
private fun StatusRow(symbol: String, label: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            text = symbol,
            style = MaterialTheme.typography.bodyMedium,
            color = ZvaPrimary,
            modifier = Modifier.width(32.dp),
        )
        Text(text = label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SettingsDialog(
    settings: AppSettings,
    onDismiss: () -> Unit,
    onSave: (AppSettings) -> Unit,
) {
    var endpoint by remember { mutableStateOf(settings.apiEndpoint) }
    var apiKey by remember { mutableStateOf(settings.apiKey) }
    var model by remember { mutableStateOf(settings.model) }
    var personaName by remember { mutableStateOf(settings.personaName) }
    var userName by remember { mutableStateOf(settings.userName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("API Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = endpoint,
                    onValueChange = { endpoint = it },
                    label = { Text("API Endpoint") },
                    placeholder = { Text("https://api.openai.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    placeholder = { Text("sk-...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("Model") },
                    placeholder = { Text("gpt-4o-mini") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = personaName,
                    onValueChange = { personaName = it },
                    label = { Text("Persona Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = userName,
                    onValueChange = { userName = it },
                    label = { Text("Your Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    settings.copy(
                        apiEndpoint = endpoint,
                        apiKey = apiKey,
                        model = model,
                        personaName = personaName,
                        userName = userName,
                    )
                )
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
