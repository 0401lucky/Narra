package com.example.myapplication.model

data class CharacterArtStyle(
    val id: String,
    val displayName: String,
    val subtitle: String,
    val promptHint: String,
)

data class CharacterArtPromptDraft(
    val traitSummary: String = "",
    val visualPrompt: String = "",
    val negativePrompt: String = "",
) {
    fun finalPrompt(style: CharacterArtStyle): String {
        val visual = visualPrompt.trim()
        val negative = negativePrompt.trim()
        return buildString {
            append(visual.ifBlank { "A polished original character portrait." })
            append("\n\nStyle direction: ")
            append(style.promptHint)
            append("\n\nHard constraints: illustrated fictional character only, non-photorealistic, adult appearance, tasteful composition, no real person likeness, no celebrity likeness, no text, no watermark, no logo, no UI frame.")
            if (negative.isNotBlank()) {
                append("\nAvoid: ")
                append(negative)
            } else {
                append("\nAvoid: photorealistic face, uncanny realism, distorted hands, extra fingers, low quality, blurry details, text, watermark, logo.")
            }
        }.trim()
    }
}

val CHARACTER_ART_STYLES: List<CharacterArtStyle> = listOf(
    CharacterArtStyle(
        id = "korean_impasto",
        displayName = "韩式厚涂",
        subtitle = "柔光、干净轮廓、精致五官",
        promptHint = "Korean semi-realistic digital painting, soft but confident brushwork, refined facial design, clean silhouette, warm cinematic lighting, premium character illustration.",
    ),
    CharacterArtStyle(
        id = "mobile_game_portrait",
        displayName = "手游立绘",
        subtitle = "可做头像，也能当角色档案图",
        promptHint = "high-end mobile game character portrait, polished concept art, expressive pose, crisp costume details, dramatic rim light, clean background.",
    ),
    CharacterArtStyle(
        id = "light_novel",
        displayName = "轻小说封面",
        subtitle = "明亮、灵动、剧情感更强",
        promptHint = "Japanese light novel cover illustration, vivid eyes, elegant anime rendering, airy composition, gentle color accents, story-rich atmosphere.",
    ),
    CharacterArtStyle(
        id = "guoman",
        displayName = "国漫插画",
        subtitle = "线条利落，情绪和身份感突出",
        promptHint = "modern Chinese animation character illustration, clean linework, expressive gaze, layered costume design, vivid but restrained colors, refined poster lighting.",
    ),
    CharacterArtStyle(
        id = "cyber_city",
        displayName = "赛博都市",
        subtitle = "霓虹、夜色、未来城市气质",
        promptHint = "cyberpunk city character art, neon reflections, dark urban mood, stylish outfit, luminous accents, cinematic futuristic portrait.",
    ),
    CharacterArtStyle(
        id = "soft_watercolor",
        displayName = "温柔水彩",
        subtitle = "低攻击性、清透、适合日常角色",
        promptHint = "soft watercolor character illustration, translucent colors, delicate facial expression, gentle daylight, minimal clean background, calm emotional tone.",
    ),
)

fun characterArtStyleById(styleId: String): CharacterArtStyle {
    return CHARACTER_ART_STYLES.firstOrNull { it.id == styleId } ?: CHARACTER_ART_STYLES.first()
}
