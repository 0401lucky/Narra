package com.example.myapplication.data.repository.asr

import com.example.myapplication.model.VoiceRecognitionSettings
import java.io.File
import kotlinx.coroutines.flow.Flow

/** 一次识别过程中对外发出的事件。message 字段一律已是可直接展示的简体中文。 */
sealed interface RecognitionEvent {
    /** 中间结果，只有系统通道会发；用于输入框上方的实时预览。 */
    data class Partial(val text: String) : RecognitionEvent

    /** 最终结果。text 为空表示没听到有效内容。 */
    data class Final(val text: String) : RecognitionEvent

    /** 失败。message 已脱敏且为中文。 */
    data class Failed(val message: String) : RecognitionEvent
}

/**
 * 语音识别服务。两条通道共用这一个契约，引擎差异不外泄到 UI 层。
 *
 * 系统通道自己采音，忽略 [audioFile]；远程通道读取 [audioFile] 上传。
 */
interface SpeechRecognitionService {
    /** 当前设备/配置下该通道是否可用。 */
    fun isAvailable(settings: VoiceRecognitionSettings): Boolean

    fun recognize(
        audioFile: File?,
        settings: VoiceRecognitionSettings,
    ): Flow<RecognitionEvent>
}
