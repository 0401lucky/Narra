package com.example.myapplication.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private const val APP_NOTICE_DISPLAY_MILLIS = 1500L
private const val APP_NOTICE_ANIMATION_MILLIS = 300
private val AppNoticeHorizontalPadding = 16.dp
private val AppNoticeVerticalPadding = 10.dp
private val AppNoticeCornerRadius = 12.dp
private val AppNoticeTopMargin = 64.dp
private val AppNoticeBottomMargin = 80.dp

@Composable
fun AppSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    AnimatedAppSnackbarHost(
        hostState = hostState,
        alignment = Alignment.BottomCenter,
        edgePadding = AppNoticeBottomMargin,
        modifier = modifier,
    )
}

@Composable
fun TopAppSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    contentTopInset: Dp = 0.dp,
) {
    AnimatedAppSnackbarHost(
        hostState = hostState,
        alignment = Alignment.TopCenter,
        edgePadding = (AppNoticeTopMargin - contentTopInset).coerceAtLeast(0.dp),
        modifier = modifier,
    )
}

@Composable
private fun AnimatedAppSnackbarHost(
    hostState: SnackbarHostState,
    alignment: Alignment,
    edgePadding: Dp,
    modifier: Modifier = Modifier,
) {
    val currentData = hostState.currentSnackbarData
    val density = LocalDensity.current
    val slideOffsetPx = with(density) { 20.dp.roundToPx() }
    var renderedData by remember { mutableStateOf<androidx.compose.material3.SnackbarData?>(null) }
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(currentData) {
        if (currentData == null) {
            return@LaunchedEffect
        }
        renderedData = currentData
        isVisible = true
        delay(APP_NOTICE_DISPLAY_MILLIS)
        isVisible = false
        delay(APP_NOTICE_ANIMATION_MILLIS.toLong())
        if (renderedData === currentData) {
            renderedData = null
        }
        if (hostState.currentSnackbarData === currentData) {
            currentData.dismiss()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        contentAlignment = alignment,
    ) {
        AnimatedVisibility(
            visible = renderedData != null && isVisible,
            enter = fadeIn(animationSpec = androidx.compose.animation.core.tween(APP_NOTICE_ANIMATION_MILLIS)) + slideInVertically(
                animationSpec = androidx.compose.animation.core.tween(APP_NOTICE_ANIMATION_MILLIS),
                initialOffsetY = { -slideOffsetPx },
            ),
            exit = fadeOut(animationSpec = androidx.compose.animation.core.tween(APP_NOTICE_ANIMATION_MILLIS)) + slideOutVertically(
                animationSpec = androidx.compose.animation.core.tween(APP_NOTICE_ANIMATION_MILLIS),
                targetOffsetY = { -slideOffsetPx },
            ),
            modifier = Modifier
                .padding(top = if (alignment == Alignment.TopCenter) edgePadding else 0.dp)
                .padding(bottom = if (alignment == Alignment.BottomCenter) edgePadding else 0.dp)
                .widthIn(max = 560.dp),
        ) {
            renderedData?.let { data ->
                Surface(
                    shape = RoundedCornerShape(AppNoticeCornerRadius),
                    color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.96f),
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    shadowElevation = 6.dp,
                ) {
                    Text(
                        text = data.visuals.message,
                        modifier = Modifier.padding(
                            horizontal = AppNoticeHorizontalPadding,
                            vertical = AppNoticeVerticalPadding,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
