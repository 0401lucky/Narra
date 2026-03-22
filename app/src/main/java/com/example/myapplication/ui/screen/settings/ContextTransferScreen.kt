package com.example.myapplication.ui.screen.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.myapplication.ui.component.AppSnackbarHost
import com.example.myapplication.viewmodel.ContextImportPreview
import com.example.myapplication.viewmodel.ContextImportPayload
import com.example.myapplication.viewmodel.ContextTransferSection
import com.example.myapplication.viewmodel.ContextTransferUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ContextTransferScreen(
    uiState: ContextTransferUiState,
    onExportJson: (ContextTransferSection, onSuccess: (String, String) -> Unit) -> Unit,
    onPreviewImportPayload: (ContextImportPayload, ContextTransferSection) -> Unit,
    onConfirmImport: () -> Unit,
    onDismissImportPreview: () -> Unit,
    onConsumeMessage: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val palette = rememberSettingsPalette()
    var pendingImportSection by rememberSaveable { mutableStateOf(ContextTransferSection.ALL) }
    var pendingExportSection by rememberSaveable { mutableStateOf(ContextTransferSection.ALL) }
    val snackbarHostState = rememberSettingsSnackbarHostState(
        message = uiState.message,
        onConsumeMessage = onConsumeMessage,
    )

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        onExportJson(pendingExportSection) { json, _ ->
            scope.launch {
                runCatching {
                    writeTextToUri(context, uri, json)
                }.onSuccess {
                    snackbarHostState.showSnackbar("上下文数据已导出")
                }.onFailure { throwable ->
                    snackbarHostState.showSnackbar(throwable.message ?: "导出失败")
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                readImportPayload(context, uri)
            }.onSuccess { payload ->
                onPreviewImportPayload(payload, pendingImportSection)
            }.onFailure { throwable ->
                snackbarHostState.showSnackbar(throwable.message ?: "导入失败")
            }
        }
    }

    Scaffold(
        topBar = {
            SettingsTopBar(
                title = "资料导入导出",
                onNavigateBack = onNavigateBack,
            )
        },
        snackbarHost = { AppSnackbarHost(hostState = snackbarHostState) },
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                SettingsPageIntro(
                    overline = "JSON 数据包",
                    title = "统一迁移角色卡、世界书、记忆与摘要",
                    summary = "采用合并导入，不会删除现有数据。相同 ID 的条目会被导入内容覆盖。",
                )
            }

            // Current Data Overview
            item { SettingsSectionHeader("当前数据概览", "") }
            item {
                SettingsGroup {
                    SettingsListRow(
                        title = "自定义角色卡",
                        supportingText = "当前共有 ${uiState.customAssistantCount} 条",
                        enabled = false,
                        showArrow = false,
                    )
                    SettingsGroupDivider()
                    SettingsListRow(
                        title = "世界书条目",
                        supportingText = "当前共有 ${uiState.worldBookCount} 条",
                        enabled = false,
                        showArrow = false,
                    )
                    SettingsGroupDivider()
                    SettingsListRow(
                        title = "记忆条目",
                        supportingText = "当前共有 ${uiState.memoryCount} 条",
                        enabled = false,
                        showArrow = false,
                    )
                    SettingsGroupDivider()
                    SettingsListRow(
                        title = "对话摘要",
                        supportingText = "当前共有 ${uiState.summaryCount} 条",
                        enabled = false,
                        showArrow = false,
                    )
                }
            }

            // Transfer Actions — each section is its own clean group
            item { SettingsSectionHeader("导入导出操作", "") }

            item {
                TransferSectionGroup(
                    title = "全部资料",
                    description = "统一迁移角色卡、世界书、记忆和摘要",
                    isBusy = uiState.isBusy,
                    onExport = {
                        pendingExportSection = ContextTransferSection.ALL
                        exportLauncher.launch(ContextTransferSection.ALL.exportFileName)
                    },
                    onImport = {
                        pendingImportSection = ContextTransferSection.ALL
                        importLauncher.launch(arrayOf("application/json", "text/plain", "image/png"))
                    },
                )
            }
            item {
                TransferSectionGroup(
                    title = "角色卡",
                    description = "支持本应用角色卡 JSON 和 Tavern PNG 角色卡",
                    isBusy = uiState.isBusy,
                    onExport = {
                        pendingExportSection = ContextTransferSection.ASSISTANTS
                        exportLauncher.launch(ContextTransferSection.ASSISTANTS.exportFileName)
                    },
                    onImport = {
                        pendingImportSection = ContextTransferSection.ASSISTANTS
                        importLauncher.launch(arrayOf("application/json", "text/plain", "image/png"))
                    },
                )
            }
            item {
                TransferSectionGroup(
                    title = "世界书",
                    description = "单独迁移世界书条目",
                    isBusy = uiState.isBusy,
                    onExport = {
                        pendingExportSection = ContextTransferSection.WORLD_BOOK
                        exportLauncher.launch(ContextTransferSection.WORLD_BOOK.exportFileName)
                    },
                    onImport = {
                        pendingImportSection = ContextTransferSection.WORLD_BOOK
                        importLauncher.launch(arrayOf("application/json", "text/plain"))
                    },
                )
            }
            item {
                TransferSectionGroup(
                    title = "记忆与摘要",
                    description = "单独迁移记忆条目和对话摘要",
                    isBusy = uiState.isBusy,
                    onExport = {
                        pendingExportSection = ContextTransferSection.MEMORY
                        exportLauncher.launch(ContextTransferSection.MEMORY.exportFileName)
                    },
                    onImport = {
                        pendingImportSection = ContextTransferSection.MEMORY
                        importLauncher.launch(arrayOf("application/json", "text/plain"))
                    },
                )
            }

            // Import Preview
            uiState.importPreview?.let { preview ->
                item { SettingsSectionHeader("导入预览", "") }
                item {
                    ImportPreviewCard(
                        preview = preview,
                        isBusy = uiState.isBusy,
                        onConfirmImport = onConfirmImport,
                        onDismiss = onDismissImportPreview,
                    )
                }
            }
        }
    }
}

@Composable
private fun TransferSectionGroup(
    title: String,
    description: String,
    isBusy: Boolean,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    SettingsGroup {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SettingsListRow(
                title = title,
                supportingText = description,
                enabled = false,
                showArrow = false,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    AnimatedSettingButton(
                        text = if (isBusy) "处理中…" else "导出",
                        onClick = onExport,
                        enabled = !isBusy,
                        isPrimary = true,
                        leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    AnimatedSettingButton(
                        text = if (isBusy) "处理中…" else "导入",
                        onClick = onImport,
                        enabled = !isBusy,
                        isPrimary = false,
                        leadingIcon = { Icon(Icons.Default.Upload, contentDescription = null) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportPreviewCard(
    preview: ContextImportPreview,
    isBusy: Boolean,
    onConfirmImport: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = rememberSettingsPalette()
    SettingsGroup {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SettingsListRow(
                title = "来源：${preview.sourceLabel}",
                supportingText = "范围：${preview.section.label}",
                enabled = false,
                showArrow = false,
            )
            if (preview.assistantCount > 0) {
                SettingsListRow(
                    title = "角色卡",
                    supportingText = "${preview.assistantCount} 条",
                    enabled = false,
                    showArrow = false,
                )
            }
            if (preview.worldBookCount > 0) {
                SettingsListRow(
                    title = "世界书",
                    supportingText = "${preview.worldBookCount} 条",
                    enabled = false,
                    showArrow = false,
                )
            }
            if (preview.memoryCount > 0) {
                SettingsListRow(
                    title = "记忆",
                    supportingText = "${preview.memoryCount} 条",
                    enabled = false,
                    showArrow = false,
                )
            }
            if (preview.summaryCount > 0) {
                SettingsListRow(
                    title = "摘要",
                    supportingText = "${preview.summaryCount} 条",
                    enabled = false,
                    showArrow = false,
                )
            }
            if (preview.conflicts.isNotEmpty()) {
                SettingsHintCard(
                    title = "检测到 ${preview.conflicts.size} 条冲突",
                    body = preview.conflicts.take(5).joinToString("\n") { "• ${it.typeLabel} / ${it.title}" },
                    containerColor = palette.accentSoft,
                    contentColor = palette.accent,
                )
            }
            AnimatedSettingButton(
                text = if (isBusy) "处理中…" else "确认导入",
                onClick = onConfirmImport,
                enabled = !isBusy,
                isPrimary = true,
            )
            AnimatedSettingButton(
                text = "取消",
                onClick = onDismiss,
                enabled = !isBusy,
                isPrimary = false,
            )
        }
    }
}

private suspend fun writeTextToUri(
    context: Context,
    uri: Uri,
    content: String,
) {
    withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
            writer.write(content)
        } ?: error("无法写入导出文件")
    }
}

private suspend fun readImportPayload(
    context: Context,
    uri: Uri,
): ContextImportPayload {
    return withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mimeType = resolver.getType(uri).orEmpty()
        val fileName = resolveImportFileName(context, uri)
        val bytes = resolver.openInputStream(uri)?.use { input ->
            input.readBytes()
        } ?: error("无法读取导入文件")
        val isImage = mimeType.startsWith("image/") || fileName.endsWith(".png", ignoreCase = true)
        if (isImage) {
            ContextImportPayload(
                fileName = fileName,
                mimeType = mimeType,
                binaryContent = bytes,
            )
        } else {
            ContextImportPayload(
                fileName = fileName,
                mimeType = mimeType,
                textContent = bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF"),
            )
        }
    }
}

private fun resolveImportFileName(
    context: Context,
    uri: Uri,
): String {
    val cursor = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
    cursor?.use {
        if (it.moveToFirst()) {
            val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index >= 0) {
                return it.getString(index).orEmpty()
            }
        }
    }
    return uri.lastPathSegment.orEmpty()
}
