package com.example.myapplication.ui.screen.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.example.myapplication.ui.component.TopAppSnackbarHost
import com.example.myapplication.system.security.SensitiveTextRedactor
import com.example.myapplication.ui.theme.NarraMinimumTouchTarget
import com.example.myapplication.ui.theme.NarraShapes
import com.example.myapplication.ui.theme.NarraUi
import com.example.myapplication.viewmodel.ContextImportPreview
import com.example.myapplication.viewmodel.ContextImportPayload
import com.example.myapplication.viewmodel.ContextTransferSection
import com.example.myapplication.viewmodel.ContextTransferUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream

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
    var showImportSourceDialog by rememberSaveable { mutableStateOf(false) }
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
                    snackbarHostState.showSnackbar(
                        SensitiveTextRedactor.throwableMessageForUi(
                            throwable = throwable,
                            fallback = "导出失败",
                        ),
                    )
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
                snackbarHostState.showSnackbar(
                    SensitiveTextRedactor.throwableMessageForUi(
                        throwable = throwable,
                        fallback = "导入失败",
                    ),
                )
            }
        }
    }

    val characterCardImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                readImportPayload(context, uri)
            }.onSuccess { payload ->
                onPreviewImportPayload(payload, pendingImportSection)
            }.onFailure { throwable ->
                snackbarHostState.showSnackbar(
                    SensitiveTextRedactor.throwableMessageForUi(
                        throwable = throwable,
                        fallback = "导入失败",
                    ),
                )
            }
        }
    }

    Scaffold(
        topBar = {
            SettingsTopBar(
                title = "资料库",
                onNavigateBack = onNavigateBack,
                horizontalPadding = NarraUi.space16,
                titleMaxLines = Int.MAX_VALUE,
                titleIsHeading = true,
            )
        },
        containerColor = palette.background,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
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
                        title = "管理 Narra 本地资源包",
                        summary = "角色包、世界书包、记忆档案包和预设包都在这里导入导出。只处理本地资料，不包含 API Key。",
                        contentPadding = PaddingValues(vertical = NarraUi.space8),
                        titleMaxLines = Int.MAX_VALUE,
                        titleIsHeading = true,
                        helpDialogShape = NarraShapes.large,
                        helpActionContentColor = palette.title,
                        helpActionMinSize = NarraMinimumTouchTarget,
                    )
                }

                item { TransferSectionHeader("资料库概览") }
                item {
                    SettingsGroup(
                        shape = NarraShapes.medium,
                        contentPadding = PaddingValues(vertical = NarraUi.space8)
                    ) {
                        TransferInfoRow(
                            title = "角色包",
                            supportingText = "当前共有 ${uiState.customAssistantCount} 条",
                        )
                        SettingsGroupDivider(contentPadding = PaddingValues(horizontal = NarraUi.space16))
                        TransferInfoRow(
                            title = "世界书条目",
                            supportingText = "当前共有 ${uiState.worldBookCount} 条",
                        )
                        SettingsGroupDivider(contentPadding = PaddingValues(horizontal = NarraUi.space16))
                        TransferInfoRow(
                            title = "记忆档案",
                            supportingText = "当前共有 ${uiState.memoryCount} 条",
                        )
                        SettingsGroupDivider(contentPadding = PaddingValues(horizontal = NarraUi.space16))
                        TransferInfoRow(
                            title = "对话摘要",
                            supportingText = "当前共有 ${uiState.summaryCount} 条",
                        )
                        SettingsGroupDivider(contentPadding = PaddingValues(horizontal = NarraUi.space16))
                        TransferInfoRow(
                            title = "自定义预设",
                            supportingText = "当前共有 ${uiState.presetCount} 条",
                        )
                    }
                }

                item { TransferSectionHeader("资源包") }

                item {
                    TransferSectionGroup(
                        title = "资料库备份",
                        description = "统一迁移角色包、世界书、记忆档案、剧情摘要和自定义预设",
                        isBusy = uiState.isBusy,
                        onExport = {
                            pendingExportSection = ContextTransferSection.ALL
                            exportLauncher.launch(ContextTransferSection.ALL.exportFileName)
                        },
                        onImport = {
                            pendingImportSection = ContextTransferSection.ALL
                            showImportSourceDialog = true
                        },
                    )
                }
                item {
                    TransferSectionGroup(
                        title = "角色包",
                        description = "支持本应用角色卡 JSON 和 Tavern PNG 角色卡",
                        isBusy = uiState.isBusy,
                        onExport = {
                            pendingExportSection = ContextTransferSection.ASSISTANTS
                            exportLauncher.launch(ContextTransferSection.ASSISTANTS.exportFileName)
                        },
                        onImport = {
                            pendingImportSection = ContextTransferSection.ASSISTANTS
                            showImportSourceDialog = true
                        },
                    )
                }
                item {
                    TransferSectionGroup(
                        title = "世界书",
                        description = "支持本应用 JSON、独立世界书 JSON，以及从角色卡 JSON/PNG 中抽取世界书",
                        isBusy = uiState.isBusy,
                        onExport = {
                            pendingExportSection = ContextTransferSection.WORLD_BOOK
                            exportLauncher.launch(ContextTransferSection.WORLD_BOOK.exportFileName)
                        },
                        onImport = {
                            pendingImportSection = ContextTransferSection.WORLD_BOOK
                            showImportSourceDialog = true
                        },
                    )
                }
                item {
                    TransferSectionGroup(
                        title = "记忆档案包",
                        description = "单独迁移记忆条目和对话摘要",
                        isBusy = uiState.isBusy,
                        onExport = {
                            pendingExportSection = ContextTransferSection.MEMORY
                            exportLauncher.launch(ContextTransferSection.MEMORY.exportFileName)
                        },
                        onImport = {
                            pendingImportSection = ContextTransferSection.MEMORY
                            importLauncher.launch(IMPORT_TEXT_MIME_TYPES)
                        },
                    )
                }
                item {
                    TransferSectionGroup(
                        title = "预设包",
                        description = "迁移自定义提示词工程预设；内置预设不会被打包覆盖",
                        isBusy = uiState.isBusy,
                        onExport = {
                            pendingExportSection = ContextTransferSection.PRESETS
                            exportLauncher.launch(ContextTransferSection.PRESETS.exportFileName)
                        },
                        onImport = {
                            pendingImportSection = ContextTransferSection.PRESETS
                            importLauncher.launch(IMPORT_TEXT_MIME_TYPES)
                        },
                    )
                }

                uiState.importPreview?.let { preview ->
                    item { TransferSectionHeader("导入预览") }
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
            TopAppSnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.TopCenter),
                contentTopInset = innerPadding.calculateTopPadding(),
            )
        }
    }

    if (showImportSourceDialog) {
        ImportSourceChooserDialog(
            onPickImage = {
                showImportSourceDialog = false
                characterCardImageLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            onPickFile = {
                showImportSourceDialog = false
                importLauncher.launch(IMPORT_TEXT_MIME_TYPES)
            },
            onDismiss = { showImportSourceDialog = false },
        )
    }
}

@Composable
private fun ImportSourceChooserDialog(
    onPickImage: () -> Unit,
    onPickFile: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = rememberSettingsPalette()
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = NarraShapes.large,
        containerColor = palette.surface,
        titleContentColor = palette.title,
        textContentColor = palette.body,
        title = { Text("选择导入来源", modifier = Modifier.semantics { heading() }) },
        text = {
            Text(
                "从相册选 Tavern PNG 角色卡，或从文件选 JSON / 文本。",
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(NarraUi.space4, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(NarraUi.space4),
            ) {
                listOf(
                    "从相册（PNG）" to onPickImage,
                    "从文件（JSON）" to onPickFile,
                    "取消" to onDismiss
                ).forEach { (label, action) ->
                    TextButton(
                        onClick = action,
                        modifier = Modifier.sizeIn(
                            minWidth = NarraMinimumTouchTarget,
                            minHeight = NarraMinimumTouchTarget
                        ),
                        colors = ButtonDefaults.textButtonColors(contentColor = palette.title),
                        contentPadding = PaddingValues(
                            horizontal = NarraUi.space12,
                            vertical = NarraUi.space8
                        ),
                    ) { Text(label) }
                }
            }
        },
    )
}

@Composable
private fun TransferSectionGroup(
    title: String,
    description: String,
    isBusy: Boolean,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    val palette = rememberSettingsPalette()
    SettingsGroup(shape = NarraShapes.medium, contentPadding = PaddingValues()) {
        Column(
            modifier = Modifier.padding(NarraUi.space16),
            verticalArrangement = Arrangement.spacedBy(NarraUi.space12),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(NarraUi.space4)
                ) {
                    Text(
                        title,
                        modifier = Modifier.semantics { heading() },
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.title
                    )
                    Text(
                        "导出或导入本地文件",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.body
                    )
                }
                SettingsHelpButton(
                    title = title,
                    description = description,
                    dialogShape = NarraShapes.large,
                    actionContentColor = palette.title,
                    actionMinSize = NarraMinimumTouchTarget,
                )
            }
            TransferActions(isBusy = isBusy, onExport = onExport, onImport = onImport)
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
    SettingsGroup(shape = NarraShapes.medium, contentPadding = PaddingValues()) {
        Column(
            modifier = Modifier.padding(NarraUi.space16),
            verticalArrangement = Arrangement.spacedBy(NarraUi.space12),
        ) {
            TransferInfoRow(
                title = "来源：${preview.sourceLabel}",
                supportingText = "范围：${preview.section.label}",
            )
            if (preview.noticeMessages.isNotEmpty()) {
                SettingsHintCard(
                    title = "安全上限提示",
                    body = preview.noticeMessages.joinToString("\n") { "• $it" },
                    containerColor = palette.accentSoft,
                    contentColor = palette.accent,
                    shape = NarraShapes.medium,
                    contentPadding = PaddingValues(NarraUi.space16),
                )
            }
            if (preview.assistantCount > 0) {
                TransferInfoRow(
                    title = "角色卡",
                    supportingText = "${preview.assistantCount} 条",
                )
            }
            if (preview.worldBookCount > 0) {
                TransferInfoRow(
                    title = "世界书",
                    supportingText = "${preview.worldBookCount} 条",
                )
            }
            if (preview.memoryCount > 0) {
                TransferInfoRow(
                    title = "记忆",
                    supportingText = "${preview.memoryCount} 条",
                )
            }
            if (preview.summaryCount > 0) {
                TransferInfoRow(
                    title = "摘要",
                    supportingText = "${preview.summaryCount} 条",
                )
            }
            if (preview.presetCount > 0) {
                TransferInfoRow(
                    title = "预设",
                    supportingText = "${preview.presetCount} 条",
                )
            }
            if (preview.conflicts.isNotEmpty()) {
                SettingsHintCard(
                    title = "检测到 ${preview.conflicts.size} 条冲突",
                    body = preview.conflicts.take(5)
                        .joinToString("\n") { "• ${it.typeLabel} / ${it.title}" },
                    containerColor = palette.accentSoft,
                    contentColor = palette.accent,
                    shape = NarraShapes.medium,
                    contentPadding = PaddingValues(NarraUi.space16),
                )
            }
            TransferActionButton(
                text = if (isBusy) "处理中…" else "确认导入",
                onClick = onConfirmImport,
                enabled = !isBusy,
                isPrimary = true,
            )
            TransferActionButton(
                text = "取消",
                onClick = onDismiss,
                enabled = !isBusy,
                isPrimary = false,
            )
        }
    }
}

@Composable
private fun TransferSectionHeader(title: String) {
    SettingsSectionHeader(
        title = title,
        description = "",
        contentPadding = PaddingValues(vertical = NarraUi.space4),
        horizontalSpacing = NarraUi.space8,
        titleMaxLines = Int.MAX_VALUE,
        titleIsHeading = true,
        titleColor = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun TransferInfoRow(title: String, supportingText: String) {
    SettingsListRow(
        title = title,
        supportingText = supportingText,
        onClick = null,
        showArrow = false,
        shape = NarraShapes.medium,
        contentPadding = PaddingValues(horizontal = NarraUi.space16, vertical = NarraUi.space12),
        horizontalSpacing = NarraUi.space12,
        textSpacing = NarraUi.space4,
        titleMaxLines = Int.MAX_VALUE,
        supportingTextMaxLines = Int.MAX_VALUE,
    )
}

@Composable
private fun TransferActions(isBusy: Boolean, onExport: () -> Unit, onImport: () -> Unit) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val label = if (isBusy) "处理中…" else "导出"
    val textWidth = with(density) {
        measurer.measure(
            AnnotatedString(label),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
        ).size.width.toDp()
    }
    // 根据实际字体和可用宽度换行，图标宽度与按钮内边距也计入测量。
    val buttonWidth = textWidth + 24.dp + NarraUi.space8 + NarraUi.space16 * 2
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val stacked = (maxWidth - NarraUi.space12) / 2 < buttonWidth
        FlowRow(
            modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
            horizontalArrangement = Arrangement.spacedBy(NarraUi.space12),
            verticalArrangement = Arrangement.spacedBy(NarraUi.space12),
            maxItemsInEachRow = if (stacked) 1 else 2,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                TransferActionButton(
                    text = if (isBusy) "处理中…" else "导出",
                    onClick = onExport,
                    enabled = !isBusy,
                    isPrimary = true,
                    leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                TransferActionButton(
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

@Composable
private fun TransferActionButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    isPrimary: Boolean,
    leadingIcon: @Composable (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    AnimatedSettingButton(
        text = text,
        onClick = onClick,
        enabled = enabled,
        isPrimary = isPrimary,
        leadingIcon = leadingIcon,
        shape = NarraShapes.medium,
        contentPadding = PaddingValues(horizontal = NarraUi.space16, vertical = NarraUi.space12),
        disabledAlpha = 1f,
        colors = if (isPrimary) {
            ButtonDefaults.buttonColors(
                containerColor = scheme.primary,
                contentColor = scheme.onPrimary,
                disabledContainerColor = scheme.surfaceVariant,
                disabledContentColor = scheme.onSurfaceVariant,
            )
        } else {
            ButtonDefaults.outlinedButtonColors(
                contentColor = scheme.onSurface,
                disabledContentColor = scheme.onSurfaceVariant
            )
        },
    )
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
            input.readBytesWithLimit(MAX_IMPORT_FILE_BYTES)
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
    val cursor = context.contentResolver.query(
        uri,
        arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null
    )
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

private fun InputStream.readBytesWithLimit(maxBytes: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var totalBytes = 0
    while (true) {
        val read = read(buffer)
        if (read == -1) break
        totalBytes += read
        if (totalBytes > maxBytes) {
            throw IllegalArgumentException("导入文件过大，请选择不超过 ${maxBytes / 1024 / 1024}MB 的文件")
        }
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}

private val IMPORT_TEXT_MIME_TYPES = arrayOf("application/json", "text/plain")
private const val MAX_IMPORT_FILE_BYTES = 16 * 1024 * 1024
