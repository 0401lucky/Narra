package com.example.myapplication.model

import java.util.UUID

const val DEFAULT_PRESET_ID = "builtin-zh-roleplay"
const val REASONING_OPTIMIZED_PRESET_ID = "builtin-reasoning-optimized"

data class Preset(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val description: String = "",
    val systemPrompt: String = "",
    val contextTemplate: String = "",
    val sampler: PresetSamplerConfig = PresetSamplerConfig(),
    val instructMode: PresetInstructConfig? = null,
    val stopSequences: List<String> = emptyList(),
    val entries: List<PresetPromptEntry> = emptyList(),
    val renderConfig: PresetRenderConfig = PresetRenderConfig(),
    val version: Int = 1,
    val builtIn: Boolean = false,
    val userModified: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
) {
    fun normalized(): Preset {
        val now = System.currentTimeMillis()
        val resolvedCreatedAt = createdAt.takeIf { it > 0L } ?: now
        return copy(
            id = id.trim().ifBlank { UUID.randomUUID().toString() },
            name = name.trim(),
            description = description.trim(),
            systemPrompt = systemPrompt.replace("\r\n", "\n").trim(),
            contextTemplate = contextTemplate.replace("\r\n", "\n").trim(),
            stopSequences = stopSequences.map(String::trim).filter(String::isNotBlank).distinct(),
            entries = entries
                .ifEmpty { legacyEntries() }
                .mapIndexed { index, entry -> entry.normalized(index) }
                .distinctBy(PresetPromptEntry::id),
            renderConfig = renderConfig.normalized(),
            version = version.coerceAtLeast(1),
            createdAt = resolvedCreatedAt,
            updatedAt = updatedAt.takeIf { it > 0L } ?: resolvedCreatedAt,
        )
    }

    private fun legacyEntries(): List<PresetPromptEntry> {
        val legacy = mutableListOf<PresetPromptEntry>()
        if (systemPrompt.isNotBlank()) {
            legacy += PresetPromptEntry(
                id = "legacy-main-prompt",
                title = "Main Prompt",
                role = PresetPromptRole.SYSTEM,
                kind = PresetPromptEntryKind.MAIN_PROMPT,
                content = systemPrompt,
                order = 0,
                locked = builtIn,
            )
        }
        if (contextTemplate.isNotBlank()) {
            legacy += PresetPromptEntry(
                id = "legacy-context-template",
                title = "Context Template",
                role = PresetPromptRole.SYSTEM,
                kind = PresetPromptEntryKind.CONTEXT_TEMPLATE,
                content = contextTemplate,
                order = 10,
                locked = builtIn,
            )
        }
        legacy += PresetPromptEntry(
            id = "legacy-chat-history",
            title = "Chat History",
            role = PresetPromptRole.SYSTEM,
            kind = PresetPromptEntryKind.CHAT_HISTORY,
            content = "",
            order = 100,
            locked = true,
        )
        return legacy
    }
}

data class PresetSamplerConfig(
    val temperature: Float? = null,
    val topP: Float? = null,
    val topK: Int? = null,
    val minP: Float? = null,
    val repetitionPenalty: Float? = null,
    val frequencyPenalty: Float? = null,
    val presencePenalty: Float? = null,
    val maxOutputTokens: Int? = null,
)

data class PresetInstructConfig(
    val systemPrefix: String = "",
    val systemSuffix: String = "",
    val userPrefix: String = "",
    val userSuffix: String = "",
    val assistantPrefix: String = "",
    val assistantSuffix: String = "",
    val wrapWithNewlines: Boolean = true,
)

enum class PresetPromptRole(val storageValue: String, val label: String) {
    SYSTEM("system", "SYSTEM"),
    USER("user", "USER"),
    ASSISTANT("assistant", "ASSISTANT");

    companion object {
        fun fromStorageValue(value: String): PresetPromptRole {
            return entries.firstOrNull { it.storageValue == value.trim().lowercase() } ?: SYSTEM
        }
    }
}

enum class PresetPromptEntryKind(val storageValue: String, val label: String) {
    MAIN_PROMPT("main_prompt", "Main Prompt"),
    CONTEXT_TEMPLATE("context_template", "Context Template"),
    CHARACTER_DESCRIPTION("character_description", "Char Description"),
    CHARACTER_PROMPT("character_prompt", "Char Prompt"),
    USER_PERSONA("user_persona", "User Persona"),
    SCENARIO("scenario", "Scenario"),
    EXAMPLE_DIALOGUE("example_dialogue", "Chat Examples"),
    WORLD_INFO_BEFORE("world_info_before", "World Info before"),
    LONG_MEMORY("long_memory", "Long Memory"),
    SUMMARY("summary", "Summary"),
    PHONE_CONTEXT("phone_context", "Phone Context"),
    CHAT_HISTORY("chat_history", "Chat History"),
    POST_HISTORY("post_history", "Post-History"),
    STATUS_RULES("status_rules", "Status Rules"),
    CUSTOM("custom", "Custom");

    companion object {
        fun fromStorageValue(value: String): PresetPromptEntryKind {
            return entries.firstOrNull { it.storageValue == value.trim().lowercase() } ?: CUSTOM
        }
    }
}

data class PresetPromptEntry(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val role: PresetPromptRole = PresetPromptRole.SYSTEM,
    val kind: PresetPromptEntryKind = PresetPromptEntryKind.CUSTOM,
    val content: String = "",
    val enabled: Boolean = true,
    val order: Int = 0,
    val locked: Boolean = false,
) {
    fun normalized(index: Int = order): PresetPromptEntry {
        return copy(
            id = id.trim().ifBlank { UUID.randomUUID().toString() },
            title = title.trim().ifBlank { kind.label },
            content = content.replace("\r\n", "\n").trim(),
            order = order.takeIf { it >= 0 } ?: index,
        )
    }
}

data class PresetRenderConfig(
    val statusCardsEnabled: Boolean = true,
    val hideStatusBlocksInBubble: Boolean = true,
) {
    fun normalized(): PresetRenderConfig = this
}

val BUILTIN_PRESETS: List<Preset> = listOf(
    Preset(
        id = DEFAULT_PRESET_ID,
        name = "Narra 默认预设",
        description = "Narra 内置只读 Prompt Manager，包装当前角色长期相处的默认提示词结构。",
        systemPrompt = """
            你正在扮演 {{char}}，请稳定遵循角色描述、世界书、长期记忆和当前剧情。
            用自然中文回应 {{user}}，保持关系连续性，避免跳出角色解释系统规则。
        """.trimIndent(),
        contextTemplate = """
            【角色描述】
            {{description}}

            【当前场景】
            {{scenario}}

            【世界书】
            {{world_info}}

            【长期记忆】
            {{long_memory}}

            【剧情摘要】
            {{summary}}

            【示例对话】
            {{example_dialogue}}
        """.trimIndent(),
        sampler = PresetSamplerConfig(
            temperature = 0.8f,
            topP = 0.92f,
            presencePenalty = 0.15f,
        ),
        stopSequences = listOf("{{user}}:", "{{char}}:"),
        entries = listOf(
            PresetPromptEntry(
                id = "narra-main-prompt",
                title = "Main Prompt",
                role = PresetPromptRole.SYSTEM,
                kind = PresetPromptEntryKind.MAIN_PROMPT,
                content = """
                    你正在扮演 {{char}}，请稳定遵循角色描述、世界书、长期记忆和当前剧情。
                    用自然中文回应 {{user}}，保持关系连续性，避免跳出角色解释系统规则。
                """.trimIndent(),
                order = 0,
                locked = true,
            ),
            PresetPromptEntry(
                id = "narra-char-description",
                title = "Char Description",
                role = PresetPromptRole.SYSTEM,
                kind = PresetPromptEntryKind.CHARACTER_DESCRIPTION,
                content = "{{description}}",
                order = 10,
                locked = true,
            ),
            PresetPromptEntry(
                id = "narra-char-prompt",
                title = "Char Prompt",
                role = PresetPromptRole.SYSTEM,
                kind = PresetPromptEntryKind.CHARACTER_PROMPT,
                content = "{{char_prompt}}",
                order = 20,
                locked = true,
            ),
            PresetPromptEntry(
                id = "narra-user-persona",
                title = "User Persona",
                role = PresetPromptRole.SYSTEM,
                kind = PresetPromptEntryKind.USER_PERSONA,
                content = "{{persona}}",
                order = 30,
                locked = true,
            ),
            PresetPromptEntry(
                id = "narra-scenario",
                title = "Scenario",
                role = PresetPromptRole.SYSTEM,
                kind = PresetPromptEntryKind.SCENARIO,
                content = "{{scenario}}",
                order = 40,
                locked = true,
            ),
            PresetPromptEntry(
                id = "narra-examples",
                title = "Chat Examples",
                role = PresetPromptRole.SYSTEM,
                kind = PresetPromptEntryKind.EXAMPLE_DIALOGUE,
                content = "{{example_dialogue}}",
                order = 50,
                locked = true,
            ),
            PresetPromptEntry(
                id = "narra-summary",
                title = "Summary",
                role = PresetPromptRole.SYSTEM,
                kind = PresetPromptEntryKind.SUMMARY,
                content = "{{summary}}",
                order = 60,
                locked = true,
            ),
            PresetPromptEntry(
                id = "narra-world-info",
                title = "World Info",
                role = PresetPromptRole.SYSTEM,
                kind = PresetPromptEntryKind.WORLD_INFO_BEFORE,
                content = "{{world_info}}",
                order = 70,
                locked = true,
            ),
            PresetPromptEntry(
                id = "narra-long-memory",
                title = "Long Memory",
                role = PresetPromptRole.SYSTEM,
                kind = PresetPromptEntryKind.LONG_MEMORY,
                content = "{{long_memory}}",
                order = 80,
                locked = true,
            ),
            PresetPromptEntry(
                id = "narra-phone-context",
                title = "Phone Context",
                role = PresetPromptRole.SYSTEM,
                kind = PresetPromptEntryKind.PHONE_CONTEXT,
                content = "{{phone_context}}",
                order = 90,
                locked = true,
            ),
            PresetPromptEntry(
                id = "narra-chat-history",
                title = "Chat History",
                role = PresetPromptRole.SYSTEM,
                kind = PresetPromptEntryKind.CHAT_HISTORY,
                order = 100,
                locked = true,
            ),
            PresetPromptEntry(
                id = "narra-status-rules",
                title = "Status Rules",
                role = PresetPromptRole.SYSTEM,
                kind = PresetPromptEntryKind.STATUS_RULES,
                content = """
                    如果需要表达角色当前状态，可以使用 <status>...</status> 输出一段简短状态块。
                    状态块会被 Narra 渲染为折叠卡片；不要在正文里重复解释状态块格式。
                """.trimIndent(),
                order = 110,
                locked = true,
            ),
        ),
        builtIn = true,
        version = 2,
        createdAt = 1L,
        updatedAt = 1L,
    ),
    Preset(
        id = REASONING_OPTIMIZED_PRESET_ID,
        name = "思考模型稳态",
        description = "面向 reasoning model 的低温度稳态输出模板。",
        systemPrompt = """
            你是 {{char}}。请先理解角色、记忆、世界书和用户当前意图，再给出克制、连贯、不中断沉浸感的回应。
        """.trimIndent(),
        contextTemplate = """
            {{description}}

            {{world_info}}

            {{long_memory}}

            {{summary}}
        """.trimIndent(),
        sampler = PresetSamplerConfig(
            temperature = 0.35f,
            topP = 0.85f,
            frequencyPenalty = 0.05f,
        ),
        entries = listOf(
            PresetPromptEntry(
                id = "reasoning-main-prompt",
                title = "Main Prompt",
                role = PresetPromptRole.SYSTEM,
                kind = PresetPromptEntryKind.MAIN_PROMPT,
                content = "你是 {{char}}。请先理解角色、记忆、世界书和用户当前意图，再给出克制、连贯、不中断沉浸感的回应。",
                order = 0,
                locked = true,
            ),
            PresetPromptEntry(
                id = "reasoning-context",
                title = "Context",
                role = PresetPromptRole.SYSTEM,
                kind = PresetPromptEntryKind.CONTEXT_TEMPLATE,
                content = """
                    {{description}}

                    {{world_info}}

                    {{long_memory}}

                    {{summary}}
                """.trimIndent(),
                order = 10,
                locked = true,
            ),
            PresetPromptEntry(
                id = "reasoning-chat-history",
                title = "Chat History",
                role = PresetPromptRole.SYSTEM,
                kind = PresetPromptEntryKind.CHAT_HISTORY,
                order = 100,
                locked = true,
            ),
        ),
        builtIn = true,
        version = 2,
        createdAt = 1L,
        updatedAt = 1L,
    ),
)
