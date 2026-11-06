package com.example.myapplication.ui.screen.settings.worldbook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import com.example.myapplication.model.WorldBookEntry
import com.example.myapplication.ui.component.NarraAlertDialog
import com.example.myapplication.ui.component.TopAppSnackbarHost
import com.example.myapplication.ui.component.narraOutlinedButtonColors
import com.example.myapplication.ui.component.narraButtonColors
import com.example.myapplication.ui.theme.NarraUi
import com.example.myapplication.ui.theme.NarraShapes
import com.example.myapplication.ui.theme.NarraMinimumTouchTarget
import com.example.myapplication.ui.screen.settings.AnimatedSettingButton
import com.example.myapplication.ui.screen.settings.SettingsGroup
import com.example.myapplication.ui.screen.settings.SettingsHintCard
import com.example.myapplication.ui.screen.settings.SettingsPageIntro
import com.example.myapplication.ui.screen.settings.SettingsSectionHeader
import com.example.myapplication.ui.screen.settings.SettingsTopBar
import com.example.myapplication.ui.screen.settings.rememberSettingsOutlineColors
import com.example.myapplication.ui.screen.settings.rememberSettingsPalette
import com.example.myapplication.ui.screen.settings.rememberSettingsSnackbarHostState
import kotlinx.coroutines.delay

@Composable
fun WorldBookBookDetailScreen(
    bookId: String,
    bookName: String,
    entries: List<WorldBookEntry>,
    isSaving: Boolean,
    onRenameBook: (String, String) -> Unit,
    onDeleteBook: (String) -> Unit,
    onAddEntry: () -> Unit,
    onOpenEntryEdit: (String) -> Unit,
    onNavigateBack: () -> Unit,
    uiMessage: String? = null,
    onConsumeMessage: () -> Unit = {},
) {
    val palette = rememberSettingsPalette()
    val outlineColors = rememberSettingsOutlineColors().copy(focusedLabelColor = palette.title)
    var renameText by rememberSaveable(bookName) { mutableStateOf(bookName) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var showManagement by rememberSaveable { mutableStateOf(false) }

    // 书详情页只处理"重命名"关键字的消息；"删除"由列表页显示
    val snackbarHostState = rememberSettingsSnackbarHostState(
        message = uiMessage?.takeIf { it.contains("重命名") },
        onConsumeMessage = onConsumeMessage,
    )
    // 不会在本页显示的 message 3s 后兜底 consume（见 T15-C8）
    LaunchedEffect(uiMessage) {
        val message = uiMessage ?: return@LaunchedEffect
        if (!message.contains("重命名")) {
            delay(3_000)
            onConsumeMessage()
        }
    }

    // 书名在上游（重命名成功后）变化时把输入框同步回退
    LaunchedEffect(bookName) {
        if (renameText != bookName) renameText = bookName
    }

    val trimmedRename = renameText.trim()
    val canRename = !isSaving && trimmedRename.isNotBlank() && trimmedRename != bookName

    Scaffold(
        topBar = {
            SettingsTopBar(
                title = "世界书详情",
                onNavigateBack = onNavigateBack,
                actionLabel = "新增",
                onAction = onAddEntry,
                horizontalPadding = NarraUi.space16,
                titleMaxLines = Int.MAX_VALUE,
                titleIsHeading = true,
            )
        },
        snackbarHost = { TopAppSnackbarHost(hostState = snackbarHostState) },
        containerColor = palette.background,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("worldbook_detail")
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = NarraUi.space16,
                top = NarraUi.space4,
                end = NarraUi.space16,
                bottom = NarraUi.space32,
            ),
            verticalArrangement = Arrangement.spacedBy(NarraUi.space16),
        ) {
            item {
                SettingsPageIntro(
                    title = bookName.ifBlank { "未命名世界书" },
                    contentPadding = PaddingValues(
                        horizontal = NarraUi.space4,
                        vertical = NarraUi.space8
                    ),
                    titleMaxLines = Int.MAX_VALUE,
                    titleIsHeading = true,
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${entries.size} 条条目",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.body
                    )
                    TextButton(
                        onClick = { showManagement = !showManagement },
                        modifier = Modifier.sizeIn(
                            minWidth = NarraMinimumTouchTarget,
                            minHeight = NarraMinimumTouchTarget
                        )
                            .semantics {
                                stateDescription = if (showManagement) "已展开" else "已收起"
                            },
                        colors = ButtonDefaults.textButtonColors(contentColor = palette.title),
                    ) {
                        Text(if (showManagement) "收起管理" else "管理世界书")
                    }
                }
            }

            if (showManagement) {
                item {
                    Column(
                        modifier = Modifier.padding(vertical = NarraUi.space8),
                        verticalArrangement = Arrangement.spacedBy(NarraUi.space12),
                    ) {
                        OutlinedTextField(
                            value = renameText,
                            onValueChange = { renameText = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("书名") },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (canRename) onRenameBook(bookId, trimmedRename)
                                },
                            ),
                            colors = outlineColors,
                            shape = NarraShapes.medium,
                        )
                        AnimatedSettingButton(
                            text = if (isSaving) "处理中…" else "保存书名",
                            onClick = { onRenameBook(bookId, trimmedRename) },
                            enabled = canRename,
                            isPrimary = true,
                            shape = NarraShapes.medium,
                            contentPadding = PaddingValues(
                                horizontal = NarraUi.space16,
                                vertical = NarraUi.space12
                            ),
                            colors = narraButtonColors().copy(
                                disabledContainerColor = palette.surfaceTint,
                                disabledContentColor = palette.body,
                            ),
                            disabledAlpha = 1f,
                        )
                        AnimatedSettingButton(
                            text = "删除整本世界书",
                            onClick = { showDeleteDialog = true },
                            enabled = !isSaving && entries.isNotEmpty(),
                            isPrimary = false,
                            shape = NarraShapes.medium,
                            contentPadding = PaddingValues(
                                horizontal = NarraUi.space16,
                                vertical = NarraUi.space12
                            ),
                            colors = narraOutlinedButtonColors().copy(
                                contentColor = palette.title,
                                disabledContainerColor = palette.surfaceTint,
                                disabledContentColor = palette.body,
                            ),
                            disabledAlpha = 1f,
                        )
                        Text(
                            "删除整本会同时移除所有条目，无法恢复。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            if (entries.isEmpty()) {
                item {
                    SettingsHintCard(
                        title = "这本世界书当前没有条目",
                        body = "暂无内容",
                        containerColor = palette.accentSoft,
                        contentColor = palette.accent,
                        shape = NarraShapes.medium,
                        contentPadding = PaddingValues(NarraUi.space16),
                    )
                }
            } else {
                items(entries, key = { it.id }) { entry ->
                    WorldBookEntryCard(
                        entry = entry,
                        onClick = { onOpenEntryEdit(entry.id) },
                    )
                }
            }
        }
    }

    if (showDeleteDialog) {
        NarraAlertDialog(
            title = "删除整本世界书",
            message = "将删除《$bookName》里的 ${entries.size} 条条目，这个操作不可撤销。",
            confirmLabel = "确认删除",
            dismissLabel = "取消",
            isDestructive = true,
            shape = NarraShapes.large,
            actionContentColor = palette.title,
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                showDeleteDialog = false
                onDeleteBook(bookId)
            },
        )
    }
}
