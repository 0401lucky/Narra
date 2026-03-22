package com.example.myapplication.ui.screen.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.DEFAULT_USER_DISPLAY_NAME
import com.example.myapplication.ui.component.UserAvatarLoadState
import com.example.myapplication.ui.component.UserAvatarSource
import com.example.myapplication.ui.component.UserProfileAvatar
import com.example.myapplication.ui.component.rememberUserProfileAvatarState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileEditorSheet(
    displayName: String,
    avatarUri: String,
    avatarUrl: String,
    onDisplayNameChange: (String) -> Unit,
    onAvatarUrlChange: (String) -> Unit,
    onPickLocalAvatar: () -> Unit,
    onClearAvatar: () -> Unit,
    onDismissRequest: () -> Unit,
    onSave: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colorScheme = MaterialTheme.colorScheme
    val normalizedAvatarUrl = avatarUrl.trim()
    val avatarPreviewState = rememberUserProfileAvatarState(
        avatarUri = avatarUri,
        avatarUrl = avatarUrl,
    )
    val hasAvatarUrl = normalizedAvatarUrl.isNotBlank()
    val avatarUrlFormatError = hasAvatarUrl && !supportsProfileAvatarUrl(normalizedAvatarUrl)
    val isRemoteAvatarLoadError = hasAvatarUrl &&
        !avatarUrlFormatError &&
        avatarPreviewState.source == UserAvatarSource.Remote &&
        avatarPreviewState.loadState == UserAvatarLoadState.Error
    val avatarSourceSummary = when {
        avatarUrl.isNotBlank() -> "当前使用链接头像，保存后会覆盖本地图片。"
        avatarUri.isNotBlank() -> "当前使用本地图片头像。"
        else -> "未设置头像时，会显示昵称首字。"
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = colorScheme.surface,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "个人资料",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = "这里会同步更新侧边栏顶部的用户名和头像。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(30.dp),
                color = colorScheme.surfaceVariant.copy(alpha = 0.4f),
                tonalElevation = 2.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(112.dp)
                            .clip(CircleShape)
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        colorScheme.primary.copy(alpha = 0.14f),
                                        colorScheme.surface.copy(alpha = 0.98f),
                                    ),
                                ),
                            )
                            .border(
                                width = 1.dp,
                                color = colorScheme.outlineVariant.copy(alpha = 0.72f),
                                shape = CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(92.dp)
                                .shadow(
                                    elevation = 12.dp,
                                    shape = CircleShape,
                                    clip = false,
                                )
                                .clip(CircleShape)
                                .background(colorScheme.surface)
                                .border(
                                    width = 3.dp,
                                    color = colorScheme.primary.copy(alpha = 0.18f),
                                    shape = CircleShape,
                                )
                                .padding(6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            UserProfileAvatar(
                                displayName = displayName.ifBlank { DEFAULT_USER_DISPLAY_NAME },
                                avatarUri = avatarUri,
                                avatarUrl = avatarUrl,
                                modifier = Modifier.fillMaxSize(),
                                containerColor = colorScheme.primaryContainer,
                                contentColor = colorScheme.onPrimaryContainer,
                                textStyle = MaterialTheme.typography.headlineSmall,
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "圆形头像预览",
                            style = MaterialTheme.typography.labelLarge,
                            color = colorScheme.primary,
                        )
                        Text(
                            text = displayName.ifBlank { DEFAULT_USER_DISPLAY_NAME },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = when {
                                avatarUrlFormatError -> "链接格式仅支持 http/https"
                                isRemoteAvatarLoadError -> "头像链接加载失败"
                                avatarPreviewState.loadState == UserAvatarLoadState.Success -> "头像预览已就绪"
                                avatarPreviewState.loadState == UserAvatarLoadState.Loading -> "正在准备头像预览"
                                else -> "当前将显示昵称首字头像"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = avatarSourceSummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            OutlinedTextField(
                value = displayName,
                onValueChange = onDisplayNameChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("用户名") },
                placeholder = { Text("输入侧边栏显示名称") },
            )

            OutlinedTextField(
                value = avatarUrl,
                onValueChange = onAvatarUrlChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("头像链接") },
                placeholder = { Text("https://example.com/avatar.png") },
                isError = avatarUrlFormatError || isRemoteAvatarLoadError,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = null,
                    )
                },
                supportingText = {
                    Text(
                        when {
                            avatarUrlFormatError -> "头像链接仅支持 http 或 https 地址"
                            isRemoteAvatarLoadError -> "头像链接加载失败，请检查地址是否可访问。"
                            else -> "填写链接后会优先使用链接头像，并覆盖本地图片。"
                        },
                    )
                },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onPickLocalAvatar,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                    )
                    Text(
                        text = "上传图片",
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                OutlinedButton(
                    onClick = onClearAvatar,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("清空头像")
                }
            }

            FilledTonalButton(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("保存资料")
            }
        }
    }
}

private fun supportsProfileAvatarUrl(url: String): Boolean {
    return url.startsWith("https://") || url.startsWith("http://")
}
