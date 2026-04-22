package com.example.myapplication.testutil

import com.example.myapplication.data.remote.OpenAiCompatibleApi
import com.example.myapplication.model.ChatCompletionRequest
import com.example.myapplication.model.ChatCompletionResponse
import com.example.myapplication.model.ImageEditRequest
import com.example.myapplication.model.ImageGenerationRequest
import com.example.myapplication.model.ImageGenerationResponse
import com.example.myapplication.model.ModelsResponse
import com.example.myapplication.model.ResponseApiRequest
import com.example.myapplication.model.ResponseApiResponse
import retrofit2.Response

open class TestOpenAiCompatibleApi : OpenAiCompatibleApi {
    override suspend fun listModels(): Response<ModelsResponse> = error("不应调用 listModels")

    override suspend fun createChatCompletion(
        request: ChatCompletionRequest,
    ): Response<ChatCompletionResponse> = error("不应调用 createChatCompletion")

    override suspend fun createChatCompletionAt(
        url: String,
        request: ChatCompletionRequest,
    ): Response<ChatCompletionResponse> = error("不应调用 createChatCompletionAt")

    override suspend fun createResponseAt(
        url: String,
        request: ResponseApiRequest,
    ): Response<ResponseApiResponse> = error("不应调用 createResponseAt")

    override suspend fun generateImage(
        request: ImageGenerationRequest,
    ): Response<ImageGenerationResponse> = error("不应调用 generateImage")

    override suspend fun editImage(
        request: ImageEditRequest,
    ): Response<ImageGenerationResponse> = error("不应调用 editImage")
}
