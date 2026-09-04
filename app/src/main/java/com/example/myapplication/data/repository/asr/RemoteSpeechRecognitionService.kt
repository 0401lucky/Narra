package com.example.myapplication.data.repository.asr

import com.example.myapplication.model.VoiceRecognitionSettings
import com.example.myapplication.model.resolveTranscriptionEndpoint
import com.example.myapplication.system.json.AppJson
import com.example.myapplication.system.security.SensitiveTextRedactor
import com.google.gson.Gson
import java.io.File
import java.io.IOException
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
import okhttp3.Response

/** OpenAI 兼容的转写响应体，只关心 text 字段。 */
private data class TranscriptionResponse(
    val text: String = "",
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
                    buildHttpErrorMessage(response.code, response.body),
                )
            }
            val text = parseText(response.body)
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
        val endpoint = runCatching { resolveTranscriptionEndpoint(normalized.baseUrl) }
            .getOrElse { throwable ->
                return Result.failure(
                    IllegalArgumentException(
                        throwable.message?.takeIf { it.isNotBlank() } ?: "语音识别 Base URL 无效",
                    ),
                )
            }
        val probeBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", normalized.model)
            .build()
        val httpRequest = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer ${normalized.apiKey}")
            .post(probeBody)
            .build()

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
                                buildHttpErrorMessage(response.code, response.body),
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

    private suspend fun parseText(body: String): String {
        return try {
            gson.fromJson(body, TranscriptionResponse::class.java)?.text?.trim().orEmpty()
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

    private fun buildHttpErrorMessage(code: Int, responseBody: String): String {
        val prefix = when (code) {
            401, 403 -> "语音识别鉴权失败，请检查 API Key"
            404 -> "语音识别地址不存在，请检查 Base URL"
            413 -> "录音文件过大，服务端拒绝接收"
            429 -> "语音识别请求过于频繁，稍后再试"
            in 500..599 -> "语音识别服务异常（HTTP $code）"
            else -> "语音识别请求失败（HTTP $code）"
        }
        val excerpt = SensitiveTextRedactor.redact(responseBody.trim(), maxLength = 160)
        return if (excerpt.isBlank()) prefix else "$prefix：$excerpt"
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

        fun defaultHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()
        }
    }
}
