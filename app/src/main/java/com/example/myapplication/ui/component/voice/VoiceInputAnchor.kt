package com.example.myapplication.ui.component.voice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myapplication.model.VoiceRecognitionSettings
import com.example.myapplication.ui.LocalVoiceInput
import com.example.myapplication.viewmodel.VoiceInputController
import com.example.myapplication.viewmodel.VoiceInputPhase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * 输入框旁的语音输入锚点：一行接入即可，自带控制器、状态与提示。
 *
 * 依赖未注入（[LocalVoiceInput] 为 null）或总开关关闭时什么都不渲染，
 * 调用方不需要做任何条件判断。
 */
@Composable
fun VoiceInputAnchor(
    onTextRecognized: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dependencies = LocalVoiceInput.current ?: return
    val scope = rememberCoroutineScope()
    var localMessage by remember { mutableStateOf("") }

    // onTextRecognized 每次重组都是新 lambda，控制器不能跟着重建，
    // 否则录音中途重组会丢状态。用一个稳定的中转读取最新回调。
    val latestOnText = remember { mutableStateOf(onTextRecognized) }
    latestOnText.value = onTextRecognized

    val controller = remember(dependencies) {
        VoiceInputController(
            scope = scope,
            audioRecorder = dependencies.audioRecorderFactory(),
            systemService = dependencies.systemService,
            remoteService = dependencies.remoteService,
            onTextRecognized = { text -> latestOnText.value(text) },
        )
    }

    // Flow 操作符必须在 remember 里建，否则每次重组都换一条新 Flow，
    // collectAsStateWithLifecycle 会跟着重启收集。
    val settingsFlow = remember(dependencies) {
        dependencies.settingsFlow.distinctVoiceRecognitionSettings()
    }
    val settings by settingsFlow.collectAsStateWithLifecycle(
        initialValue = VoiceRecognitionSettings(),
    )
    LaunchedEffect(settings) {
        controller.updateSettings(settings)
    }

    val state by controller.uiState.collectAsStateWithLifecycle()
    if (!state.visible) return

    val message = localMessage.ifBlank { state.message }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (message.isNotBlank()) {
            Text(
                text = message,
                style = MaterialTheme.typography.labelSmall,
                color = if (state.phase == VoiceInputPhase.FAILED) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center,
            )
        }
        if (state.partialText.isNotBlank()) {
            Text(
                text = state.partialText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        VoiceInputButton(
            state = state,
            onStartRecording = {
                localMessage = ""
                controller.startRecording()
            },
            onStopAndTranscribe = controller::stopAndTranscribe,
            onCancel = controller::cancel,
            onDismissMessage = {
                localMessage = ""
                controller.dismissMessage()
            },
            onShowMessage = { text -> localMessage = text },
        )
    }
}

/** 只在语音识别设置真正变化时重新发射，避免其它设置改动触发按钮重组。 */
private fun Flow<VoiceRecognitionSettings>.distinctVoiceRecognitionSettings(): Flow<VoiceRecognitionSettings> {
    return map { it.normalized() }.distinctUntilChanged()
}
