package com.example.myapplication.roleplay

import com.example.myapplication.model.RoleplayMessageUiModel
import com.example.myapplication.model.RoleplaySpeaker

enum class RoleplaySceneMood(val defaultLabel: String) {
    NEUTRAL("平稳"),
    WARM("靠近"),
    TENSE("紧张"),
    SHARP("锋利"),
    MUTED("低落"),
}

data class RoleplaySceneMoodState(
    val mood: RoleplaySceneMood,
    val label: String,
)

fun resolveRoleplaySceneMood(
    messages: List<RoleplayMessageUiModel>,
): RoleplaySceneMoodState {
    val latestEmotion = messages
        .asReversed()
        .firstOrNull { message ->
            message.speaker == RoleplaySpeaker.CHARACTER &&
                message.emotion.isNotBlank()
        }
        ?.emotion
        ?.trim()
        .orEmpty()
    if (latestEmotion.isBlank()) {
        return RoleplaySceneMoodState(
            mood = RoleplaySceneMood.NEUTRAL,
            label = RoleplaySceneMood.NEUTRAL.defaultLabel,
        )
    }

    val mood = when {
        latestEmotion.containsAny("温柔", "开心", "暧昧", "放松", "轻快", "安心") -> RoleplaySceneMood.WARM
        latestEmotion.containsAny("紧张", "警惕", "压迫", "不安", "危险", "慌乱", "试探") -> RoleplaySceneMood.TENSE
        latestEmotion.containsAny("生气", "冷淡", "强势", "讥讽", "凌厉", "疏离") -> RoleplaySceneMood.SHARP
        latestEmotion.containsAny("疲惫", "悲伤", "失落", "低落", "倦怠", "苦涩") -> RoleplaySceneMood.MUTED
        else -> RoleplaySceneMood.NEUTRAL
    }
    return RoleplaySceneMoodState(
        mood = mood,
        label = latestEmotion,
    )
}

private fun String.containsAny(vararg candidates: String): Boolean {
    return candidates.any { candidate -> contains(candidate) }
}
