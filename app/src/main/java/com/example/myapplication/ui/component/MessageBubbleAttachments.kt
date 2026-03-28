package com.example.myapplication.ui.component

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import com.example.myapplication.model.AttachmentType
import com.example.myapplication.model.MessageAttachment
import java.io.File

private val UserUploadedImageThumbnailSize = 148.dp
private const val MessageBubbleAttachmentTag = "MessageBubble"

@Composable
internal fun LegacyAttachmentCard(
    attachment: MessageAttachment,
    isUser: Boolean,
    contentColor: androidx.compose.ui.graphics.Color,
    autoPreviewImages: Boolean,
) {
    if (!isUser && attachment.type == AttachmentType.IMAGE) {
        GeneratedImageAttachment(
            uri = attachment.uri,
            fileName = attachment.fileName,
            autoPreviewImages = autoPreviewImages,
        )
        return
    }

    PartAttachmentCard(
        attachment = attachment,
        contentColor = contentColor,
    )
}

@Composable
internal fun PartAttachmentCard(
    attachment: MessageAttachment,
    contentColor: androidx.compose.ui.graphics.Color,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = contentColor.copy(alpha = 0.08f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = if (attachment.type == AttachmentType.IMAGE) {
                    Icons.Default.Image
                } else {
                    Icons.Default.Description
                },
                contentDescription = null,
                modifier = Modifier.padding(top = 1.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = attachment.fileName.ifBlank {
                        if (attachment.type == AttachmentType.IMAGE) "已附加图片" else "已附加文件"
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor,
                )
                Text(
                    text = if (attachment.type == AttachmentType.IMAGE) "图片输入" else "文件输入",
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor.copy(alpha = 0.78f),
                )
            }
        }
    }
}

@Composable
internal fun GeneratedImageAttachment(
    uri: String,
    fileName: String,
    autoPreviewImages: Boolean,
) {
    if (!autoPreviewImages) {
        PartAttachmentPreviewCard(
            fileName = fileName.ifBlank { "图片预览已关闭" },
            supportingText = "已关闭自动图片预览，可通过复制、导出或系统分享查看原始链接。",
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }
    var loadFailed by remember(uri) { mutableStateOf(false) }
    val model = remember(uri) {
        resolveImageModel(uri)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 140.dp, max = 320.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f)),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = model,
            contentDescription = fileName.ifBlank { "生成的图片" },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 140.dp, max = 320.dp)
                .clip(RoundedCornerShape(16.dp)),
            contentScale = ContentScale.FillWidth,
            onLoading = {
                loadFailed = false
            },
            onSuccess = {
                loadFailed = false
            },
            onError = { state ->
                loadFailed = true
                Log.w(MessageBubbleAttachmentTag, "图片加载失败：$uri", state.result.throwable)
            },
        )

        if (loadFailed) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp, max = 320.dp),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null)
                        Text(
                            text = fileName.ifBlank { "图片加载失败" },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Text(
                        text = imageLoadFailureHint(uri),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun resolveImageModel(uri: String): Any {
    return when {
        uri.startsWith("http://", ignoreCase = true) ||
            uri.startsWith("https://", ignoreCase = true) ||
            uri.startsWith("data:image", ignoreCase = true) ||
            uri.startsWith("file://", ignoreCase = true) -> uri
        uri.startsWith("content://", ignoreCase = true) -> uri.toUri()
        else -> File(uri)
    }
}

private fun imageLoadFailureHint(uri: String): String {
    return when {
        uri.startsWith("http://", ignoreCase = true) ||
            uri.startsWith("https://", ignoreCase = true) ->
            "远程图片不可访问，可能是链接过期、鉴权失败或网络受限。"
        uri.startsWith("data:image", ignoreCase = true) ->
            "Base64 图片数据无效、过大，或在解析过程中被截断。"
        uri.startsWith("content://", ignoreCase = true) ->
            "内容 URI 无法读取，请确认权限仍然有效。"
        else ->
            "本地图片文件不存在，或当前路径无法被图片加载器读取。"
    }
}

internal fun shouldHideLegacyAttachmentSummary(
    content: String,
    attachments: List<MessageAttachment>,
): Boolean {
    if (attachments.isEmpty()) {
        return false
    }

    return content in setOf("图片已发送", "文件已附加")
}

@Composable
internal fun UserUploadedImageThumbnail(
    uri: String,
    fileName: String,
    autoPreviewImages: Boolean,
) {
    if (!autoPreviewImages) {
        PartAttachmentPreviewCard(
            fileName = fileName.ifBlank { "已选择图片" },
            supportingText = "已关闭自动图片预览",
            modifier = Modifier.size(UserUploadedImageThumbnailSize),
        )
        return
    }
    var loadFailed by remember(uri) { mutableStateOf(false) }
    val model = remember(uri) {
        resolveImageModel(uri)
    }

    Surface(
        modifier = Modifier.size(UserUploadedImageThumbnailSize),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.10f)),
        shadowElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = model,
                contentDescription = fileName.ifBlank { "上传的图片" },
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onLoading = {
                    loadFailed = false
                },
                onSuccess = {
                    loadFailed = false
                },
                onError = { state ->
                    loadFailed = true
                    Log.w(MessageBubbleAttachmentTag, "图片加载失败：$uri", state.result.throwable)
                },
            )

            if (loadFailed) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "图片不可预览",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
internal fun PartAttachmentPreviewCard(
    fileName: String,
    supportingText: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .heightIn(min = 72.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = fileName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
