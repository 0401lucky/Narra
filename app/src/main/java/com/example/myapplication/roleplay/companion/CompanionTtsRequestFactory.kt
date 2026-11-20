package com.example.myapplication.roleplay.companion

import com.example.myapplication.data.repository.VoiceCloneSampleStorage
import com.example.myapplication.data.repository.tts.MimoTtsRequest
import com.example.myapplication.model.*
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okio.ByteString.Companion.toByteString

/** API Key 和克隆负载只驻本次请求内存；identity 是无凭据的实际参数。 */
class FrozenCompanionTts internal constructor(
    val identity: CompanionSpeechSynthesisIdentity,
    internal val apiKey: String,
    val baseUrl: String,
    private val voice: String,
    private val prompt: String,
) {
    fun request(text: String): MimoTtsRequest = when (identity.voiceMode) {
        VoiceProfileMode.PRESET -> MimoTtsRequest.preset(text, voice)
        VoiceProfileMode.VOICE_DESIGN -> MimoTtsRequest.voiceDesign(text, prompt)
        VoiceProfileMode.VOICE_CLONE -> MimoTtsRequest.voiceClone(text, voice, prompt)
        else -> error("音色不可用")
    }

    override fun toString(): String = "FrozenCompanionTts(identity=$identity)"
}

class CompanionTtsRequestFactory(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val readCloneSample: suspend (String) -> ByteArray = { path -> boundedCloneBytes(path) },
) {
    suspend fun freeze(settings: VoiceSynthesisSettings, characterId: String): FrozenCompanionTts =
        withContext(ioDispatcher) {
            currentCoroutineContext().ensureActive()
            val normalized = settings.normalized()
            require(normalized.apiKey.isNotBlank()) { "请先在语音设置中配置 MiMo API Key" }
            val profile = normalized.profileForAssistant(characterId)
            require(profile.mode != VoiceProfileMode.DISABLED) { "当前角色音色已停用，请先选择可用音色" }
            val baseUrl = requireSecureMimoBaseUrl(normalized.baseUrl)
            val uri = runCatching { java.net.URI(baseUrl) }.getOrNull()
                ?: throw IllegalArgumentException("语音服务地址无效，请检查配置")
            require(uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null) {
                "语音服务地址应为不带认证信息或查询参数的 API 路径"
            }
            require(!uri.rawPath.orEmpty().contains(normalized.apiKey) && !uri.path.orEmpty().contains(normalized.apiKey)) {
                "语音服务地址不能包含 API Key，请使用独立密钥设置"
            }
            val parameters = linkedMapOf("format" to "wav")
            val prompt: String
            val voice: String
            when (profile.mode) {
                VoiceProfileMode.PRESET -> {
                    voice = profile.presetVoiceId
                    prompt = ""
                    parameters["voiceId"] = voice
                }

                VoiceProfileMode.VOICE_DESIGN -> {
                    prompt = profile.voiceDesignPrompt.trim()
                    require(prompt.isNotBlank()) { "请先填写当前角色的音色描述" }
                    voice = ""
                    parameters["promptHash"] =
                        CompanionSpeechRequestKeys.bytesHash(prompt.toByteArray(Charsets.UTF_8))
                }

                VoiceProfileMode.VOICE_CLONE -> {
                    require(profile.voiceCloneSamplePath.isNotBlank()) { "请先为当前角色选择声音克隆样本" }
                    val bytes = readCloneSample(profile.voiceCloneSamplePath)
                    currentCoroutineContext().ensureActive()
                    require(bytes.isNotEmpty() && bytes.size <= MAX_CLONE_BYTES) { "声音克隆样本为空或超过 10 MB 编码上限" }
                    val mime = actualCloneMime(bytes)
                        ?: throw IllegalArgumentException("声音克隆样本不是可识别的 WAV/MP3")
                    val encoded = bytes.toByteString().base64()
                    require(encoded.length <= VoiceCloneSampleStorage.MAX_BASE64_CHARS) { "声音克隆样本编码后超过 10 MB" }
                    voice = "data:$mime;base64,$encoded"
                    prompt = ""
                    parameters["sampleHash"] = CompanionSpeechRequestKeys.bytesHash(bytes)
                    parameters["sampleMime"] = mime
                    parameters["instructionHash"] =
                        CompanionSpeechRequestKeys.bytesHash(ByteArray(0))
                }

                else -> throw IllegalArgumentException("当前角色音色不可用")
            }
            FrozenCompanionTts(
                identity = CompanionSpeechSynthesisIdentity(
                    endpoint = resolveMimoChatCompletionsEndpoint(baseUrl),
                    model = profile.ttsModel(),
                    characterId = characterId,
                    voiceMode = profile.mode,
                    parameters = parameters.toMap(),
                ),
                apiKey = normalized.apiKey, baseUrl = baseUrl, voice = voice, prompt = prompt,
            )
        }
}

private const val MAX_CLONE_BYTES = (VoiceCloneSampleStorage.MAX_BASE64_CHARS / 4) * 3

private suspend fun boundedCloneBytes(path: String): ByteArray {
    val file = File(path.trim())
    require(file.isFile) { "声音克隆样本文件不存在，请重新选择" }
    return file.inputStream().use { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var length = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            val read = input.read(buffer)
            if (read < 0) break
            length += read
            require(length <= MAX_CLONE_BYTES) { "声音克隆样本超过 10 MB 编码上限" }
            output.write(buffer, 0, read)
        }
        output.toByteArray()
    }
}

private fun actualCloneMime(bytes: ByteArray): String? = when {
    bytes.size >= 12 && bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII) == "RIFF" &&
            bytes.copyOfRange(8, 12).toString(Charsets.US_ASCII) == "WAVE" -> "audio/wav"

    bytes.size >= 3 && bytes.copyOfRange(0, 3).toString(Charsets.US_ASCII) == "ID3" -> "audio/mpeg"
    bytes.size >= 2 && bytes[0] == 0xFF.toByte() && (bytes[1].toInt() and 0xE0) == 0xE0 -> "audio/mpeg"
    else -> null
}
