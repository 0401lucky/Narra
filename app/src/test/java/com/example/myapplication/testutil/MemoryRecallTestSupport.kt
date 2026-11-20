package com.example.myapplication.testutil

import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.MemoryCommitBatch
import com.example.myapplication.model.MemoryRecallPlan
import com.example.myapplication.model.MemoryRecallRequest
import com.example.myapplication.model.MemoryRecord
import com.example.myapplication.model.MemoryRecordDraft
import com.example.myapplication.model.MemoryRuntimeContext
import com.example.myapplication.model.MemoryWriteResult
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.MessageStatus
import java.util.UUID

/** 合成原文经真实来源/任务事务落库，不调用模型。 */
internal suspend fun MemoryExtractionFixture.remember(
    context: MemoryRuntimeContext,
    content: String,
    id: String = UUID.randomUUID().toString(),
    importance: Int = 30,
): MemoryRecord {
    add(context, id, content)
    val source = archive.listPendingIngress(context).single { it.source.messageId == id }.source
    val job = archive.planJob(context, "recall-$id", listOf(source.fullRef()))
    check(archive.claimJob(job.id, "recall-test") != null)
    check(archive.saveExtraction(job.id, "recall-test", "{\"synthetic\":true}"))
    val result = archive.commitJob(
        job.id, "recall-test", MemoryCommitBatch(
            records = listOf(
                MemoryRecordDraft("N0", content, listOf(source.fullRef()), importance = importance),
            )
        )
    ) as MemoryWriteResult.Committed
    return requireNotNull(archive.getRecordDetails(context, result.recordIds.single())).record
}

internal suspend fun MemoryExtractionFixture.recallRequest(plan: MemoryRecallPlan): MemoryRecallRequest {
    val requestId = UUID.randomUUID().toString()
    val anchorId = "anchor-$requestId"
    store.upsertMessages(
        listOf(
            ChatMessage(
                anchorId, plan.context.conversationId, MessageRole.ASSISTANT, "",
                MessageStatus.LOADING, ++now, memoryRecallRequestId = requestId
            )
        )
    )
    return MemoryRecallRequest(
        requestId, anchorId,
        store.listMessages(plan.context.conversationId)
            .lastOrNull { it.role == MessageRole.USER }?.id.orEmpty(), plan, now
    )
}
