package com.zva.agent.domain.tool

import com.zva.agent.data.model.FunctionDefinition
import com.zva.agent.data.model.FunctionParameters
import com.zva.agent.data.model.ParameterProperty
import com.zva.agent.data.model.ToolDefinition
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.text.SimpleDateFormat
import java.util.*

interface Tool {
    val name: String
    val description: String
    val parameters: FunctionParameters
    suspend fun execute(args: JsonObject): String

    fun toDefinition() = ToolDefinition(
        function = FunctionDefinition(
            name = name,
            description = description,
            parameters = parameters,
        )
    )
}

// ── Built-in Tools ──────────────────────────────────────────────────────────

class GetTimeTool : Tool {
    override val name = "get_current_time"
    override val description = "Get the current date and time"
    override val parameters = FunctionParameters(
        properties = mapOf(
            "format" to ParameterProperty(
                type = "string",
                description = "Date format pattern, default 'yyyy-MM-dd HH:mm:ss'"
            )
        )
    )

    override suspend fun execute(args: JsonObject): String {
        val format = if (args.has("format")) args.get("format").asString else "yyyy-MM-dd HH:mm:ss"
        return SimpleDateFormat(format, Locale.getDefault()).format(Date())
    }
}

class CalculatorTool : Tool {
    override val name = "calculate"
    override val description = "Evaluate a mathematical expression. Supports +, -, *, /, ^, sqrt, sin, cos, tan, log, pi, e"
    override val parameters = FunctionParameters(
        properties = mapOf(
            "expression" to ParameterProperty(
                type = "string",
                description = "The math expression to evaluate, e.g. '2 + 3 * 4' or 'sqrt(16)'"
            )
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
        val clean = expr.replace(" ", "")
            .replace("pi", Math.PI.toString())
            .replace("e", Math.E.toString())
        // Simple recursive descent for basic math
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

    // Injected externally
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

class SetReminderTool : Tool {
    override val name = "set_reminder"
    override val description = "Set a reminder for the user"
    override val parameters = FunctionParameters(
        properties = mapOf(
            "content" to ParameterProperty(type = "string", description = "What to remind about"),
            "time" to ParameterProperty(type = "string", description = "When to remind, e.g. 'tomorrow 9am', 'in 30 minutes'"),
        ),
        required = listOf("content", "time")
    )

    override suspend fun execute(args: JsonObject): String {
        val content = args.get("content").asString
        val time = args.get("time").asString
        return "Reminder set: '$content' at $time"
    }
}

class WebSearchTool : Tool {
    override val name = "web_search"
    override val description = "Search the web for information"
    override val parameters = FunctionParameters(
        properties = mapOf(
            "query" to ParameterProperty(type = "string", description = "Search query"),
        ),
        required = listOf("query")
    )

    override suspend fun execute(args: JsonObject): String {
        val query = args.get("query").asString
        // Stub — in production, integrate Tavily or similar
        return "Web search for '$query': (web search is not yet connected. This is a demo stub.)"
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
    }

    fun register(tool: Tool) {
        tools[tool.name] = tool
    }

    fun get(name: String): Tool? = tools[name]

    fun getAll(): List<Tool> = tools.values.toList()

    fun getDefinitions(): List<ToolDefinition> = tools.values.map { it.toDefinition() }
}
