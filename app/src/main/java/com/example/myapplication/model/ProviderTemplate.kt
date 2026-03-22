package com.example.myapplication.model

/** 提供商新建模板，一键填充名称和默认 URL。 */
data class ProviderTemplate(
    val type: ProviderType,
    val name: String,
    val description: String,
    val defaultBaseUrl: String,
)

/** 内置提供商模板列表。 */
val BUILT_IN_TEMPLATES: List<ProviderTemplate> = listOf(
    ProviderTemplate(
        type = ProviderType.OPENAI,
        name = "OpenAI",
        description = "GPT-4o / o3 / o4-mini 等系列模型",
        defaultBaseUrl = ProviderType.OPENAI.defaultBaseUrl,
    ),
    ProviderTemplate(
        type = ProviderType.DEEPSEEK,
        name = "DeepSeek",
        description = "DeepSeek-V3 / R1 等系列模型",
        defaultBaseUrl = ProviderType.DEEPSEEK.defaultBaseUrl,
    ),
    ProviderTemplate(
        type = ProviderType.GOOGLE,
        name = "Google Gemini",
        description = "Gemini 2.5 Pro / Flash 等系列模型",
        defaultBaseUrl = ProviderType.GOOGLE.defaultBaseUrl,
    ),
    ProviderTemplate(
        type = ProviderType.ANTHROPIC,
        name = "Anthropic",
        description = "Claude Opus / Sonnet / Haiku 系列模型",
        defaultBaseUrl = ProviderType.ANTHROPIC.defaultBaseUrl,
    ),
    ProviderTemplate(
        type = ProviderType.MISTRAL,
        name = "Mistral AI",
        description = "Mistral Large / Codestral 等系列模型",
        defaultBaseUrl = ProviderType.MISTRAL.defaultBaseUrl,
    ),
    ProviderTemplate(
        type = ProviderType.GROK,
        name = "xAI Grok",
        description = "Grok-2 / Grok-3 系列模型",
        defaultBaseUrl = ProviderType.GROK.defaultBaseUrl,
    ),
    ProviderTemplate(
        type = ProviderType.MOONSHOT,
        name = "Moonshot AI",
        description = "Kimi / Moonshot 系列模型",
        defaultBaseUrl = ProviderType.MOONSHOT.defaultBaseUrl,
    ),
    ProviderTemplate(
        type = ProviderType.ZHIPU,
        name = "智谱 AI",
        description = "GLM-4 / CogView 系列模型",
        defaultBaseUrl = ProviderType.ZHIPU.defaultBaseUrl,
    ),
    ProviderTemplate(
        type = ProviderType.QWEN,
        name = "通义千问",
        description = "Qwen-Max / Qwen-Plus 等系列模型",
        defaultBaseUrl = ProviderType.QWEN.defaultBaseUrl,
    ),
    ProviderTemplate(
        type = ProviderType.CUSTOM,
        name = "自定义提供商",
        description = "任意 OpenAI 兼容 API 端点",
        defaultBaseUrl = "",
    ),
)
