package com.example.myapplication.model

const val REASONING_BUDGET_LOW = 1_024
const val REASONING_BUDGET_MEDIUM = 16_000
const val REASONING_BUDGET_HIGH = 32_000

data class ThinkingRequestConfig(
    val reasoningEffort: String? = null,
    val thinking: ThinkingConfigDto? = null,
)

private enum class ThinkingBudgetProtocol {
    OPENAI_REASONING_EFFORT,
    ANTHROPIC_THINKING,
}

enum class ReasoningBudgetPreset(
    val label: String,
    val budget: Int?,
) {
    AUTO(
        label = "自动",
        budget = null,
    ),
    LOW(
        label = "轻度",
        budget = REASONING_BUDGET_LOW,
    ),
    MEDIUM(
        label = "标准",
        budget = REASONING_BUDGET_MEDIUM,
    ),
    HIGH(
        label = "深度",
        budget = REASONING_BUDGET_HIGH,
    ),
}

fun supportsThinkingBudgetControl(
    provider: ProviderSettings,
    modelId: String = provider.selectedModel,
): Boolean {
    return resolveThinkingBudgetProtocol(provider, modelId) != null
}

fun reasoningBudgetSupportHint(
    provider: ProviderSettings,
    modelId: String = provider.selectedModel,
): String? {
    if (modelId.isBlank() || ModelAbility.REASONING !in provider.resolveModelAbilities(modelId)) {
        return null
    }

    val lower = modelId.lowercase()
    return when {
        provider.resolvedType() == ProviderType.GROK &&
            hasModelFeature(modelId, ModelFeature.GROK_OPAQUE_REASONING) -> {
            "该模型会内部思考，但 xAI 当前接口不返回明文推理轨迹，也不开放思考预算调节。"
        }

        hasModelFeature(modelId, ModelFeature.ANTHROPIC_THINKING) &&
            resolveThinkingBudgetProtocol(provider, modelId) == ThinkingBudgetProtocol.ANTHROPIC_THINKING -> {
            "思考预算会映射为 Claude 兼容接口的 thinking.budget_tokens；选择“自动”时不会显式开启扩展思考，这类接口通常也不会返回完整明文思考轨迹。"
        }

        isGeminiReasoningModel(lower) -> {
            "思考预算会映射为 Gemini OpenAI 兼容接口的 reasoning_effort；Gemini 3 的“标准”档会自动提升为 high。"
        }

        resolveThinkingBudgetProtocol(provider, modelId) == ThinkingBudgetProtocol.OPENAI_REASONING_EFFORT -> {
            "思考预算会映射为接口支持的 reasoning_effort。"
        }

        else -> {
            "当前模型支持推理，但这类兼容接口暂不开放思考预算控制。"
        }
    }
}

fun resolveReasoningBudgetLabel(thinkingBudget: Int?): String {
    return ReasoningBudgetPreset.entries.firstOrNull { it.budget == thinkingBudget }?.label
        ?: when {
            thinkingBudget == null -> "自动"
            thinkingBudget <= 0 -> "自动"
            else -> "${thinkingBudget} token"
        }
}

fun mapThinkingBudgetToReasoningEffort(
    provider: ProviderSettings?,
    modelId: String = provider?.selectedModel.orEmpty(),
    thinkingBudget: Int? = provider?.thinkingBudget,
): String? {
    val resolvedProvider = provider ?: return null
    if (resolveThinkingBudgetProtocol(resolvedProvider, modelId) != ThinkingBudgetProtocol.OPENAI_REASONING_EFFORT) {
        return null
    }

    val resolvedBudget = thinkingBudget ?: return null
    if (resolvedBudget <= 0) {
        return null
    }

    val lower = modelId.lowercase()
    return when {
        resolvedBudget <= REASONING_BUDGET_LOW -> "low"
        isGeminiThreeReasoningModel(lower) -> "high"
        resolvedBudget <= REASONING_BUDGET_MEDIUM -> "medium"
        else -> "high"
    }
}

fun buildThinkingRequestConfig(
    provider: ProviderSettings?,
    modelId: String = provider?.selectedModel.orEmpty(),
    thinkingBudget: Int? = provider?.thinkingBudget,
): ThinkingRequestConfig {
    val resolvedProvider = provider ?: return ThinkingRequestConfig()
    return when (resolveThinkingBudgetProtocol(resolvedProvider, modelId)) {
        ThinkingBudgetProtocol.OPENAI_REASONING_EFFORT -> {
            ThinkingRequestConfig(
                reasoningEffort = mapThinkingBudgetToReasoningEffort(
                    provider = resolvedProvider,
                    modelId = modelId,
                    thinkingBudget = thinkingBudget,
                ),
            )
        }

        ThinkingBudgetProtocol.ANTHROPIC_THINKING -> {
            val budgetTokens = thinkingBudget?.takeIf { it > 0 }
            ThinkingRequestConfig(
                thinking = budgetTokens?.let { ThinkingConfigDto(budgetTokens = it) },
            )
        }

        null -> ThinkingRequestConfig()
    }
}

private fun resolveThinkingBudgetProtocol(
    provider: ProviderSettings,
    modelId: String = provider.selectedModel,
): ThinkingBudgetProtocol? {
    if (modelId.isBlank() || ModelAbility.REASONING !in provider.resolveModelAbilities(modelId)) {
        return null
    }

    if (provider.resolvedType() == ProviderType.GROK &&
        hasModelFeature(modelId, ModelFeature.GROK_OPAQUE_REASONING)
    ) {
        return null
    }

    return when {
        hasModelFeature(modelId, ModelFeature.ANTHROPIC_THINKING) -> {
            if (provider.resolvedType() == ProviderType.ANTHROPIC) {
                ThinkingBudgetProtocol.ANTHROPIC_THINKING
            } else {
                ThinkingBudgetProtocol.OPENAI_REASONING_EFFORT
            }
        }

        hasModelFeature(modelId, ModelFeature.OPENAI_REASONING_EFFORT) -> {
            ThinkingBudgetProtocol.OPENAI_REASONING_EFFORT
        }

        else -> null
    }
}

private fun isGeminiReasoningModel(lower: String): Boolean {
    return lower.startsWith("gemini-2.5") ||
        isGeminiThreeReasoningModel(lower) ||
        lower.startsWith("gemini-flash-latest") ||
        lower.startsWith("gemini-pro-latest")
}

private fun isGeminiThreeReasoningModel(lower: String): Boolean {
    return lower.startsWith("gemini-3")
}
