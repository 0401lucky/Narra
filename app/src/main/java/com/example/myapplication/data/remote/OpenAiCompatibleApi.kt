package com.example.myapplication.data.remote

import com.example.myapplication.model.ChatCompletionRequest
import com.example.myapplication.model.ChatCompletionResponse
import com.example.myapplication.model.ImageGenerationRequest
import com.example.myapplication.model.ImageGenerationResponse
import com.example.myapplication.model.ModelsResponse
import com.example.myapplication.model.ResponseApiRequest
import com.example.myapplication.model.ResponseApiResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Url

interface OpenAiCompatibleApi {
    @GET("models")
    suspend fun listModels(): Response<ModelsResponse>

    @POST("chat/completions")
    suspend fun createChatCompletion(
        @Body request: ChatCompletionRequest,
    ): Response<ChatCompletionResponse>

    @POST
    suspend fun createChatCompletionAt(
        @Url url: String,
        @Body request: ChatCompletionRequest,
    ): Response<ChatCompletionResponse>

    @POST
    suspend fun createResponseAt(
        @Url url: String,
        @Body request: ResponseApiRequest,
    ): Response<ResponseApiResponse>

    @POST("images/generations")
    suspend fun generateImage(
        @Body request: ImageGenerationRequest,
    ): Response<ImageGenerationResponse>
}
