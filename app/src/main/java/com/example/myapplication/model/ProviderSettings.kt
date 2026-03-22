package com.example.myapplication.model

import java.util.UUID

const val DEFAULT_PROVIDER_NAME = "默认提供商"
const val LEGACY_PROVIDER_ID = "legacy-provider"

data class ProviderSettings(
    val id: String = UUID.randomUUID().toString(),
    val name: String = DEFAULT_PROVIDER_NAME,
    val baseUrl: String = "",
    val apiKey: String = "",
    val selectedModel: String = "",
    val thinkingBudget: Int? = null,
    val availableModels: List<String> = emptyList(),
    val type: ProviderType? = null,
    val models: List<ModelInfo>? = null,
    val enabled: Boolean = true,
    val titleSummaryModel: String = "",
    val chatSuggestionModel: String = "",
    val memoryModel: String = "",
    val translationModel: String = "",
) {
    fun hasBaseCredentials(): Boolean {
        return baseUrl.isNotBlank() && apiKey.isNotBlank()
    }

    fun hasRequiredConfig(): Boolean {
        return hasBaseCredentials() && selectedModel.isNotBlank()
    }

    /** 返回 non-null 的提供商类型，null 时从 baseUrl 推断。 */
    fun resolvedType(): ProviderType {
        return type ?: ProviderType.fromBaseUrl(baseUrl)
    }

    /** 返回 non-null 的模型信息列表，null 时从 availableModels 字符串列表转换。 */
    fun resolvedModels(): List<ModelInfo> {
        return models?.map(ModelInfo::withResolvedAbilities) ?: availableModels.map(::inferredModelInfo)
    }

    /** 优先读取 provider 已保存的模型能力，再回退到本地 registry 推断。 */
    fun resolveModelAbilities(modelId: String): Set<ModelAbility> {
        if (modelId.isBlank()) {
            return emptySet()
        }

        val explicitModel = models?.firstOrNull { it.modelId == modelId }
        if (explicitModel != null) {
            return explicitModel.resolvedAbilities()
        }

        return inferModelAbilities(modelId)
    }

    /** 返回所有模型 ID，优先使用 models 字段。 */
    fun resolvedModelIds(): List<String> {
        return models?.map { it.modelId } ?: availableModels
    }

    fun resolveFunctionModel(function: ProviderFunction): String {
        val taskModel = when (function) {
            ProviderFunction.CHAT -> selectedModel
            ProviderFunction.TITLE_SUMMARY -> titleSummaryModel
            ProviderFunction.CHAT_SUGGESTION -> chatSuggestionModel
            ProviderFunction.MEMORY -> memoryModel
            ProviderFunction.TRANSLATION -> translationModel
        }.trim()

        return taskModel.ifBlank { selectedModel.trim() }
    }
}

enum class ProviderFunction {
    CHAT,
    TITLE_SUMMARY,
    CHAT_SUGGESTION,
    MEMORY,
    TRANSLATION,
}

fun createDefaultProvider(
    id: String = UUID.randomUUID().toString(),
    name: String = DEFAULT_PROVIDER_NAME,
    baseUrl: String = "",
    apiKey: String = "",
    selectedModel: String = "",
    thinkingBudget: Int? = null,
    availableModels: List<String> = emptyList(),
    type: ProviderType? = null,
): ProviderSettings {
    return ProviderSettings(
        id = id,
        name = name,
        baseUrl = baseUrl,
        apiKey = apiKey,
        selectedModel = selectedModel,
        thinkingBudget = thinkingBudget,
        availableModels = availableModels,
        type = type,
    )
}
