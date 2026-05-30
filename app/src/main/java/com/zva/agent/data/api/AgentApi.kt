package com.zva.agent.data.api

import com.zva.agent.data.model.ChatCompletionRequest
import com.zva.agent.data.model.ChatCompletionResponse
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

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
}
