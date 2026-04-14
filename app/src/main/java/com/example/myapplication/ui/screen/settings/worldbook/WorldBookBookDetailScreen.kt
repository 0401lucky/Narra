package com.example.myapplication.ui.screen.settings.worldbook

import com.example.myapplication.ui.component.*

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.WorldBookEntry
import com.example.myapplication.ui.screen.settings.AnimatedSettingButton
import com.example.myapplication.ui.screen.settings.SettingsGroup
import com.example.myapplication.ui.screen.settings.SettingsHintCard
import com.example.myapplication.ui.screen.settings.SettingsPageIntro
import com.example.myapplication.ui.screen.settings.SettingsScreenPadding
import com.example.myapplication.ui.screen.settings.SettingsSectionHeader
import com.example.myapplication.ui.screen.settings.SettingsTopBar
import com.example.myapplication.ui.screen.settings.rememberSettingsOutlineColors
import com.example.myapplication.ui.screen.settings.rememberSettingsPalette

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
) {
    val palette = rememberSettingsPalette()
    val outlineColors = rememberSettingsOutlineColors()
    var renameText by rememberSaveable(bookName) { mutableStateOf(bookName) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            SettingsTopBar(
                title = bookName.ifBlank { "世界书" },
                subtitle = "${entries.size} 条条目",
                onNavigateBack = onNavigateBack,
                actionLabel = "新增",
                onAction = onAddEntry,
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
                bottom = 36.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                SettingsPageIntro(
                    overline = "书内条目",
                    title = "书内条目",
                )
            }

            item {
                SettingsSectionHeader(
                    title = "整本操作",
                    description = "",
                )
            }

            item {
                SettingsGroup {
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedTextField(
                            value = renameText,
                            onValueChange = { renameText = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { androidx.compose.material3.Text("书名") },
                            colors = outlineColors,
                        )
                        AnimatedSettingButton(
                            text = if (isSaving) "处理中…" else "重命名这本书",
                            onClick = {
                                onRenameBook(bookId, renameText)
                            },
                            enabled = !isSaving && renameText.trim().isNotBlank() && renameText.trim() != bookName,
                            isPrimary = true,
                        )
                        AnimatedSettingButton(
                            text = "删除这本书",
                            onClick = { showDeleteDialog = true },
                            enabled = !isSaving && entries.isNotEmpty(),
                            isPrimary = false,
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
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { androidx.compose.material3.Text("删除整本世界书") },
            text = {
                androidx.compose.material3.Text(
                    "将删除《$bookName》里的 ${entries.size} 条条目，这个操作不可撤销。",
                )
            },
            confirmButton = {
                NarraTextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDeleteBook(bookId)
                    },
                ) {
                    androidx.compose.material3.Text("确认删除")
                }
            },
            dismissButton = {
                NarraTextButton(
                    onClick = { showDeleteDialog = false },
                ) {
                    androidx.compose.material3.Text("取消")
                }
            },
        )
    }
}
