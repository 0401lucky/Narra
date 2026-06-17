package com.example.myapplication.data.repository.ai

import com.example.myapplication.model.Assistant
import com.example.myapplication.model.CharacterArtPromptDraft
import com.example.myapplication.model.CharacterArtStyle
import com.example.myapplication.model.ChatMessageDto
import com.example.myapplication.model.PromptMode
import com.example.myapplication.model.ProviderApiProtocol
import com.example.myapplication.model.ProviderSettings
import com.google.gson.JsonObject

private const val CHARACTER_ART_OPERATION = "角色图提示词生成"

internal class CharacterArtPromptService(
    private val core: PromptExtrasCore,
) {
    suspend fun generateCharacterArtPrompt(
        assistant: Assistant,
        style: CharacterArtStyle,
        revisionInstruction: String,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: ProviderApiProtocol = ProviderApiProtocol.OPENAI_COMPATIBLE,
        provider: ProviderSettings? = null,
    ): CharacterArtPromptDraft {
        val request = core.buildRequestWithRoleplaySampling(
            model = modelId,
            baseUrl = baseUrl,
            apiProtocol = apiProtocol,
            promptMode = PromptMode.ROLEPLAY,
            messages = listOf(
                ChatMessageDto(
                    role = "user",
                    content = buildPrompt(assistant, style, revisionInstruction),
                ),
            ),
        )
        val content = core.requestCompletionContent(
            baseUrl = baseUrl,
            apiKey = apiKey,
            operation = CHARACTER_ART_OPERATION,
            request = request,
            apiProtocol = apiProtocol,
            provider = provider,
            allowRoleplaySamplingFallback = true,
        )
        val json = core.parseRequiredStructuredJsonObject(
            content = content,
            operation = CHARACTER_ART_OPERATION,
        )
        return parsePromptDraft(json, style)
    }

    private fun buildPrompt(
        assistant: Assistant,
        style: CharacterArtStyle,
        revisionInstruction: String,
    ): String = buildString {
        appendLine("你是 Narra 的角色视觉设定师。请根据角色卡提取可用于文生图的角色形象提示词。")
        appendLine("目标是生成原创虚构角色图，不要真人照片感，不要现实公众人物或可识别真人。")
        appendLine()
        appendLine("【角色信息】")
        appendLine("名称：${assistant.name.ifBlank { "未命名角色" }}")
        if (assistant.description.isNotBlank()) appendLine("简介：${assistant.description.take(300)}")
        if (assistant.systemPrompt.isNotBlank()) appendLine("人设：${assistant.systemPrompt.take(1600)}")
        if (assistant.scenario.isNotBlank()) appendLine("场景：${assistant.scenario.take(600)}")
        if (assistant.creatorNotes.isNotBlank()) appendLine("备注：${assistant.creatorNotes.take(500)}")
        if (assistant.tags.isNotEmpty()) appendLine("标签：${assistant.tags.joinToString("、")}")
        appendLine()
        appendLine("【风格选择】")
        appendLine("${style.displayName}：${style.promptHint}")
        val revision = revisionInstruction.trim()
        if (revision.isNotBlank()) {
            appendLine()
            appendLine("【用户修改意见】")
            appendLine(revision.take(500))
        }
        appendLine()
        appendLine("【要求】")
        appendLine("1. 只提取外貌、气质、服装、姿态、表情、画面氛围；不要把聊天规则写进画面。")
        appendLine("2. 如果角色年龄不明确，统一表现为 20 岁以上成年人。")
        appendLine("3. 不要生成真人摄影、写实证件照、影视明星脸、公众人物脸。")
        appendLine("4. visual_prompt 用英文，适合直接发给文生图模型；trait_summary 用中文给用户看。")
        appendLine("5. 输出 JSON 对象，不要 Markdown，不要解释。")
        appendLine(
            """
            {
              "trait_summary": "中文，80 字以内，概括角色视觉关键词",
              "visual_prompt": "English image prompt, one original fictional adult character portrait...",
              "negative_prompt": "photorealistic, real person, celebrity likeness, childlike, text, watermark..."
            }
            """.trimIndent(),
        )
    }

    private fun parsePromptDraft(
        json: JsonObject,
        style: CharacterArtStyle,
    ): CharacterArtPromptDraft {
        val visualPrompt = json.stringValueAny("visual_prompt", "visualPrompt", "prompt")
            .take(1800)
        val traitSummary = json.stringValueAny("trait_summary", "traitSummary", "summary")
            .take(160)
        val negativePrompt = json.stringValueAny("negative_prompt", "negativePrompt", "negative")
            .take(500)
        val fallbackPrompt = "An original fictional adult character portrait, ${style.promptHint}"
        return CharacterArtPromptDraft(
            traitSummary = traitSummary,
            visualPrompt = visualPrompt.ifBlank { fallbackPrompt },
            negativePrompt = negativePrompt.ifBlank {
                "photorealistic, real person, celebrity likeness, childlike, text, watermark, logo, low quality"
            },
        )
    }
}

private fun JsonObject.stringValueAny(vararg keys: String): String {
    return keys.firstNotNullOfOrNull { key ->
        get(key)
            ?.takeIf { !it.isJsonNull && it.isJsonPrimitive }
            ?.asString
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }.orEmpty()
}
