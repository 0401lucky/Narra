package com.example.myapplication.data.repository.asr

import com.example.myapplication.model.TranscriptionProtocol
import com.example.myapplication.model.VoiceRecognitionSettings
import com.example.myapplication.model.resolveAsrEndpoint
import com.example.myapplication.model.resolveChatCompletionsEndpointForAsr
import com.example.myapplication.model.resolveTranscriptionEndpoint
import com.example.myapplication.system.json.AppJson
import com.example.myapplication.system.security.SensitiveTextRedactor
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.io.File
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/** OpenAI 兼容的转写响应体，只关心 text 字段。 */
private data class TranscriptionResponse(
    val text: String = "",
)

/** 多模态对话请求：把音频作为 content part 发给 /chat/completions。 */
private data class ChatTranscriptionRequest(
    val model: String,
    val messages: List<ChatTranscriptionMessage>,
    val temperature: Double = 0.0,
)

private data class ChatTranscriptionMessage(
    val role: String,
    val content: List<ChatTranscriptionPart>,
)

private data class ChatTranscriptionPart(
    val type: String,
    val text: String? = null,
    @SerializedName("input_audio") val inputAudio: ChatTranscriptionAudio? = null,
)

private data class ChatTranscriptionAudio(
    val data: String,
    val format: String,
)

private data class ChatTranscriptionResponse(
    val choices: List<ChatTranscriptionChoice> = emptyList(),
)

private data class ChatTranscriptionChoice(
    val message: ChatTranscriptionResponseMessage? = null,
)

private data class ChatTranscriptionResponseMessage(
    val content: String = "",
)

/**
 * 大模型转写通道：把录好的音频以 multipart 上传到 `{baseUrl}/audio/transcriptions`。
 *
 * 只发一次请求、不做重试——重试应由用户重新录音决定，静默重传会让用户以为没生效。
 */
class RemoteSpeechRecognitionService(
    private val httpClient: OkHttpClient = defaultHttpClient(),
    private val gson: Gson = AppJson.gson,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SpeechRecognitionService {

    override fun isAvailable(settings: VoiceRecognitionSettings): Boolean {
        return settings.hasUsableCredential()
    }

    override fun recognize(
        audioFile: File?,
        settings: VoiceRecognitionSettings,
        releaseSignal: Flow<Unit>?,
    ): Flow<RecognitionEvent> = flow {
        emit(transcribe(audioFile, settings.normalized()))
    }.flowOn(dispatcher)

    private suspend fun transcribe(
        audioFile: File?,
        settings: VoiceRecognitionSettings,
    ): RecognitionEvent {
        if (audioFile == null || !audioFile.exists() || audioFile.length() <= 0L) {
            return RecognitionEvent.Failed("没有可上传的录音文件")
        }
        if (settings.apiKey.isBlank()) {
            return RecognitionEvent.Failed("请先填写语音识别 API Key")
        }
        return when (settings.protocol) {
            TranscriptionProtocol.TRANSCRIPTION_ENDPOINT ->
                transcribeViaTranscriptionEndpoint(audioFile, settings)

            TranscriptionProtocol.CHAT_MULTIMODAL ->
                transcribeViaChatCompletions(audioFile, settings)
        }
    }

    private suspend fun transcribeViaTranscriptionEndpoint(
        audioFile: File,
        settings: VoiceRecognitionSettings,
    ): RecognitionEvent {
        val endpoint = runCatching { resolveTranscriptionEndpoint(settings.baseUrl) }
            .getOrElse { throwable ->
                return RecognitionEvent.Failed(
                    throwable.message?.takeIf { it.isNotBlank() } ?: "语音识别 Base URL 无效",
                )
            }

        val bodyBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                audioFile.name,
                audioFile.asRequestBody(AUDIO_MEDIA_TYPE),
            )
            .addFormDataPart("model", settings.model)
            .addFormDataPart("response_format", "json")
        // language 留空时不发该字段：部分服务商对空值会直接报错
        if (settings.language.isNotBlank()) {
            bodyBuilder.addFormDataPart("language", settings.language)
        }

        val httpRequest = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer ${settings.apiKey}")
            .post(bodyBuilder.build())
            .build()

        return try {
            val response = executeCancellable(httpRequest)
            if (!response.isSuccessful) {
                return RecognitionEvent.Failed(
                    buildHttpErrorMessage(response.code, response.body, settings.protocol),
                )
            }
            val text = parseTranscriptionText(response.body)
            if (text.isBlank()) {
                RecognitionEvent.Failed("识别服务没有返回文本内容")
            } else {
                RecognitionEvent.Final(text)
            }
        } catch (throwable: Throwable) {
            currentCoroutineContext().ensureActive()
            RecognitionEvent.Failed(buildNetworkErrorMessage(throwable))
        }
    }

    private suspend fun transcribeViaChatCompletions(
        audioFile: File,
        settings: VoiceRecognitionSettings,
    ): RecognitionEvent {
        val endpoint = runCatching { resolveChatCompletionsEndpointForAsr(settings.baseUrl) }
            .getOrElse { throwable ->
                return RecognitionEvent.Failed(
                    throwable.message?.takeIf { it.isNotBlank() } ?: "语音识别 Base URL 无效",
                )
            }
        if (audioFile.length() > MAX_MULTIMODAL_AUDIO_BYTES) {
            return RecognitionEvent.Failed("录音过长，多模态识别单次上限约 1 分钟")
        }
        val base64Audio = runCatching {
            Base64.getEncoder().encodeToString(audioFile.readBytes())
        }.getOrElse {
            return RecognitionEvent.Failed("读取录音文件失败")
        }

        val request = ChatTranscriptionRequest(
            model = settings.model,
            messages = listOf(
                ChatTranscriptionMessage(
                    role = "user",
                    content = listOf(
                        ChatTranscriptionPart(
                            type = "text",
                            text = buildTranscriptionInstruction(settings.language),
                        ),
                        ChatTranscriptionPart(
                            type = "input_audio",
                            inputAudio = ChatTranscriptionAudio(
                                data = base64Audio,
                                format = audioFile.extension.lowercase().ifBlank { "m4a" },
                            ),
                        ),
                    ),
                ),
            ),
        )
        val httpRequest = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer ${settings.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(gson.toJson(request).toRequestBody(JSON_MEDIA_TYPE))
            .build()

        return try {
            val response = executeCancellable(httpRequest)
            if (!response.isSuccessful) {
                return RecognitionEvent.Failed(
                    buildHttpErrorMessage(response.code, response.body, settings.protocol),
                )
            }
            val text = parseChatText(response.body)
            if (text.isBlank()) {
                RecognitionEvent.Failed("识别服务没有返回文本内容")
            } else {
                RecognitionEvent.Final(text)
            }
        } catch (throwable: Throwable) {
            currentCoroutineContext().ensureActive()
            RecognitionEvent.Failed(buildNetworkErrorMessage(throwable))
        }
    }

    private fun buildTranscriptionInstruction(language: String): String {
        val languageHint = if (language.isBlank()) "" else "音频语言是 $language。"
        return "请把这段音频逐字转写成文本。$languageHint" +
            "只输出转写结果本身，不要加引号、解释、翻译或任何前后缀。"
    }

    /**
     * 只校验端点连通性与鉴权，不做真实转写。
     *
     * 录一段静音上传成本高、语义也怪，所以这里刻意发一个不带音频的 multipart，
     * 凭状态码区分「地址错」「鉴权失败」和「端点存在且鉴权通过」。
     * 代价是无法验证模型名是否可用——这一点需要在 UI 上向用户说明。
     */
    suspend fun testConnection(settings: VoiceRecognitionSettings): Result<String> {
        val normalized = settings.normalized()
        if (normalized.apiKey.isBlank()) {
            return Result.failure(IllegalArgumentException("请先填写语音识别 API Key"))
        }
        val endpoint = runCatching {
            resolveAsrEndpoint(normalized.baseUrl, normalized.protocol)
        }.getOrElse { throwable ->
            return Result.failure(
                IllegalArgumentException(
                    throwable.message?.takeIf { it.isNotBlank() } ?: "语音识别 Base URL 无效",
                ),
            )
        }
        // 两条协议都发一个刻意缺内容的请求：能拿到 400/422 就说明端点存在且鉴权通过
        val probeRequest = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer ${normalized.apiKey}")
        val httpRequest = when (normalized.protocol) {
            TranscriptionProtocol.TRANSCRIPTION_ENDPOINT -> probeRequest.post(
                MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("model", normalized.model)
                    .build(),
            )

            TranscriptionProtocol.CHAT_MULTIMODAL -> probeRequest
                .addHeader("Content-Type", "application/json")
                .post(
                    gson.toJson(
                        ChatTranscriptionRequest(
                            model = normalized.model,
                            messages = emptyList(),
                        ),
                    ).toRequestBody(JSON_MEDIA_TYPE),
                )
        }.build()

        return withContext(dispatcher) {
            runCatching { executeCancellable(httpRequest) }.fold(
                onSuccess = { response ->
                    when {
                        // 400/422 = 端点存在、鉴权通过，只是缺音频文件——正是我们要的信号
                        response.code == 400 || response.code == 422 || response.isSuccessful ->
                            Result.success("端点可达、鉴权通过：$endpoint")

                        response.code == 401 || response.code == 403 ->
                            Result.failure(IllegalStateException("鉴权失败，请检查 API Key"))

                        response.code == 404 ->
                            Result.failure(IllegalStateException("地址不存在，请检查 Base URL"))

                        else -> Result.failure(
                            IllegalStateException(
                                buildHttpErrorMessage(
                                    response.code,
                                    response.body,
                                    normalized.protocol,
                                ),
                            ),
                        )
                    }
                },
                onFailure = { throwable ->
                    currentCoroutineContext().ensureActive()
                    Result.failure(IllegalStateException(buildNetworkErrorMessage(throwable)))
                },
            )
        }
    }

    private suspend fun parseTranscriptionText(body: String): String {
        return try {
            gson.fromJson(body, TranscriptionResponse::class.java)?.text?.trim().orEmpty()
        } catch (throwable: Throwable) {
            currentCoroutineContext().ensureActive()
            ""
        }
    }

    private suspend fun parseChatText(body: String): String {
        return try {
            gson.fromJson(body, ChatTranscriptionResponse::class.java)
                ?.choices
                ?.firstOrNull()
                ?.message
                ?.content
                ?.trim()
                .orEmpty()
        } catch (throwable: Throwable) {
            currentCoroutineContext().ensureActive()
            ""
        }
    }

    private suspend fun executeCancellable(request: Request): TranscriptionHttpResponse {
        return suspendCancellableCoroutine { continuation ->
            val call = httpClient.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
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
                                val payload = response.use { it.body?.string().orEmpty() }
                                if (continuation.isActive) {
                                    continuation.resume(
                                        TranscriptionHttpResponse(
                                            code = response.code,
                                            isSuccessful = response.isSuccessful,
                                            body = payload,
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

    private fun buildHttpErrorMessage(
        code: Int,
        responseBody: String,
        protocol: TranscriptionProtocol,
    ): String {
        val prefix = when (code) {
            401, 403 -> "语音识别鉴权失败，请检查 API Key"
            404 -> "语音识别地址不存在，请检查 Base URL"
            413 -> "录音文件过大，服务端拒绝接收"
            429 -> "语音识别请求过于频繁，稍后再试"
            in 500..599 -> "语音识别服务异常（HTTP $code）"
            else -> "语音识别请求失败（HTTP $code）"
        }
        val excerpt = SensitiveTextRedactor.redact(responseBody.trim(), maxLength = 160)
        val hint = protocolHint(responseBody, protocol)
        return listOf(prefix, excerpt, hint)
            .filter { it.isNotBlank() }
            .joinToString("：")
    }

    /**
     * 针对「协议选错」这一最常见的失败给出可行动的提示。
     *
     * 中转把 OpenAI 转写请求转成上游格式失败时（典型如 gemini 没有转写端点），
     * 会返回 not implemented / convert_request_failed，光看 HTTP 500 无从下手。
     */
    private fun protocolHint(responseBody: String, protocol: TranscriptionProtocol): String {
        val lowered = responseBody.lowercase()
        val looksUnsupported = lowered.contains("not implemented") ||
            lowered.contains("convert_request_failed") ||
            lowered.contains("unsupported")
        if (!looksUnsupported) return ""
        return when (protocol) {
            TranscriptionProtocol.TRANSCRIPTION_ENDPOINT ->
                "该模型可能没有转写接口，试试把转写协议改成「多模态对话」"

            TranscriptionProtocol.CHAT_MULTIMODAL ->
                "该模型可能不接受音频输入，或服务商不支持 m4a 格式"
        }
    }

    private fun buildNetworkErrorMessage(throwable: Throwable): String {
        return SensitiveTextRedactor.throwableMessageForUi(
            throwable = throwable,
            fallback = "语音识别请求失败，请检查网络",
        )
    }

    private data class TranscriptionHttpResponse(
        val code: Int,
        val isSuccessful: Boolean,
        val body: String,
    )

    companion object {
        private val AUDIO_MEDIA_TYPE = "audio/mp4".toMediaType()
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        /** 多模态通道要把音频整体 base64 塞进 JSON，留出足够余量但不放任无限大。 */
        private const val MAX_MULTIMODAL_AUDIO_BYTES = 8L * 1024L * 1024L

        fun defaultHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()
        }
    }
}
