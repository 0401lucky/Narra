package com.example.myapplication.ui.screen.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import androidx.core.net.toUri
import com.example.myapplication.R
import java.io.File

@Composable
internal fun ChatImagePreviewDialog(
    payload: ChatImagePreviewPayload,
    onDismissRequest: () -> Unit,
    onSaveImage: (ChatPreviewImageItem) -> Unit,
) {
    var currentIndex by rememberSaveable(payload.images, payload.initialIndex) {
        mutableStateOf(payload.initialIndex.coerceIn(0, payload.images.lastIndex))
    }
    val currentImage = payload.images[currentIndex]

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            ZoomablePreviewImage(
                image = currentImage,
                modifier = Modifier.fillMaxSize(),
            )

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PreviewActionChip(
                        text = "关闭",
                        onClick = onDismissRequest,
                    )
                    PreviewActionChip(
                        text = "保存",
                        onClick = { onSaveImage(currentImage) },
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = payload.title.ifBlank { "图片预览" },
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                    Text(
                        text = "${currentIndex + 1} / ${payload.images.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.82f),
                    )
                    Text(
                        text = "双击可切换放大，双指可缩放拖动",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.72f),
                    )
                }
            }

            if (payload.images.size > 1) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PreviewActionChip(
                        text = "上一张",
                        enabled = currentIndex > 0,
                        onClick = { currentIndex = (currentIndex - 1).coerceAtLeast(0) },
                    )
                    PreviewActionChip(
                        text = "下一张",
                        enabled = currentIndex < payload.images.lastIndex,
                        onClick = { currentIndex = (currentIndex + 1).coerceAtMost(payload.images.lastIndex) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ZoomablePreviewImage(
    image: ChatPreviewImageItem,
    modifier: Modifier = Modifier,
) {
    var scale by remember(image.source) { mutableFloatStateOf(1f) }
    var offsetX by remember(image.source) { mutableFloatStateOf(0f) }
    var offsetY by remember(image.source) { mutableFloatStateOf(0f) }
    var loadFailed by remember(image.source) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .pointerInput(image.source) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        } else {
                            scale = 2.5f
                        }
                    },
                )
            }
            .pointerInput(image.source) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val nextScale = (scale * zoom).coerceIn(1f, 5f)
                    scale = nextScale
                    if (nextScale <= 1f) {
                        offsetX = 0f
                        offsetY = 0f
                    } else {
                        offsetX += pan.x
                        offsetY += pan.y
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = resolvePreviewImageModel(image.source),
            contentDescription = image.fileName.ifBlank { "图片预览" },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY,
                ),
            contentScale = ContentScale.Fit,
            onLoading = { loadFailed = false },
            onSuccess = { loadFailed = false },
            onError = { loadFailed = true },
        )

        if (loadFailed) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = Color.White.copy(alpha = 0.12f),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "图片加载失败",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                    )
                    Text(
                        text = image.fileName.ifBlank { image.source },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.74f),
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewActionChip(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .clip(MaterialTheme.shapes.extraLarge),
        shape = MaterialTheme.shapes.extraLarge,
        color = if (enabled) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.06f),
        contentColor = if (enabled) Color.White else Color.White.copy(alpha = 0.42f),
        onClick = onClick,
        enabled = enabled,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

private fun resolvePreviewImageModel(source: String): Any {
    return when {
        source.startsWith("http://", ignoreCase = true) ||
            source.startsWith("https://", ignoreCase = true) ||
            source.startsWith("data:image", ignoreCase = true) ||
            source.startsWith("file://", ignoreCase = true) -> source
        source.startsWith("content://", ignoreCase = true) -> source.toUri()
        else -> File(source)
    }
}
