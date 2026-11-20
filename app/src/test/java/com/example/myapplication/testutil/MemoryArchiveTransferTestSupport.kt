package com.example.myapplication.testutil

import com.example.myapplication.data.repository.context.MemoryTransferLegacyConversion
import com.example.myapplication.data.repository.context.RoomMemoryArchiveTransferRepository
import com.example.myapplication.data.repository.context.ContextTransferCodec
import com.example.myapplication.model.*

internal fun MemoryExtractionFixture.transfer(onImported: (MemoryRuntimeContext) -> Unit = {}) =
    RoomMemoryArchiveTransferRepository(
        database,
        archive,
        isCharacterAvailable = { id -> settings.resolvedAssistants().any { it.id == id } },
        onImported = onImported,
        nowProvider = { now },
    )

internal suspend fun MemoryExtractionFixture.transferBundle(): ContextDataBundle =
    ContextTransferCodec().let { codec ->
        codec.decode(
            codec.encode(
                ContextDataBundle(
                    version = 2,
                    exportedAt = now,
                    memoryArchive = transfer().exportArchive()
                )
            )
        )
    }

internal fun MemoryRuntimeContext.importTarget() = MemoryImportTarget(
    MemoryImportTargetMode.EXISTING, characterId, spaceId, personaKey, generation,
)

internal fun targetsFor(
    bundle: ContextDataBundle,
    context: MemoryRuntimeContext
): Map<String, MemoryImportTarget> =
    MemoryTransferLegacyConversion.sourceGroups(MemoryTransferLegacyConversion.materialize(bundle))
        .associate { it.key to context.importTarget() }

internal suspend fun MemoryExtractionFixture.fromSource(
    context: MemoryRuntimeContext,
    source: MemorySourceRef,
    content: String
): MemoryRecord {
    val id = java.util.UUID.randomUUID().toString()
    val job = archive.planJob(context, "transfer-$id", listOf(source))
    checkNotNull(archive.claimJob(job.id, "transfer-test"))
    check(archive.saveExtraction(job.id, "transfer-test", "synthetic-memory"))
    val result = archive.commitJob(
        job.id,
        "transfer-test",
        MemoryCommitBatch(records = listOf(MemoryRecordDraft("N0", content, listOf(source))))
    ) as MemoryWriteResult.Committed
    return requireNotNull(archive.getRecordDetails(context, result.recordIds.single())).record
}
