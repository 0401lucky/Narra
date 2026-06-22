package com.example.myapplication.model

enum class ImagePromptPurpose(val storageValue: String, val displayName: String) {
    GENERAL("general", "通用图片"),
    GIFT("gift", "礼物图"),
    MOMENT("moment", "动态配图"),
    AI_PHOTO("ai_photo", "聊天照片"),
    CHARACTER_ART("character_art", "角色图"),
    SHOP_ITEM("shop_item", "商品图"),
    PROP("prop", "道具图");
}

data class ImagePromptPolishRequest(
    val purpose: ImagePromptPurpose,
    val basePrompt: String,
    val subject: String = "",
    val styleHint: String = "",
    val roleContext: String = "",
    val sceneContext: String = "",
    val userInstruction: String = "",
    val negativePrompt: String = "",
)

data class ImagePromptPolishResult(
    val visualPrompt: String,
    val negativePrompt: String = "",
    val styleSummary: String = "",
    val safetyNotes: String = "",
) {
    fun finalPrompt(): String {
        val visual = visualPrompt.trim()
        val negative = negativePrompt.trim()
        return buildString {
            append(visual.ifBlank { "high quality, detailed, clean composition" })
            if (negative.isNotBlank()) {
                append("\nAvoid: ")
                append(negative)
            }
        }.trim()
    }
}

fun ImagePromptPolishRequest.fallbackPolishResult(): ImagePromptPolishResult {
    val qualityPack = when (purpose) {
        ImagePromptPurpose.GENERAL -> "high quality, detailed, polished image, clean composition, cinematic lighting, depth of field"
        ImagePromptPurpose.MOMENT -> "high quality, natural phone photo, detailed, realistic daily-life lighting, clean composition, depth of field"
        ImagePromptPurpose.AI_PHOTO -> "high quality, realistic in-chat photo, natural phone-camera composition, believable lighting, detailed, depth of field"
        ImagePromptPurpose.CHARACTER_ART -> "high quality, detailed character illustration, polished rendering, clean silhouette, cinematic lighting"
        ImagePromptPurpose.GIFT,
        ImagePromptPurpose.SHOP_ITEM,
        ImagePromptPurpose.PROP,
        -> "high quality, detailed, premium object rendering, refined materials, soft diffuse lighting, clean aesthetic, depth of field"
    }
    val negative = negativePrompt.ifBlank {
        "low quality, blurry, watermark, logo, text, UI frame, distorted, extra fingers, deformed, duplicate objects"
    }
    return ImagePromptPolishResult(
        visualPrompt = listOf(
            qualityPack,
            styleHint,
            subject,
            basePrompt,
            sceneContext,
        ).map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .joinToString(separator = ", ")
            .take(1800),
        negativePrompt = negative.take(500),
    )
}
