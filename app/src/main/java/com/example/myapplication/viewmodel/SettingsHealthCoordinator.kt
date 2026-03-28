package com.example.myapplication.viewmodel

import com.example.myapplication.data.repository.ai.AiModelCatalogRepository
import com.example.myapplication.model.ConnectionHealth
import com.example.myapplication.model.ProviderSettings

data class ProviderHealthCheckResult(
    val health: ConnectionHealth,
    val message: String? = null,
)

class SettingsHealthCoordinator(
    private val modelCatalogRepository: AiModelCatalogRepository,
) {
    suspend fun checkProviderHealth(provider: ProviderSettings): ProviderHealthCheckResult {
        if (!provider.hasBaseCredentials()) {
            return ProviderHealthCheckResult(
                health = ConnectionHealth.UNHEALTHY,
                message = "请先填写 Base URL 和 API Key",
            )
        }
        return runCatching {
            modelCatalogRepository.fetchModels(
                baseUrl = provider.baseUrl,
                apiKey = provider.apiKey,
                apiProtocol = provider.resolvedApiProtocol(),
            )
            ProviderHealthCheckResult(ConnectionHealth.HEALTHY)
        }.getOrElse { throwable ->
            ProviderHealthCheckResult(
                health = ConnectionHealth.UNHEALTHY,
                message = throwable.message ?: "连接检测失败",
            )
        }
    }
}
