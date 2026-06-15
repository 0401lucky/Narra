package com.example.myapplication.data.repository.ai

import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ChatMessageDto
import com.example.myapplication.model.CharacterShakeFilters
import com.example.myapplication.model.DEFAULT_ASSISTANT_ICON
import com.example.myapplication.model.PRESET_ASSISTANT_ICONS
import com.example.myapplication.model.PromptMode
import com.example.myapplication.model.ProviderApiProtocol
import com.example.myapplication.model.ProviderSettings
import com.google.gson.JsonObject

private const val CHARACTER_SHAKE_OPERATION = "摇一摇角色卡生成"
private const val DEFAULT_GENERATED_ICON = "auto_stories"

internal class CharacterShakePromptService(
    private val core: PromptExtrasCore,
) {
    suspend fun generateAssistantCard(
        filters: CharacterShakeFilters,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: ProviderApiProtocol = ProviderApiProtocol.OPENAI_COMPATIBLE,
        provider: ProviderSettings? = null,
    ): Assistant {
        val request = core.buildRequestWithRoleplaySampling(
            model = modelId,
            baseUrl = baseUrl,
            apiProtocol = apiProtocol,
            promptMode = PromptMode.ROLEPLAY,
            messages = listOf(
                ChatMessageDto(
                    role = "user",
                    content = buildCharacterShakePrompt(filters),
                ),
            ),
        )
        val content = core.requestCompletionContent(
            baseUrl = baseUrl,
            apiKey = apiKey,
            operation = CHARACTER_SHAKE_OPERATION,
            request = request,
            apiProtocol = apiProtocol,
            provider = provider,
            allowRoleplaySamplingFallback = true,
        )
        val json = core.parseRequiredStructuredJsonObject(
            content = content,
            operation = CHARACTER_SHAKE_OPERATION,
        )
        return parseAssistant(json)
    }

    private fun buildCharacterShakePrompt(filters: CharacterShakeFilters): String = buildString {
        appendLine("你是 Narra 的角色卡策划。请根据筛选条件创作一份可以直接投入长期沉浸式对话的原创角色卡。")
        appendLine()
        appendLine("【筛选条件】")
        appendLine("- 性别偏好：${filters.gender.ifBlank { "不限，请随机" }}")
        appendLine("- 年龄区间：${filters.ageRange.ifBlank { "不限，请随机，但角色必须是成年人" }}")
        appendLine("- 性格特征：${filters.personality.ifBlank { "不限，请随机" }}")
        appendLine("- 身份特点：${filters.identity.ifBlank { "不限，请随机" }}")
        appendLine("- 关系定位：${filters.relationship.ifBlank { "不限，请随机" }}")
        appendLine("- 个人特征：${filters.personalTrait.ifBlank { "不限，请随机" }}")
        appendLine()
        appendLine("【创作要求】")
        appendLine("1. 角色必须为 18 岁以上成年人；不要写现实公众人物、未成年人或真实可识别私人信息。")
        appendLine("2. 人设要有可持续互动的矛盾点、边界感、说话习惯和关系推进空间；如果指定了关系定位，scenario 和 greeting 都要自然体现该关系。")
        appendLine("3. system_prompt 必须是完整角色人设，不是简介；需要包含性格、身份、表达风格、互动边界、长期扮演规则。")
        appendLine("4. greeting 要像第一条消息，能直接出现在聊天里。")
        appendLine("5. example_dialogues 写 2 到 4 条，每条使用“用户：...\\n角色：...”格式。")
        appendLine("6. tags 写 3 到 6 个短标签；icon_name 必须从下列值中选一个：")
        appendLine(PRESET_ASSISTANT_ICONS.joinToString(", ") { it.name })
        appendLine()
        appendLine("【输出格式】")
        appendLine("只输出一个 JSON 对象，不要 Markdown，不要解释。字段如下：")
        appendLine(
            """
            {
              "name": "角色名",
              "icon_name": "auto_stories",
              "description": "30 到 80 字的角色简介",
              "system_prompt": "完整角色人设，适合直接注入对话上下文",
              "scenario": "默认关系、相遇背景与当前互动起点",
              "greeting": "角色开场白",
              "example_dialogues": ["用户：...\\n角色：...", "用户：...\\n角色：..."],
              "creator_notes": "适合玩法和生成依据",
              "tags": ["标签1", "标签2"],
              "memory_enabled": true
            }
            """.trimIndent(),
        )
    }

    private fun parseAssistant(json: JsonObject): Assistant {
        val name = json.stringValueAny("name", "名称", "角色名")
            .take(24)
            .ifBlank { "摇出的角色" }
        val description = json.stringValueAny("description", "简介", "角色简介")
            .take(180)
        val systemPrompt = json.stringValueAny("system_prompt", "systemPrompt", "角色人设", "人设")
            .ifBlank { description }
        val iconName = json.stringValueAny("icon_name", "iconName", "图标")
            .takeIf { icon -> icon in PRESET_ASSISTANT_ICONS.map { it.name }.toSet() }
            ?: DEFAULT_GENERATED_ICON
        val tags = json.stringArrayValue("tags", "标签")
            .map { it.take(12) }
            .distinct()
            .take(6)
            .ifEmpty { listOf("摇一摇") }
        val creatorNotes = buildString {
            val notes = json.stringValueAny("creator_notes", "creatorNotes", "作者备注").trim()
            if (notes.isNotBlank()) append(notes)
            if (isNotBlank()) append("\n\n")
            append("由发现页“摇一摇”生成，可继续在角色资料中微调。")
        }

        return Assistant(
            name = name,
            iconName = iconName.ifBlank { DEFAULT_ASSISTANT_ICON },
            description = description,
            systemPrompt = systemPrompt,
            scenario = json.stringValueAny("scenario", "场景设定", "默认场景"),
            greeting = json.stringValueAny("greeting", "开场白"),
            exampleDialogues = json.stringArrayValue("example_dialogues", "exampleDialogues", "示例对话")
                .filter { it.isNotBlank() }
                .take(4),
            creatorNotes = creatorNotes,
            tags = tags,
            memoryEnabled = json.booleanValueAny("memory_enabled", "memoryEnabled") ?: true,
            isBuiltin = false,
        )
    }
}

private fun JsonObject.stringValueAny(vararg keys: String): String {
    return keys.firstNotNullOfOrNull { key ->
        stringValue(key).takeIf { it.isNotBlank() }
    }.orEmpty()
}

private fun JsonObject.stringArrayValue(vararg keys: String): List<String> {
    val array = keys.firstNotNullOfOrNull { key -> getAsJsonArrayOrNull(key) } ?: return emptyList()
    return array.mapNotNull { element ->
        element.takeIf { it.isJsonPrimitive }
            ?.asString
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }
}

private fun JsonObject.booleanValueAny(vararg keys: String): Boolean? {
    return keys.firstNotNullOfOrNull { key ->
        get(key)
            ?.takeIf { !it.isJsonNull && it.isJsonPrimitive }
            ?.let { runCatching { it.asBoolean }.getOrNull() }
    }
}
