package com.example.myapplication.testutil

import com.example.myapplication.conversation.MemoryEventCoordinator
import com.example.myapplication.data.repository.ai.AiPromptExtrasService
import com.example.myapplication.data.repository.context.MemoryArchiveRepository
import com.example.myapplication.model.*
import com.example.myapplication.system.json.AppJson

/** 未覆写的协议仍由 RecordingMemoryAi 的显式拒绝网络工厂保护。 */
internal class RecordingMemoryEventAi : AiPromptExtrasService by RecordingMemoryAi() {
    val snapshots = mutableListOf<MemoryEventSummarySnapshot>()
    val models = mutableListOf<String>()
    var answer: suspend (MemoryEventSummarySnapshot) -> PromptCompletionResult =
        { memoryEventResponse(it) }

    override suspend fun requestMemoryEventSummary(
        snapshot: MemoryEventSummarySnapshot, baseUrl: String, apiKey: String,
        modelId: String, apiProtocol: ProviderApiProtocol, provider: ProviderSettings?
    ): PromptCompletionResult {
        snapshots += snapshot
        models += modelId
        return answer(snapshot)
    }
}

internal fun memoryEventResponse(
    snapshot: MemoryEventSummarySnapshot,
    summary: String = (listOf(snapshot.previousSummary).filter { snapshot.mode == MemoryEventSummaryMode.INCREMENTAL } + snapshot.members.map {
        it.record.content.takeLast(
            180
        )
    }).filter(String::isNotBlank).joinToString("；"),
    state: String = snapshot.members.last().record.content.takeLast(180),
    status: PromptCompletionStatus = PromptCompletionStatus.COMPLETED,
): PromptCompletionResult = PromptCompletionResult(
    AppJson.gson.toJson(
        mapOf(
            "schema_version" to 1, "summary" to summary, "current_state" to state,
            "covered_refs" to snapshot.members.indices.map { "M$it" },
        )
    ), status
)

internal fun MemoryExtractionFixture.eventCoordinator(
    ai: RecordingMemoryEventAi,
    repository: MemoryArchiveRepository = archive,
    policy: MemoryEventSummaryPolicy = MemoryEventSummaryPolicy(),
    leaseMillis: Long = 120_000L,
    waitForLease: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
) = MemoryEventCoordinator(
    repository,
    ai,
    { settings },
    { consent },
    policy,
    leaseMillis,
    { now },
    waitForLease
)

internal suspend fun MemoryExtractionFixture.createEvent(
    context: MemoryRuntimeContext,
    contents: List<String> = listOf(
        "11月1日芮珩委托修复罗盘",
        "11月2日确定齿轮编号706",
        "11月3日送达新齿轮",
        "11月4日完成修复归还罗盘"
    ),
    title: String = "罗盘修复",
): Pair<MemoryEventBox, List<MemoryRecord>> {
    require(contents.size >= 2)
    val records = contents.map { remember(context, it) }
    var eventId = ""
    records.drop(1).forEach { record ->
        val result = archive.linkEventMembers(
            context, MemoryRecordVersion(record.id, record.revision),
            MemoryRecordVersion(records.first().id, records.first().revision), title
        ) as MemoryEventMutationResult.Updated
        eventId = result.eventId
    }
    return requireNotNull(archive.getEventDetails(context, eventId)).event to records
}
