package com.zva.agent.data.api

import com.google.gson.Gson
import com.zva.agent.data.model.ChatCompletionChunk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.ResponseBody

/**
 * Parses SSE stream from OpenAI-compatible API into ChatCompletionChunk flow.
 */
class StreamProcessor(private val gson: Gson) {

    fun processStream(body: ResponseBody): Flow<ChatCompletionChunk> = flow {
        val source = body.source()
        try {
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (line.isBlank()) continue
                if (!line.startsWith("data: ")) continue

                val data = line.removePrefix("data: ").trim()
                if (data == "[DONE]") break

                try {
                    val chunk = gson.fromJson(data, ChatCompletionChunk::class.java)
                    if (chunk != null && chunk.choices.isNotEmpty()) {
                        emit(chunk)
                    }
                } catch (_: Exception) {
                    // Skip malformed chunks
                }
            }
        } finally {
            body.close()
        }
    }.flowOn(Dispatchers.IO)
}
