package com.example.myapplication.data.repository.context

import androidx.room.withTransaction
import com.example.myapplication.context.MemoryArchiveIdentity
import com.example.myapplication.context.MemoryRecallText
import com.example.myapplication.data.local.chat.ChatDatabase
import com.example.myapplication.data.local.memory.MemoryRecallReceiptEntity
import com.example.myapplication.data.local.memory.MemoryRecordEntity
import com.example.myapplication.model.MemoryDerivedRecallEntry
import com.example.myapplication.model.MemoryMaterialKind
import com.example.myapplication.model.MemoryMaterialRef
import com.example.myapplication.model.MemoryRecallOmission
import com.example.myapplication.model.MemoryRecallReceipt
import com.example.myapplication.model.MemoryRecallRequest
import com.example.myapplication.model.MemoryRecallSnapshot
import com.example.myapplication.model.MemoryRecallStatus
import com.example.myapplication.model.MemoryRecallTransmission
import com.example.myapplication.model.MemoryRecord
import com.example.myapplication.model.MemoryRecordVersion
import com.example.myapplication.model.MemoryRuntimeContext
import com.example.myapplication.model.MemorySourceRef
import com.example.myapplication.model.MemoryResidentCategory
import com.example.myapplication.model.MemoryResidentLimits
import com.example.myapplication.model.MemoryStateKey
import com.example.myapplication.system.json.AppJson

/** 与提取共用运行/来源守卫。自动召回不接受档案管理上下文或不明来源。 */
internal class MemoryRecallStore(
    private val database: ChatDatabase,
    private val validRuntime: suspend (MemoryRuntimeContext) -> Boolean,
    private val validSource: suspend (MemoryRuntimeContext, MemorySourceRef) -> Boolean,
    private val now: () -> Long,
    private val usesLegacyGlobalMemory: suspend (String) -> Boolean,
    private val residentLimits: suspend (String) -> MemoryResidentLimits = { MemoryResidentLimits() },
) {
    private val dao = database.memoryArchiveDao()
    private val gson = AppJson.gson
    private val knowledge = MemoryKnowledgeStorage(database, validSource)

    suspend fun listRecords(context: MemoryRuntimeContext): List<MemoryRecord> =
        database.withTransaction {
            if (!validRuntime(context)) return@withTransaction emptyList()
            legalRecords(context).map { it.toDomain() }
        }

    suspend fun validateRecords(
        context: MemoryRuntimeContext,
        revisions: Map<String, Long>
    ): Boolean = database.withTransaction {
        validRuntime(context) && revisions.all { (id, revision) ->
            dao.getRecord(id)?.let { it.revision == revision && legalRecord(context, it) } == true
        }
    }

    suspend fun snapshot(context: MemoryRuntimeContext): MemoryRecallSnapshot? =
        database.withTransaction {
            if (!validRuntime(context)) return@withTransaction null
            val records = legalRecords(context)
            val legacyRecords = accessibleLegacyRecords(context)
            val byId = records.associateBy { it.id }
            val sources = (records + legacyRecords).associate { it.id to sourceRefs(it) }
            suspend fun imported(refs: List<MemorySourceRef>): Boolean =
                refs.any { dao.getSource(it.sourceId)?.kind == "IMPORTED" }

            val importedRecordIds = sources.filterValues { imported(it) }.keys
            suspend fun derived(
                kind: MemoryMaterialKind,
                id: String,
                content: String,
                revision: Long,
                label: String,
                updatedAt: Long
            ): MemoryDerivedRecallEntry? {
                if (content.isBlank() || revision < 1) return null
                val evidence = knowledge.evidence(context, kind.name, id, revision) ?: return null
                return MemoryDerivedRecallEntry(
                    id, kind, content, revision, label,
                    evidence.records, evidence.sources, updatedAt,
                    imported = imported(evidence.sources),
                )
            }

            val limits = residentLimits(context.characterId).normalized()
            val residentCounts = mutableMapOf<MemoryResidentCategory, Int>()
            val residents = dao.listResidentEntries(context.spaceId)
                .sortedWith(compareByDescending<com.example.myapplication.data.local.memory.MemoryResidentEntryEntity> { it.editedByUser }
                    .thenByDescending { it.updatedAt }.thenBy { it.id }).mapNotNull { entry ->
                    val category =
                        MemoryResidentCategory.entries.firstOrNull { it.name == entry.category }
                            ?: return@mapNotNull null
                    if (residentCounts.getOrDefault(
                            category,
                            0
                        ) >= limits.capacity(category)
                    ) return@mapNotNull null
                    if (entry.characterId != context.characterId || entry.personaKey != context.personaKey || entry.status != "READY") null
                    else derived(
                        MemoryMaterialKind.RESIDENT,
                        entry.id,
                        entry.content,
                        entry.revision,
                        "常驻认知 · ${
                            when (category) {
                                MemoryResidentCategory.USER -> "关于对方"
                                MemoryResidentCategory.CHARACTER -> "角色自身"
                                MemoryResidentCategory.RELATIONSHIP -> "彼此相处"
                                MemoryResidentCategory.SHARED -> "共同积累"
                            }
                        }" + if (entry.subjective) " · 主观看法" else " · 有来源的认知",
                        entry.updatedAt
                    )?.also {
                        residentCounts[category] = residentCounts.getOrDefault(category, 0) + 1
                    }
                }
            val timestamp = now()
            val states = dao.listCurrentStates(context.spaceId).mapNotNull { entry ->
                if (entry.characterId != context.characterId || entry.personaKey != context.personaKey || entry.status != "ACTIVE" ||
                    entry.endedAt != null || (entry.expiresAt != null && entry.expiresAt <= timestamp)
                ) null
                else derived(
                    MemoryMaterialKind.STATE,
                    entry.id,
                    entry.content,
                    entry.revision,
                    "当前状态 · " + (MemoryStateKey.entries.firstOrNull {
                        it.name.equals(
                            entry.stateKey,
                            true
                        )
                    }?.label ?: "未分类"),
                    entry.updatedAt
                )
            }
            val eventMemberships = linkedMapOf<String, List<MemoryRecordVersion>>()
            val eventGenerations = linkedMapOf<String, Long>()
            val events = dao.listEventBoxes(context.spaceId).mapNotNull { event ->
                if (event.characterId != context.characterId || event.personaKey != context.personaKey || event.status !in setOf(
                        "ACTIVE",
                        "ARCHIVED"
                    )
                ) return@mapNotNull null
                val members = dao.listEventMembers(event.id)
                if (members.isEmpty() || members.any { it.recordId !in byId }) return@mapNotNull null
                // 未达压缩阈值或摘要失效时，合法的同事件关系仍能展开原记录。
                eventMemberships[event.id] = members.map {
                    MemoryRecordVersion(it.recordId, byId.getValue(it.recordId).revision)
                }
                eventGenerations[event.id] = event.generation
                if (event.summaryStatus != "READY" || event.summary.isBlank() || event.summaryRevision < 1) return@mapNotNull null
                val covered = members.filter { it.coveredRevision != null }
                // 盒子可以有尚未覆盖的新成员；已经覆盖的每个成员必须仍是当前合法版本。
                if (covered.isEmpty() || covered.any { byId[it.recordId]?.revision != it.coveredRevision } ||
                    members.any { it.recordId !in byId }) return@mapNotNull null
                MemoryDerivedRecallEntry(
                    event.id,
                    MemoryMaterialKind.EVENT,
                    buildString {
                        append(event.summary)
                        if (event.currentState.isNotBlank()) append("\n摘要覆盖时的进展：").append(
                            event.currentState
                        )
                        val uncovered = members.count { it.coveredRevision == null }
                        if (uncovered > 0) append("\n另有 ").append(uncovered)
                            .append(" 条新进展尚未收入本摘要。")
                    },
                    event.summaryRevision,
                    "事件摘要（仅覆盖所列版本）",
                    covered.map {
                        MemoryRecordVersion(
                            it.recordId,
                            requireNotNull(it.coveredRevision)
                        )
                    },
                    covered.flatMap { sources[it.recordId].orEmpty() }.distinct(),
                    event.updatedAt,
                    members.map {
                        MemoryRecordVersion(
                            it.recordId,
                            byId.getValue(it.recordId).revision
                        )
                    },
                    imported = imported(covered.flatMap { sources[it.recordId].orEmpty() }),
                )
            }
            MemoryRecallSnapshot(
                context,
                records.map { it.toDomain() },
                sources,
                events,
                residents,
                states,
                legacyRecords.map { it.toDomain() },
                eventMemberships,
                eventGenerations,
                importedRecordIds,
            )
        }

    suspend fun validateMaterials(
        context: MemoryRuntimeContext,
        materials: List<MemoryMaterialRef>
    ): Boolean = database.withTransaction {
        if (!validRuntime(context)) return@withTransaction false
        val current = snapshot(context) ?: return@withTransaction false
        materials.all { ref ->
            ref.eventContext?.let { association ->
                val members = current.eventMemberships[association.eventId].orEmpty()
                if (ref.kind != MemoryMaterialKind.RECORD || current.eventGenerations[association.eventId] != association.generation ||
                    association.anchor !in members || MemoryRecordVersion(
                        ref.id,
                        ref.revision
                    ) !in members
                ) return@all false
            }
            val text: String
            val sources: List<MemorySourceRef>
            val supporting: List<MemoryRecordVersion>
            val imported: Boolean
            if (ref.kind == MemoryMaterialKind.RECORD) {
                val record = (if (ref.legacy) current.legacyRecords else current.records)
                    .firstOrNull { it.id == ref.id && it.revision == ref.revision }
                    ?: return@all false
                text = record.content
                sources = current.recordSources[record.id].orEmpty()
                supporting = emptyList()
                imported = record.id in current.importedRecordIds
            } else {
                val entry = (current.events + current.residents + current.states).firstOrNull {
                    it.kind == ref.kind && it.id == ref.id && it.revision == ref.revision
                } ?: return@all false
                text = entry.content
                sources = entry.sourceRefs
                supporting = entry.supportingRecords
                imported = entry.imported
            }
            ref.imported == imported && ref.spanStart >= 0 && ref.spanEnd > ref.spanStart && ref.spanEnd <= text.length &&
                    MemoryRecallText.safeBoundary(
                        text,
                        ref.spanStart
                    ) == ref.spanStart && MemoryRecallText.safeBoundary(
                text,
                ref.spanEnd
            ) == ref.spanEnd &&
                    ref.sourceRefs.toSet() == sources.toSet() && ref.supportingRecords.toSet() == supporting.toSet() &&
                    MemoryArchiveIdentity.fingerprint(
                        text.substring(
                            ref.spanStart,
                            ref.spanEnd
                        )
                    ) == ref.contentFingerprint
        }
    }

    suspend fun archiveOverflow(context: MemoryRuntimeContext, activeCapacity: Int) =
        database.withTransaction {
            if (!validRuntime(context)) return@withTransaction
            if (dao.isProcessingPaused(context.spaceId, "")) return@withTransaction
            val active =
                dao.listRecallRecords(context.spaceId, context.characterId, context.personaKey)
                    .filter { it.kind == "EPISODE" && it.status == "ACTIVE" && !(it.pinned && (it.pinnedUntil == null || it.pinnedUntil > now())) }
                    .sortedWith(compareByDescending<MemoryRecordEntity> { it.importance }.thenByDescending { it.updatedAt }
                        .thenBy { it.id })
            active.drop(activeCapacity.coerceAtLeast(0))
                .forEach { dao.upsertRecord(it.copy(status = "ARCHIVED")) }
        }

    suspend fun prepare(request: MemoryRecallRequest): Boolean = database.withTransaction {
        if (request.requestId.isBlank() || request.anchorMessageId.isBlank() || !validRuntime(
                request.plan.context
            )
        ) return@withTransaction false
        if (!validRequestAnchor(
                request.plan.context,
                request.requestId,
                request.anchorMessageId
            )
        ) return@withTransaction false
        if (dao.getReceipt(request.requestId) != null) return@withTransaction false
        dao.upsertReceipt(
            MemoryRecallReceipt(
                request.requestId,
                request.plan.context,
                request.anchorMessageId,
                request.userMessageId,
                request.plan.selected,
                emptyList(),
                request.plan.omitted,
                "",
                MemoryRecallStatus.PREPARED,
                request.createdAt
            ).toEntity()
        )
        true
    }

    suspend fun prepareTransmission(
        requestId: String,
        fingerprint: String,
        materials: List<MemoryMaterialRef>,
        omitted: List<MemoryRecallOmission>
    ): Int? = database.withTransaction {
        val receipt = dao.getReceipt(requestId)?.toDomain() ?: return@withTransaction null
        if (!validRuntime(receipt.context) || !validateMaterials(
                receipt.context,
                materials
            )
        ) return@withTransaction null
        if (!validRequestAnchor(
                receipt.context,
                requestId,
                receipt.anchorMessageId
            )
        ) return@withTransaction null
        val sequence = receipt.transmissions.size + 1
        val attempt = MemoryRecallTransmission(
            sequence,
            fingerprint,
            materials,
            MemoryRecallStatus.PREPARED,
            now()
        )
        dao.upsertReceipt(
            receipt.copy(
                status = MemoryRecallStatus.PREPARED,
                payloadFingerprint = fingerprint,
                sent = emptyList(),
                sentAt = null,
                omitted = (receipt.omitted + omitted).distinct(),
                failureCode = "",
                transmissions = receipt.transmissions + attempt
            ).toEntity()
        )
        sequence
    }

    suspend fun confirmTransmission(requestId: String, sequence: Int): Boolean =
        database.withTransaction {
            val receipt = dao.getReceipt(requestId)?.toDomain() ?: return@withTransaction false
            val attempt = receipt.transmissions.lastOrNull()?.takeIf { it.sequence == sequence }
                ?: return@withTransaction false
            val timestamp = now()
            // 网络已有回应是发送证据；此处即使运行已切换也保留原请求历史，不把它移入新空间。
            dao.upsertReceipt(
                receipt.copy(
                    sent = attempt.materials, status = MemoryRecallStatus.SENT, sentAt = timestamp,
                    transmissions = receipt.transmissions.dropLast(1) + attempt.copy(
                        status = MemoryRecallStatus.SENT,
                        sentAt = timestamp
                    )
                ).toEntity()
            )
            true
        }

    suspend fun fail(requestId: String, failureCode: String) = database.withTransaction {
        val receipt = dao.getReceipt(requestId)?.toDomain() ?: return@withTransaction
        val allowedCode = failureCode.takeIf { it in FAILURE_CODES } ?: "REQUEST_FAILED"
        dao.upsertReceipt(
            receipt.copy(
                status = MemoryRecallStatus.FAILED,
                failureCode = allowedCode
            ).toEntity()
        )
    }

    suspend fun get(context: MemoryRuntimeContext, requestId: String): MemoryRecallReceipt? =
        database.withTransaction {
            if (requestId.isBlank()) return@withTransaction null
            val receipt = dao.getReceipt(requestId)?.toDomain() ?: return@withTransaction null
            // 不查最新一条，不把旧身份或旧运行的现场借给当前轮次。
            receipt.takeIf { it.context == context && validRuntime(context) }
        }

    suspend fun receiptForPublication(requestId: String): MemoryRecallReceipt? =
        dao.getReceipt(requestId)?.toDomain()

    private suspend fun legalRecords(context: MemoryRuntimeContext) =
        dao.listRecallRecords(context.spaceId, context.characterId, context.personaKey)
            .filter { legalRecord(context, it) }

    private suspend fun legalRecord(
        context: MemoryRuntimeContext,
        record: MemoryRecordEntity
    ): Boolean {
        if (record.spaceId != context.spaceId || record.characterId != context.characterId || record.personaKey != context.personaKey ||
            record.status !in setOf("ACTIVE", "ARCHIVED") || record.content.isBlank()
        ) return false
        val refs = sourceRefs(record)
        return refs.isNotEmpty() && refs.all { validSource(context, it) }
    }

    private suspend fun accessibleLegacyRecords(context: MemoryRuntimeContext): List<MemoryRecordEntity> {
        if (dao.getSpace(context.spaceId)?.purpose != "legacy_compat") return emptyList()
        val globalOnly = usesLegacyGlobalMemory(context.characterId)
        return dao.listLegacyRecords().filter { record ->
            val accessible =
                record.status in setOf("ACTIVE", "ARCHIVED") && record.content.isNotBlank() &&
                        record.characterId in setOf(
                    context.characterId,
                    com.example.myapplication.model.MEMORY_UNASSIGNED_CHARACTER
                ) &&
                        when (record.legacyScopeType) {
                            "global" -> true
                            "assistant" -> !globalOnly && record.legacyScopeId == context.characterId
                            "conversation" -> record.legacyScopeId == context.conversationId
                            else -> false
                        }
            if (!accessible || dao.getSpace(record.spaceId)?.status == "DELETED") false
            else {
                val refs = sourceRefs(record)
                refs.isNotEmpty() && refs.all { ref ->
                    val source = dao.getSource(ref.sourceId)
                    source != null && source.spaceId == record.spaceId && source.characterId == record.characterId &&
                            source.messageRevision == ref.sourceRevision && when (source.kind) {
                        "LEGACY_HINT" -> source.status in setOf(
                            "UNKNOWN",
                            "MISSING"
                        ) && ref.start == 0 && ref.end == 0

                        "MANUAL", "IMPORTED" -> validSource(
                            context.copy(
                                spaceId = record.spaceId,
                                characterId = record.characterId,
                                personaKey = record.personaKey
                            ), ref
                        )

                        else -> false
                    }
                }
            }
        }
    }

    private suspend fun sourceRefs(record: MemoryRecordEntity) =
        dao.listRecordSources(record.id, record.revision).map {
            MemorySourceRef(it.sourceId, it.sourceRevision, it.spanStart, it.spanEnd)
        }

    private fun MemoryRecallReceipt.toEntity() = MemoryRecallReceiptEntity(
        requestId = requestId,
        spaceId = context.spaceId,
        characterId = context.characterId,
        conversationId = context.conversationId,
        generation = context.generation,
        selectedJson = gson.toJson(selected),
        sentJson = gson.toJson(sent),
        omittedJson = gson.toJson(omitted),
        payloadFingerprint = payloadFingerprint,
        status = status.name,
        createdAt = createdAt,
        sentAt = sentAt,
        personaKey = context.personaKey,
        scenarioId = context.scenarioId,
        runId = context.runId,
        anchorMessageId = anchorMessageId,
        userMessageId = userMessageId,
        failureCode = failureCode,
        transmissionsJson = gson.toJson(transmissions),
    )

    private fun MemoryRecallReceiptEntity.toDomain() = MemoryRecallReceipt(
        requestId,
        MemoryRuntimeContext(
            characterId,
            spaceId,
            conversationId,
            scenarioId,
            runId,
            personaKey,
            generation
        ),
        anchorMessageId,
        userMessageId,
        gson.fromJson(selectedJson, Array<MemoryMaterialRef>::class.java).toList(),
        gson.fromJson(sentJson, Array<MemoryMaterialRef>::class.java).toList(),
        gson.fromJson(omittedJson, Array<MemoryRecallOmission>::class.java).toList(),
        payloadFingerprint,
        MemoryRecallStatus.valueOf(status),
        createdAt,
        sentAt,
        failureCode,
        gson.fromJson(transmissionsJson, Array<MemoryRecallTransmission>::class.java).toList(),
    )

    private suspend fun validRequestAnchor(
        context: MemoryRuntimeContext,
        requestId: String,
        messageId: String
    ): Boolean {
        val anchor = database.conversationDao().getMessage(messageId)
        if (anchor?.conversationId == context.conversationId && anchor.role == "ASSISTANT" &&
            anchor.memoryRecallRequestId == requestId
        ) return true
        // 开场尚未接受时没有 canonical；只允许实际持久 claim 的陪伴 attempt 预留消息锚点。
        val attempt = database.companionDao().getAttempt(requestId) ?: return false
        if (attempt.state != "REQUESTED") return false
        val turn = database.companionDao().getTurn(attempt.turnId) ?: return false
        if (turn.activeAttemptId != attempt.id || turn.mutationRevision != attempt.expectedTurnRevision) return false
        val descriptor = runCatching {
            gson.fromJson(
                attempt.requestDescriptorJson,
                com.example.myapplication.model.CompanionRequestDescriptor::class.java
            )
        }.getOrNull() ?: return false
        if (descriptor.historyScope?.context != context || descriptor.replyMessageId != messageId) return false
        if (anchor != null && (turn.replyMessageId != messageId || anchor.conversationId != context.conversationId)) return false
        return runCatching {
            gson.fromJson(attempt.frozenRuntimeJson, MemoryRuntimeContext::class.java) == context
        }.getOrDefault(false)
    }

    private companion object {
        val FAILURE_CODES = setOf(
            "NOT_STARTED",
            "SCOPE_CHANGED",
            "SOURCE_CHANGED",
            "PAYLOAD_CHANGED",
            "HTTP_FAILED",
            "TRANSPORT_UNCONFIRMED",
            "CANCELLED_UNCONFIRMED",
            "CANCELLED_AFTER_SEND",
            "REQUEST_FAILED"
        )
    }
}
