package com.example.myapplication.data.repository.tts

import com.example.myapplication.model.MIMO_DEFAULT_VOICE_ID
import com.example.myapplication.model.MIMO_DEFAULT_BASE_URL
import com.example.myapplication.model.MIMO_TTS_MODEL_PRESET
import com.example.myapplication.model.MIMO_TTS_MODEL_VOICE_CLONE
import com.example.myapplication.model.MIMO_TTS_MODEL_VOICE_DESIGN
import com.example.myapplication.model.requireSecureMimoBaseUrl
import com.example.myapplication.model.resolveMimoChatCompletionsEndpoint
import com.example.myapplication.system.json.AppJson
import com.example.myapplication.system.security.SensitiveTextRedactor
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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
    private val maxResponseBodyBytes: Long = MAX_RESPONSE_BODY_BYTES,
) {
    init {
        require(maxResponseBodyBytes > 0) { "MiMo TTS 响应体上限必须大于 0" }
        require(maxResponseBodyBytes <= Int.MAX_VALUE) {
            "MiMo TTS 响应体上限不能超过 Int 最大值"
        }
    }

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

        val endpoint = resolveMimoChatCompletionsEndpoint(requireSecureMimoBaseUrl(baseUrl))
        val body = gson.toJson(request).toRequestBody(JSON_MEDIA_TYPE)
        val httpRequest = Request.Builder()
            .url(endpoint)
            .addHeader("api-key", normalizedApiKey)
            .addHeader("Authorization", "Bearer $normalizedApiKey")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        try {
            val response = executeCancellable(httpRequest)
            if (!response.isSuccessful) {
                throw IOException(buildHttpErrorMessage(response.code, response.body))
            }
            val audioData = try {
                gson.fromJson(response.body, MimoTtsResponse::class.java)
                    ?.choices
                    ?.firstOrNull()
                    ?.message
                    ?.audio
                    ?.data
                    .orEmpty()
                    .trim()
            } catch (throwable: Throwable) {
                currentCoroutineContext().ensureActive()
                ""
            }
            if (audioData.isBlank()) {
                throw IOException("MiMo 未返回音频数据")
            }
            MimoTtsResult(b64Audio = audioData)
        } catch (throwable: Throwable) {
            currentCoroutineContext().ensureActive()
            throw throwable
        }
    }

    private suspend fun executeCancellable(request: Request): MimoHttpResponse {
        return suspendCancellableCoroutine { continuation ->
            val call = httpClient.newCall(request)
            continuation.invokeOnCancellation {
                call.cancel()
            }
            try {
                call.enqueue(
                    object : Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            if (continuation.isActive) {
                                continuation.resumeWithException(e)
                            }
                        }

                        override fun onResponse(call: Call, response: Response) {
                            try {
                                val responseBody = response.use { readResponseBody(it) }
                                if (continuation.isActive) {
                                    continuation.resume(
                                        MimoHttpResponse(
                                            code = response.code,
                                            isSuccessful = response.isSuccessful,
                                            body = responseBody,
                                        ),
                                    )
                                }
                            } catch (throwable: Throwable) {
                                if (continuation.isActive) {
                                    continuation.resumeWithException(throwable)
                                }
                            }
                        }
                    },
                )
            } catch (throwable: Throwable) {
                if (continuation.isActive) {
                    continuation.resumeWithException(throwable)
                }
            }
        }
    }

    private fun readResponseBody(response: okhttp3.Response): String {
        val body = response.body ?: return ""
        val declaredLength = body.contentLength()
        if (declaredLength > maxResponseBodyBytes) {
            throw IOException("MiMo TTS 响应体过大")
        }
        val output = ByteArrayOutputStream(
            minOf(maxResponseBodyBytes, INITIAL_RESPONSE_BUFFER_BYTES.toLong()).toInt(),
        )
        body.byteStream().use { input ->
            val buffer = ByteArray(RESPONSE_READ_BUFFER_BYTES)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > maxResponseBodyBytes) {
                    throw IOException("MiMo TTS 响应体过大")
                }
                output.write(buffer, 0, read)
            }
        }
        return output.toByteArray().toString(Charsets.UTF_8)
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
        val bodyExcerpt = SensitiveTextRedactor.redact(responseBody.trim(), maxLength = 160)
        return if (bodyExcerpt.isBlank()) {
            "MiMo TTS 请求失败：HTTP $code"
        } else {
            "MiMo TTS 请求失败：HTTP $code，$bodyExcerpt"
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = MIMO_DEFAULT_BASE_URL
        const val MAX_RESPONSE_BODY_BYTES = 16L * 1024L * 1024L
        private const val INITIAL_RESPONSE_BUFFER_BYTES = 8 * 1024
        private const val RESPONSE_READ_BUFFER_BYTES = 8 * 1024
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

private data class MimoHttpResponse(
    val code: Int,
    val isSuccessful: Boolean,
    val body: String,
)
