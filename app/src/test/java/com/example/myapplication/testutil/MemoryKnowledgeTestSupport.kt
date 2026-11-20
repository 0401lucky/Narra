package com.example.myapplication.testutil

import com.example.myapplication.conversation.MemoryExtractionParser
import com.example.myapplication.model.*
import com.example.myapplication.system.json.AppJson
import com.google.gson.JsonNull
import com.google.gson.JsonParser
import java.util.UUID

internal fun residentCandidate(
    content: String,
    category: MemoryResidentCategory = MemoryResidentCategory.USER,
    subjective: Boolean = false,
    operation: String = "add",
    targetRef: String? = null,
    expectedRevision: Long? = null,
    durability: String? = "stable",
): Map<String, Any> = buildMap {
    put("category", category.name.lowercase())
    put("content", content)
    put("subjective", subjective)
    put("source_refs", listOf("M0"))
    put("operation", operation)
    targetRef?.let { put("target_ref", it) }
    expectedRevision?.let { put("expected_revision", it) }
    durability?.let { put("durability", it) }
}

internal fun stateUpdate(
    key: MemoryStateKey,
    content: String,
    operation: String = "set",
    expectedRevision: Long? = 0L,
    expiresAt: Long? = null,
): Map<String, Any> = buildMap {
    put("state_key", key.name.lowercase())
    put("operation", operation)
    put("content", content)
    put("source_refs", listOf("M0"))
    expectedRevision?.let { put("expected_revision", it) }
    expiresAt?.let { put("expires_at", it) }
}

internal fun knowledgeResponse(
    residents: List<Map<String, Any>> = emptyList(),
    states: List<Map<String, Any>> = emptyList(),
    memories: List<Map<String, Any>> = emptyList(),
    corrections: List<Map<String, Any>> = emptyList(),
    status: PromptCompletionStatus = PromptCompletionStatus.COMPLETED,
): PromptCompletionResult {
    val json = JsonParser.parseString(
        AppJson.gson.toJson(
            mapOf(
                "schema_version" to 2,
                "memories" to memories,
                "corrections" to corrections,
                "state_updates" to states,
                "resident_candidates" to residents,
            )
        )
    ).asJsonObject
    json.getAsJsonArray("memories")
        .forEach { it.asJsonObject.add("occurred_date", JsonNull.INSTANCE) }
    return PromptCompletionResult(json.toString(), status)
}

internal fun knowledgeEpisode(content: String): Map<String, Any> = mapOf(
    "local_id" to "N0", "kind" to "episode", "category" to "shared", "content" to content,
    "source_refs" to listOf("M0"), "related_to" to emptyList<String>(),
)

internal data class MemoryKnowledgeWork(
    val jobId: String,
    val message: ChatMessage,
    val snapshot: MemoryExtractionSnapshot,
    val batch: MemoryCommitBatch,
)

/** 正文由调用者合成；来源、冻结快照与响应保存都走生产仓储，不访问任何网络。 */
internal suspend fun MemoryExtractionFixture.prepareKnowledge(
    context: MemoryRuntimeContext,
    sourceText: String = "合成声明：芮珩搬到星河路182号。",
    freezeTargets: Boolean = true,
    semanticKind: MemorySemanticKind = MemorySemanticKind.SPEECH,
    targets: List<MemoryExtractionTarget> = emptyList(),
    response: (MemoryExtractionSnapshot) -> PromptCompletionResult,
): MemoryKnowledgeWork {
    val message = add(context, "knowledge-${UUID.randomUUID()}", sourceText)
    val source =
        archive.listPendingIngress(context).single { it.source.messageId == message.id }.source
    val snapshot = MemoryExtractionSnapshot(
        listOf(
            MemoryExtractionSpan(
                source.fullRef(), message.id, source.speakerId, source.speakerRole,
                context.personaKey, listOf(context.characterId), source.occurredAt, source.kind,
                semanticKind, source.content,
            )
        ),
        targets, "合成角色", "合成身份",
        knowledgeTargets = if (freezeTargets) archive.getKnowledgeTargets(context) else null,
    )
    val job = archive.planJob(
        context,
        "knowledge-${UUID.randomUUID()}",
        listOf(source.fullRef()),
        targetRevisions = targets.associate { it.id to it.revision },
        requestJson = AppJson.gson.toJson(snapshot)
    )
    check(archive.claimJob(job.id, KNOWLEDGE_TEST_OWNER) != null)
    check(archive.markRequestStarted(job.id, KNOWLEDGE_TEST_OWNER))
    val result = response(snapshot)
    check(
        archive.saveExtraction(
            job.id,
            KNOWLEDGE_TEST_OWNER,
            AppJson.gson.toJson(MemoryExtractionArtifact(completion = result))
        )
    )
    return MemoryKnowledgeWork(
        job.id,
        message,
        snapshot,
        MemoryExtractionParser.parse(result, snapshot)
    )
}

internal suspend fun MemoryExtractionFixture.commitKnowledge(work: MemoryKnowledgeWork): MemoryWriteResult =
    archive.commitJob(work.jobId, KNOWLEDGE_TEST_OWNER, work.batch)

/** v52 已提交 C2 候选但没有 C5 消费表；仅在隔离测试库构造这一升级前状态。 */
internal suspend fun MemoryExtractionFixture.commitBeforeKnowledge(work: MemoryKnowledgeWork) {
    check(
        archive.commitJob(
            work.jobId, KNOWLEDGE_TEST_OWNER,
            work.batch.copy(residents = emptyList(), states = emptyList())
        ) is MemoryWriteResult.Committed
    )
    database.openHelper.writableDatabase.execSQL(
        "DELETE FROM memory_knowledge_consumptions WHERE jobId = ?", arrayOf(work.jobId),
    )
}

internal const val KNOWLEDGE_TEST_OWNER = "knowledge-test"
