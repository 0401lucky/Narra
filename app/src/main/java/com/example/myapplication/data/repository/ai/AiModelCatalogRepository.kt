package com.example.myapplication.data.repository.ai

import com.example.myapplication.data.remote.AnthropicApi
import com.example.myapplication.data.remote.ApiServiceFactory
import com.example.myapplication.data.remote.OpenAiCompatibleApi
import com.example.myapplication.model.ModelInfo
import com.example.myapplication.model.ProviderApiProtocol
import com.example.myapplication.model.inferredModelInfo
import java.net.UnknownHostException

interface AiModelCatalogRepository {
    suspend fun fetchModels(
        baseUrl: String,
        apiKey: String,
        apiProtocol: ProviderApiProtocol = ProviderApiProtocol.OPENAI_COMPATIBLE,
    ): List<String>

    suspend fun fetchModelInfos(
        baseUrl: String,
        apiKey: String,
        apiProtocol: ProviderApiProtocol = ProviderApiProtocol.OPENAI_COMPATIBLE,
    ): List<ModelInfo>
}

class DefaultAiModelCatalogRepository(
    private val apiServiceFactory: ApiServiceFactory,
    private val apiServiceProvider: (String, String) -> OpenAiCompatibleApi = { baseUrl, apiKey ->
        apiServiceFactory.create(
            baseUrl = baseUrl,
            apiKey = apiKey,
        )
    },
    private val anthropicApiProvider: (String, String) -> AnthropicApi = { baseUrl, apiKey ->
        apiServiceFactory.createAnthropic(
            baseUrl = baseUrl,
            apiKey = apiKey,
        )
    },
) : AiModelCatalogRepository {
    override suspend fun fetchModels(
        baseUrl: String,
        apiKey: String,
        apiProtocol: ProviderApiProtocol,
    ): List<String> {
        require(baseUrl.isNotBlank()) { "请先填写 Base URL" }
        require(apiKey.isNotBlank()) { "请先填写 API Key" }

        val models = try {
            when (apiProtocol) {
                ProviderApiProtocol.OPENAI_COMPATIBLE -> {
                    val response = apiServiceProvider(
                        baseUrl,
                        apiKey.trim(),
                    ).listModels()
                    if (!response.isSuccessful) {
                        throw IllegalStateException("模型拉取失败：${response.code()}")
                    }
                    response.body()?.data.orEmpty().map { it.id }
                }
                ProviderApiProtocol.ANTHROPIC -> {
                    val response = anthropicApiProvider(
                        baseUrl,
                        apiKey.trim(),
                    ).listModels()
                    if (!response.isSuccessful) {
                        throw IllegalStateException("模型拉取失败：${response.code()}")
                    }
                    response.body()?.data.orEmpty().map { it.id }
                }
            }
        } catch (exception: Exception) {
            throw exception.toReadableNetworkException()
        }.filter { it.isNotBlank() }

        if (models.isEmpty()) {
            throw IllegalStateException("未获取到可用模型")
        }
        return models
    }

    override suspend fun fetchModelInfos(
        baseUrl: String,
        apiKey: String,
        apiProtocol: ProviderApiProtocol,
    ): List<ModelInfo> {
        return fetchModels(baseUrl, apiKey, apiProtocol).map(::inferredModelInfo)
    }

    private fun Exception.toReadableNetworkException(): Exception {
        if (findUnknownHostException() != null) {
            return IllegalStateException(
                "无法解析服务地址，请检查设备网络、DNS 设置，并确认 Base URL 是否可访问",
                this,
            )
        }
        return this
    }

    private fun Throwable.findUnknownHostException(): UnknownHostException? {
        var current: Throwable? = this
        while (current != null) {
            if (current is UnknownHostException) {
                return current
            }
            current = current.cause
        }
        return null
    }
}
