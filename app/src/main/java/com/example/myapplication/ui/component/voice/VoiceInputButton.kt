package com.example.myapplication.ui.component.voice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.myapplication.viewmodel.VoiceInputPhase
import com.example.myapplication.viewmodel.VoiceInputUiState
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/** 上滑超过这个距离即判定为取消。 */
private val CANCEL_THRESHOLD_DP = 80.dp

/** 波形条数量。 */
private const val WAVE_BAR_COUNT = 8

/**
 * 麦克风按钮：按住说话、松手识别、上滑取消。
 *
 * 只认 [VoiceInputUiState]，不直接接触任何识别服务；录音权限在按钮内联申请。
 */
@Composable
fun VoiceInputButton(
    state: VoiceInputUiState,
    onStartRecording: () -> Unit,
    onStopAndTranscribe: () -> Unit,
    onCancel: () -> Unit,
    onDismissMessage: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.visible) return

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var permissionRequestCount by remember { mutableIntStateOf(0) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val cancelThresholdPx = with(LocalDensity.current) { CANCEL_THRESHOLD_DP.toPx() }
    val willCancel = dragOffsetY < -cancelThresholdPx

    // 以申请次数为 key 重算：授权成功后重组即可拿到最新权限状态
    val hasPermission = remember(permissionRequestCount) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionRequestCount += 1
        if (!granted) {
            onShowMessage("需要录音权限才能语音输入，可在系统设置中开启")
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (state.phase == VoiceInputPhase.RECORDING) 1.1f else 1f,
        animationSpec = spring(),
        label = "voiceButtonScale",
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        when (state.phase) {
            VoiceInputPhase.RECORDING -> RecordingHint(
                elapsedMillis = state.elapsedMillis,
                willCancel = willCancel,
            )

            VoiceInputPhase.TRANSCRIBING -> Text(
                text = "正在识别…",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            VoiceInputPhase.IDLE,
            VoiceInputPhase.FAILED,
            -> Unit
        }

        Box(contentAlignment = Alignment.Center) {
            if (state.phase == VoiceInputPhase.RECORDING) {
                WaveformRing(
                    amplitude = state.amplitude,
                    willCancel = willCancel,
                )
            }
            when (state.phase) {
                VoiceInputPhase.TRANSCRIBING -> CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                )

                VoiceInputPhase.FAILED -> IconButton(onClick = onDismissMessage) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = "语音识别失败，点击关闭提示",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }

                VoiceInputPhase.IDLE,
                VoiceInputPhase.RECORDING,
                -> MicIcon(
                    enabled = state.enabled,
                    scale = scale,
                    willCancel = willCancel,
                    modifier = Modifier.pointerInput(state.enabled, hasPermission) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val accepted = when {
                                !state.enabled -> {
                                    onShowMessage("请先在设置 → 语音识别中开启并完成配置")
                                    false
                                }

                                !hasPermission -> {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    false
                                }

                                else -> {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    dragOffsetY = 0f
                                    onStartRecording()
                                    true
                                }
                            }
                            if (!accepted) {
                                waitForUpOrCancellation()
                                return@awaitEachGesture
                            }
                            var lastY = down.position.y
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                    ?: break
                                if (!change.pressed) break
                                dragOffsetY += change.position.y - lastY
                                lastY = change.position.y
                            }
                            if (dragOffsetY < -cancelThresholdPx) {
                                onCancel()
                            } else {
                                onStopAndTranscribe()
                            }
                            dragOffsetY = 0f
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun RecordingHint(
    elapsedMillis: Long,
    willCancel: Boolean,
) {
    val seconds = (elapsedMillis / 1000f).roundToInt()
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = if (willCancel) "松手取消" else "松手发送 · 上滑取消",
            style = MaterialTheme.typography.labelSmall,
            color = if (willCancel) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Text(
            text = "${seconds}s",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MicIcon(
    enabled: Boolean,
    scale: Float,
    willCancel: Boolean,
    modifier: Modifier = Modifier,
) {
    val tint = when {
        willCancel -> MaterialTheme.colorScheme.error
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Icon(
        imageVector = Icons.Default.Mic,
        contentDescription = if (enabled) "按住说话" else "语音输入不可用",
        tint = tint,
        modifier = modifier
            .size((24 * scale).dp),
    )
}

@Composable
private fun WaveformRing(
    amplitude: Float,
    willCancel: Boolean,
) {
    val animatedAmplitude by animateFloatAsState(
        targetValue = amplitude.coerceIn(0f, 1f),
        animationSpec = spring(),
        label = "voiceAmplitude",
    )
    val color = if (willCancel) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }
    Canvas(modifier = Modifier.size(48.dp)) {
        val barWidth = size.width / (WAVE_BAR_COUNT * 2f)
        val centerY = size.height / 2f
        repeat(WAVE_BAR_COUNT) { index ->
            // 用固定相位的正弦让相邻条高度不同，避免整排等高像进度条
            val phase = sin((index + 1) * 1.1f)
            val ratio = (0.25f + abs(phase) * 0.75f) * animatedAmplitude
            val half = (centerY * ratio).coerceAtLeast(1f)
            val x = barWidth + index * barWidth * 2f
            drawLine(
                color = color,
                start = Offset(x, centerY - half),
                end = Offset(x, centerY + half),
                strokeWidth = barWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}
