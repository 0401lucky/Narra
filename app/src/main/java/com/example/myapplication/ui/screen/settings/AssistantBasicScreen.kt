package com.example.myapplication.ui.screen.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.DEFAULT_ASSISTANT_ICON
import com.example.myapplication.model.PRESET_ASSISTANT_ICONS
import com.example.myapplication.ui.component.AssistantAvatar
import com.example.myapplication.ui.component.resolveAssistantIcon

@Composable
fun AssistantBasicScreen(
    assistant: Assistant?,
    isNew: Boolean,
    onSave: (Assistant) -> Unit,
    onDelete: (String) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val palette = rememberSettingsPalette()
    val outlineColors = rememberSettingsOutlineColors()

    var iconName by rememberSaveable { mutableStateOf(assistant?.iconName ?: DEFAULT_ASSISTANT_ICON) }
    var avatarUri by rememberSaveable { mutableStateOf(assistant?.avatarUri ?: "") }
    var name by rememberSaveable { mutableStateOf(assistant?.name ?: "") }
    var description by rememberSaveable { mutableStateOf(assistant?.description ?: "") }
    var tagsText by rememberSaveable {
        mutableStateOf(assistant?.tags?.joinToString(", ") ?: "")
    }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            avatarUri = it.toString()
            iconName = ""
        }
    }

    Scaffold(
        topBar = {
            SettingsTopBar(
                title = if (isNew) "新建助手" else "基础设定",
                onNavigateBack = onNavigateBack,
            )
        },
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
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                AssistantWorkspaceIntro(
                    assistant = assistant ?: Assistant(name = name, description = description),
                    overline = "Basic",
                    title = "先把助手做得像一个角色",
                    summary = "头像、名称、描述和标签是最先被感知到的部分，尽量别空着。",
                )
            }

            item {
                AssistantSubsectionTitle(
                    title = "外观识别",
                    subtitle = "先定头像和图标，角色感会立刻提升。",
                )
            }

            item {
                SettingsGroup {
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            AssistantAvatar(
                                name = name,
                                iconName = iconName,
                                avatarUri = avatarUri,
                                size = 72.dp,
                                containerColor = palette.subtleChip,
                                contentColor = palette.subtleChipContent,
                                cornerRadius = 20.dp,
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable { imagePickerLauncher.launch(arrayOf("image/*")) },
                                    shape = RoundedCornerShape(14.dp),
                                    color = palette.accentSoft,
                                    border = BorderStroke(1.dp, palette.border.copy(alpha = 0.5f)),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Icon(
                                            Icons.Default.AddAPhoto,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = palette.accent,
                                        )
                                        Text(
                                            text = "上传头像",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = palette.accent,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                }

                                if (avatarUri.isNotBlank()) {
                                    Surface(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(14.dp))
                                            .clickable {
                                                avatarUri = ""
                                                if (iconName.isBlank()) iconName = DEFAULT_ASSISTANT_ICON
                                            },
                                        shape = RoundedCornerShape(14.dp),
                                        color = palette.surfaceTint,
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = palette.body,
                                            )
                                            Text(
                                                text = "移除头像",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = palette.body,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            PRESET_ASSISTANT_ICONS.forEach { preset ->
                                val isSelected = iconName == preset.name
                                val icon = resolveAssistantIcon(preset.name)
                                Surface(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable {
                                            iconName = preset.name
                                            avatarUri = ""
                                        },
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isSelected) palette.accentSoft else palette.surfaceTint,
                                    border = BorderStroke(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) palette.accent else palette.border.copy(alpha = 0.4f),
                                    ),
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (icon != null) {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = preset.label,
                                                modifier = Modifier.size(26.dp),
                                                tint = if (isSelected) palette.accent else palette.body,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                AssistantSubsectionTitle(
                    title = "文字名片",
                    subtitle = "用简短但准确的名字和描述，把助手定位说清楚。",
                )
            }

            item {
                SettingsGroup {
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("名称") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(18.dp),
                            colors = outlineColors,
                        )
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text("描述") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = outlineColors,
                            maxLines = 4,
                        )
                        OutlinedTextField(
                            value = tagsText,
                            onValueChange = { tagsText = it },
                            label = { Text("标签") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(18.dp),
                            colors = outlineColors,
                            placeholder = { Text("例如：推理, 悬疑") },
                        )
                    }
                }
            }

            item {
                SettingsGroup {
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        AnimatedSettingButton(
                            text = "保存基础设定",
                            onClick = {
                                val result = (assistant ?: Assistant()).copy(
                                    iconName = iconName.ifBlank { DEFAULT_ASSISTANT_ICON },
                                    avatarUri = avatarUri,
                                    name = name.trim(),
                                    description = description.trim(),
                                    tags = tagsText.split(",", "，")
                                        .map { it.trim() }
                                        .filter { it.isNotBlank() },
                                )
                                onSave(result)
                                onNavigateBack()
                            },
                            enabled = name.isNotBlank(),
                            isPrimary = true,
                        )
                        if (!isNew && assistant != null) {
                            AnimatedSettingButton(
                                text = "删除助手",
                                onClick = {
                                    showDeleteDialog = true
                                },
                                enabled = true,
                                isPrimary = false,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog && assistant != null) {
        AssistantDeleteConfirmDialog(
            assistantName = assistant.name.ifBlank { "未命名助手" },
            onDismissRequest = { showDeleteDialog = false },
            onConfirm = {
                showDeleteDialog = false
                onDelete(assistant.id)
            },
        )
    }
}
