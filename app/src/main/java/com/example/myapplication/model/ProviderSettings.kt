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
    val apiProtocol: ProviderApiProtocol? = null,
    val openAiTextApiMode: OpenAiTextApiMode? = null,
    val chatCompletionsPath: String = DEFAULT_CHAT_COMPLETIONS_PATH,
    val models: List<ModelInfo>? = null,
    val enabled: Boolean = true,
    val titleSummaryModel: String = "",
    val titleSummaryModelMode: ProviderFunctionModelMode = ProviderFunctionModelMode.FOLLOW_DEFAULT,
    val chatSuggestionModel: String = "",
    val chatSuggestionModelMode: ProviderFunctionModelMode = ProviderFunctionModelMode.FOLLOW_DEFAULT,
    val memoryModel: String = "",
    val memoryModelMode: ProviderFunctionModelMode = ProviderFunctionModelMode.FOLLOW_DEFAULT,
    val translationModel: String = "",
    val translationModelMode: ProviderFunctionModelMode = ProviderFunctionModelMode.FOLLOW_DEFAULT,
    val phoneSnapshotModel: String = "",
    val phoneSnapshotModelMode: ProviderFunctionModelMode = ProviderFunctionModelMode.FOLLOW_DEFAULT,
    val searchModel: String = "",
    val searchModelMode: ProviderFunctionModelMode = ProviderFunctionModelMode.FOLLOW_DEFAULT,
    val giftImageModel: String = "",
    val giftImageModelMode: ProviderFunctionModelMode = ProviderFunctionModelMode.DISABLED,
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

    fun resolvedApiProtocol(): ProviderApiProtocol {
        apiProtocol?.let { return it }
        val normalizedBaseUrl = baseUrl.trim().lowercase()
        return when {
            "api.anthropic.com" in normalizedBaseUrl -> ProviderApiProtocol.ANTHROPIC
            normalizedBaseUrl.isBlank() && resolvedType() == ProviderType.ANTHROPIC -> ProviderApiProtocol.ANTHROPIC
            else -> ProviderApiProtocol.OPENAI_COMPATIBLE
        }
    }

    fun supportsAnthropicProtocolSelection(): Boolean {
        return resolvedType() == ProviderType.ANTHROPIC || resolvedType() == ProviderType.CUSTOM
    }

    fun supportsOpenAiTextApiModeSelection(): Boolean {
        return resolvedApiProtocol() == ProviderApiProtocol.OPENAI_COMPATIBLE
    }

    fun resolvedOpenAiTextApiMode(): OpenAiTextApiMode {
        return openAiTextApiMode ?: OpenAiTextApiMode.CHAT_COMPLETIONS
    }

    fun resolvedChatCompletionsPath(): String {
        val trimmed = chatCompletionsPath.trim().ifBlank { DEFAULT_CHAT_COMPLETIONS_PATH }
        val prefixed = if (trimmed.startsWith('/')) trimmed else "/$trimmed"
        return prefixed.replace(Regex("/{2,}"), "/")
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

    fun resolveFunctionModelMode(function: ProviderFunction): ProviderFunctionModelMode {
        if (function == ProviderFunction.CHAT) {
            return ProviderFunctionModelMode.CUSTOM
        }
        val explicitModel = resolveExplicitFunctionModel(function)
        val storedMode = when (function) {
            ProviderFunction.CHAT -> ProviderFunctionModelMode.CUSTOM
            ProviderFunction.TITLE_SUMMARY -> titleSummaryModelMode
            ProviderFunction.CHAT_SUGGESTION -> chatSuggestionModelMode
            ProviderFunction.MEMORY -> memoryModelMode
            ProviderFunction.TRANSLATION -> translationModelMode
            ProviderFunction.PHONE_SNAPSHOT -> phoneSnapshotModelMode
            ProviderFunction.SEARCH -> searchModelMode
            ProviderFunction.GIFT_IMAGE -> giftImageModelMode
        }
        return when {
            explicitModel.isNotBlank() && storedMode != ProviderFunctionModelMode.CUSTOM -> {
                ProviderFunctionModelMode.CUSTOM
            }

            explicitModel.isBlank() && storedMode == ProviderFunctionModelMode.CUSTOM -> {
                defaultFunctionModelMode(function)
            }

            else -> storedMode
        }
    }

    fun resolveExplicitFunctionModel(function: ProviderFunction): String {
        return when (function) {
            ProviderFunction.CHAT -> selectedModel
            ProviderFunction.TITLE_SUMMARY -> titleSummaryModel
            ProviderFunction.CHAT_SUGGESTION -> chatSuggestionModel
            ProviderFunction.MEMORY -> memoryModel
            ProviderFunction.TRANSLATION -> translationModel
            ProviderFunction.PHONE_SNAPSHOT -> phoneSnapshotModel
            ProviderFunction.SEARCH -> searchModel
            ProviderFunction.GIFT_IMAGE -> giftImageModel
        }.trim()
    }

    fun resolveFunctionModel(function: ProviderFunction): String {
        val explicitModel = resolveExplicitFunctionModel(function)
        return when (resolveFunctionModelMode(function)) {
            ProviderFunctionModelMode.CUSTOM -> explicitModel
            ProviderFunctionModelMode.FOLLOW_DEFAULT -> selectedModel.trim()
            ProviderFunctionModelMode.DISABLED -> ""
        }
    }

    fun supportsLlmSearchSource(): Boolean {
        return when (resolvedApiProtocol()) {
            ProviderApiProtocol.ANTHROPIC -> true
            ProviderApiProtocol.OPENAI_COMPATIBLE -> {
                resolvedOpenAiTextApiMode() == OpenAiTextApiMode.RESPONSES
            }
        }
    }

    private fun defaultFunctionModelMode(function: ProviderFunction): ProviderFunctionModelMode {
        return when (function) {
            ProviderFunction.GIFT_IMAGE -> ProviderFunctionModelMode.DISABLED
            ProviderFunction.CHAT -> ProviderFunctionModelMode.CUSTOM
            else -> ProviderFunctionModelMode.FOLLOW_DEFAULT
        }
    }
}

enum class ProviderFunctionModelMode {
    FOLLOW_DEFAULT,
    CUSTOM,
    DISABLED,
}

enum class ProviderFunction {
    CHAT,
    TITLE_SUMMARY,
    CHAT_SUGGESTION,
    MEMORY,
    TRANSLATION,
    PHONE_SNAPSHOT,
    SEARCH,
    GIFT_IMAGE,
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
    apiProtocol: ProviderApiProtocol? = null,
    openAiTextApiMode: OpenAiTextApiMode? = null,
    chatCompletionsPath: String = DEFAULT_CHAT_COMPLETIONS_PATH,
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
        apiProtocol = apiProtocol,
        openAiTextApiMode = openAiTextApiMode,
        chatCompletionsPath = chatCompletionsPath,
    )
}
