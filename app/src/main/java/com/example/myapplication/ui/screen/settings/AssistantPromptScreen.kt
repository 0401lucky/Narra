package com.example.myapplication.ui.screen.settings

import com.example.myapplication.ui.component.*

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.model.Assistant
import com.example.myapplication.ui.component.AssistantAvatar

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssistantPromptScreen(
    assistant: Assistant,
    onSave: (Assistant) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val palette = rememberSettingsPalette()
    val outlineColors = rememberSettingsOutlineColors()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    var systemPrompt by rememberSaveable { mutableStateOf(assistant.systemPrompt) }
    var scenario by rememberSaveable { mutableStateOf(assistant.scenario) }
    var greeting by rememberSaveable { mutableStateOf(assistant.greeting) }
    var creatorNotes by rememberSaveable { mutableStateOf(assistant.creatorNotes) }
    val exampleDialogues = remember {
        mutableStateListOf<String>().apply {
            addAll(assistant.exampleDialogues)
        }
    }

    // Auto-save on leave
    DisposableEffect(Unit) {
        onDispose {
            onSave(
                assistant.copy(
                    systemPrompt = systemPrompt.trim(),
                    scenario = scenario.trim(),
                    greeting = greeting.trim(),
                    exampleDialogues = exampleDialogues.map { it.trim() }.filter { it.isNotBlank() },
                    creatorNotes = creatorNotes.trim(),
                ),
            )
        }
    }

    Scaffold(
        topBar = {
            SettingsTopBar(
                title = "提示词",
                subtitle = assistant.name.takeIf { it.isNotBlank() },
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
                bottom = 40.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── System Prompt ──
            item {
                PromptSection(
                    title = "系统提示词",
                    palette = palette,
                    trailingIcons = {
                        NarraIconButton(
                            onClick = {
                                if (systemPrompt.isNotBlank()) {
                                    clipboardManager.setText(AnnotatedString(systemPrompt))
                                    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                Icons.Outlined.ContentCopy,
                                contentDescription = "复制",
                                tint = palette.body.copy(alpha = 0.5f),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    },
                ) {
                    OutlinedTextField(
                        value = systemPrompt,
                        onValueChange = { systemPrompt = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 160.dp),
                        maxLines = 30,
                        shape = RoundedCornerShape(16.dp),
                        colors = outlineColors,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            lineHeight = 22.sp,
                        ),
                    )
                }
            }

            // ── Scenario ──
            item {
                PromptSection(
                    title = "场景设定",
                    palette = palette,
                ) {
                    Text(
                        text = "描述对话发生的场景、时间线和背景环境",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.body.copy(alpha = 0.6f),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = scenario,
                        onValueChange = { scenario = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp),
                        maxLines = 12,
                        shape = RoundedCornerShape(16.dp),
                        colors = outlineColors,
                    )
                }
            }

            // ── Greeting with chat bubble preview ──
            item {
                PromptSection(
                    title = "预览",
                    palette = palette,
                ) {
                    // Mini chat bubble preview — inspired by RikkaHub
                    if (greeting.isNotBlank()) {
                        GreetingPreviewBubble(
                            assistantName = assistant.name.ifBlank { "助手" },
                            assistant = assistant,
                            greeting = greeting,
                            palette = palette,
                        )
                        Spacer(Modifier.height(12.dp))
                    }

                    Text(
                        text = "开场白",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = palette.title,
                    )
                    Text(
                        text = "助手在新对话开始时的第一条消息，留空则不发送",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.body.copy(alpha = 0.6f),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = greeting,
                        onValueChange = { greeting = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 96.dp),
                        maxLines = 8,
                        shape = RoundedCornerShape(16.dp),
                        colors = outlineColors,
                    )
                }
            }

            // ── Example Dialogues as list items with + button ──
            item {
                PromptSection(
                    title = "示例对话",
                    palette = palette,
                ) {
                    Text(
                        text = "添加示例对话帮助模型理解风格, 每条是一轮完整的对话示例",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.body.copy(alpha = 0.6f),
                    )
                }
            }

            // Each example dialogue as a separate mini-card
            itemsIndexed(exampleDialogues) { index, dialogue ->
                ExampleDialogueItem(
                    index = index,
                    value = dialogue,
                    palette = palette,
                    outlineColors = outlineColors,
                    onValueChange = { exampleDialogues[index] = it },
                    onRemove = { exampleDialogues.removeAt(index) },
                )
            }

            // Add button
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = palette.accentSoft.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, palette.accent.copy(alpha = 0.15f)),
                    onClick = { exampleDialogues.add("") },
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "添加示例对话",
                            tint = palette.accent,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }

            // ── Creator Notes ──
            item {
                PromptSection(
                    title = "作者备注",
                    palette = palette,
                ) {
                    Text(
                        text = "仅供自己参考的笔记，不会注入到对话上下文中",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.body.copy(alpha = 0.6f),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = creatorNotes,
                        onValueChange = { creatorNotes = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 80.dp),
                        maxLines = 6,
                        shape = RoundedCornerShape(16.dp),
                        colors = outlineColors,
                    )
                }
            }
        }
    }
}

/**
 * A clean section container with a bold title header and optional trailing icons.
 * Inspired by RikkaHub's prompt field sections.
 */
@Composable
private fun PromptSection(
    title: String,
    palette: SettingsPalette,
    trailingIcons: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = palette.surface,
        border = BorderStroke(0.5.dp, palette.border.copy(alpha = 0.3f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.title,
                )
                if (trailingIcons != null) {
                    trailingIcons()
                }
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

/**
 * Greeting chat bubble preview — shows how the greeting would look in-chat.
 */
@Composable
private fun GreetingPreviewBubble(
    assistantName: String,
    assistant: Assistant,
    greeting: String,
    palette: SettingsPalette,
) {
    val bubbleColor = lerp(
        MaterialTheme.colorScheme.surface,
        MaterialTheme.colorScheme.surfaceVariant,
        0.3f,
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Assistant info line
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AssistantAvatar(
                name = assistant.name,
                iconName = assistant.iconName,
                avatarUri = assistant.avatarUri,
                size = 28.dp,
                containerColor = palette.accentSoft.copy(alpha = 0.5f),
                contentColor = palette.accentStrong,
                cornerRadius = 10.dp,
            )
            Text(
                text = assistantName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = palette.body,
            )
        }

        // Bubble
        Surface(
            modifier = Modifier.widthIn(max = 300.dp),
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = 6.dp,
                bottomEnd = 20.dp,
            ),
            color = bubbleColor,
            border = BorderStroke(0.5.dp, palette.border.copy(alpha = 0.25f)),
        ) {
            Text(
                text = greeting,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.title,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A single example dialogue item — editable with a remove button.
 */
@Composable
private fun ExampleDialogueItem(
    index: Int,
    value: String,
    palette: SettingsPalette,
    outlineColors: androidx.compose.material3.TextFieldColors,
    onValueChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = palette.surface,
        border = BorderStroke(0.5.dp, palette.border.copy(alpha = 0.3f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "示例 ${index + 1}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.body,
                )
                NarraIconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp),
                maxLines = 10,
                shape = RoundedCornerShape(14.dp),
                colors = outlineColors,
            )
        }
    }
}
