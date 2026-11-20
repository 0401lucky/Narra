package com.example.myapplication.data.local.memory

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.myapplication.context.MemoryArchiveIdentity
import com.example.myapplication.model.ConversationSummarySource
import com.example.myapplication.system.json.AppJson
import com.example.myapplication.system.logging.logFailure

@Entity(
    tableName = "conversation_summaries",
    indices = [
        Index(value = ["assistantId"]),
        Index(value = ["updatedAt"]),
    ],
)
data class ConversationSummaryEntity(
    @PrimaryKey val conversationId: String,
    val assistantId: String = "",
    val summary: String = "",
    val coveredMessageCount: Int = 0,
    val updatedAt: Long = 0L,
    @ColumnInfo(defaultValue = "'[]'") val sourceSnapshotJson: String = "[]",
    @ColumnInfo(defaultValue = "'unknown'") val completionStatus: String = "unknown",
    @ColumnInfo(defaultValue = "''") val memoryContextJson: String = "",
)

/** 内部缓存保留每个运行的聚合结果；原来没有来源的历史摘要也只按原有未知信息保存。 */
internal fun ConversationSummaryEntity.toAggregateCache(): ConversationSummarySegmentEntity {
    val sources = runCatching {
        AppJson.gson.fromJson(sourceSnapshotJson, Array<ConversationSummarySource>::class.java)
            .orEmpty().toList()
    }.logFailure("SummaryCache") { "summary cache sources decode failed, raw.len=${sourceSnapshotJson.length}" }
        .getOrDefault(emptyList())
    return ConversationSummarySegmentEntity(
        id = MemoryArchiveIdentity.id("summary-aggregate", conversationId, memoryContextJson),
        conversationId = conversationId,
        assistantId = assistantId,
        startMessageId = sources.firstOrNull()?.messageId.orEmpty(),
        endMessageId = sources.lastOrNull()?.messageId.orEmpty(),
        startCreatedAt = sources.firstOrNull()?.createdAt ?: 0L,
        endCreatedAt = sources.lastOrNull()?.createdAt ?: 0L,
        messageCount = coveredMessageCount,
        summary = summary,
        createdAt = updatedAt,
        updatedAt = updatedAt,
        sourceSnapshotJson = sourceSnapshotJson,
        completionStatus = completionStatus,
        memoryContextJson = memoryContextJson,
        isAggregate = true,
    )
}
