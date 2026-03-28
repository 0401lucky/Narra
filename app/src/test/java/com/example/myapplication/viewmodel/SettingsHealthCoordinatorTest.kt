package com.example.myapplication.viewmodel

import com.example.myapplication.data.repository.ai.AiModelCatalogRepository
import com.example.myapplication.model.ConnectionHealth
import com.example.myapplication.model.ModelInfo
import com.example.myapplication.model.ProviderSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsHealthCoordinatorTest {
    @Test
    fun checkProviderHealth_returnsHealthyWhenFetchSucceeds() = runBlocking {
        val coordinator = SettingsHealthCoordinator(
            modelCatalogRepository = object : AiModelCatalogRepository {
                override suspend fun fetchModels(baseUrl: String, apiKey: String, apiProtocol: com.example.myapplication.model.ProviderApiProtocol): List<String> = listOf("model-a")
                override suspend fun fetchModelInfos(baseUrl: String, apiKey: String, apiProtocol: com.example.myapplication.model.ProviderApiProtocol): List<ModelInfo> = error("不应调用")
            },
        )

        val result = coordinator.checkProviderHealth(
            ProviderSettings(
                id = "provider-1",
                name = "Provider",
                baseUrl = "https://example.com/v1/",
                apiKey = "key",
                selectedModel = "model-a",
            ),
        )

        assertEquals(ConnectionHealth.HEALTHY, result.health)
        assertEquals(null, result.message)
    }

    @Test
    fun checkProviderHealth_returnsUnhealthyWhenFetchFails() = runBlocking {
        val coordinator = SettingsHealthCoordinator(
            modelCatalogRepository = object : AiModelCatalogRepository {
                override suspend fun fetchModels(baseUrl: String, apiKey: String, apiProtocol: com.example.myapplication.model.ProviderApiProtocol): List<String> {
                    error("network error")
                }

                override suspend fun fetchModelInfos(baseUrl: String, apiKey: String, apiProtocol: com.example.myapplication.model.ProviderApiProtocol): List<ModelInfo> = error("不应调用")
            },
        )

        val result = coordinator.checkProviderHealth(
            ProviderSettings(
                id = "provider-1",
                name = "Provider",
                baseUrl = "https://example.com/v1/",
                apiKey = "key",
                selectedModel = "model-a",
            ),
        )

        assertEquals(ConnectionHealth.UNHEALTHY, result.health)
        assertEquals("network error", result.message)
    }

    @Test
    fun checkProviderHealth_returnsReadableMessageForUnsupportedOfficialEndpoint() = runBlocking {
        val coordinator = SettingsHealthCoordinator(
            modelCatalogRepository = object : AiModelCatalogRepository {
                override suspend fun fetchModels(baseUrl: String, apiKey: String, apiProtocol: com.example.myapplication.model.ProviderApiProtocol): List<String> {
                    error("当前版本仅支持 OpenAI 兼容接口。Anthropic 官方 API 不兼容 /models 与 /chat/completions，请改用 Claude 的 OpenAI 兼容网关或代理地址。")
                }

                override suspend fun fetchModelInfos(baseUrl: String, apiKey: String, apiProtocol: com.example.myapplication.model.ProviderApiProtocol): List<ModelInfo> = error("不应调用")
            },
        )

        val result = coordinator.checkProviderHealth(
            ProviderSettings(
                id = "provider-1",
                name = "Anthropic",
                baseUrl = "https://api.anthropic.com/v1/",
                apiKey = "key",
                selectedModel = "claude-3-7-sonnet",
            ),
        )

        assertEquals(ConnectionHealth.UNHEALTHY, result.health)
        assertEquals(
            "当前版本仅支持 OpenAI 兼容接口。Anthropic 官方 API 不兼容 /models 与 /chat/completions，请改用 Claude 的 OpenAI 兼容网关或代理地址。",
            result.message,
        )
    }
}
