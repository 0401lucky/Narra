package com.example.myapplication.model

import java.io.Serializable
import kotlinx.coroutines.flow.StateFlow

enum class CompanionVoiceReadMode { ORIGINAL, TRANSLATED }

/** 陪伴偏好独立于 VoiceSynthesisSettings.enabled，不保存凭据或第二份音色。 */
data class CompanionVoicePreferences(
    val schemaVersion: Int = 1,
    val enabled: Boolean = false,
    val autoPlay: Boolean = false,
    val readMode: CompanionVoiceReadMode = CompanionVoiceReadMode.ORIGINAL,
    val sourceLanguage: String = "自动检测",
    val targetLanguage: String = "英语",
    val cacheLimitBytes: Long = DEFAULT_CACHE_LIMIT_BYTES,
) : Serializable {
    fun normalized(): CompanionVoicePreferences = copy(
        sourceLanguage = sourceLanguage.trim().ifBlank { "自动检测" },
        targetLanguage = targetLanguage.trim().ifBlank { "英语" },
        cacheLimitBytes = cacheLimitBytes.coerceIn(MIN_CACHE_LIMIT_BYTES, MAX_CACHE_LIMIT_BYTES),
    )

    companion object {
        const val MAX_LANGUAGE_LENGTH = 60
        const val DEFAULT_CACHE_LIMIT_BYTES = 64L * 1024 * 1024
        const val MIN_CACHE_LIMIT_BYTES = 1L * 1024 * 1024
        const val MAX_CACHE_LIMIT_BYTES = 512L * 1024 * 1024
    }
}

/** mode 包含 LIST/STAGE/READER；变更 owner 必须撤销整条声音链。 */
data class CompanionVoiceOwner(val scenarioId: String, val characterId: String, val mode: String)

data class SpeechRequestKey(val value: String)
data class TranslationRequestKey(val value: String)

/** 只记录实际发送的非敏感参数；克隆参数使用本次样本字节 SHA-256。 */
data class CompanionSpeechSynthesisIdentity(
    val endpoint: String,
    val model: String,
    val characterId: String,
    val voiceMode: VoiceProfileMode,
    val parameters: Map<String, String>,
    val builderVersion: String = "companion-speech-v1",
)

/** 选区/分段范围针对 unit.text，unit.rawSpan 仅作原文出处。 */
data class CompanionVoiceSegment(
    val unit: CompanionSpeechUnit,
    val range: CompanionSpeechRange,
    val originalText: String,
)

data class CompanionVoiceClip(
    val segment: CompanionVoiceSegment,
    val spokenText: String,
    val sourceLanguage: String,
    val languageTag: String,
    val synthesis: CompanionSpeechSynthesisIdentity,
    val translationKey: TranslationRequestKey? = null,
)

enum class CompanionAudioResourceState { PENDING, READY }
enum class CompanionAudioAttachmentState { PENDING, ATTACHED }
enum class CompanionVoiceSourceState { AVAILABLE, UNAVAILABLE }

/** 文件已经校验并发布；attachmentState 单独说明 Room 挂接是否完成。 */
data class CompanionAudioReceipt(
    val audioId: String,
    val key: SpeechRequestKey,
    val path: String,
    val mimeType: String,
    val sha256: String,
    val sizeBytes: Long,
    val createdAt: Long,
    val attachmentState: CompanionAudioAttachmentState,
) {
    override fun toString(): String =
        "CompanionAudioReceipt(audioId=$audioId, sizeBytes=$sizeBytes, attachmentState=$attachmentState)"
}

data class CompanionVoiceAudio(val receipt: CompanionAudioReceipt, val clip: CompanionVoiceClip)

data class CompanionAudioClaim(
    val audioId: String,
    val key: SpeechRequestKey,
    val ownerToken: String
)

sealed interface CompanionAudioClaimResult {
    data class Granted(val claim: CompanionAudioClaim) : CompanionAudioClaimResult
    data class Existing(val receipt: CompanionAudioReceipt) : CompanionAudioClaimResult
    data object Busy : CompanionAudioClaimResult

    /** 已发起过生成，但没有合法持久副本；不能偷偷再次发出收费请求。 */
    data object NeedsExplicitRegeneration : CompanionAudioClaimResult
}

data class CompanionVoiceFavorite(
    val id: String,
    val audio: CompanionVoiceAudio,
    val sourceState: CompanionVoiceSourceState,
    val createdAt: Long,
)

data class CompanionVoiceCacheUsage(
    val cacheBytes: Long = 0,
    val cacheCount: Int = 0,
    val favoriteBytes: Long = 0,
    val favoriteCount: Int = 0,
    val pendingCount: Int = 0,
)

enum class CompanionVoicePhase {
    IDLE, TRANSLATING, SYNTHESIZING, PUBLISHING, PREPARING, PLAYING, COMPLETED, ERROR,
}

enum class CompanionVoiceFailureStage { CONFIGURATION, SOURCE, TRANSLATION, SYNTHESIS, STORAGE, PLAYBACK }

data class CompanionVoiceState(
    val phase: CompanionVoicePhase = CompanionVoicePhase.IDLE,
    val generation: Long = 0,
    val selection: CompanionSpeechSelection? = null,
    val currentIndex: Int = 0,
    val total: Int = 0,
    val currentAudio: CompanionVoiceAudio? = null,
    val originalText: String = "",
    val spokenText: String = "",
    val languageTag: String = "",
    val failureStage: CompanionVoiceFailureStage? = null,
    val errorMessage: String? = null,
    val noticeMessage: String? = null,
    val needsExplicitRegeneration: Boolean = false,
)

/** 一个宿主只有一个实例；全部动作在该实例的 scope/generation 下协调。 */
interface CompanionVoiceSession {
    val state: StateFlow<CompanionVoiceState>
    fun updateContext(
        owner: CompanionVoiceOwner,
        settings: AppSettings,
        units: List<CompanionSpeechUnit>
    )

    fun select(selection: CompanionSpeechSelection?)
    fun playSelection(
        selection: CompanionSpeechSelection,
        settings: AppSettings,
        automatic: Boolean = false
    )

    fun offerAutomatic(units: List<CompanionSpeechUnit>, settings: AppSettings)
    fun generateAndFavorite(selection: CompanionSpeechSelection, settings: AppSettings)
    fun favoriteCurrent()
    fun playFavorite(favoriteId: String)
    fun retry()
    fun readOriginal()
    fun regenerateExplicitly()
    fun stop()
    fun close()
}

/** listener 必须在 prepare 前安装；仅 start 返回成功后可报告 PLAYING。 */
interface CompanionVoicePlayer {
    fun setCallbacks(onPrepared: () -> Unit, onCompleted: () -> Unit, onError: (Throwable?) -> Unit)
    fun prepare(path: String)
    fun start()
    fun stop()
    fun release()
}

fun interface CompanionVoicePlayerFactory {
    fun create(): CompanionVoicePlayer
}
