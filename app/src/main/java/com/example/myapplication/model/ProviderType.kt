package com.example.myapplication.model

import com.example.myapplication.R

/**
 * 提供商类型枚举。
 * 所有提供商都走 OpenAI Compatible API，区别仅在品牌视觉和默认 URL。
 */
enum class ProviderType(
    val displayName: String,
    val defaultBaseUrl: String,
    val iconRes: Int?,
) {
    OPENAI(
        displayName = "OpenAI",
        defaultBaseUrl = "https://api.openai.com/v1/",
        iconRes = R.drawable.ic_model_openai,
    ),
    DEEPSEEK(
        displayName = "DeepSeek",
        defaultBaseUrl = "https://api.deepseek.com/v1/",
        iconRes = R.drawable.ic_model_deepseek,
    ),
    GOOGLE(
        displayName = "Google Gemini",
        defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta/",
        iconRes = R.drawable.ic_model_gemini,
    ),
    ANTHROPIC(
        displayName = "Anthropic",
        defaultBaseUrl = "https://api.anthropic.com/v1/",
        iconRes = R.drawable.ic_model_claude,
    ),
    MISTRAL(
        displayName = "Mistral AI",
        defaultBaseUrl = "https://api.mistral.ai/v1/",
        iconRes = R.drawable.ic_model_mistral,
    ),
    GROK(
        displayName = "xAI Grok",
        defaultBaseUrl = "https://api.x.ai/v1/",
        iconRes = R.drawable.ic_model_grok,
    ),
    MOONSHOT(
        displayName = "Moonshot AI",
        defaultBaseUrl = "https://api.moonshot.cn/v1/",
        iconRes = R.drawable.ic_model_kimi,
    ),
    ZHIPU(
        displayName = "智谱 AI",
        defaultBaseUrl = "https://open.bigmodel.cn/api/paas/v4/",
        iconRes = R.drawable.ic_model_zhipu,
    ),
    QWEN(
        displayName = "通义千问",
        defaultBaseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1/",
        iconRes = R.drawable.ic_model_qwen,
    ),
    META_LLAMA(
        displayName = "Meta Llama",
        defaultBaseUrl = "",
        iconRes = R.drawable.ic_model_meta,
    ),
    CUSTOM(
        displayName = "自定义",
        defaultBaseUrl = "",
        iconRes = null,
    );

    companion object {
        /** 根据 Base URL 启发式推断提供商类型。 */
        fun fromBaseUrl(baseUrl: String): ProviderType {
            val lower = baseUrl.lowercase()
            return when {
                "openai.com" in lower -> OPENAI
                "deepseek.com" in lower -> DEEPSEEK
                "googleapis.com" in lower || "generativelanguage" in lower -> GOOGLE
                "anthropic.com" in lower -> ANTHROPIC
                "mistral.ai" in lower -> MISTRAL
                "x.ai" in lower -> GROK
                "moonshot.cn" in lower -> MOONSHOT
                "bigmodel.cn" in lower -> ZHIPU
                "dashscope.aliyuncs.com" in lower -> QWEN
                else -> CUSTOM
            }
        }
    }
}
