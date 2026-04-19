package com.example.myapplication.ui.component

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.ChatReasoningStep
import com.mikepenz.markdown.model.MarkdownPadding
import com.mikepenz.markdown.model.MarkdownTypography
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale

@Composable
internal fun ReasoningTimelineCard(
    reasoningSteps: List<ChatReasoningStep>,
    expanded: Boolean,
    previewVisible: Boolean,
    isLoading: Boolean,
    markdownTypography: MarkdownTypography,
    markdownPadding: MarkdownPadding,
    onToggleExpanded: () -> Unit,
    previewTextStyle: TextStyle,
    autoPreviewImages: Boolean,
    codeBlockAutoWrap: Boolean,
    codeBlockAutoCollapse: Boolean,
    performanceMode: ChatMessagePerformanceMode,
    modifier: Modifier = Modifier,
) {
    if (reasoningSteps.isEmpty()) {
        return
    }

    val visibleSteps = remember(reasoningSteps, expanded) {
        visibleReasoningTimelineSteps(reasoningSteps, expanded)
    }
    val contentVisible = expanded || previewVisible
    val cardModifier = if (isLoading || performanceMode != ChatMessagePerformanceMode.FULL) {
        modifier
    } else {
        modifier.animateContentSize(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
        )
    }

    Surface(
        modifier = cardModifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (reasoningSteps.size > 2) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onToggleExpanded)
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = if (expanded) {
                            "收起思考过程"
                        } else {
                            "展开 ${reasoningSteps.size - 2} 条思考步骤"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            val lineColor = MaterialTheme.colorScheme.outlineVariant
            Box(
                modifier = Modifier.drawBehind {
                    val x = 11.dp.toPx()
                    val top = 18.dp.toPx()
                    val bottom = size.height - 18.dp.toPx()
                    if (bottom > top) {
                        drawLine(
                            color = lineColor,
                            start = Offset(x, top),
                            end = Offset(x, bottom),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }
                },
            ) {
                Column {
                    visibleSteps.forEachIndexed { index, step ->
                        ReasoningTimelineStep(
                            step = step,
                            isFirst = index == 0,
                            isLast = index == visibleSteps.lastIndex,
                            contentVisible = contentVisible,
                            previewVisible = previewVisible && !expanded,
                            markdownTypography = markdownTypography,
                            markdownPadding = markdownPadding,
                            previewTextStyle = previewTextStyle,
                            autoPreviewImages = autoPreviewImages,
                            codeBlockAutoWrap = codeBlockAutoWrap,
                            codeBlockAutoCollapse = codeBlockAutoCollapse,
                            performanceMode = performanceMode,
                            onToggleExpanded = onToggleExpanded,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReasoningTimelineStep(
    step: ChatReasoningStep,
    isFirst: Boolean,
    isLast: Boolean,
    contentVisible: Boolean,
    previewVisible: Boolean,
    markdownTypography: MarkdownTypography,
    markdownPadding: MarkdownPadding,
    previewTextStyle: TextStyle,
    autoPreviewImages: Boolean,
    codeBlockAutoWrap: Boolean,
    codeBlockAutoCollapse: Boolean,
    performanceMode: ChatMessagePerformanceMode,
    onToggleExpanded: () -> Unit,
) {
    val scrollState = rememberScrollState()
    var currentTimeMillis by remember(step.id, step.finishedAt) {
        mutableStateOf(step.finishedAt ?: System.currentTimeMillis())
    }
    LaunchedEffect(step.id, step.finishedAt) {
        if (step.finishedAt != null) {
            currentTimeMillis = step.finishedAt
            return@LaunchedEffect
        }
        while (isActive) {
            currentTimeMillis = System.currentTimeMillis()
            delay(100)
        }
    }
    LaunchedEffect(previewVisible, step.id, step.text, step.finishedAt) {
        if (previewVisible && step.finishedAt == null) {
            scrollState.scrollTo(scrollState.maxValue)
        }
    }

    val durationSeconds = remember(step.createdAt, step.finishedAt, currentTimeMillis) {
        val endAt = step.finishedAt ?: currentTimeMillis
        val elapsed = ((endAt - step.createdAt).coerceAtLeast(0L)).toDouble() / 1000.0
        if (elapsed <= 0.0) null else elapsed
    }
    val thinkingTitle = remember(step.text) {
        extractReasoningStepTitle(step.text)
    }
    val headerLabel = remember(thinkingTitle, durationSeconds, step.finishedAt) {
        when {
            !thinkingTitle.isNullOrBlank() -> thinkingTitle
            durationSeconds != null && step.finishedAt == null -> {
                "思考了 ${String.format(Locale.getDefault(), "%.1f", durationSeconds)} 秒"
            }
            else -> "深度思考"
        }
    }
    val durationLabel = remember(thinkingTitle, durationSeconds, step.finishedAt) {
        if (thinkingTitle.isNullOrBlank() || durationSeconds == null) {
            null
        } else {
            "${String.format(Locale.getDefault(), "%.1f", durationSeconds)}s"
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onToggleExpanded)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                modifier = Modifier.width(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .background(
                            color = if (isFirst) androidx.compose.ui.graphics.Color.Transparent else MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(999.dp),
                        ),
                )
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Psychology,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f, fill = true)
                        .width(1.dp)
                        .background(
                            color = if (isLast) androidx.compose.ui.graphics.Color.Transparent else MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(999.dp),
                        ),
                )
            }

            Text(
                text = headerLabel,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (!durationLabel.isNullOrBlank()) {
                Text(
                    text = durationLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Icon(
                imageVector = if (contentVisible) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (contentVisible) {
            val contentModifier = if (previewVisible) {
                Modifier
                    .graphicsLayer { alpha = 0.99f }
                    .drawWithCache {
                        val fadeHeight = 28.dp.toPx()
                        val brush = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to androidx.compose.ui.graphics.Color.Transparent,
                                (fadeHeight / size.height.coerceAtLeast(1f)) to androidx.compose.ui.graphics.Color.Black,
                                (1f - fadeHeight / size.height.coerceAtLeast(1f)) to androidx.compose.ui.graphics.Color.Black,
                                1f to androidx.compose.ui.graphics.Color.Transparent,
                            ),
                        )
                        onDrawWithContent {
                            drawContent()
                            drawRect(
                                brush = brush,
                                size = Size(size.width, size.height),
                                blendMode = BlendMode.DstIn,
                            )
                        }
                    }
                    .heightIn(max = 100.dp)
                    .verticalScroll(scrollState)
            } else {
                Modifier
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 32.dp, end = 4.dp, bottom = 8.dp)
                    .then(contentModifier),
            ) {
                RenderMessageText(
                    text = step.text,
                    useMarkdown = step.finishedAt != null,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    assistantMarkdownTypography = markdownTypography,
                    assistantMarkdownPadding = markdownPadding,
                    plainTextStyle = previewTextStyle,
                    codeBlockAutoWrap = codeBlockAutoWrap,
                    codeBlockAutoCollapse = codeBlockAutoCollapse,
                    performanceMode = performanceMode,
                )
            }
        }
    }
}

internal fun extractReasoningStepTitle(text: String): String? {
    val lines = text.lines()
    for (index in lines.indices.reversed()) {
        val match = Regex("^\\*\\*(.+?)\\*\\*$").find(lines[index].trim()) ?: continue
        val title = match.groupValues.getOrNull(1)?.trim().orEmpty()
        if (title.isNotBlank()) {
            return title
        }
    }
    return null
}

internal fun visibleReasoningTimelineSteps(
    reasoningSteps: List<ChatReasoningStep>,
    expanded: Boolean,
    collapsedVisibleCount: Int = 2,
): List<ChatReasoningStep> {
    if (expanded || reasoningSteps.size <= collapsedVisibleCount) {
        return reasoningSteps
    }
    return reasoningSteps.takeLast(collapsedVisibleCount)
}

internal fun buildReasoningPreview(reasoningContent: String): String {
    if (reasoningContent.isBlank()) {
        return ""
    }

    val normalized = reasoningContent
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toList()
        .takeLast(4)
        .joinToString(separator = "\n")

    if (normalized.length <= 220) {
        return normalized
    }

    return "…" + normalized.takeLast(220)
}
