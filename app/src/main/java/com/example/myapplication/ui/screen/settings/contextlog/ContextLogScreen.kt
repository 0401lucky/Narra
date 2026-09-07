package com.example.myapplication.ui.screen.settings.contextlog

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Brain
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.Lucide
import com.example.myapplication.model.ContextGovernanceSnapshot
import com.example.myapplication.system.json.AppJson
import com.example.myapplication.system.security.SensitiveTextRedactor
import com.example.myapplication.ui.component.ContextLogDetailBody
import com.example.myapplication.ui.component.NarraAlertDialog
import com.example.myapplication.ui.component.NarraIconButton
import com.example.myapplication.ui.component.copyPlainTextToClipboard
import com.example.myapplication.viewmodel.ContextLogViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ContextLogScreen(
    viewModel: ContextLogViewModel,
    contextLogEnabled: Boolean,
    contextLogCapacity: Int,
    onUpdateContextLogEnabled: (Boolean) -> Unit,
    onUpdateContextLogCapacity: (Int) -> Unit,
    onNavigateBack: () -> Unit,
    onOpenMemoryManagement: () -> Unit,
) {
    val snapshots by viewModel.snapshots.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val clipboardScope = rememberCoroutineScope()
    var pendingExport by remember { mutableStateOf<ContextGovernanceSnapshot?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val snapshot = pendingExport
        pendingExport = null
        if (uri == null || snapshot == null) return@rememberLauncherForActivityResult
        runCatching {
            val stream = requireNotNull(context.contentResolver.openOutputStream(uri)) {
                "无法打开导出文件，请重新选择保存位置"
            }
            stream.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(AppJson.gson.toJson(snapshot))
            }
            Toast.makeText(context, "已导出上下文日志", Toast.LENGTH_SHORT).show()
        }.onFailure { error ->
            val message = SensitiveTextRedactor.throwableMessageForUi(
                throwable = error,
                fallback = "无法保存日志，请重新选择保存位置",
            )
            Toast.makeText(context, "导出失败：$message", Toast.LENGTH_LONG).show()
        }
    }

    ContextLogContent(
        snapshots = snapshots,
        contextLogEnabled = contextLogEnabled,
        contextLogCapacity = contextLogCapacity,
        onUpdateContextLogEnabled = onUpdateContextLogEnabled,
        onUpdateContextLogCapacity = onUpdateContextLogCapacity,
        onNavigateBack = onNavigateBack,
        onOpenMemoryManagement = onOpenMemoryManagement,
        onDelete = { viewModel.delete(it.id) },
        onExport = { snapshot ->
            pendingExport = snapshot
            exportLauncher.launch(suggestedFileName(snapshot))
        },
        onCopy = { snapshot ->
            clipboardScope.copyPlainTextToClipboard(
                clipboard = clipboard,
                label = "context-governance",
                text = snapshot.rawDebugDump,
            )
            Toast.makeText(context, "原始上下文已复制", Toast.LENGTH_SHORT).show()
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ContextLogContent(
    snapshots: List<ContextGovernanceSnapshot>,
    contextLogEnabled: Boolean,
    contextLogCapacity: Int,
    onUpdateContextLogEnabled: (Boolean) -> Unit,
    onUpdateContextLogCapacity: (Int) -> Unit,
    onNavigateBack: () -> Unit,
    onOpenMemoryManagement: () -> Unit,
    onDelete: (ContextGovernanceSnapshot) -> Unit,
    onExport: (ContextGovernanceSnapshot) -> Unit,
    onCopy: (ContextGovernanceSnapshot) -> Unit,
) {
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedSnapshot = snapshots.firstOrNull { it.id == selectedId }
    val pendingDelete = snapshots.firstOrNull { it.id == pendingDeleteId }
    val listState = rememberLazyListState()

    BackHandler(enabled = selectedSnapshot != null) { selectedId = null }

    Scaffold(
        modifier = Modifier.testTag("context_log_screen"),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (selectedSnapshot == null) "上下文日志" else "日志详情",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    NarraIconButton(onClick = {
                        if (selectedSnapshot != null) selectedId = null else onNavigateBack()
                    }) {
                        Icon(Lucide.ArrowLeft, contentDescription = "返回", modifier = Modifier.size(24.dp))
                    }
                },
                actions = {
                    if (selectedSnapshot != null) {
                        NarraIconButton(onClick = onOpenMemoryManagement) {
                            Icon(
                                imageVector = Lucide.Brain,
                                contentDescription = "打开长记忆管理",
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        NarraIconButton(
                            enabled = selectedSnapshot.rawDebugDump.isNotBlank(),
                            onClick = { onCopy(selectedSnapshot) },
                        ) {
                            Icon(
                                imageVector = Lucide.Copy,
                                contentDescription = "复制原文",
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        if (selectedSnapshot == null) {
            ContextLogListContent(
                snapshots = snapshots,
                enabled = contextLogEnabled,
                capacity = contextLogCapacity,
                onEnabledChange = onUpdateContextLogEnabled,
                onCapacityChange = onUpdateContextLogCapacity,
                modifier = Modifier.fillMaxSize().padding(padding),
                listState = listState,
                onSelect = { selectedId = it.id },
                onDelete = { pendingDeleteId = it.id },
                onExport = onExport,
            )
        } else {
            ContextLogDetailBody(
                snapshot = selectedSnapshot,
                rawDebugDump = selectedSnapshot.rawDebugDump,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .testTag("context_log_detail"),
            )
        }
    }

    if (pendingDelete != null) {
        NarraAlertDialog(
            title = "删除上下文日志",
            message = "这条上下文日志将被删除，无法恢复。其他日志和对话内容会保留。",
            confirmLabel = "删除日志",
            isDestructive = true,
            onDismiss = { pendingDeleteId = null },
            onConfirm = {
                pendingDeleteId = null
                onDelete(pendingDelete)
            },
        )
    }
}

private fun suggestedFileName(snapshot: ContextGovernanceSnapshot): String {
    val timestamp = if (snapshot.generatedAt > 0L) {
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date(snapshot.generatedAt))
    } else {
        "snapshot"
    }
    return "context-log-$timestamp.json"
}
