package com.example.myapplication.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.example.myapplication.data.repository.asr.AudioRecording
import com.example.myapplication.data.repository.asr.SpeechRecognitionService
import com.example.myapplication.model.VoiceRecognitionSettings
import kotlinx.coroutines.flow.Flow

/**
 * 语音输入所需的依赖集合。
 *
 * 麦克风按钮出现在输入框这类很深的组件里，逐层透传这几个依赖会污染大量签名，
 * 因此沿用本项目 [LocalImagePersister] 的做法：由 AppRoot 从 AppGraph 注入。
 */
class VoiceInputDependencies(
    val settingsFlow: Flow<VoiceRecognitionSettings>,
    val systemService: SpeechRecognitionService,
    val remoteService: SpeechRecognitionService,
    val audioRecorderFactory: () -> AudioRecording,
)

/**
 * Composition-scope 单例：语音输入依赖。
 *
 * 默认 null 表示未注入——此时麦克风按钮直接不渲染，而不是抛异常。这样
 * 单独预览某个输入框组件时不会崩。
 */
val LocalVoiceInput = staticCompositionLocalOf<VoiceInputDependencies?> { null }
