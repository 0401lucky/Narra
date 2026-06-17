package com.example.myapplication.ui.screen.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage
import com.example.myapplication.data.repository.AndroidChatImageGalleryWriter
import com.example.myapplication.data.repository.AndroidChatImageSourceReader
import com.example.myapplication.data.repository.ChatImageGallerySaver
import com.example.myapplication.data.repository.SaveImageResult
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.CharacterArtStyle
import com.example.myapplication.ui.component.AssistantAvatar
import com.example.myapplication.ui.screen.chat.ChatImagePreviewDialog
import com.example.myapplication.ui.screen.chat.ChatImagePreviewPayload
import com.example.myapplication.ui.screen.chat.ChatPreviewImageItem
import com.example.myapplication.viewmodel.CharacterArtGeneratedImage
import com.example.myapplication.viewmodel.CharacterArtStudioUiState
import kotlinx.coroutines.launch

@Composable
fun CharacterArtStudioScreen(
    uiState: CharacterArtStudioUiState,
    onSelectAssistant: (String) -> Unit,
    onSelectStyle: (String) -> Unit,
    onPromptChange: (String) -> Unit,
    onNegativePromptChange: (String) -> Unit,
    onRevisionInstructionChange: (String) -> Unit,
    onExtractPrompt: () -> Unit,
    onGenerateImage: () -> Unit,
    onApplyAvatar: () -> Unit,
    onClearMessages: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    val palette = rememberSettingsPalette()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val imageGallerySaver = remember(context) {
        ChatImageGallerySaver(
            sourceReader = AndroidChatImageSourceReader(context),
            galleryWriter = AndroidChatImageGalleryWriter(context),
        )
    }
    var previewImage by remember { mutableStateOf<ChatPreviewImageItem?>(null) }
    var pendingGalleryImage by remember { mutableStateOf<ChatPreviewImageItem?>(null) }
    val legacyGalleryPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val image = pendingGalleryImage
        pendingGalleryImage = null
        if (image == null) return@rememberLauncherForActivityResult
        if (!granted) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("没有存储权限，无法保存图片")
            }
            return@rememberLauncherForActivityResult
        }
        coroutineScope.launch {
            saveCharacterArtImage(
                image = image,
                imageGallerySaver = imageGallerySaver,
                snackbarHostState = snackbarHostState,
            )
        }
    }
    val saveImageToGallery: (ChatPreviewImageItem) -> Unit = { image ->
        val needsLegacyPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
        val hasLegacyPermission = !needsLegacyPermission || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (hasLegacyPermission) {
            coroutineScope.launch {
                saveCharacterArtImage(
                    image = image,
                    imageGallerySaver = imageGallerySaver,
                    snackbarHostState = snackbarHostState,
                )
            }
        } else {
            pendingGalleryImage = image
            legacyGalleryPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    Scaffold(
        topBar = {
            SettingsTopBar(
                title = "角色图工作台",
                subtitle = uiState.selectedAssistant?.name ?: "选择一个角色开始创作",
                onNavigateBack = onNavigateBack,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = palette.background,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = SettingsScreenPadding,
                top = 4.dp,
                end = SettingsScreenPadding,
                bottom = 34.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                CharacterArtHero(
                    assistant = uiState.selectedAssistant,
                    generatedImage = uiState.generatedImage,
                    isGenerating = uiState.isGeneratingImage,
                    onOpenPreview = {
                        uiState.generatedImage?.let { image ->
                            previewImage = image.toPreviewItem(uiState.selectedAssistant)
                        }
                    },
                    onSaveImage = {
                        uiState.generatedImage?.let { image ->
                            val item = image.toPreviewItem(uiState.selectedAssistant)
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                            ) {
                                pendingGalleryImage = item
                                legacyGalleryPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            } else {
                                saveImageToGallery(item)
                            }
                        }
                    },
                )
            }

            if (uiState.message != null || uiState.errorMessage != null) {
                item {
                    CharacterArtNotice(
                        message = uiState.message,
                        errorMessage = uiState.errorMessage,
                        onDismiss = onClearMessages,
                    )
                }
            }

            item {
                AssistantSubsectionTitle(
                    title = "角色与风格",
                    subtitle = "先定角色，再选一套非真人绘制风格",
                )
            }

            item {
                CharacterArtAssistantPicker(
                    assistants = uiState.assistants,
                    selectedAssistantId = uiState.selectedAssistantId,
                    enabled = !uiState.isExtractingPrompt && !uiState.isGeneratingImage,
                    onSelectAssistant = onSelectAssistant,
                )
            }

            item {
                CharacterArtStylePicker(
                    styles = uiState.styles,
                    selectedStyleId = uiState.selectedStyleId,
                    enabled = !uiState.isGeneratingImage,
                    onSelectStyle = onSelectStyle,
                )
            }

            item {
                AssistantSubsectionTitle(
                    title = "视觉提示词",
                    subtitle = "可先让 AI 提取，也可以自己继续改",
                )
            }

            item {
                SettingsGroup {
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (uiState.promptDraft.traitSummary.isNotBlank()) {
                            Text(
                                text = uiState.promptDraft.traitSummary,
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.body,
                            )
                        }
                        OutlinedTextField(
                            value = uiState.revisionInstruction,
                            onValueChange = onRevisionInstructionChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("修改意见") },
                            placeholder = { Text("例如：更成熟一点，黑色短发，城市夜景氛围") },
                            minLines = 2,
                            maxLines = 4,
                            shape = RoundedCornerShape(18.dp),
                            enabled = !uiState.isExtractingPrompt && !uiState.isGeneratingImage,
                        )
                        Button(
                            onClick = onExtractPrompt,
                            enabled = uiState.selectedAssistant != null &&
                                !uiState.isExtractingPrompt &&
                                !uiState.isGeneratingImage,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            if (uiState.isExtractingPrompt) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("正在提取")
                            } else {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(if (uiState.editablePrompt.isBlank()) "提取视觉提示词" else "按修改意见重新提取")
                            }
                        }
                        OutlinedTextField(
                            value = uiState.editablePrompt,
                            onValueChange = onPromptChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("生图提示词") },
                            minLines = 5,
                            maxLines = 10,
                            shape = RoundedCornerShape(18.dp),
                            enabled = !uiState.isGeneratingImage,
                        )
                        OutlinedTextField(
                            value = uiState.editableNegativePrompt,
                            onValueChange = onNegativePromptChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("避免元素") },
                            minLines = 2,
                            maxLines = 5,
                            shape = RoundedCornerShape(18.dp),
                            enabled = !uiState.isGeneratingImage,
                        )
                    }
                }
            }

            item {
                CharacterArtActionPanel(
                    uiState = uiState,
                    onGenerateImage = onGenerateImage,
                    onApplyAvatar = onApplyAvatar,
                )
            }
        }
    }

    previewImage?.let { image ->
        ChatImagePreviewDialog(
            payload = ChatImagePreviewPayload(
                title = uiState.selectedAssistant?.name?.ifBlank { "角色图预览" } ?: "角色图预览",
                images = listOf(image),
            ),
            onDismissRequest = { previewImage = null },
            onSaveImage = { saveImageToGallery(it) },
        )
    }
}

@Composable
private fun CharacterArtHero(
    assistant: Assistant?,
    generatedImage: CharacterArtGeneratedImage?,
    isGenerating: Boolean,
    onOpenPreview: () -> Unit,
    onSaveImage: () -> Unit,
) {
    val palette = rememberSettingsPalette()
    val heroGradient = Brush.linearGradient(
        colors = listOf(
            palette.accent.copy(alpha = 0.20f),
            palette.subtleChip.copy(alpha = 0.18f),
            palette.surface.copy(alpha = 0.96f),
        ),
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(heroGradient),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AssistantAvatar(
                    name = assistant?.name.orEmpty(),
                    iconName = assistant?.iconName.orEmpty(),
                    avatarUri = assistant?.avatarUri.orEmpty(),
                    size = 54.dp,
                    containerColor = palette.accentSoft.copy(alpha = 0.45f),
                    contentColor = palette.accent,
                    cornerRadius = 18.dp,
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = assistant?.name?.ifBlank { "未命名角色" } ?: "还没有选择角色",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = palette.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = assistant?.description?.ifBlank { "将角色卡变成可直接使用的头像图" }
                            ?: "从通讯录里选一个角色，生成非真人风格角色图",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.body,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                shape = RoundedCornerShape(24.dp),
                color = palette.surface.copy(alpha = 0.78f),
                border = BorderStroke(1.dp, palette.border.copy(alpha = 0.55f)),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (generatedImage != null) {
                        AsyncImage(
                            model = generatedImage.uri,
                            contentDescription = "生成的角色图",
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable(onClick = onOpenPreview),
                            contentScale = ContentScale.Crop,
                        )
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CharacterArtImageChip(
                                text = "查看",
                                icon = Icons.Default.Image,
                                onClick = onOpenPreview,
                            )
                            CharacterArtImageChip(
                                text = "保存",
                                icon = Icons.Default.Download,
                                onClick = onSaveImage,
                            )
                        }
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(24.dp),
                        ) {
                            if (isGenerating) {
                                CircularProgressIndicator()
                                Text(
                                    text = "正在生成角色图",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = palette.body,
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = null,
                                    modifier = Modifier.size(44.dp),
                                    tint = palette.accent,
                                )
                                Text(
                                    text = "生成结果会显示在这里",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = palette.body,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CharacterArtImageChip(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.clip(RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        color = Color.Black.copy(alpha = 0.42f),
        contentColor = Color.White,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(15.dp))
            Text(text = text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun CharacterArtNotice(
    message: String?,
    errorMessage: String?,
    onDismiss: () -> Unit,
) {
    val palette = rememberSettingsPalette()
    val isError = errorMessage != null
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = if (isError) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            palette.accentSoft.copy(alpha = 0.7f)
        },
        contentColor = if (isError) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            palette.accent
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = if (isError) Icons.Default.Refresh else Icons.Default.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = errorMessage ?: message.orEmpty(),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = onDismiss) {
                Text("知道了")
            }
        }
    }
}

private fun CharacterArtGeneratedImage.toPreviewItem(
    assistant: Assistant?,
): ChatPreviewImageItem {
    return ChatPreviewImageItem(
        source = uri,
        fileName = buildCharacterArtFileName(assistant),
        mimeType = mimeType,
    )
}

private fun buildCharacterArtFileName(assistant: Assistant?): String {
    val name = assistant?.name
        ?.trim()
        ?.replace(Regex("""[^\p{L}\p{N}._-]+"""), "_")
        ?.trim('_', '.', '-')
        ?.takeIf { it.isNotBlank() }
        ?: "character"
    return "narra-character-art-$name"
}

private suspend fun saveCharacterArtImage(
    image: ChatPreviewImageItem,
    imageGallerySaver: ChatImageGallerySaver,
    snackbarHostState: SnackbarHostState,
) {
    when (val result = imageGallerySaver.save(image.source, image.fileName)) {
        is SaveImageResult.Success -> {
            snackbarHostState.showSnackbar("已保存到系统相册：${result.savedFileName}")
        }

        is SaveImageResult.Failure -> {
            snackbarHostState.showSnackbar(result.message.ifBlank { "保存图片失败" })
        }
    }
}

@Composable
private fun CharacterArtAssistantPicker(
    assistants: List<Assistant>,
    selectedAssistantId: String,
    enabled: Boolean,
    onSelectAssistant: (String) -> Unit,
) {
    val palette = rememberSettingsPalette()
    if (assistants.isEmpty()) {
        SettingsGroup {
            Text(
                text = "还没有可用角色",
                modifier = Modifier.padding(18.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.body,
            )
        }
        return
    }
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 2.dp),
    ) {
        items(assistants, key = { it.id }) { assistant ->
            val selected = assistant.id == selectedAssistantId
            Surface(
                modifier = Modifier
                    .height(66.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(enabled = enabled) { onSelectAssistant(assistant.id) },
                shape = RoundedCornerShape(18.dp),
                color = if (selected) palette.accentSoft.copy(alpha = 0.7f) else palette.surface,
                border = BorderStroke(
                    width = if (selected) 1.5.dp else 1.dp,
                    color = if (selected) palette.accent.copy(alpha = 0.7f) else palette.border,
                ),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AssistantAvatar(
                        name = assistant.name,
                        iconName = assistant.iconName,
                        avatarUri = assistant.avatarUri,
                        size = 42.dp,
                        cornerRadius = 14.dp,
                    )
                    Text(
                        text = assistant.name.ifBlank { "未命名角色" },
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) palette.accent else palette.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun CharacterArtStylePicker(
    styles: List<CharacterArtStyle>,
    selectedStyleId: String,
    enabled: Boolean,
    onSelectStyle: (String) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 2.dp),
    ) {
        items(styles, key = { it.id }) { style ->
            FilterChip(
                selected = style.id == selectedStyleId,
                enabled = enabled,
                onClick = { onSelectStyle(style.id) },
                modifier = Modifier.heightIn(min = 44.dp),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                    )
                },
                label = {
                    Column {
                        Text(style.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            text = style.subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                shape = RoundedCornerShape(14.dp),
            )
        }
    }
}

@Composable
private fun CharacterArtActionPanel(
    uiState: CharacterArtStudioUiState,
    onGenerateImage: () -> Unit,
    onApplyAvatar: () -> Unit,
) {
    val palette = rememberSettingsPalette()
    SettingsGroup {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onGenerateImage,
                enabled = uiState.editablePrompt.isNotBlank() &&
                    !uiState.isExtractingPrompt &&
                    !uiState.isGeneratingImage,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                if (uiState.isGeneratingImage) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("正在生成")
                } else {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("生成角色图")
                }
            }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(
                        enabled = uiState.generatedImage != null && !uiState.isApplyingAvatar,
                        onClick = onApplyAvatar,
                    ),
                shape = RoundedCornerShape(16.dp),
                color = if (uiState.generatedImage != null) {
                    palette.accentSoft.copy(alpha = 0.72f)
                } else {
                    palette.surfaceTint.copy(alpha = 0.45f)
                },
                border = BorderStroke(1.dp, palette.border.copy(alpha = 0.45f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Face,
                        contentDescription = null,
                        tint = if (uiState.generatedImage != null) palette.accent else palette.body,
                    )
                    Text(
                        text = if (uiState.isApplyingAvatar) "正在应用头像" else "设为角色头像",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (uiState.generatedImage != null) palette.accent else palette.body,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
