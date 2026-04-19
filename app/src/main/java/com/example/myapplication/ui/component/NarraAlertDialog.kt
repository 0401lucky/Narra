package com.example.myapplication.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * 统一的 Narra 确认/信息弹窗。取代原生 Material3 AlertDialog 的硬壳观感，提供：
 * - 淡入缩放进入动画（150ms，EaseOutCubic）
 * - 自定义 scrim / 圆角 / padding，不受平台默认对话框宽度限制
 * - 与 NarraTextButton 风格一致的确认/取消按钮
 *
 * 典型用法：
 * ```
 * NarraAlertDialog(
 *     title = "删除助手",
 *     message = "将删除“阿尘”，这个操作不可撤销。",
 *     confirmLabel = "确认删除",
 *     onConfirm = { ... },
 *     onDismiss = { ... },
 * )
 * ```
 */
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = true,
            dismissOnBackPress = true,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.32f)),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(
                visibleState = visibleState,
                enter = fadeIn(animationSpec = tween(150, easing = EaseOutCubic)) +
                    scaleIn(
                        animationSpec = tween(180, easing = EaseOutCubic),
                        initialScale = 0.92f,
                    ),
                exit = fadeOut(animationSpec = tween(120)) + scaleOut(targetScale = 0.96f),
            ) {
                Surface(
                    modifier = modifier
                        .padding(horizontal = 32.dp)
                        .widthIn(max = 400.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (!message.isNullOrBlank()) {
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (content != null) {
                            content()
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        ) {
                            if (dismissLabel != null) {
                                NarraTextButton(onClick = onDismiss) {
                                    Text(dismissLabel)
                                }
                            }
                            NarraTextButton(
                                onClick = onConfirm,
                                enabled = confirmEnabled,
                            ) {
                                Text(
                                    text = confirmLabel,
                                    color = if (isDestructive) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        Color.Unspecified
                                    },
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
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

@Suppress("unused")
private val NarraAlertDialogPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp)
