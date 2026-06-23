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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.formatMoneyLabel
import com.example.myapplication.model.parseMoneyToCents
import com.example.myapplication.ui.component.AssistantAvatar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssistantPromptScreen(
    assistant: Assistant,
    onSave: (Assistant) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val palette = rememberSettingsPalette()
    val outlineColors = rememberSettingsOutlineColors()
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val clipboardScope = rememberCoroutineScope()

    var systemPrompt by rememberSaveable(assistant.id) { mutableStateOf(assistant.systemPrompt) }
    var scenario by rememberSaveable(assistant.id) { mutableStateOf(assistant.scenario) }
    var greeting by rememberSaveable(assistant.id) { mutableStateOf(assistant.greeting) }
    var creatorNotes by rememberSaveable(assistant.id) { mutableStateOf(assistant.creatorNotes) }
    var initialWalletBalanceText by rememberSaveable(assistant.id) {
        mutableStateOf(assistant.initialWalletBalanceCents.toMoneyInputText())
    }
    var contextMessageSizeText by rememberSaveable(assistant.id) {
        mutableStateOf(
            assistant.contextMessageSize
                .takeIf { it > 0 }
                ?.toString()
                .orEmpty(),
        )
    }
    val exampleDialogues = remember(assistant.id) {
        mutableStateListOf<String>().apply {
            addAll(assistant.exampleDialogues)
        }
    }

    // ── 脏检查支持 ──
    var lastSavedDraft by remember(assistant.id) { mutableStateOf(assistant.toPromptDraft()) }
    var savedFlashActive by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val feedbackScope = rememberCoroutineScope()

    val currentDraft by remember {
        derivedStateOf {
            AssistantPromptDraft(
                systemPrompt = systemPrompt,
                scenario = scenario,
                greeting = greeting,
                exampleDialogues = exampleDialogues.toList(),
                creatorNotes = creatorNotes,
                initialWalletBalanceText = initialWalletBalanceText,
                contextMessageSizeText = contextMessageSizeText,
            )
        }
    }
    val isDirty by remember {
        derivedStateOf { currentDraft != lastSavedDraft }
    }

    LaunchedEffect(savedFlashActive) {
        if (savedFlashActive) {
            delay(1500L)
            savedFlashActive = false
        }
    }

    val saveNow: () -> Unit = saveNow@{
        val snapshot = currentDraft
        if (snapshot == lastSavedDraft) return@saveNow
        onSave(snapshot.applyTo(assistant))
        lastSavedDraft = snapshot
        savedFlashActive = true
        feedbackScope.launch {
            snackbarHostState.showSnackbar(
                message = "已保存",
                duration = SnackbarDuration.Short,
            )
        }
    }

    // 兜底：离开页面若仍有未保存的真实改动，写回一次。
    DisposableEffect(Unit) {
        onDispose {
            val pending = currentDraft
            if (pending != lastSavedDraft) {
                onSave(pending.applyTo(assistant))
            }
        }
    }

    Scaffold(
        topBar = {
            SettingsTopBar(
                title = "角色人设",
                subtitle = assistant.name.takeIf { it.isNotBlank() },
                onNavigateBack = onNavigateBack,
                actionLabel = if (savedFlashActive) "已保存 ✓" else "保存",
                onAction = saveNow,
                actionEnabled = isDirty || savedFlashActive,
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
                bottom = 40.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── System Prompt ──
            item {
                PromptSection(
                    title = "核心人设",
                    palette = palette,
                    trailingIcons = {
                        NarraIconButton(
                            onClick = {
                                if (systemPrompt.isNotBlank()) {
                                    clipboardScope.copyPlainTextToClipboard(clipboard, "assistant-system-prompt", systemPrompt)
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
                        label = { Text("角色核心人设") },
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
                    title = "关系与场景",
                    palette = palette,
                ) {
                    Text(
                        text = "描述默认关系、时间线、相遇背景和互动起点。",
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
                    title = "开场预览",
                    palette = palette,
                ) {
                    // Mini chat bubble preview — inspired by RikkaHub
                    if (greeting.isNotBlank()) {
                        GreetingPreviewBubble(
                            assistantName = assistant.name.ifBlank { "角色" },
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
                        text = "新对话的开场白，留空即不发送。",
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
                        text = "提供多轮对话示例，帮助模型理解风格。",
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
                    title = "创作备注",
                    palette = palette,
                ) {
                    Text(
                        text = "私密笔记，不会注入对话上下文，适合记录创作意图和玩法边界。",
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

            item {
                PromptSection(
                    title = "钱包设定",
                    palette = palette,
                ) {
                    Text(
                        text = "设置角色首次进入场景钱包时的余额。留空使用默认 ${DEFAULT_ROLE_WALLET_BALANCE_LABEL}，已有钱包不会被自动覆盖。",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.body.copy(alpha = 0.6f),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = initialWalletBalanceText,
                        onValueChange = { raw ->
                            initialWalletBalanceText = raw
                                .filter { it.isDigit() || it == '.' || it == '¥' || it == '￥' || it == ',' }
                                .take(18)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("角色初始钱包余额") },
                        placeholder = { Text("例如 52000 或 520.00") },
                        supportingText = {
                            Text(
                                initialWalletBalanceText.toWalletBalancePreview()
                                    ?: "留空：使用默认 ${DEFAULT_ROLE_WALLET_BALANCE_LABEL}",
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(16.dp),
                        colors = outlineColors,
                    )
                }
            }

            item {
                PromptSection(
                    title = "最近消息窗口",
                    palette = palette,
                ) {
                    Text(
                        text = "填 0 为自动。需要摘要模型可用时，超出窗口的旧消息会由摘要承接；最近 N 条仍保留原文发送。",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.body.copy(alpha = 0.6f),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = contextMessageSizeText,
                        onValueChange = { raw ->
                            contextMessageSizeText = raw.filter(Char::isDigit).take(2)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("消息条数") },
                        shape = RoundedCornerShape(16.dp),
                        colors = outlineColors,
                    )
                }
            }
        }
    }
}

/**
 * 沿用 SettingsGroup 的壳，不再自绘 Surface + border。保留内联标题 + trailing 图标的编排习惯。
 * 标题左侧增加 accent 色装饰柱，让章节边界与"基础 / 扩展 / 记忆"等子页的 AssistantSubsectionTitle 保持视觉一致。
 */
@Composable
private fun PromptSection(
    title: String,
    palette: SettingsPalette,
    trailingIcons: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    SettingsGroup {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(20.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        palette.accent,
                                        palette.accent.copy(alpha = 0.55f),
                                    ),
                                ),
                            ),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = palette.title,
                    )
                }
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

/**
 * 提示词页的表单快照：承载可编辑字段，用于脏检查避免没有真实改动时仍调用 onSave。
 */
@Immutable
private data class AssistantPromptDraft(
    val systemPrompt: String,
    val scenario: String,
    val greeting: String,
    val exampleDialogues: List<String>,
    val creatorNotes: String,
    val initialWalletBalanceText: String,
    val contextMessageSizeText: String,
)

private fun Assistant.toPromptDraft(): AssistantPromptDraft = AssistantPromptDraft(
    systemPrompt = systemPrompt,
    scenario = scenario,
    greeting = greeting,
    exampleDialogues = exampleDialogues.toList(),
    creatorNotes = creatorNotes,
    initialWalletBalanceText = initialWalletBalanceCents.toMoneyInputText(),
    contextMessageSizeText = contextMessageSize.takeIf { it > 0 }?.toString().orEmpty(),
)

private fun AssistantPromptDraft.applyTo(base: Assistant): Assistant = base.copy(
    systemPrompt = systemPrompt.trim(),
    scenario = scenario.trim(),
    greeting = greeting.trim(),
    exampleDialogues = exampleDialogues.map { it.trim() }.filter { it.isNotBlank() },
    creatorNotes = creatorNotes.trim(),
    initialWalletBalanceCents = initialWalletBalanceText.toWalletBalanceCents(),
    contextMessageSize = contextMessageSizeText.toIntOrNull()?.coerceIn(0, 30) ?: 0,
)

private fun Long.toMoneyInputText(): String {
    if (this <= 0L) return ""
    val yuan = this / 100
    val cents = this % 100
    return if (cents == 0L) {
        yuan.toString()
    } else {
        "$yuan.${cents.toString().padStart(2, '0')}"
    }
}

private fun String.toWalletBalanceCents(): Long {
    val normalized = trim()
    if (normalized.isBlank()) return 0L
    return parseMoneyToCents(normalized)?.coerceAtLeast(0L) ?: 0L
}

private fun String.toWalletBalancePreview(): String? {
    val normalized = trim()
    if (normalized.isBlank()) return null
    val cents = parseMoneyToCents(normalized)?.takeIf { it >= 0L } ?: return "金额格式不对，保存时会使用默认余额"
    return "保存为 ${cents.formatMoneyLabel()}"
}

private const val DEFAULT_ROLE_WALLET_BALANCE_LABEL = "¥300.00"
