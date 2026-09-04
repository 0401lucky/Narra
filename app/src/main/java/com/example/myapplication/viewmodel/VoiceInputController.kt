package com.example.myapplication.viewmodel

import com.example.myapplication.data.repository.asr.AudioRecording
import com.example.myapplication.data.repository.asr.MAX_RECORDING_MILLIS
import com.example.myapplication.data.repository.asr.MIN_RECORDING_MILLIS
import com.example.myapplication.data.repository.asr.RecognitionEvent
import com.example.myapplication.data.repository.asr.SpeechRecognitionService
import com.example.myapplication.model.VoiceRecognitionEngine
import com.example.myapplication.model.VoiceRecognitionSettings
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 麦克风按钮的四个外观状态。 */
enum class VoiceInputPhase {
    IDLE,
    RECORDING,
    TRANSCRIBING,
    FAILED,
}

data class VoiceInputUiState(
    /** 总开关关闭时按钮完全不出现。 */
    val visible: Boolean = false,
    /** 可见但凭据不全时按钮置灰。 */
    val enabled: Boolean = false,
    val phase: VoiceInputPhase = VoiceInputPhase.IDLE,
    /** 0f..1f，驱动波形。 */
    val amplitude: Float = 0f,
    /** 系统通道的实时中间结果。 */
    val partialText: String = "",
    val elapsedMillis: Long = 0L,
    /** 中文提示或错误，供按钮上方气泡展示。 */
    val message: String = "",
) {
    val isBusy: Boolean
        get() = phase == VoiceInputPhase.RECORDING || phase == VoiceInputPhase.TRANSCRIBING
}

private const val TICK_INTERVAL_MILLIS = 100L

/**
 * 把识别结果追加到输入框已有文本末尾。
 *
 * 中文之间不该插空格，但英文单词之间必须插，否则会粘成一个词。因此只在
 * 衔接处两侧都是 ASCII 字母或数字时补一个空格。
 */
fun appendRecognizedText(current: String, recognized: String): String {
    val addition = recognized.trim()
    if (addition.isEmpty()) return current
    if (current.isEmpty()) return addition
    val last = current.last()
    if (last.isWhitespace()) return current + addition
    val needsSpace = last.isAsciiWordChar() && addition.first().isAsciiWordChar()
    return if (needsSpace) "$current $addition" else current + addition
}

private fun Char.isAsciiWordChar(): Boolean {
    return this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'
}

/**
 * 录音 + 识别的状态机。
 *
 * 不引用任何 Android UI 类型，因此可在 JVM 单测里完整验证状态流转。
 * 识别成功的文本通过 [onTextRecognized] 交给调用方追加到输入框，
 * 控制器自己不持有输入框内容。
 */
class VoiceInputController(
    private val scope: CoroutineScope,
    private val audioRecorder: AudioRecording,
    private val systemService: SpeechRecognitionService,
    private val remoteService: SpeechRecognitionService,
    private val onTextRecognized: (String) -> Unit,
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
) {
    private val _uiState = MutableStateFlow(VoiceInputUiState())
    val uiState: StateFlow<VoiceInputUiState> = _uiState.asStateFlow()

    private var settings: VoiceRecognitionSettings = VoiceRecognitionSettings()
    private var recognizeJob: Job? = null
    private var tickerJob: Job? = null
    private var releaseSignal: MutableSharedFlow<Unit>? = null
    private var pendingAudioFile: File? = null
    private var recordingStartedAt: Long = 0L

    /** 设置变化时由 Compose 调用，驱动按钮的可见性与可用性。 */
    fun updateSettings(next: VoiceRecognitionSettings) {
        val normalized = next.normalized()
        settings = normalized
        _uiState.update { current ->
            current.copy(
                visible = normalized.enabled,
                enabled = normalized.isReadyForInput(),
            )
        }
    }

    /** 按下按钮。凭据不全或已在忙时直接给提示，不进入录音。 */
    fun startRecording() {
        val current = _uiState.value
        if (current.isBusy) return
        if (!settings.enabled) return
        if (!settings.hasUsableCredential()) {
            _uiState.update {
                it.copy(
                    phase = VoiceInputPhase.IDLE,
                    message = "请先在设置 → 语音识别 → 语音识别 API 中填写 Key 与 Base URL",
                )
            }
            return
        }

        recordingStartedAt = nowProvider()
        _uiState.update {
            it.copy(
                phase = VoiceInputPhase.RECORDING,
                amplitude = 0f,
                partialText = "",
                elapsedMillis = 0L,
                message = "",
            )
        }

        if (settings.engine == VoiceRecognitionEngine.SYSTEM) {
            startSystemRecognition()
        } else {
            val started = runCatching { audioRecorder.start() }
            val file = started.getOrNull()
            if (file == null) {
                failWith("无法开始录音，请检查麦克风权限")
                return
            }
            pendingAudioFile = file
        }
        startTicker()
    }

    /** 松手。系统通道等最终结果，远程通道停止录音并上传。 */
    fun stopAndTranscribe() {
        if (_uiState.value.phase != VoiceInputPhase.RECORDING) return
        val elapsed = nowProvider() - recordingStartedAt
        stopTicker()

        if (elapsed < MIN_RECORDING_MILLIS) {
            discardRecording()
            _uiState.update {
                it.copy(
                    phase = VoiceInputPhase.IDLE,
                    amplitude = 0f,
                    partialText = "",
                    elapsedMillis = 0L,
                    message = "说话时间太短",
                )
            }
            return
        }

        _uiState.update { it.copy(phase = VoiceInputPhase.TRANSCRIBING, amplitude = 0f) }

        if (settings.engine == VoiceRecognitionEngine.SYSTEM) {
            // 识别流仍在收集，只通知识别器收音；最终结果由已有的收集协程处理
            releaseSignal?.let { signal ->
                scope.launch { signal.emit(Unit) }
            }
            return
        }

        val file = runCatching { audioRecorder.stop() }.getOrNull()
        pendingAudioFile = file
        if (file == null) {
            failWith("录音没有保存成功，请再试一次")
            return
        }
        startRemoteTranscription(file)
    }

    /** 上滑取消：不产出任何文本，也不留下音频文件。 */
    fun cancel() {
        if (!_uiState.value.isBusy) return
        stopTicker()
        recognizeJob?.cancel()
        recognizeJob = null
        releaseSignal = null
        discardRecording()
        _uiState.update {
            it.copy(
                phase = VoiceInputPhase.IDLE,
                amplitude = 0f,
                partialText = "",
                elapsedMillis = 0L,
                message = "",
            )
        }
    }

    /** 失败态点击后回到空闲，或提示气泡消失。 */
    fun dismissMessage() {
        _uiState.update {
            it.copy(
                phase = if (it.phase == VoiceInputPhase.FAILED) VoiceInputPhase.IDLE else it.phase,
                message = "",
            )
        }
    }

    private fun startSystemRecognition() {
        val signal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        releaseSignal = signal
        recognizeJob?.cancel()
        recognizeJob = scope.launch {
            systemService.recognize(null, settings, signal).collect(::handleEvent)
        }
    }

    private fun startRemoteTranscription(file: File) {
        recognizeJob?.cancel()
        recognizeJob = scope.launch {
            try {
                remoteService.recognize(file, settings, null).collect(::handleEvent)
            } finally {
                // 成功失败都删：音频不留在磁盘上
                discardRecording()
            }
        }
    }

    private fun handleEvent(event: RecognitionEvent) {
        when (event) {
            is RecognitionEvent.Partial -> _uiState.update { it.copy(partialText = event.text) }
            is RecognitionEvent.Level -> _uiState.update { it.copy(amplitude = event.level) }
            is RecognitionEvent.Final -> finishWith(event.text)
            is RecognitionEvent.Failed -> failWith(event.message)
        }
    }

    private fun finishWith(text: String) {
        stopTicker()
        releaseSignal = null
        val trimmed = text.trim()
        if (trimmed.isBlank()) {
            failWith("没听清，再说一次？")
            return
        }
        // 不清 message：录满 60 秒自动结束这类提示在识别成功后依然要让用户看到。
        // 正常路径下 message 在 startRecording 时已经清空。
        _uiState.update {
            it.copy(
                phase = VoiceInputPhase.IDLE,
                amplitude = 0f,
                partialText = "",
                elapsedMillis = 0L,
            )
        }
        onTextRecognized(trimmed)
    }

    private fun failWith(message: String) {
        stopTicker()
        releaseSignal = null
        discardRecording()
        _uiState.update {
            it.copy(
                phase = VoiceInputPhase.FAILED,
                amplitude = 0f,
                partialText = "",
                elapsedMillis = 0L,
                message = message,
            )
        }
    }

    private fun discardRecording() {
        runCatching { audioRecorder.cancel() }
        pendingAudioFile?.let { file -> runCatching { file.delete() } }
        pendingAudioFile = null
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                delay(TICK_INTERVAL_MILLIS)
                val elapsed = nowProvider() - recordingStartedAt
                val level = if (settings.engine == VoiceRecognitionEngine.SYSTEM) {
                    _uiState.value.amplitude
                } else {
                    runCatching { audioRecorder.amplitude() }.getOrDefault(0f)
                }
                _uiState.update { it.copy(elapsedMillis = elapsed, amplitude = level) }
                if (elapsed >= MAX_RECORDING_MILLIS) {
                    stopAndTranscribe()
                    _uiState.update { it.copy(message = "单次录音最长 60 秒，已自动结束") }
                    return@launch
                }
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }
}
