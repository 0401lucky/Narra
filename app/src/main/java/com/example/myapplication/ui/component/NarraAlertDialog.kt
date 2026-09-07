package com.example.myapplication.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.myapplication.ui.theme.NarraMinimumTouchTarget
import com.example.myapplication.ui.theme.NarraUi

/**
 * 统一的 Narra 确认/信息弹窗。正文独立滚动，操作区在窄屏下可换行。
 * 遮罩和外部点击由平台 Dialog 处理，避免全屏容器拦截关闭手势。
 *
 * 典型用法：
 * ```
 * NarraAlertDialog(
 *     title = "删除角色",
 *     message = "将删除“阿尘”，这个操作不可撤销。",
 *     confirmLabel = "确认删除",
 *     onConfirm = { ... },
 *     onDismiss = { ... },
 * )
 * ```
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NarraAlertDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    confirmLabel: String = "确认",
    dismissLabel: String? = "取消",
    confirmEnabled: Boolean = true,
    isDestructive: Boolean = false,
    content: (@Composable () -> Unit)? = null,
) {
    val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }
    val configuration = LocalConfiguration.current
    val maxWidth = (configuration.screenWidthDp.dp - 48.dp).coerceIn(0.dp, 400.dp)
    val maxHeight = (configuration.screenHeightDp.dp - 48.dp).coerceAtLeast(0.dp)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = true,
            dismissOnBackPress = true,
        ),
    ) {
        AnimatedVisibility(
            visibleState = visibleState,
            modifier = Modifier
                .widthIn(max = maxWidth)
                .heightIn(max = maxHeight),
            enter = fadeIn(animationSpec = tween(150, easing = EaseOutCubic)) +
                scaleIn(
                    animationSpec = tween(180, easing = EaseOutCubic),
                    initialScale = 0.92f,
                ),
            exit = fadeOut(animationSpec = tween(120)) + scaleOut(targetScale = 0.96f),
        ) {
            Surface(
                modifier = modifier.fillMaxWidth(),
                shape = RoundedCornerShape(NarraUi.cardRadius),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 0.dp,
                shadowElevation = 4.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(NarraUi.compactGap),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .testTag("narra_alert_dialog_content")
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (!message.isNullOrBlank()) {
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        content?.invoke()
                    }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(NarraUi.compactGap, Alignment.End),
                        verticalArrangement = Arrangement.spacedBy(NarraUi.compactGap),
                    ) {
                        if (dismissLabel != null) {
                            NarraTextButton(
                                onClick = onDismiss,
                                modifier = Modifier.heightIn(min = NarraMinimumTouchTarget),
                            ) {
                                Text(dismissLabel)
                            }
                        }
                        NarraTextButton(
                            onClick = onConfirm,
                            enabled = confirmEnabled,
                            modifier = Modifier.heightIn(min = NarraMinimumTouchTarget),
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = if (isDestructive) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                            ),
                        ) {
                            Text(text = confirmLabel, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 仅提示型弹窗（单按钮关闭）。
 */
@Composable
fun NarraInfoDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    confirmLabel: String = "知道了",
) {
    NarraAlertDialog(
        title = title,
        message = message,
        onDismiss = onDismiss,
        onConfirm = onDismiss,
        confirmLabel = confirmLabel,
        dismissLabel = null,
    )
}
