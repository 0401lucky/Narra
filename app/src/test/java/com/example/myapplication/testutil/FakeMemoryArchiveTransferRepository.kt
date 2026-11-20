package com.example.myapplication.testutil

import com.example.myapplication.data.repository.context.MemoryArchiveTransferRepository
import com.example.myapplication.data.repository.context.MemoryTransferLegacyConversion
import com.example.myapplication.model.*
import java.util.UUID

/** 仅供资料 ViewModel 编排测试；真正的图/事务测试使用两个隔离 Room 库。 */
class FakeMemoryArchiveTransferRepository : MemoryArchiveTransferRepository {
    var exportedData = MemoryArchiveTransferData()
    val importedBundles = mutableListOf<ContextDataBundle>()
    val plans = mutableListOf<MemoryImportPlan>()
    var notifications = 0
    var failure: Exception? = null
    var conflicts: List<MemoryImportStateConflict> = emptyList()
    private val receipts = mutableMapOf<String, MemoryArchiveImportResult.Imported>()

    override suspend fun exportArchive(includeVectors: Boolean) = exportedData
    override suspend fun listTargetOptions(characterId: String) = listOf(
        MemoryImportTargetOption(
            "测试空间 · 未设置扮演身份",
            MemoryImportTarget(
                MemoryImportTargetMode.EXISTING,
                characterId,
                "space-$characterId",
                expectedGeneration = 1L
            ),
        )
    )

    override suspend fun prepareImport(
        bundle: ContextDataBundle,
        targets: Map<String, MemoryImportTarget>
    ): MemoryImportPlan {
        check(
            targets.keys == MemoryTransferLegacyConversion.sourceGroups(
                MemoryTransferLegacyConversion.materialize(bundle)
            ).map { it.key }.toSet()
        )
        return MemoryImportPlan(
            UUID.randomUUID().toString(),
            "fake-fingerprint",
            targets,
            conflicts
        ).also(plans::add)
    }

    override suspend fun importArchive(
        bundle: ContextDataBundle,
        plan: MemoryImportPlan
    ): MemoryArchiveImportResult {
        failure?.let { throw it }
        receipts[plan.localPlanId]?.let { return it.copy(alreadyImported = true) }
        if (plan.stateConflicts.isNotEmpty() && !plan.replaceExistingStates) return MemoryArchiveImportResult.Conflict(
            "状态冲突"
        )
        importedBundles += bundle
        exportedData = MemoryTransferLegacyConversion.materialize(bundle)
        return MemoryArchiveImportResult.Imported(
            exportedData.itemCount,
            exportedData.sources.size,
            0,
            exportedData.vectors.size,
            emptyList()
        )
            .also { receipts[plan.localPlanId] = it }
    }

    override suspend fun notifyImportCommitted(plan: MemoryImportPlan) {
        notifications++
    }

    override suspend fun getOrigins(
        context: MemoryRuntimeContext,
        kind: MemoryTransferObjectKind,
        id: String
    ) = emptyList<MemoryTransferOriginLink>()
}
