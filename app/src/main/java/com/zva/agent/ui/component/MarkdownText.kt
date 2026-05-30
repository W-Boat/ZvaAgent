package com.zva.agent.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Minimal markdown renderer for chat messages.
 * Supports: headers, bold, italic, code, code blocks, lists, links, strikethrough.
 */
@Composable
fun MarkdownText(
    markdown: String,
    color: Color = Color.Unspecified,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }

    Column(modifier = modifier) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.CodeBlock -> {
                    CodeBlockContent(block.code, block.language)
                }
                is MdBlock.TextBlock -> {
                    Text(
                        text = parseInlineMarkdown(block.text),
                        color = color,
                        style = style,
                    )
                }
                is MdBlock.HeaderBlock -> {
                    Text(
                        text = parseInlineMarkdown(block.text),
                        color = color,
                        style = when (block.level) {
                            1 -> MaterialTheme.typography.headlineMedium
                            2 -> MaterialTheme.typography.headlineSmall
                            3 -> MaterialTheme.typography.titleLarge
                            else -> MaterialTheme.typography.titleMedium
                        }.copy(fontWeight = FontWeight.Bold),
                    )
                }
                is MdBlock.ListItemBlock -> {
                    Row {
                        Text(
                            text = if (block.isOrdered) "${block.index}. " else "• ",
                            color = color,
                            style = style,
                        )
                        Text(
                            text = parseInlineMarkdown(block.text),
                            color = color,
                            style = style,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CodeBlockContent(code: String, language: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.06f))
            .horizontalScroll(rememberScrollState())
            .padding(12.dp),
    ) {
        Column {
            if (language.isNotBlank()) {
                Text(
                    text = language,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.outline,
                        fontFamily = FontFamily.Monospace,
                    ),
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            Text(
                text = code.trimEnd(),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                ),
            )
        }
    }
}

// ── Block parsing ────────────────────────────────────────────────────────────

sealed class MdBlock {
    data class TextBlock(val text: String) : MdBlock()
    data class HeaderBlock(val level: Int, val text: String) : MdBlock()
    data class CodeBlock(val language: String, val code: String) : MdBlock()
    data class ListItemBlock(val isOrdered: Boolean, val index: Int, val text: String) : MdBlock()
}

private fun parseMarkdownBlocks(markdown: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val lines = markdown.lines()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]

        // Code block
        if (line.trimStart().startsWith("```")) {
            val lang = line.trimStart().removePrefix("```").trim()
            val code = StringBuilder()
            i++
            while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                code.appendLine(lines[i])
                i++
            }
            blocks.add(MdBlock.CodeBlock(lang, code.toString()))
            i++ // skip closing ```
            continue
        }

        // Header
        val headerMatch = Regex("^(#{1,6})\\s+(.+)").matchEntire(line)
        if (headerMatch != null) {
            blocks.add(MdBlock.HeaderBlock(headerMatch.groupValues[1].length, headerMatch.groupValues[2]))
            i++
            continue
        }

        // Unordered list
        val ulMatch = Regex("^[\\-*]\\s+(.+)").matchEntire(line)
        if (ulMatch != null) {
            blocks.add(MdBlock.ListItemBlock(isOrdered = false, index = 0, text = ulMatch.groupValues[1]))
            i++
            continue
        }

        // Ordered list
        val olMatch = Regex("^(\\d+)\\.\\s+(.+)").matchEntire(line)
        if (olMatch != null) {
            blocks.add(MdBlock.ListItemBlock(isOrdered = true, index = olMatch.groupValues[1].toInt(), text = olMatch.groupValues[2]))
            i++
            continue
        }

        // Regular text (merge consecutive non-empty lines)
        if (line.isNotBlank()) {
            blocks.add(MdBlock.TextBlock(line))
        }
        i++
    }

    return blocks
}

// ── Inline parsing ───────────────────────────────────────────────────────────

private fun parseInlineMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            // Bold: **text** or __text__
            if (i + 1 < text.length && (text.startsWith("**", i) || text.startsWith("__", i))) {
                val marker = text.substring(i, i + 2)
                val end = text.indexOf(marker, i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                    continue
                }
            }

            // Inline code: `text`
            if (text[i] == '`') {
                val end = text.indexOf('`', i + 1)
                if (end != -1) {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                    continue
                }
            }

            // Strikethrough: ~~text~~
            if (i + 1 < text.length && text.startsWith("~~", i)) {
                val end = text.indexOf("~~", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                    continue
                }
            }

            // Italic: *text* (but not **)
            if (text[i] == '*' && (i + 1 >= text.length || text[i + 1] != '*')) {
                val end = text.indexOf('*', i + 1)
                if (end != -1 && (end + 1 >= text.length || text[end + 1] != '*')) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                    continue
                }
            }

            // Link: [text](url)
            if (text[i] == '[') {
                val closeBracket = text.indexOf(']', i + 1)
                if (closeBracket != -1 && closeBracket + 1 < text.length && text[closeBracket + 1] == '(') {
                    val closeParen = text.indexOf(')', closeBracket + 2)
                    if (closeParen != -1) {
                        val linkText = text.substring(i + 1, closeBracket)
                        withStyle(SpanStyle(color = Color(0xFF6750A4), textDecoration = TextDecoration.Underline)) {
                            append(linkText)
                        }
                        i = closeParen + 1
                        continue
                    }
                }
            }

            append(text[i])
            i++
        }
    }
}
