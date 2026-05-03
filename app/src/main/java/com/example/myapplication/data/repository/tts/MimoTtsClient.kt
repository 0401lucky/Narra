package com.example.myapplication.data.repository.tts

import com.example.myapplication.model.MIMO_DEFAULT_VOICE_ID
import com.example.myapplication.model.MIMO_DEFAULT_BASE_URL
import com.example.myapplication.model.MIMO_TTS_MODEL_PRESET
import com.example.myapplication.model.MIMO_TTS_MODEL_VOICE_CLONE
import com.example.myapplication.model.MIMO_TTS_MODEL_VOICE_DESIGN
import com.example.myapplication.model.resolveMimoChatCompletionsEndpoint
import com.example.myapplication.system.json.AppJson
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

data class MimoTtsRequest(
    val model: String,
    val messages: List<MimoTtsMessage>,
    val audio: MimoTtsAudio,
) {
    companion object {
        fun preset(
            text: String,
            voiceId: String,
        ): MimoTtsRequest {
            return MimoTtsRequest(
                model = MIMO_TTS_MODEL_PRESET,
                messages = listOf(
                    MimoTtsMessage(
                        role = "assistant",
                        content = text.trim(),
                    ),
                ),
                audio = MimoTtsAudio(
                    format = "wav",
                    voice = voiceId.trim().ifBlank { MIMO_DEFAULT_VOICE_ID },
                ),
            )
        }

        fun voiceDesign(
            text: String,
            prompt: String,
        ): MimoTtsRequest {
            return MimoTtsRequest(
                model = MIMO_TTS_MODEL_VOICE_DESIGN,
                messages = listOf(
                    MimoTtsMessage(
                        role = "user",
                        content = prompt.trim(),
                    ),
                    MimoTtsMessage(
                        role = "assistant",
                        content = text.trim(),
                    ),
                ),
                audio = MimoTtsAudio(format = "wav"),
            )
        }

        fun voiceClone(
            text: String,
            sampleDataUri: String,
            instruction: String = "",
        ): MimoTtsRequest {
            return MimoTtsRequest(
                model = MIMO_TTS_MODEL_VOICE_CLONE,
                messages = listOf(
                    MimoTtsMessage(
                        role = "user",
                        content = instruction.trim(),
                    ),
                    MimoTtsMessage(
                        role = "assistant",
                        content = text.trim(),
                    ),
                ),
                audio = MimoTtsAudio(
                    format = "wav",
                    voice = sampleDataUri.trim(),
                ),
            )
        }
    }
}

data class MimoTtsMessage(
    val role: String,
    val content: String,
)

data class MimoTtsAudio(
    val format: String,
    val voice: String? = null,
)

data class MimoTtsResult(
    val b64Audio: String,
)

data class MimoTtsResponse(
    val choices: List<MimoTtsChoice> = emptyList(),
)

data class MimoTtsChoice(
    val message: MimoTtsResponseMessage? = null,
)

data class MimoTtsResponseMessage(
    val audio: MimoTtsResponseAudio? = null,
)

data class MimoTtsResponseAudio(
    val data: String = "",
)

class MimoTtsClient(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val httpClient: OkHttpClient = defaultHttpClient(),
    private val gson: Gson = AppJson.gson,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun synthesize(
        apiKey: String,
        request: MimoTtsRequest,
        baseUrl: String = this.baseUrl,
    ): MimoTtsResult = withContext(dispatcher) {
        val normalizedApiKey = normalizeApiKey(apiKey)
        if (normalizedApiKey.isBlank()) {
            throw IllegalArgumentException("MiMo API Key 为空")
        }
        val normalizedText = request.messages
            .lastOrNull { it.role == "assistant" }
            ?.content
            ?.trim()
            .orEmpty()
        if (normalizedText.isBlank()) {
            throw IllegalArgumentException("语音文本为空")
        }

        val endpoint = resolveMimoChatCompletionsEndpoint(baseUrl)
        val body = gson.toJson(request).toRequestBody(JSON_MEDIA_TYPE)
        val httpRequest = Request.Builder()
            .url(endpoint)
            .addHeader("api-key", normalizedApiKey)
            .addHeader("Authorization", "Bearer $normalizedApiKey")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        httpClient.newCall(httpRequest).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException(buildHttpErrorMessage(response.code, responseBody))
            }
            val audioData = runCatching {
                gson.fromJson(responseBody, MimoTtsResponse::class.java)
                    ?.choices
                    ?.firstOrNull()
                    ?.message
                    ?.audio
                    ?.data
                    .orEmpty()
                    .trim()
            }.getOrDefault("")
            if (audioData.isBlank()) {
                throw IOException("MiMo 未返回音频数据")
            }
            MimoTtsResult(b64Audio = audioData)
        }
    }

    private fun normalizeApiKey(apiKey: String): String {
        return apiKey
            .trim()
            .trim('"', '\'', '`')
            .replace(Regex("""(?i)^api-key\s*:\s*"""), "")
            .replace(Regex("""(?i)^authorization\s*:\s*"""), "")
            .replace(Regex("""(?i)^bearer\s+"""), "")
            .trim()
    }

    private fun buildHttpErrorMessage(
        code: Int,
        responseBody: String,
    ): String {
        val bodyExcerpt = responseBody.trim().take(160)
        return if (bodyExcerpt.isBlank()) {
            "MiMo TTS 请求失败：HTTP $code"
        } else {
            "MiMo TTS 请求失败：HTTP $code，$bodyExcerpt"
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = MIMO_DEFAULT_BASE_URL
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun defaultHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
        }
    }
}
