package com.example.myapplication.model

import androidx.compose.runtime.Immutable

const val MIMO_TTS_MODEL_PRESET = "mimo-v2.5-tts"
const val MIMO_TTS_MODEL_VOICE_DESIGN = "mimo-v2.5-tts-voicedesign"
const val MIMO_TTS_MODEL_VOICE_CLONE = "mimo-v2.5-tts-voiceclone"
const val MIMO_DEFAULT_VOICE_ID = "mimo_default"
const val MIMO_TOKEN_PLAN_BASE_URL = "https://token-plan-cn.xiaomimimo.com/v1"
const val MIMO_STANDARD_BASE_URL = "https://api.xiaomimimo.com/v1"
const val MIMO_DEFAULT_BASE_URL = MIMO_TOKEN_PLAN_BASE_URL
const val MIMO_CHAT_COMPLETIONS_PATH = "/chat/completions"

@Immutable
data class VoiceSynthesisSettings(
    val enabled: Boolean = false,
    val apiKey: String = "",
    val baseUrl: String = MIMO_DEFAULT_BASE_URL,
    val defaultProfile: VoiceProfile = VoiceProfile(
        mode = VoiceProfileMode.PRESET,
        presetVoiceId = MIMO_DEFAULT_VOICE_ID,
    ),
    val assistantProfiles: Map<String, VoiceProfile> = emptyMap(),
) {
    fun normalized(): VoiceSynthesisSettings {
        val normalizedDefaultProfile = defaultProfile
            .normalized()
            .let { profile ->
                if (profile.mode == VoiceProfileMode.INHERIT || profile.mode == VoiceProfileMode.DISABLED) {
                    VoiceProfile(
                        mode = VoiceProfileMode.PRESET,
                        presetVoiceId = MIMO_DEFAULT_VOICE_ID,
                    )
                } else {
                    profile
                }
            }
        val normalizedProfiles = assistantProfiles
            .mapNotNull { (assistantId, profile) ->
                val normalizedId = assistantId.trim()
                if (normalizedId.isBlank()) {
                    null
                } else {
                    normalizedId to profile.normalized()
                }
            }
            .toMap(linkedMapOf())
        return copy(
            apiKey = apiKey.trim(),
            baseUrl = baseUrl.trim(),
            defaultProfile = normalizedDefaultProfile,
            assistantProfiles = normalizedProfiles,
        )
    }

    fun hasUsableCredential(): Boolean {
        return enabled && apiKey.trim().isNotBlank()
    }

    fun profileForAssistant(assistantId: String): VoiceProfile {
        val profile = assistantProfiles[assistantId.trim()]?.normalized()
        return when (profile?.mode) {
            VoiceProfileMode.PRESET,
            VoiceProfileMode.VOICE_DESIGN,
            VoiceProfileMode.VOICE_CLONE,
            VoiceProfileMode.DISABLED,
            -> profile

            VoiceProfileMode.INHERIT,
            null,
            -> defaultProfile.normalized()
        }
    }

    fun stripSensitiveFields(): VoiceSynthesisSettings {
        return normalized().copy(apiKey = "")
    }
}

fun normalizeMimoBaseUrl(baseUrl: String): String {
    return baseUrl.trim().ifBlank { MIMO_DEFAULT_BASE_URL }
}

fun resolveMimoChatCompletionsEndpoint(baseUrl: String): String {
    val normalized = normalizeMimoBaseUrl(baseUrl).trimEnd('/')
    return if (normalized.endsWith(MIMO_CHAT_COMPLETIONS_PATH, ignoreCase = true)) {
        normalized
    } else {
        normalized + MIMO_CHAT_COMPLETIONS_PATH
    }
}

@Immutable
data class VoiceProfile(
    val mode: VoiceProfileMode = VoiceProfileMode.INHERIT,
    val presetVoiceId: String = "",
    val voiceDesignPrompt: String = "",
    val voiceCloneSamplePath: String = "",
    val voiceCloneSampleMimeType: String = "",
    val voiceCloneSampleFileName: String = "",
    val voiceCloneSampleSizeBytes: Long = 0L,
) {
    fun normalized(): VoiceProfile {
        val resolvedMode = mode
        return copy(
            mode = resolvedMode,
            presetVoiceId = presetVoiceId.trim().ifBlank { MIMO_DEFAULT_VOICE_ID },
            voiceDesignPrompt = voiceDesignPrompt.replace("\r\n", "\n").trim(),
            voiceCloneSamplePath = voiceCloneSamplePath.trim(),
            voiceCloneSampleMimeType = voiceCloneSampleMimeType.trim(),
            voiceCloneSampleFileName = voiceCloneSampleFileName.trim(),
            voiceCloneSampleSizeBytes = voiceCloneSampleSizeBytes.coerceAtLeast(0L),
        )
    }

    fun ttsModel(): String {
        return when (mode) {
            VoiceProfileMode.VOICE_DESIGN -> MIMO_TTS_MODEL_VOICE_DESIGN
            VoiceProfileMode.VOICE_CLONE -> MIMO_TTS_MODEL_VOICE_CLONE
            VoiceProfileMode.PRESET,
            VoiceProfileMode.INHERIT,
            VoiceProfileMode.DISABLED,
            -> MIMO_TTS_MODEL_PRESET
        }
    }

    fun storageLabel(): String {
        return when (mode) {
            VoiceProfileMode.INHERIT -> "继承全局"
            VoiceProfileMode.PRESET -> "预置音色：${presetVoiceId.ifBlank { MIMO_DEFAULT_VOICE_ID }}"
            VoiceProfileMode.VOICE_DESIGN -> "文本设计音色"
            VoiceProfileMode.VOICE_CLONE -> voiceCloneSampleFileName
                .ifBlank { "声音克隆样本" }
                .let { "声音克隆：$it" }
            VoiceProfileMode.DISABLED -> "不生成语音"
        }
    }
}

enum class VoiceProfileMode(
    val storageValue: String,
    val label: String,
) {
    INHERIT("inherit", "继承全局"),
    PRESET("preset", "预置音色"),
    VOICE_DESIGN("voice_design", "文本设计音色"),
    VOICE_CLONE("voice_clone", "声音克隆"),
    DISABLED("disabled", "不生成语音");

    companion object {
        fun fromStorageValue(value: String): VoiceProfileMode {
            return entries.firstOrNull { it.storageValue == value.trim().lowercase() } ?: INHERIT
        }
    }
}

data class MimoPresetVoice(
    val id: String,
    val displayName: String,
    val language: String,
)

val MIMO_PRESET_VOICES: List<MimoPresetVoice> = listOf(
    MimoPresetVoice(MIMO_DEFAULT_VOICE_ID, "mimo_default", "跟随集群默认"),
    MimoPresetVoice("冰糖", "冰糖", "中文女声"),
    MimoPresetVoice("茉莉", "茉莉", "中文女声"),
    MimoPresetVoice("苏打", "苏打", "中文男声"),
    MimoPresetVoice("白桦", "白桦", "中文男声"),
    MimoPresetVoice("Mia", "Mia", "英文女声"),
    MimoPresetVoice("Chloe", "Chloe", "英文女声"),
    MimoPresetVoice("Milo", "Milo", "英文男声"),
    MimoPresetVoice("Dean", "Dean", "英文男声"),
)
