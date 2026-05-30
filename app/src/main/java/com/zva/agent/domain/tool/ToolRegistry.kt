package com.zva.agent.domain.tool

import com.zva.agent.data.model.FunctionDefinition
import com.zva.agent.data.model.FunctionParameters
import com.zva.agent.data.model.ParameterProperty
import com.zva.agent.data.model.ToolDefinition
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*

interface Tool {
    val name: String
    val description: String
    val parameters: FunctionParameters
    suspend fun execute(args: JsonObject): String

    fun toDefinition() = ToolDefinition(
        function = FunctionDefinition(name = name, description = description, parameters = parameters)
    )
}

// ── Get Current Time ─────────────────────────────────────────────────────────

class GetTimeTool : Tool {
    override val name = "get_current_time"
    override val description = "Get the current date and time with timezone info"
    override val parameters = FunctionParameters(
        properties = mapOf(
            "format" to ParameterProperty(type = "string", description = "Date format pattern, default 'yyyy-MM-dd HH:mm:ss z'")
        )
    )

    override suspend fun execute(args: JsonObject): String {
        val format = if (args.has("format")) args.get("format").asString else "yyyy-MM-dd HH:mm:ss z"
        val now = Date()
        val tz = TimeZone.getDefault()
        return "Current time: ${SimpleDateFormat(format, Locale.getDefault()).apply { timeZone = tz }.format(now)} (timezone: ${tz.id})"
    }
}

// ── Calculator ───────────────────────────────────────────────────────────────

class CalculatorTool : Tool {
    override val name = "calculate"
    override val description = "Evaluate a mathematical expression. Supports +, -, *, /, parentheses"
    override val parameters = FunctionParameters(
        properties = mapOf(
            "expression" to ParameterProperty(type = "string", description = "The math expression to evaluate, e.g. '2 + 3 * 4'")
        ),
        required = listOf("expression")
    )

    override suspend fun execute(args: JsonObject): String {
        val expr = args.get("expression").asString
        return try {
            val result = evaluateSimple(expr)
            "$expr = $result"
        } catch (e: Exception) {
            "Error evaluating '$expr': ${e.message}"
        }
    }

    private fun evaluateSimple(expr: String): Double {
        val clean = expr.replace(" ", "").replace("pi", Math.PI.toString()).replace("e", Math.E.toString())
        return parseExpression(clean, 0).first
    }

    private fun parseExpression(expr: String, pos: Int): Pair<Double, Int> {
        var (value, newPos) = parseTerm(expr, pos)
        while (newPos < expr.length) {
            when (expr[newPos]) {
                '+' -> { val (r, p) = parseTerm(expr, newPos + 1); value += r; newPos = p }
                '-' -> { val (r, p) = parseTerm(expr, newPos + 1); value -= r; newPos = p }
                else -> break
            }
        }
        return value to newPos
    }

    private fun parseTerm(expr: String, pos: Int): Pair<Double, Int> {
        var (value, newPos) = parseFactor(expr, pos)
        while (newPos < expr.length) {
            when (expr[newPos]) {
                '*' -> { val (r, p) = parseFactor(expr, newPos + 1); value *= r; newPos = p }
                '/' -> { val (r, p) = parseFactor(expr, newPos + 1); value /= r; newPos = p }
                else -> break
            }
        }
        return value to newPos
    }

    private fun parseFactor(expr: String, pos: Int): Pair<Double, Int> {
        if (pos >= expr.length) return 0.0 to pos
        return when {
            expr[pos] == '(' -> {
                val (value, newPos) = parseExpression(expr, pos + 1)
                value to (if (newPos < expr.length && expr[newPos] == ')') newPos + 1 else newPos)
            }
            expr[pos] == '-' -> {
                val (value, newPos) = parseFactor(expr, pos + 1)
                -value to newPos
            }
            expr.startsWith("sqrt", pos) -> {
                val innerStart = pos + 5
                val (value, newPos) = parseExpression(expr, innerStart)
                Math.sqrt(value) to (if (newPos < expr.length && expr[newPos] == ')') newPos + 1 else newPos)
            }
            else -> parseNumber(expr, pos)
        }
    }

    private fun parseNumber(expr: String, pos: Int): Pair<Double, Int> {
        var newPos = pos
        while (newPos < expr.length && (expr[newPos].isDigit() || expr[newPos] == '.')) newPos++
        return expr.substring(pos, newPos).toDouble() to newPos
    }
}

// ── Memory Tools ─────────────────────────────────────────────────────────────

class RememberTool : Tool {
    override val name = "remember"
    override val description = "Save an important piece of information to long-term memory"
    override val parameters = FunctionParameters(
        properties = mapOf(
            "content" to ParameterProperty(type = "string", description = "The information to remember"),
            "category" to ParameterProperty(type = "string", description = "Memory category", enumValues = listOf("episodic", "semantic", "preference")),
            "importance" to ParameterProperty(type = "string", description = "Importance 0.0-1.0"),
        ),
        required = listOf("content")
    )

    var onRemember: (suspend (String, String, Float) -> Unit)? = null

    override suspend fun execute(args: JsonObject): String {
        val content = args.get("content").asString
        val category = if (args.has("category")) args.get("category").asString else "episodic"
        val importance = if (args.has("importance")) args.get("importance").asString.toFloatOrNull() ?: 0.5f else 0.5f
        onRemember?.invoke(content, category, importance)
        return "Remembered: $content"
    }
}

class RecallMemoryTool : Tool {
    override val name = "recall_memory"
    override val description = "Search and recall information from long-term memory"
    override val parameters = FunctionParameters(
        properties = mapOf(
            "query" to ParameterProperty(type = "string", description = "What to search for in memory"),
        ),
        required = listOf("query")
    )

    var onRecall: (suspend (String) -> List<String>)? = null

    override suspend fun execute(args: JsonObject): String {
        val query = args.get("query").asString
        val results = onRecall?.invoke(query) ?: emptyList()
        return if (results.isEmpty()) "No memories found for '$query'" else results.joinToString("\n")
    }
}

// ── Reminder ─────────────────────────────────────────────────────────────────

class SetReminderTool : Tool {
    override val name = "set_reminder"
    override val description = "Set a reminder for the user. The system will send a notification."
    override val parameters = FunctionParameters(
        properties = mapOf(
            "content" to ParameterProperty(type = "string", description = "What to remind about"),
            "time" to ParameterProperty(type = "string", description = "When to remind, e.g. 'tomorrow 9am', 'in 30 minutes'"),
        ),
        required = listOf("content", "time")
    )

    var onSetReminder: (suspend (String, String) -> Unit)? = null

    override suspend fun execute(args: JsonObject): String {
        val content = args.get("content").asString
        val time = args.get("time").asString
        onSetReminder?.invoke(content, time)
        return "Reminder set: '$content' at $time. Dia will notify you."
    }
}

// ── Real Web Search (DuckDuckGo HTML) ────────────────────────────────────────

class WebSearchTool : Tool {
    override val name = "web_search"
    override val description = "Search the web for real-time information using DuckDuckGo"
    override val parameters = FunctionParameters(
        properties = mapOf(
            "query" to ParameterProperty(type = "string", description = "Search query"),
        ),
        required = listOf("query")
    )

    override suspend fun execute(args: JsonObject): String {
        val query = args.get("query").asString
        return withContext(Dispatchers.IO) {
            try {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val url = URL("https://html.duckduckgo.com/html/?q=$encoded")
                val conn = url.openConnection() as HttpURLConnection
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                conn.connectTimeout = 8000
                conn.readTimeout = 8000

                val html = conn.inputStream.bufferedReader().readText()
                conn.disconnect()

                val results = parseSearchResults(html)
                if (results.isEmpty()) {
                    "No results found for '$query'"
                } else {
                    buildString {
                        appendLine("Search results for '$query':")
                        results.take(5).forEachIndexed { i, r ->
                            appendLine("${i + 1}. ${r.title}")
                            appendLine("   ${r.snippet}")
                            appendLine("   ${r.url}")
                        }
                    }
                }
            } catch (e: Exception) {
                "Search error: ${e.message}"
            }
        }
    }

    private data class SearchResult(val title: String, val snippet: String, val url: String)

    private fun parseSearchResults(html: String): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        // Parse DuckDuckGo HTML results
        val resultPattern = Regex("""<a[^>]*class="result__a"[^>]*href="([^"]*)"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
        val snippetPattern = Regex("""<a[^>]*class="result__snippet"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)

        val titleMatches = resultPattern.findAll(html).toList()
        val snippetMatches = snippetPattern.findAll(html).toList()

        for (i in titleMatches.indices) {
            val titleRaw = titleMatches[i].groupValues[2].replace(Regex("<[^>]+>"), "").trim()
            val urlRaw = titleMatches[i].groupValues[1]
                .replace("https://duckduckgo.com/l/?uddg=", "")
                .split("&")[0]
                .let { java.net.URLDecoder.decode(it, "UTF-8") }
            val snippetRaw = if (i < snippetMatches.size) {
                snippetMatches[i].groupValues[1].replace(Regex("<[^>]+>"), "").trim()
            } else ""

            if (titleRaw.isNotBlank()) {
                results.add(SearchResult(titleRaw, snippetRaw, urlRaw))
            }
        }
        return results
    }
}

// ── Fetch URL Content ────────────────────────────────────────────────────────

class FetchUrlTool : Tool {
    override val name = "fetch_url"
    override val description = "Fetch and read the content of a web page URL"
    override val parameters = FunctionParameters(
        properties = mapOf(
            "url" to ParameterProperty(type = "string", description = "The URL to fetch"),
            "max_length" to ParameterProperty(type = "string", description = "Max characters to return (default 3000)"),
        ),
        required = listOf("url")
    )

    override suspend fun execute(args: JsonObject): String {
        val urlStr = args.get("url").asString
        val maxLength = if (args.has("max_length")) args.get("max_length").asString.toIntOrNull() ?: 3000 else 3000

        return withContext(Dispatchers.IO) {
            try {
                val url = URL(urlStr)
                val conn = url.openConnection() as HttpURLConnection
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                conn.instanceFollowRedirects = true

                val contentType = conn.contentType ?: ""
                val text = conn.inputStream.bufferedReader().readText()
                conn.disconnect()

                // Strip HTML tags for readability
                val cleaned = if ("html" in contentType) {
                    text.replace(Regex("<script[^>]*>[\\s\\S]*?</script>"), "")
                        .replace(Regex("<style[^>]*>[\\s\\S]*?</style>"), "")
                        .replace(Regex("<[^>]+>"), " ")
                        .replace(Regex("\\s+"), " ")
                        .trim()
                } else {
                    text
                }

                if (cleaned.length > maxLength) cleaned.take(maxLength) + "..." else cleaned
            } catch (e: Exception) {
                "Failed to fetch $urlStr: ${e.message}"
            }
        }
    }
}

// ── Send Notification ────────────────────────────────────────────────────────

class SendNotificationTool : Tool {
    override val name = "send_notification"
    override val description = "Send a system notification to the user's device"
    override val parameters = FunctionParameters(
        properties = mapOf(
            "title" to ParameterProperty(type = "string", description = "Notification title"),
            "message" to ParameterProperty(type = "string", description = "Notification body text"),
        ),
        required = listOf("title", "message")
    )

    var onNotify: (suspend (String, String) -> Unit)? = null

    override suspend fun execute(args: JsonObject): String {
        val title = args.get("title").asString
        val message = args.get("message").asString
        onNotify?.invoke(title, message)
        return "Notification sent: '$title' — $message"
    }
}

// ── List Skills ──────────────────────────────────────────────────────────────

class ListSkillsTool : Tool {
    override val name = "list_skills"
    override val description = "List all available skills and their status"
    override val parameters = FunctionParameters(properties = emptyMap())

    var onListSkills: (suspend () -> String)? = null

    override suspend fun execute(args: JsonObject): String {
        return onListSkills?.invoke() ?: "No skills available"
    }
}

// ── Create Sub-Agent ─────────────────────────────────────────────────────────

class CreateSubAgentTool : Tool {
    override val name = "create_sub_agent"
    override val description = "Create a child sub-agent with a specific role for complex multi-step tasks. The sub-agent will be available for delegation."
    override val parameters = FunctionParameters(
        properties = mapOf(
            "name" to ParameterProperty(type = "string", description = "Name for the sub-agent"),
            "role" to ParameterProperty(type = "string", description = "Role description, e.g. 'researcher', 'coder', 'writer'"),
            "system_prompt" to ParameterProperty(type = "string", description = "System prompt defining the sub-agent's behavior and expertise"),
        ),
        required = listOf("name", "role", "system_prompt")
    )

    var onCreate: (suspend (String, String, String) -> Unit)? = null

    override suspend fun execute(args: JsonObject): String {
        val name = args.get("name").asString
        val role = args.get("role").asString
        val prompt = args.get("system_prompt").asString
        onCreate?.invoke(name, role, prompt)
        return "Sub-agent '$name' ($role) created. It can now be delegated tasks."
    }
}

// ── Registry ─────────────────────────────────────────────────────────────────

class ToolRegistry {
    private val tools = mutableMapOf<String, Tool>()

    init {
        register(GetTimeTool())
        register(CalculatorTool())
        register(RememberTool())
        register(RecallMemoryTool())
        register(SetReminderTool())
        register(WebSearchTool())
        register(FetchUrlTool())
        register(SendNotificationTool())
        register(ListSkillsTool())
        register(CreateSubAgentTool())
    }

    fun register(tool: Tool) {
        tools[tool.name] = tool
    }

    fun get(name: String): Tool? = tools[name]

    fun getAll(): List<Tool> = tools.values.toList()

    fun getDefinitions(): List<ToolDefinition> = tools.values.map { it.toDefinition() }
}
