package com.zva.agent.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zva.agent.domain.agent.ChatMessage
import com.zva.agent.domain.agent.Speaker
import com.zva.agent.ui.theme.DiaPrimary
import com.zva.agent.ui.theme.ZvaPrimary
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MessageBubble(message: ChatMessage, modifier: Modifier = Modifier) {
    val isUser = message.speaker is Speaker.User
    val isTool = message.speaker is Speaker.Tool
    val isDia = message.speaker is Speaker.Dia

    val alignment = if (isUser) Alignment.End else Alignment.Start
    val bubbleColor = when {
        isUser -> MaterialTheme.colorScheme.primary
        isTool -> MaterialTheme.colorScheme.surfaceVariant
        isDia -> DiaPrimary.copy(alpha = 0.15f)
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val textColor = when {
        isUser -> MaterialTheme.colorScheme.onPrimary
        isTool -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = alignment,
    ) {
        // Speaker label
        if (!isUser) {
            Text(
                text = when (val s = message.speaker) {
                    Speaker.Zva -> "◈ Zva"
                    Speaker.Dia -> "● Dia"
                    is Speaker.Tool -> "⚙ ${s.toolName}"
                    else -> ""
                },
                style = MaterialTheme.typography.labelSmall,
                color = when {
                    isDia -> DiaPrimary
                    isTool -> MaterialTheme.colorScheme.outline
                    else -> ZvaPrimary
                },
                modifier = Modifier.padding(start = 8.dp, bottom = 2.dp),
            )
        }

        // Bubble
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp,
                    )
                )
                .background(bubbleColor)
                .padding(12.dp),
        ) {
            if (isTool) {
                // Tool results in monospace
                Text(
                    text = message.content,
                    color = textColor,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                )
            } else {
                Text(
                    text = message.content,
                    color = textColor,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        // Timestamp
        Text(
            text = formatTime(message.timestamp),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(top = 2.dp, start = 8.dp, end = 8.dp),
        )
    }
}

@Composable
fun StatusIndicator(status: com.zva.agent.domain.agent.AgentStatus) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Text(
            text = status.symbol,
            color = ZvaPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = status.label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

private fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
