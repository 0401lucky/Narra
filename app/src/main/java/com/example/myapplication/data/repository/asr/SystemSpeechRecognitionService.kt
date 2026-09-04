package com.example.myapplication.data.repository.asr

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import com.example.myapplication.model.VoiceRecognitionSettings
import java.io.File
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers

/**
 * 系统识别通道，包装 Android [SpeechRecognizer]。
 *
 * [SpeechRecognizer] 是主线程 API：必须在主线程创建与调用，因此整条 Flow 固定在
 * [Dispatchers.Main]。自行采音，不使用外部传入的音频文件。
 */
class SystemSpeechRecognitionService(
    private val context: Context,
) : SpeechRecognitionService {

    override fun isAvailable(settings: VoiceRecognitionSettings): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    private fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun recognize(
        audioFile: File?,
        settings: VoiceRecognitionSettings,
        releaseSignal: Flow<Unit>?,
    ): Flow<RecognitionEvent> = callbackFlow {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            trySend(RecognitionEvent.Failed("这台设备没有可用的系统语音识别，建议改用大模型识别"))
            close()
            return@callbackFlow
        }

        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit

            override fun onBeginningOfSpeech() = Unit

            override fun onRmsChanged(rmsdB: Float) {
                trySend(RecognitionEvent.Level(normalizeRmsDb(rmsdB)))
            }

            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() = Unit

            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults.firstRecognizedText()
                if (text.isNotBlank()) {
                    trySend(RecognitionEvent.Partial(text))
                }
            }

            override fun onResults(results: Bundle?) {
                trySend(RecognitionEvent.Final(results.firstRecognizedText()))
                close()
            }

            override fun onError(error: Int) {
                trySend(
                    RecognitionEvent.Failed(
                        systemRecognitionErrorMessage(
                            error = error,
                            appHasRecordPermission = hasRecordAudioPermission(),
                        ),
                    ),
                )
                close()
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        }
        recognizer.setRecognitionListener(listener)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            val language = settings.normalized().language
            if (language.isNotBlank()) {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            }
        }
        runCatching { recognizer.startListening(intent) }.onFailure { throwable ->
            trySend(RecognitionEvent.Failed("无法启动系统语音识别：${throwable.message.orEmpty()}".trim()))
            close()
        }

        // 用户松手：让识别器收音并给出最终结果。这里不能直接 close()，
        // 否则 awaitClose 会先销毁识别器，最终结果就永远拿不到了。
        if (releaseSignal != null) {
            launch {
                releaseSignal.first()
                runCatching { recognizer.stopListening() }
            }
        }

        awaitClose {
            runCatching { recognizer.stopListening() }
            runCatching { recognizer.cancel() }
            runCatching { recognizer.destroy() }
        }
    }.flowOn(Dispatchers.Main)
}

/**
 * 枚举设备上声明了 RecognitionService 的应用包名。
 *
 * `isRecognitionAvailable()` 只回答有没有，不说是谁提供的。而 ERROR_INSUFFICIENT_PERMISSIONS
 * 这类失败往往取决于具体是哪个应用在提供识别服务，因此把包名显示出来，用户排查时有据可依。
 * 需要 AndroidManifest 里的 <queries> 声明，否则 Android 11+ 查不到。
 */
fun resolveRecognitionServicePackages(context: Context): List<String> {
    return runCatching {
        context.packageManager
            .queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), 0)
            .mapNotNull { it.serviceInfo?.packageName }
            .distinct()
    }.getOrDefault(emptyList())
}

private fun Bundle?.firstRecognizedText(): String {
    return this
        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        ?.firstOrNull()
        ?.trim()
        .orEmpty()
}

/**
 * onRmsChanged 给出的是分贝值，实测大致落在 -2..10 之间，归一化到 0f..1f 驱动波形。
 */
internal fun normalizeRmsDb(rmsdB: Float): Float {
    return ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
}

/**
 * 把 SpeechRecognizer 的错误码翻成能直接展示的中文。
 *
 * [appHasRecordPermission] 用来区分 ERROR_INSUFFICIENT_PERMISSIONS 的两种情形：
 * 应用自己没权限，还是应用已授权但识别服务仍拒绝录音。后者在部分 ROM 上很常见
 * （系统识别由另一个应用提供，而那个应用没有麦克风权限，或该服务对三方应用不开放），
 * 此时提示「未获得录音权限」会把用户送去反复检查一个已经给过的开关。
 */
internal fun systemRecognitionErrorMessage(
    error: Int,
    appHasRecordPermission: Boolean = false,
): String {
    return when (error) {
        SpeechRecognizer.ERROR_NO_MATCH -> "没听清，再说一次？"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "没有检测到说话内容"
        SpeechRecognizer.ERROR_NETWORK -> "网络不可用，系统识别失败"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "网络超时，系统识别失败"
        SpeechRecognizer.ERROR_AUDIO -> "录音出错，请检查麦克风"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> if (appHasRecordPermission) {
            "系统识别服务拒绝了录音。应用已获得麦克风权限，说明这台设备的系统识别对三方应用不可用，建议改用大模型识别"
        } else {
            "未获得录音权限，请在系统设置中开启麦克风权限"
        }
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别服务正忙，稍后再试"
        SpeechRecognizer.ERROR_CLIENT -> "系统识别客户端出错"
        SpeechRecognizer.ERROR_SERVER -> "系统识别服务端出错"
        else -> "系统语音识别失败（错误码 $error）"
    }
}
