package com.example.myapplication.ui.screen.settings.memory

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.myapplication.model.*
import com.example.myapplication.ui.component.NarraAlertDialog
import com.example.myapplication.ui.theme.NarraShapes
import com.example.myapplication.viewmodel.MemoryArchiveUiState

internal fun LazyListScope.archiveReceiptItems(
    state: MemoryArchiveUiState,
    callbacks: MemoryArchiveCallbacks
) {
    item {
        ArchiveConversationPicker(state, callbacks)
        Text(
            "默认显示这段原文中最新的助手回复；没有回执时不会退回上一轮。",
            style = MaterialTheme.typography.bodySmall
        )
        if (state.messagesLoading) Text("正在读取回合…")
        state.messagesError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        ArchiveChoices(
            (state.messages + listOfNotNull(state.latestRoundMessage)).distinctBy { it.message.id }
            .filter { it.message.role == MessageRole.ASSISTANT }
            .sortedWith(compareByDescending<MemoryArchiveMessage> { it.message.createdAt }.thenByDescending { it.message.id }),
            { it.message.id },
            { "${archiveDate(it.message.createdAt)} · ${it.message.content.take(20)}" },
            state.selectedMessageId,
            callbacks.onSelectMessage,
            "memory_round"
        )
        if (state.olderMessageOffset > 0) ArchiveAction(
            "载入更早回合",
            callbacks.onLoadOlderMessages,
            !state.messagesLoading
        )
    }
    item {
        val selected =
            (state.messages + listOfNotNull(state.latestRoundMessage)).find { it.message.id == state.selectedMessageId }?.message
        if (selected != null) {
            SelectionContainer {
                Text(
                    "本轮消息 ID：${selected.id}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            SelectionContainer {
                Text(
                    "请求 ID：${selected.memoryRecallRequestId.ifBlank { "无" }}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            val persona =
                (state.messages + listOfNotNull(state.latestRoundMessage)).find { it.message.id == selected.id }?.personaKey
            if (persona != null && persona != state.context?.personaKey) Text(
                "这条最新回复属于另一历史身份，当前所选身份不会借用旧轮回执。",
                style = MaterialTheme.typography.bodySmall
            )
        }
        state.receiptError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.receiptLoading) Text("正在读取持久回执…")
        else if (state.receipt == null) Text(
            "本轮无记录",
            modifier = Modifier.testTag("memory_receipt_missing")
        )
    }
    state.receipt?.let { receipt ->
        item {
            ArchiveCard(tag = "memory_receipt") {
                Text(receipt.status.label(), style = MaterialTheme.typography.titleMedium)
                Text("准备：${archiveDate(receipt.createdAt)} · 发出：${archiveDate(receipt.sentAt)}")
                SelectionContainer { Text("本轮 ID：${receipt.requestId}") }
                Text(
                    "身份：${receipt.context.personaKey}",
                    style = MaterialTheme.typography.bodySmall
                )
                if (receipt.status == MemoryRecallStatus.FAILED) Text("请求失败，已选材料不等于实际发送。")
                Text("选中 ${receipt.selected.size} 项 · 实际发送 ${receipt.sent.size} 项 · 省略 ${receipt.omitted.size} 项")
            }
        }
        item { Text("选中材料", style = MaterialTheme.typography.titleSmall) }
        items(
            receipt.selected.withIndex().toList(),
            key = { "selected:${it.index}" }) { (_, ref) -> ReceiptRef(ref) }
        item { Text("实际发送", style = MaterialTheme.typography.titleSmall) }
        items(
            receipt.sent.withIndex().toList(),
            key = { "sent:${it.index}" }) { (_, ref) -> ReceiptRef(ref) }
        item { Text("省略材料", style = MaterialTheme.typography.titleSmall) }
        items(
            receipt.omitted.withIndex().toList(),
            key = { "omitted:${it.index}" }) { (_, omission) ->
            ReceiptRef(omission.ref, omission.reason.label)
        }
        if (receipt.transmissions.isNotEmpty()) item {
            Text("发送尝试", style = MaterialTheme.typography.titleSmall)
            receipt.transmissions.forEach { Text("第 ${it.sequence} 次：${it.status.label()} · ${it.materials.size} 项") }
        }
    }
}

@Composable
private fun ReceiptRef(ref: MemoryMaterialRef, omission: String? = null) {
    ArchiveCard {
        SelectionContainer { Text("${ref.kind.label()} · ${ref.id} · 版本 ${ref.revision}") }
        Text(
            "所列正文范围：${ref.spanStart}–${ref.spanEnd}",
            style = MaterialTheme.typography.bodySmall
        )
        omission?.let { Text(it) }
    }
}

@Composable
private fun ArchiveConversationPicker(
    state: MemoryArchiveUiState,
    callbacks: MemoryArchiveCallbacks
) {
    Text("原始对话", style = MaterialTheme.typography.titleSmall)
    ArchiveChoices(
        state.conversations,
        { it.id },
        { "${it.title.ifBlank { "未命名对话" }}${if (it.archived) " · 已归档" else ""}${if (!it.exists) " · 原文缺失" else ""}" },
        state.conversationId,
        callbacks.onSelectConversation,
        "memory_conversation"
    )
    val conversation = state.conversations.find { it.id == state.conversationId }
    if (conversation != null && conversation.exists) ArchiveAction("只读查看整段原文", {
        state.context?.let { callbacks.onReadOriginal(conversation.id, it.personaKey) }
    })
    if (conversation == null) Text("当前档案没有可读取的原始对话。")
}

internal fun LazyListScope.archiveProcessingItems(
    state: MemoryArchiveUiState,
    callbacks: MemoryArchiveCallbacks,
    characterAvailable: Boolean,
    vectorConfigured: Boolean
) {
    item {
        Text("整理控制", style = MaterialTheme.typography.titleMedium)
        val control = state.processingControl
        if (control != null) ArchiveAction(
            if (control.userPaused) "解除空间暂停" else "暂停空间处理",
            {
                callbacks.onSetPaused("", !control.userPaused)
            },
            !state.isSaving
        )
        Text(if (control?.userPaused == true) "此空间已暂停。已收到的产物和任务进度保留。" else "继续按任务 ID 复用已保存进度；未请求的后续批次仍可能产生费用。")
        if (!characterAvailable) Text("角色已移除，档案仍可查看和清理，不能发起模型整理。")
        if (state.isWorking) ArchiveAction("取消本次操作", callbacks.onCancelWork)
        state.indexError?.let {
            Text(
                "索引状态读取失败：$it",
                color = MaterialTheme.colorScheme.error
            )
        }
        state.indexProgress?.let { progress ->
            var rebuildConfirm by rememberSaveable(
                state.context?.spaceId,
                state.context?.personaKey
            ) { mutableStateOf(false) }
            var retryUncertain by rememberSaveable(
                state.context?.spaceId,
                state.context?.personaKey
            ) { mutableStateOf(false) }
            Text("向量索引", style = MaterialTheme.typography.titleSmall)
            if (!vectorConfigured) Text("向量尚未启用，可在模型设置配置独立向量模型。")
            else if (!state.indexCanRebuild) Text("当前档案没有活跃对话，暂不可重建；本地资料仍可查看。")
            if (progress.isEmpty()) Text("当前配置没有索引记录。")
            progress.forEach { index ->
                Text(
                    "${
                        when (index.status) {
                            "READY" -> "就绪"; "PARTIAL" -> "部分就绪"; "IMPORTED_PENDING" -> "导入资料待重建"; "STALE" -> "需要重建"; "BUILDING" -> "构建中"; "FAILED" -> "失败"; else -> "待处理"
                        }
                    } · ${index.completedChunks}/${index.totalChunks} 段${if (index.active) " · 当前使用" else ""}"
                )
                if (index.pendingImportedRecords > 0) Text("还有 ${index.pendingImportedRecords} 条导入记忆需明确重建。")
                if (index.requiresManualRetry) Text("此索引需要明确重试，读取档案不会重新请求模型。")
            }
            ArchiveAction(
                "重建向量索引",
                { retryUncertain = false; rebuildConfirm = true },
                vectorConfigured && state.indexCanRebuild && !state.isWorking && characterAvailable
            )
            if (rebuildConfirm) NarraAlertDialog(
                title = "重建此空间的向量索引",
                message = "将使用当前向量模型重新构建索引，可能产生费用。原记忆正文保留。",
                onDismiss = { rebuildConfirm = false },
                onConfirm = { rebuildConfirm = false; callbacks.onRebuildIndex(retryUncertain) },
                shape = NarraShapes.large, actionContentColor = MaterialTheme.colorScheme.onSurface,
                content = {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(retryUncertain, { retryUncertain = it })
                        Text("即使旧请求费用未知，也明确重新请求")
                    }
                },
            )
        }
    }
    item { Text("任务历史", style = MaterialTheme.typography.titleMedium) }
    items(state.jobs, key = { "job:${it.id}" }) { job ->
        ArchiveCard {
            Text(job.status.label(), style = MaterialTheme.typography.titleSmall)
            SelectionContainer {
                Text(
                    "任务 ID：${job.id}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text("${archiveDate(job.createdAt)} · ${job.sources.size} 个原文片段")
            Text(
                when {
                    job.artifact.isNotBlank() -> "已有保存产物，继续时先复用"; job.requestState == MemoryRequestState.NOT_STARTED -> "尚未发起请求"; else -> "请求可能已发出，费用待确认；普通继续不会重新请求"
                }
            )
            if (job.failureCode.isNotBlank()) Text("上次处理未完成，请检查来源、暂停状态或模型设置。")
            val control = state.jobControls[job.id]
            ArchiveActionsRow {
                if (job.status != MemoryJobStatus.COMMITTED && job.status != MemoryJobStatus.INVALIDATED) {
                    ArchiveAction(
                        "继续此任务",
                        { callbacks.onResumeJob(job.id) },
                        characterAvailable && !state.isWorking && state.processingControl?.userPaused != true && control?.userPaused != true
                    )
                    if (control != null) ArchiveAction(
                        if (control.userPaused) "解除任务暂停" else "暂停此任务",
                        {
                            callbacks.onSetPaused(job.id, !control.userPaused)
                        },
                        !state.isSaving
                    )
                }
            }
        }
    }
    item {
        if (state.jobsLoading) Text("正在读取任务…")
        if (state.hasMoreJobs) ArchiveAction(
            "更多任务",
            callbacks.onLoadMoreJobs,
            !state.jobsLoading
        )
        if (state.jobs.isEmpty() && !state.jobsLoading) Text("暂无整理任务。")
    }
    item {
        Text("从明确原文选区整理", style = MaterialTheme.typography.titleMedium)
        ArchiveConversationPicker(state, callbacks)
        Text("只整理勾选的消息，保留当时身份；不会自动回填整库历史。")
    }
    items(state.messages.asReversed(), key = { "selection:${it.message.id}" }) { row ->
        val message = row.message
        ArchiveCard {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(
                    message.id in state.selectedMessageIds,
                    { callbacks.onToggleSelection(message.id) },
                    enabled = !state.isWorking
                )
                Text(
                    "${message.speakerName.ifBlank { if (message.role == MessageRole.USER) "用户（历史署名未知）" else "角色（历史署名未知）" }} · ${
                        archiveDate(
                            message.createdAt
                        )
                    }",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(message.content.ifBlank { "没有可整理的正文" }, maxLines = 5)
        }
    }
    item {
        if (state.olderMessageOffset > 0) ArchiveAction(
            "载入更早原文",
            callbacks.onLoadOlderMessages,
            !state.messagesLoading
        )
        state.messagesError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Text("已选 ${state.selectedMessageIds.size} 条消息")
        ArchiveAction(
            "检查所选范围",
            callbacks.onInspectSelection,
            characterAvailable && state.selectedMessageIds.isNotEmpty() && !state.isWorking
        )
        state.inspection?.let { inspection ->
            SelectionInspectionCard(
                inspection,
                state,
                callbacks,
                characterAvailable
            )
        }
    }
}

@Composable
private fun SelectionInspectionCard(
    inspection: MemorySelectionInspection,
    state: MemoryArchiveUiState,
    callbacks: MemoryArchiveCallbacks,
    characterAvailable: Boolean
) {
    var confirm by rememberSaveable(inspection.selection) { mutableStateOf(false) }
    var retryUncertain by rememberSaveable(inspection.selection) { mutableStateOf(false) }
    ArchiveCard(tag = "memory_selection_inspection") {
        Text("范围检查：${inspection.availableMessageIds.size} 条可用，${inspection.unavailableMessageIds.size} 条不可用")
        if (inspection.failureCode.isNotBlank()) Text(
            "所选范围无法整理，请刷新来源后重试。",
            color = MaterialTheme.colorScheme.error
        )
        inspection.overlappingJobs.forEach { overlap ->
            Text("已有任务 ${overlap.jobId} · ${overlap.status.label()}${if (overlap.hasArtifact) " · 有保存产物" else ""}")
        }
        if (inspection.requiresReview) Text("选区与已有任务重叠。可以从任务历史继续；再次整理会成为新的明确请求。")
        ArchiveAction(
            if (inspection.requiresReview) "明确重新整理此选区" else "整理所选消息",
            { confirm = true },
            characterAvailable && !state.isWorking && inspection.unavailableMessageIds.isEmpty() && inspection.failureCode.isBlank()
        )
    }
    if (confirm) NarraAlertDialog(
        title = "整理所选原文",
        message = "对话 ${inspection.selection.conversationId}，共 ${inspection.selection.messageIds.size} 条已选消息。此操作会使用记忆模型，可能产生费用。",
        onDismiss = { confirm = false },
        onConfirm = {
            confirm = false; callbacks.onProcessSelection(
            inspection.requiresReview,
            retryUncertain
        )
        },
        confirmEnabled = !inspection.hasUncertainRequest || retryUncertain,
        shape = NarraShapes.large, actionContentColor = MaterialTheme.colorScheme.onSurface,
        content = if (inspection.hasUncertainRequest) ({
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(retryUncertain, { retryUncertain = it })
                Text("已有请求费用未知，我仍明确选择重新请求")
            }
        }) else null,
    )
}

internal fun MemoryRecallStatus.label() = when (this) {
    MemoryRecallStatus.PREPARED -> "已准备，尚未确认发出"; MemoryRecallStatus.SENT -> "已确认发出"; MemoryRecallStatus.FAILED -> "请求失败"
}

private fun MemoryMaterialKind.label() = when (this) {
    MemoryMaterialKind.RECORD -> "经历"; MemoryMaterialKind.EVENT -> "事件"; MemoryMaterialKind.RESIDENT -> "长期认知"; MemoryMaterialKind.STATE -> "当前状态"
}

private fun MemoryJobStatus.label() = when (this) {
    MemoryJobStatus.PLANNED -> "等待处理"; MemoryJobStatus.EXTRACTING -> "处理中"; MemoryJobStatus.EXTRACTED -> "产物已保存"; MemoryJobStatus.COMMITTED -> "已完成"; MemoryJobStatus.FAILED -> "处理失败"; MemoryJobStatus.PAUSED -> "处理已暂停"; MemoryJobStatus.INVALIDATED -> "来源或运行已失效"
}
