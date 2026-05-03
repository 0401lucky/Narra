package com.example.myapplication.conversation

import com.example.myapplication.data.repository.ConversationRepository
import com.example.myapplication.data.repository.SavedAudioFile
import com.example.myapplication.data.repository.tts.MimoTtsClient
import com.example.myapplication.data.repository.tts.MimoTtsRequest
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.ChatActionType
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.ChatMessagePart
import com.example.myapplication.model.MIMO_DEFAULT_VOICE_ID
import com.example.myapplication.data.repository.VoiceCloneSampleStorage
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.MessageStatus
import com.example.myapplication.model.VoiceAudioStatus
import com.example.myapplication.model.VoiceProfile
import com.example.myapplication.model.VoiceProfileMode
import com.example.myapplication.model.voiceAudioStatus
import com.example.myapplication.model.voiceMessageContent
import com.example.myapplication.model.withVoiceAudioFailure
import com.example.myapplication.model.withVoiceAudioGenerating
import com.example.myapplication.model.withVoiceAudioSuccess
import com.example.myapplication.system.logging.AppLogger
import java.security.MessageDigest
import java.io.File
import java.util.Base64

data class VoiceSynthesisRequest(
    val conversationId: String,
    val selectedModel: String,
    val messages: List<ChatMessage>,
    val settings: AppSettings,
    val fallbackAssistantId: String,
)

class VoiceSynthesisCoordinator(
    private val mimoTtsClient: MimoTtsClient,
    private val conversationRepository: ConversationRepository,
    private val audioSaver: suspend (String, String) -> SavedAudioFile,
) {
    suspend fun generate(request: VoiceSynthesisRequest): List<ChatMessage>? {
        val voiceSettings = request.settings.voiceSynthesisSettings.normalized()
        if (!voiceSettings.hasUsableCredential() || request.conversationId.isBlank()) {
            return null
        }

        var latestMessages: List<ChatMessage>? = null
        request.messages
            .filter { message ->
                message.conversationId == request.conversationId &&
                    message.role == MessageRole.ASSISTANT &&
                    message.status == MessageStatus.COMPLETED
            }
            .forEach { message ->
                message.parts.forEach { part ->
                    if (!part.shouldGenerateVoiceAudio()) {
                        return@forEach
                    }
                    val assistantId = message.speakerId.trim().ifBlank { request.fallbackAssistantId }
                    val voiceProfile = voiceSettings.profileForAssistant(assistantId)
                    if (voiceProfile.mode == VoiceProfileMode.DISABLED) {
                        return@forEach
                    }
                    latestMessages = generatePart(
                        request = request,
                        message = message,
                        part = part,
                        voiceProfile = voiceProfile.normalized(),
                    ) ?: latestMessages
                }
            }
        return latestMessages
    }

    private suspend fun generatePart(
        request: VoiceSynthesisRequest,
        message: ChatMessage,
        part: ChatMessagePart,
        voiceProfile: VoiceProfile,
    ): List<ChatMessage>? {
        val voiceText = part.voiceMessageContent()
        val voiceSettings = request.settings.voiceSynthesisSettings.normalized()
        val profileHash = buildVoiceProfileHash(voiceProfile)
        val model = voiceProfile.ttsModel()
        val voiceId = when (voiceProfile.mode) {
            VoiceProfileMode.VOICE_DESIGN,
            VoiceProfileMode.VOICE_CLONE,
            -> ""
            else -> voiceProfile.presetVoiceId.ifBlank { MIMO_DEFAULT_VOICE_ID }
        }
        var latestMessages = conversationRepository.updateVoiceMessagePart(
            conversationId = request.conversationId,
            messageId = message.id,
            actionId = part.actionId,
            selectedModel = request.selectedModel,
        ) { currentPart ->
            currentPart.withVoiceAudioGenerating(
                model = model,
                voiceMode = voiceProfile.mode,
                voiceId = voiceId,
                voicePromptHash = profileHash,
            )
        }

        val result = runCatching {
            val ttsRequest = buildMimoRequest(
                voiceText = voiceText,
                voiceProfile = voiceProfile,
            )
            val ttsResult = mimoTtsClient.synthesize(
                apiKey = voiceSettings.apiKey,
                request = ttsRequest,
                baseUrl = voiceSettings.baseUrl,
            )
            val savedAudio = audioSaver(
                ttsResult.b64Audio,
                "voice-${message.id}-${part.actionId}",
            )
            conversationRepository.updateVoiceMessagePart(
                conversationId = request.conversationId,
                messageId = message.id,
                actionId = part.actionId,
                selectedModel = request.selectedModel,
            ) { currentPart ->
                currentPart.withVoiceAudioSuccess(
                    audioPath = savedAudio.path,
                    mimeType = savedAudio.mimeType,
                    fileName = savedAudio.fileName,
                )
            }
        }.getOrElse { throwable ->
            AppLogger.w(
                tag = TAG,
                message = "MiMo 语音生成失败 messageId=${message.id}, actionId=${part.actionId}, model=$model, mode=${voiceProfile.mode.storageValue}",
                throwable = throwable,
            )
            conversationRepository.updateVoiceMessagePart(
                conversationId = request.conversationId,
                messageId = message.id,
                actionId = part.actionId,
                selectedModel = request.selectedModel,
            ) { currentPart ->
                currentPart.withVoiceAudioFailure(
                    errorMessage = buildVoiceErrorMessage(throwable),
                )
            }
        }
        latestMessages = result
        return latestMessages
    }

    private fun buildMimoRequest(
        voiceText: String,
        voiceProfile: VoiceProfile,
    ): MimoTtsRequest {
        val normalizedText = voiceText.trim()
        return when (voiceProfile.mode) {
            VoiceProfileMode.VOICE_DESIGN -> {
                val prompt = voiceProfile.voiceDesignPrompt.trim()
                if (prompt.isBlank()) {
                    throw IllegalArgumentException("角色音色设计描述为空")
                }
                MimoTtsRequest.voiceDesign(
                    text = normalizedText,
                    prompt = prompt,
                )
            }

            VoiceProfileMode.VOICE_CLONE -> {
                MimoTtsRequest.voiceClone(
                    text = normalizedText,
                    sampleDataUri = buildVoiceCloneSampleDataUri(voiceProfile),
                )
            }

            VoiceProfileMode.PRESET,
            VoiceProfileMode.INHERIT,
            VoiceProfileMode.DISABLED,
            -> MimoTtsRequest.preset(
                text = normalizedText,
                voiceId = voiceProfile.presetVoiceId.ifBlank { MIMO_DEFAULT_VOICE_ID },
            )
        }
    }

    private fun ChatMessagePart.shouldGenerateVoiceAudio(): Boolean {
        return actionType == ChatActionType.VOICE_MESSAGE &&
            actionId.isNotBlank() &&
            voiceMessageContent().isNotBlank() &&
            voiceAudioStatus() != VoiceAudioStatus.READY &&
            voiceAudioStatus() != VoiceAudioStatus.GENERATING
    }

    private fun buildVoiceErrorMessage(throwable: Throwable): String {
        return throwable.message
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.take(160)
            ?: "语音生成失败"
    }

    private fun buildVoiceCloneSampleDataUri(profile: VoiceProfile): String {
        val samplePath = profile.voiceCloneSamplePath.trim()
        if (samplePath.isBlank()) {
            throw IllegalArgumentException("声音克隆样本未选择")
        }
        val file = File(samplePath)
        if (!file.isFile) {
            throw IllegalArgumentException("声音克隆样本文件不存在")
        }
        val mimeType = profile.voiceCloneSampleMimeType.trim().ifBlank {
            when (file.extension.lowercase()) {
                "wav" -> "audio/wav"
                "mp3" -> "audio/mpeg"
                else -> "application/octet-stream"
            }
        }
        if (mimeType !in setOf("audio/wav", "audio/x-wav", "audio/mpeg", "audio/mp3")) {
            throw IllegalArgumentException("声音克隆仅支持 wav 或 mp3 样本")
        }
        val bytes = file.readBytes()
        val encoded = Base64.getEncoder().encodeToString(bytes)
        if (encoded.length > VoiceCloneSampleStorage.MAX_BASE64_CHARS) {
            throw IllegalArgumentException("声音克隆样本太大，Base64 后超过 10 MB")
        }
        val normalizedMimeType = when (mimeType) {
            "audio/x-wav" -> "audio/wav"
            "audio/mp3" -> "audio/mpeg"
            else -> mimeType
        }
        return "data:$normalizedMimeType;base64,$encoded"
    }

    private fun buildVoiceProfileHash(profile: VoiceProfile): String {
        val raw = listOf(
            profile.mode.storageValue,
            profile.presetVoiceId,
            profile.voiceDesignPrompt,
            profile.voiceCloneSamplePath,
            profile.voiceCloneSampleMimeType,
            profile.voiceCloneSampleSizeBytes.toString(),
        ).joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte) }.take(16)
    }

    private companion object {
        const val TAG = "VoiceSynthesis"
    }
}
