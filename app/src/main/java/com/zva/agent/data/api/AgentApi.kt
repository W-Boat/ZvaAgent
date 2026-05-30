package com.zva.agent.data.api

import com.zva.agent.data.model.ChatCompletionRequest
import com.zva.agent.data.model.ChatCompletionResponse
import okhttp3.ResponseBody
import retrofit2.http.*

interface AgentApi {

    @POST("v1/chat/completions")
    suspend fun chatCompletion(
        @Body request: ChatCompletionRequest,
        @Header("Authorization") auth: String,
    ): ChatCompletionResponse

    @POST
    suspend fun chatCompletion(
        @Url url: String,
        @Body request: ChatCompletionRequest,
        @Header("Authorization") auth: String,
    ): ChatCompletionResponse

    @Streaming
    @POST
    suspend fun chatCompletionStream(
        @Url url: String,
        @Body request: ChatCompletionRequest,
        @Header("Authorization") auth: String,
    ): ResponseBody
}
