package com.example.myapplication.data.remote

import com.example.myapplication.model.AnthropicMessageRequest
import com.example.myapplication.model.AnthropicMessageResponse
import com.example.myapplication.model.AnthropicModelsResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface AnthropicApi {
    @GET("models")
    suspend fun listModels(): Response<AnthropicModelsResponse>

    @POST("messages")
    suspend fun createMessage(
        @Body request: AnthropicMessageRequest,
    ): Response<AnthropicMessageResponse>
}
