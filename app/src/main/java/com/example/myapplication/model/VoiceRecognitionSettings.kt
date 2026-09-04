package com.example.myapplication.model

import androidx.compose.runtime.Immutable
import com.example.myapplication.system.network.normalizeSecureHttpBaseUrl

const val DEFAULT_TRANSCRIPTION_MODEL = "whisper-1"
const val DEFAULT_TRANSCRIPTION_LANGUAGE = "zh"
const val TRANSCRIPTIONS_PATH = "/audio/transcriptions"

/** 语音识别通道。系统通道离线免配置，大模型通道质量更好但需要联网与凭据。 */
enum class VoiceRecognitionEngine(
    val storageValue: String,
    val label: String,
) {
    SYSTEM("system", "系统识别"),
    REMOTE_MODEL("remote_model", "大模型识别");

    companion object {
        fun fromStorageValue(value: String): VoiceRecognitionEngine {
            return entries.firstOrNull { it.storageValue == value.trim().lowercase() } ?: SYSTEM
        }
    }
}

@Immutable
data class VoiceRecognitionSettings(
    val enabled: Boolean = false,
    val engine: VoiceRecognitionEngine = VoiceRecognitionEngine.SYSTEM,
    val apiKey: String = "",
    val baseUrl: String = "",
    val model: String = DEFAULT_TRANSCRIPTION_MODEL,
    val language: String = DEFAULT_TRANSCRIPTION_LANGUAGE,
) {
    fun normalized(): VoiceRecognitionSettings {
        return copy(
            apiKey = apiKey.trim(),
            baseUrl = baseUrl.trim(),
            model = model.trim().ifBlank { DEFAULT_TRANSCRIPTION_MODEL },
            language = language.trim(),
        )
    }

    /** 大模型通道需要 Key 与 Base URL；系统通道不需要任何凭据。 */
    fun hasUsableCredential(): Boolean {
        return when (engine) {
            VoiceRecognitionEngine.SYSTEM -> true
            VoiceRecognitionEngine.REMOTE_MODEL ->
                apiKey.trim().isNotBlank() && baseUrl.trim().isNotBlank()
        }
    }

    /** 输入框上的麦克风按钮是否可用。 */
    fun isReadyForInput(): Boolean = enabled && hasUsableCredential()

    fun stripSensitiveFields(): VoiceRecognitionSettings {
        return normalized().copy(apiKey = "")
    }
}

fun normalizeVoiceRecognitionBaseUrl(baseUrl: String): String {
    return baseUrl.trim().trimEnd('/')
}

fun requireSecureVoiceRecognitionBaseUrl(baseUrl: String): String {
    return normalizeSecureHttpBaseUrl(
        baseUrl = baseUrl,
        blankMessage = "语音识别 Base URL 为空",
        schemeMessage = "语音识别 Base URL 必须以 http:// 或 https:// 开头",
        secureMessage = "语音识别 Base URL 必须使用 https://，本机调试地址除外",
    ).trimEnd('/')
}

/**
 * 解析转写端点。调用方只填 Base URL 即可，已经带上路径的也不会被重复拼接。
 */
fun resolveTranscriptionEndpoint(baseUrl: String): String {
    val normalized = requireSecureVoiceRecognitionBaseUrl(baseUrl)
    return if (normalized.endsWith(TRANSCRIPTIONS_PATH, ignoreCase = true)) {
        normalized
    } else {
        normalized + TRANSCRIPTIONS_PATH
    }
}
