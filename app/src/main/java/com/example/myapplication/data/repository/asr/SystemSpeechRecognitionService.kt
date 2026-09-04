package com.example.myapplication.data.repository.asr

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
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
                trySend(RecognitionEvent.Failed(systemRecognitionErrorMessage(error)))
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

/** 把 SpeechRecognizer 的错误码翻成能直接展示的中文。 */
internal fun systemRecognitionErrorMessage(error: Int): String {
    return when (error) {
        SpeechRecognizer.ERROR_NO_MATCH -> "没听清，再说一次？"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "没有检测到说话内容"
        SpeechRecognizer.ERROR_NETWORK -> "网络不可用，系统识别失败"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "网络超时，系统识别失败"
        SpeechRecognizer.ERROR_AUDIO -> "录音出错，请检查麦克风"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "未获得录音权限"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别服务正忙，稍后再试"
        SpeechRecognizer.ERROR_CLIENT -> "系统识别客户端出错"
        SpeechRecognizer.ERROR_SERVER -> "系统识别服务端出错"
        else -> "系统语音识别失败（错误码 $error）"
    }
}
