package com.example.myapplication.data.repository.ai

import com.example.myapplication.model.ChatMessageDto
import com.example.myapplication.model.ImagePromptPolishRequest
import com.example.myapplication.model.ImagePromptPolishResult
import com.example.myapplication.model.ImagePromptPurpose
import com.example.myapplication.model.PromptMode
import com.example.myapplication.model.ProviderApiProtocol
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.fallbackPolishResult
import com.google.gson.JsonObject

private const val IMAGE_PROMPT_POLISH_OPERATION = "图片提示词润色"

internal class ImagePromptPolishService(
    private val core: PromptExtrasCore,
) {
    suspend fun polishImagePrompt(
        request: ImagePromptPolishRequest,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: ProviderApiProtocol,
        provider: ProviderSettings?,
    ): ImagePromptPolishResult {
        val fallback = request.fallbackPolishResult()
        if (request.basePrompt.isBlank() && request.subject.isBlank()) {
            return fallback
        }
        val content = core.requestCompletionContent(
            baseUrl = baseUrl,
            apiKey = apiKey,
            operation = IMAGE_PROMPT_POLISH_OPERATION,
            request = core.buildRequestWithRoleplaySampling(
                model = modelId,
                baseUrl = baseUrl,
                apiProtocol = apiProtocol,
                promptMode = PromptMode.ROLEPLAY,
                messages = listOf(
                    ChatMessageDto(
                        role = "user",
                        content = buildPrompt(request),
                    ),
                ),
            ),
            apiProtocol = apiProtocol,
            provider = provider,
            allowRoleplaySamplingFallback = true,
        )
        val json = core.parseRequiredStructuredJsonObject(
            content = content,
            operation = IMAGE_PROMPT_POLISH_OPERATION,
        )
        return parseResult(json, fallback)
    }

    private fun buildPrompt(request: ImagePromptPolishRequest): String = buildString {
        appendLine("你是 Narra 的全局文生图提示词润色器。")
        appendLine("目标：把用户/系统给出的简短画面描述，润色为适合直接发给生图模型的英文高质量提示词。")
        appendLine("输出必须服务角色扮演沉浸感，同时保持画面清晰、稳定、可控。")
        appendLine()
        appendLine("【图片用途】")
        appendLine("${request.purpose.displayName} / ${request.purpose.storageValue}")
        appendLine(purposeRules(request.purpose))
        appendLine()
        appendLine("【基础画面】")
        appendLine(request.basePrompt.take(1600))
        if (request.subject.isNotBlank()) {
            appendLine()
            appendLine("【主体】")
            appendLine(request.subject.take(300))
        }
        if (request.styleHint.isNotBlank()) {
            appendLine()
            appendLine("【风格偏好】")
            appendLine(request.styleHint.take(500))
        }
        if (request.roleContext.isNotBlank()) {
            appendLine()
            appendLine("【角色外观/人设参考】")
            appendLine(request.roleContext.take(1500))
        }
        if (request.sceneContext.isNotBlank()) {
            appendLine()
            appendLine("【剧情/场景参考】")
            appendLine(request.sceneContext.take(900))
        }
        if (request.userInstruction.isNotBlank()) {
            appendLine()
            appendLine("【用户补充】")
            appendLine(request.userInstruction.take(500))
        }
        if (request.negativePrompt.isNotBlank()) {
            appendLine()
            appendLine("【已有负面词】")
            appendLine(request.negativePrompt.take(500))
        }
        appendLine()
        appendLine("【强制要求】")
        appendLine("1. visual_prompt 必须是英文，包含质量、主体、材质/外观、镜头、光影、构图、氛围和必要细节。")
        appendLine("2. 不要把角色卡原文、聊天规则、系统规则、脏话、无关剧情、数据库/功能词写进画面。")
        appendLine("3. 中文人设只用于提取有效视觉特征；无法视觉化的性格/规则要转成画面气质或忽略。")
        appendLine("4. 如果涉及人物，默认表现为虚构成年角色；不要真人公众人物脸、未成年化、证件照或怪异写实脸。")
        appendLine("5. negative_prompt 要包含低质量、模糊、水印、文字、logo、UI、畸形、错误手指等通用负面词。")
        appendLine("6. 严格输出 JSON 对象，不要 Markdown，不要解释。")
        appendLine(
            """
            {
              "visual_prompt": "English high quality image prompt...",
              "negative_prompt": "low quality, blurry, watermark...",
              "style_summary": "中文，30 字以内，概括风格",
              "safety_notes": "中文，必要时说明过滤了什么"
            }
            """.trimIndent(),
        )
    }

    private fun purposeRules(purpose: ImagePromptPurpose): String {
        return when (purpose) {
            ImagePromptPurpose.GENERAL -> "通用文生图要严格保留用户原始主体和意图，只增强质量词、镜头、光影、构图和负面词。"
            ImagePromptPurpose.GIFT -> "礼物图要突出物品本体、包装、材质、送礼氛围，不要人物正脸，不要界面元素。"
            ImagePromptPurpose.MOMENT -> "朋友圈配图要像真实生活记录或手机随手拍，避免广告图和纯概念海报。"
            ImagePromptPurpose.AI_PHOTO -> "聊天照片要像角色会发来的照片，保持角色外观、当前场景和普通手机摄影感。"
            ImagePromptPurpose.CHARACTER_ART -> "角色图要突出虚构成年角色外貌、服装、姿态和统一画风。"
            ImagePromptPurpose.SHOP_ITEM -> "商品图要像可购买道具详情图，突出可识别主体、质感、用途和收藏价值。"
            ImagePromptPurpose.PROP -> "道具图要突出剧情可使用性，让物品看起来能在后续剧情中发挥作用。"
        }
    }

    private fun parseResult(
        json: JsonObject,
        fallback: ImagePromptPolishResult,
    ): ImagePromptPolishResult {
        return ImagePromptPolishResult(
            visualPrompt = json.stringValueAny("visual_prompt", "visualPrompt", "prompt")
                .ifBlank { fallback.visualPrompt }
                .take(2000),
            negativePrompt = json.stringValueAny("negative_prompt", "negativePrompt", "negative")
                .ifBlank { fallback.negativePrompt }
                .take(700),
            styleSummary = json.stringValueAny("style_summary", "styleSummary", "summary")
                .take(80),
            safetyNotes = json.stringValueAny("safety_notes", "safetyNotes", "safety")
                .take(160),
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
