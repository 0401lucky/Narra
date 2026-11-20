package com.example.myapplication.data.repository.context

import com.example.myapplication.model.ContextDataBundle
import com.example.myapplication.model.MemoryArchiveImportResult
import com.example.myapplication.model.MemoryArchiveTransferData
import com.example.myapplication.model.MemoryImportPlan
import com.example.myapplication.model.MemoryImportTarget
import com.example.myapplication.model.MemoryImportTargetOption
import com.example.myapplication.model.MemoryRuntimeContext
import com.example.myapplication.model.MemoryTransferObjectKind
import com.example.myapplication.model.MemoryTransferOriginLink

interface MemoryArchiveTransferRepository {
    suspend fun exportArchive(includeVectors: Boolean = true): MemoryArchiveTransferData
    suspend fun listTargetOptions(characterId: String): List<MemoryImportTargetOption>
    suspend fun prepareImport(
        bundle: ContextDataBundle,
        targets: Map<String, MemoryImportTarget>
    ): MemoryImportPlan

    suspend fun importArchive(
        bundle: ContextDataBundle,
        plan: MemoryImportPlan
    ): MemoryArchiveImportResult

    /** 由持有最外层 Room 事务的调用者在提交后调用；importArchive 不猜测线程局部事务状态。 */
    suspend fun notifyImportCommitted(plan: MemoryImportPlan)
    suspend fun getOrigins(
        context: MemoryRuntimeContext,
        kind: MemoryTransferObjectKind,
        id: String
    ): List<MemoryTransferOriginLink>
}
