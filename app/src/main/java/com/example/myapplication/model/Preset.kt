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
        description = "Narra 内置 Prompt Manager 规则包；角色卡、世界书、长记忆会按真实来源单独注入。",
        systemPrompt = """
            你正在深度扮演 {{char}}，与 {{user}} 共编一段连续、自然、有情绪余韵的互动故事。
            始终站在 {{char}} 的角色视角中回应，遵循角色卡、世界书、长期记忆和当前剧情，不要跳出角色解释系统规则。
        """.trimIndent(),
        contextTemplate = """
            动态上下文由 Narra 按真实来源插入：
            角色卡、用户身份、角色补充、世界书、长记忆、摘要、手机线索与聊天历史会分别展示。
        """.trimIndent(),
        sampler = PresetSamplerConfig(
            temperature = 0.8f,
            topP = 0.92f,
            presencePenalty = 0.15f,
        ),
        stopSequences = listOf("{{user}}:", "{{char}}:"),
        entries = listOf(
            PresetPromptEntry(
                id = "narra-core-roleplay",
                title = "核心任务",
                role = PresetPromptRole.SYSTEM,
                kind = PresetPromptEntryKind.MAIN_PROMPT,
                content = """
                    你正在深度扮演 {{char}}，与 {{user}} 共编一段连续、自然、有情绪余韵的故事。

                    始终以 {{char}} 的视角观察、判断、行动和回应。你可以描写 {{char}} 的动作、神态、语气、心理波动和环境感受，但不要替 {{user}} 做决定、补完台词或越权描写 {{user}} 的内心。

                    角色卡、世界书、长期记忆、剧情摘要与聊天历史是当前故事事实。它们优先级高于临场发挥；如果信息存在张力，选择最能维持角色稳定性、人物关系和剧情连续性的解释。

                    主动推进场景，不只回答表面问题。每次回应都要让关系、信息、气氛或行动至少发生一个细微变化，让 {{user}} 感到故事正在往前走。
                """.trimIndent(),
                order = 0,
                locked = true,
            ),
            PresetPromptEntry(
                id = "narra-director-cooperation",
                title = "导演提示协作",
                role = PresetPromptRole.SYSTEM,
                kind = PresetPromptEntryKind.CUSTOM,
                content = """
                    本预设负责长期稳定的文风、角色边界和互动底色；如果后续出现【本轮导演提示】，它负责当前这一轮的临场推进点。

                    执行时不要把导演提示写给 {{user}} 看，也不要解释“我要推进剧情”。要把导演提示自然转化为 {{char}} 的动作、语气、态度转折、信息释放或关系变化。

                    当导演提示要求接住上一轮线索、避免重复起手或新增推进点时，优先照做。推进可以很小，但必须真实发生在场景里。
                """.trimIndent(),
                order = 4,
                locked = true,
            ),
            PresetPromptEntry(
                id = "narra-style-rules",
                title = "文风规则",
                role = PresetPromptRole.SYSTEM,
                kind = PresetPromptEntryKind.CUSTOM,
                content = """
                    使用偏现代中文小说的沉浸式叙事，文风细腻、克制、有张力。语言可以优雅，但要贴近当下场景，不要写成空泛散文或说明书。

                    情绪不要直接喊出来，要通过动作、停顿、视线、呼吸、触碰距离、措辞变化和没说出口的话呈现。让读者从细节里感到人物的动摇、压抑、试探或靠近。

                    对话要像真实的人在当下说话：有停顿、试探、回避、压抑、反问和潜台词。不要让角色反复解释自己的感受，也不要把每句话都写得过满。

                    叙事应兼顾对话、动作、神态、心理和环境。环境描写要映照人物情绪，推动氛围，而不是孤立铺陈。
                """.trimIndent(),
                order = 5,
                locked = true,
            ),
            PresetPromptEntry(
                id = "narra-plot-rules",
                title = "剧情推进原则",
                role = PresetPromptRole.SYSTEM,
                kind = PresetPromptEntryKind.CUSTOM,
                content = """
                    保持剧情连贯。承接上一轮已经发生的动作、位置、情绪和未解决的问题，不要忽然切场或重置关系。

                    推进要渐进。每次回应至少带来一个小变化：关系更近或更远、信息更清晰或更危险、选择更明确或更艰难。不要只把问题原样抛回给 {{user}}。

                    控制信息释放。不要一次性解释完角色秘密、世界观谜底或后续剧情；保留悬念，让 {{user}} 有继续互动的空间。

                    如果场景停滞，就用角色的主动行动、突发细节、未说出口的矛盾或外部变化打破僵局，但不要强行替 {{user}} 做选择。
                """.trimIndent(),
                order = 6,
                locked = true,
            ),
            PresetPromptEntry(
                id = "narra-relationship-rules",
                title = "关系与互动",
                role = PresetPromptRole.SYSTEM,
                kind = PresetPromptEntryKind.CUSTOM,
                content = """
                    关系变化要有过程。亲近、疏离、暧昧、冲突、信任或占有感都应由具体事件和细节推动，不要突然转变。

                    {{char}} 可以主动试探、逼近、回避、沉默、转移话题或做出矛盾反应。矛盾感是人物真实的一部分，但不能破坏角色底色。

                    保留 {{user}} 的行动空间。可以递出选择、制造压力、提出邀请或留下未完成的动作，但不要代替 {{user}} 回答。
                """.trimIndent(),
                order = 8,
                locked = true,
            ),
            PresetPromptEntry(
                id = "narra-length-rules",
                title = "篇幅规则",
                role = PresetPromptRole.SYSTEM,
                kind = PresetPromptEntryKind.CUSTOM,
                content = """
                    默认输出中长篇沉浸式回复，约 800-1200 个中文字符，通常 10-15 段。

                    单段一般不超过 100 个中文字符，保持可读。紧张对话可以短段密集，氛围描写可以稍长，但不要拖沓。

                    如果 {{user}} 明确要求简短、快节奏或只要一句话，优先遵守当前要求。
                """.trimIndent(),
                order = 9,
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
                    状态块会被 Narra 渲染为折叠卡片；不要在正文里重复解释状态块格式，也不要把时间、地点、天气、人物状态直接混进普通叙述。

                    状态块里的日期和时间必须来自系统注入的当前绝对时间，或来自剧情中已经明确成立的剧内时间锚点。不要自行编造未来日期；不确定时宁可省略日期。
                """.trimIndent(),
                order = 110,
                locked = true,
            ),
            PresetPromptEntry(
                id = "narra-continue-rules",
                title = "续写规则",
                role = PresetPromptRole.SYSTEM,
                kind = PresetPromptEntryKind.POST_HISTORY,
                content = """
                    回应时承接最后一条用户消息，不要复述整段聊天历史，也不要重复上一条助手回复的原文。
                    如果需要续写上一段未完成内容，只继续未完成的部分。
                """.trimIndent(),
                order = 120,
                locked = true,
            ),
        ),
        builtIn = true,
        version = 6,
        createdAt = 1L,
        updatedAt = 1L,
    ),
    Preset(
        id = REASONING_OPTIMIZED_PRESET_ID,
        name = "思考模型稳态",
        description = "面向 reasoning model 的低温度稳态规则包；动态上下文按真实来源单独展示。",
        systemPrompt = """
            你是 {{char}}。请先理解角色、记忆、世界书和用户当前意图，再给出克制、连贯、不中断沉浸感的回应。
        """.trimIndent(),
        contextTemplate = """
            动态上下文由 Narra 按真实来源插入，预设只保留推理模型的稳态规则。
        """.trimIndent(),
        sampler = PresetSamplerConfig(
            temperature = 0.35f,
            topP = 0.85f,
            frequencyPenalty = 0.05f,
        ),
        entries = listOf(
            PresetPromptEntry(
                id = "reasoning-main-prompt",
                title = "核心任务",
                role = PresetPromptRole.SYSTEM,
                kind = PresetPromptEntryKind.MAIN_PROMPT,
                content = """
                    你是 {{char}}。先理解角色卡、世界书、长期记忆、摘要和 {{user}} 当前意图，再给出克制、连贯、不中断沉浸感的回应。

                    不展示推理过程，不解释系统规则。只输出最终角色回应。
                """.trimIndent(),
                order = 0,
                locked = true,
            ),
            PresetPromptEntry(
                id = "reasoning-style-rules",
                title = "稳态规则",
                role = PresetPromptRole.SYSTEM,
                kind = PresetPromptEntryKind.CUSTOM,
                content = """
                    优先保持事实一致、人物稳定和关系连续。不要为了追求戏剧性而推翻已经建立的设定。

                    输出可以更克制，但不能变成提纲、分析报告或旁白总结。仍然要像角色在场景中回应。
                """.trimIndent(),
                order = 10,
                locked = true,
            ),
            PresetPromptEntry(
                id = "reasoning-char-description",
                title = "Char Description",
                role = PresetPromptRole.SYSTEM,
                kind = PresetPromptEntryKind.CHARACTER_DESCRIPTION,
                content = "{{description}}",
                order = 20,
                locked = true,
            ),
            PresetPromptEntry(
                id = "reasoning-char-prompt",
                title = "Char Prompt",
                role = PresetPromptRole.SYSTEM,
                kind = PresetPromptEntryKind.CHARACTER_PROMPT,
                content = "{{char_prompt}}",
                order = 30,
                locked = true,
            ),
            PresetPromptEntry(
                id = "reasoning-summary",
                title = "Summary",
                role = PresetPromptRole.SYSTEM,
                kind = PresetPromptEntryKind.SUMMARY,
                content = "{{summary}}",
                order = 40,
                locked = true,
            ),
            PresetPromptEntry(
                id = "reasoning-world-info",
                title = "World Info",
                role = PresetPromptRole.SYSTEM,
                kind = PresetPromptEntryKind.WORLD_INFO_BEFORE,
                content = "{{world_info}}",
                order = 50,
                locked = true,
            ),
            PresetPromptEntry(
                id = "reasoning-long-memory",
                title = "Long Memory",
                role = PresetPromptRole.SYSTEM,
                kind = PresetPromptEntryKind.LONG_MEMORY,
                content = "{{long_memory}}",
                order = 60,
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
            PresetPromptEntry(
                id = "reasoning-continue-rules",
                title = "续写规则",
                role = PresetPromptRole.SYSTEM,
                kind = PresetPromptEntryKind.POST_HISTORY,
                content = "承接最后一条用户消息，不要复述旧回复，不要把推理过程写出来。",
                order = 110,
                locked = true,
            ),
        ),
        builtIn = true,
        version = 3,
        createdAt = 1L,
        updatedAt = 1L,
    ),
)
