package com.example.myapplication.context

import com.example.myapplication.data.repository.context.MemoryArchiveRepository
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.MemoryDerivedRecallEntry
import com.example.myapplication.model.MemoryEventRecallContext
import com.example.myapplication.model.MemoryMaterialKind
import com.example.myapplication.model.MemoryMaterialRef
import com.example.myapplication.model.MemoryRecallLayer
import com.example.myapplication.model.MemoryRecallMaterial
import com.example.myapplication.model.MemoryRecallOmission
import com.example.myapplication.model.MemoryRecallOmissionReason
import com.example.myapplication.model.MemoryRecallPlan
import com.example.myapplication.model.MemoryRecord
import com.example.myapplication.model.MemoryRecordKind
import com.example.myapplication.model.MemoryRecordVersion
import com.example.myapplication.model.MemoryRuntimeContext
import com.example.myapplication.model.MemoryVectorAvailability
import com.example.myapplication.model.MemoryVectorHit
import com.example.myapplication.system.logging.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

private const val IMPORTED_MEMORY_LABEL = "导入资料（未验证本机原聊天，不视作本机共同经历） · "

class MemoryRecallEngine(
    private val archive: MemoryArchiveRepository,
    private val localSearch: MemoryLocalSearch = MemoryLocalIndex(),
    private val vectorSearch: MemoryVectorSearch = EmptyMemoryVectorSearch,
    private val budget: MemoryRecallBudget = MemoryRecallBudget(),
    private val vectorTimeoutMillis: Long = 1_200L,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val legacyLocalSearch = MemoryLocalIndex(allowLegacy = true)
    suspend fun recall(
        context: MemoryRuntimeContext,
        userInputText: String,
        recentMessages: List<ChatMessage>,
        maxExperiences: Int,
        tokenBudget: Int,
        allowVector: Boolean,
    ): MemoryRecallPlan? {
        val snapshot = archive.getRecallSnapshot(context) ?: return null
        val queries = MemoryRecallQueryBuilder.build(userInputText, recentMessages)
        val scopedLocal = try {
            localSearch.search(context, snapshot.records, queries)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            AppLogger.w("MemoryRecall", "local index failed; rebuilding scoped terms")
            MemoryLocalIndex().search(context, snapshot.records, queries)
        }
        val local =
            scopedLocal + if (snapshot.legacyRecords.isEmpty()) emptyList() else legacyLocalSearch.search(
                context,
                snapshot.legacyRecords,
                queries
            )
        var vectorAvailability = MemoryVectorAvailability.NOT_REQUESTED
        val vectorQueries = queries.filter { it.currentUser }.map { it.text }
        val vectors =
            if (allowVector && vectorQueries.isNotEmpty() && vectorSearch !== EmptyMemoryVectorSearch) {
                try {
                    val result = withTimeoutOrNull(vectorTimeoutMillis.coerceAtLeast(1L)) {
                        vectorSearch.search(context, snapshot.records, vectorQueries)
                    }
                    vectorAvailability = when {
                        result == null -> MemoryVectorAvailability.TIMED_OUT
                        result.isEmpty() -> MemoryVectorAvailability.EMPTY
                        else -> MemoryVectorAvailability.AVAILABLE
                    }
                    result.orEmpty()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    AppLogger.w("MemoryRecall", "vector query failed; local recall retained")
                    vectorAvailability = MemoryVectorAvailability.FAILED
                    emptyList()
                }
            } else emptyList()
        val records = (snapshot.records + snapshot.legacyRecords).associateBy { it.id }
        val legacyIds = snapshot.legacyRecords.map { it.id }.toSet()
        val fused = fuse(local, vectors.filterNot { it.recordId in legacyIds }, records)
        val eventsById = snapshot.events.associateBy { it.id }
        val eventMemberships =
            snapshot.eventMemberships + snapshot.events.associate { it.id to it.associatedRecords }

        fun recordMaterial(record: MemoryRecord, layer: MemoryRecallLayer) = MemoryRecallMaterial(
            MemoryMaterialRef(
                MemoryMaterialKind.RECORD,
                record.id,
                record.revision,
                0,
                record.content.length,
                MemoryArchiveIdentity.fingerprint(record.content),
                snapshot.recordSources[record.id].orEmpty(),
                legacy = record.id in legacyIds,
                imported = record.id in snapshot.importedRecordIds,
            ),
            layer, record.content,
            (if (record.id in snapshot.importedRecordIds) IMPORTED_MEMORY_LABEL else "") +
                    if (record.id in legacyIds) "旧未分类材料（来源未验证，不当作已确认事实）" else when (record.kind) {
                        MemoryRecordKind.OPINION -> "主观看法"
                        MemoryRecordKind.WISH -> "愿望"
                        MemoryRecordKind.FACT -> "有来源的事实原料"
                        MemoryRecordKind.LEGACY_UNCLASSIFIED -> "未分类材料"
                        MemoryRecordKind.EPISODE -> "经历"
                    },
        )

        val candidates = buildList {
            snapshot.residents.forEach {
                add(
                    MemoryRecallCandidate(
                        it.material(MemoryRecallLayer.RESIDENT),
                        2.0
                    )
                )
            }
            records.values.filter { it.pinned && (it.pinnedUntil == null || it.pinnedUntil > now()) }
                .sortedWith(compareByDescending<MemoryRecord> { it.importance }.thenBy { it.id })
                .forEach {
                    add(
                        MemoryRecallCandidate(
                            recordMaterial(it, MemoryRecallLayer.PINNED),
                            3.0 + it.importance / 1000.0
                        )
                    )
                }
            fused.forEach { hit ->
                val record = records[hit.recordId] ?: return@forEach
                add(
                    MemoryRecallCandidate(
                        recordMaterial(record, MemoryRecallLayer.EXPERIENCE),
                        hit.rankScore + if (hit.currentUserMatch) 10.0 else 0.0,
                        hit.spanStart,
                        hit.spanEnd,
                        hit.requiredStart,
                        hit.requiredEnd
                    )
                )
            }
            // 先登记直接命中，防止事件展开抢走同一记录的真实命中范围与独立话题名额。
            fused.forEach { hit ->
                val record = records[hit.recordId] ?: return@forEach
                eventMemberships.filterValues { members -> members.any { it.recordId == record.id } }
                    .forEach eventGroup@{ (eventId, members) ->
                        val event = eventsById[eventId]
                        if (event != null) {
                            add(
                                MemoryRecallCandidate(
                                    event.material(MemoryRecallLayer.EXPERIENCE),
                                    hit.rankScore + if (hit.currentUserMatch) 9.0 else 1.0
                                )
                            )
                        }
                        val eventGeneration =
                            snapshot.eventGenerations[eventId] ?: return@eventGroup
                        val covered = event?.supportingRecords.orEmpty().map { it.recordId }.toSet()
                        val chronological = members.filter { it.recordId !in covered }
                            .mapNotNull { records[it.recordId] }
                            .sortedWith(compareBy<MemoryRecord> {
                                it.occurredAt ?: it.createdAt
                            }.thenBy { it.id })
                        val continuity = if (event == null) {
                            (chronological.take(1) + chronological.asReversed()).distinctBy { it.id }
                        } else chronological.asReversed()
                        continuity.filter { it.id != record.id }.forEachIndexed { index, latest ->
                            val material = recordMaterial(latest, MemoryRecallLayer.EXPERIENCE)
                            add(
                                MemoryRecallCandidate(
                                    material.copy(
                                        ref = material.ref.copy(
                                            eventContext = MemoryEventRecallContext(
                                                eventId,
                                                eventGeneration,
                                                MemoryRecordVersion(record.id, record.revision),
                                            )
                                        ),
                                        label = if (event == null) "${material.label}（同事件的原始前情或进展）" else "${material.label}（尚未收入事件摘要的新进展）",
                                    ),
                                    hit.rankScore + (if (hit.currentUserMatch) 8.0 else 0.5) + 1.0 / (1000 + index),
                                )
                            )
                        }
                    }
            }
            snapshot.states.forEach {
                add(
                    MemoryRecallCandidate(
                        it.material(MemoryRecallLayer.STATE),
                        2.5
                    )
                )
            }
        }
        val plan =
            budget.allocate(context, candidates, maxExperiences, tokenBudget, vectorAvailability)
        if (archive.validateRecallMaterials(context, plan.materials.map { it.ref })) return plan
        return plan.copy(
            materials = emptyList(), section = "", estimatedTokens = 0,
            omitted = (plan.omitted + plan.selected.map {
                MemoryRecallOmission(
                    it,
                    MemoryRecallOmissionReason.SOURCE_CHANGED
                )
            }).distinct()
        )
    }

    private fun MemoryDerivedRecallEntry.material(layer: MemoryRecallLayer) = MemoryRecallMaterial(
        MemoryMaterialRef(
            kind,
            id,
            revision,
            0,
            content.length,
            MemoryArchiveIdentity.fingerprint(content),
            sourceRefs,
            supportingRecords,
            imported = imported,
        ),
        layer, content, (if (imported) IMPORTED_MEMORY_LABEL else "") + label,
    )

    private fun fuse(
        local: List<MemoryLocalHit>,
        vectors: List<MemoryVectorHit>,
        records: Map<String, MemoryRecord>
    ): List<MemoryLocalHit> {
        val combined = local.associateByTo(linkedMapOf()) { it.recordId }
        vectors.filter { hit ->
            val record = records[hit.recordId]
            record != null && record.revision == hit.revision && hit.score.isFinite() && hit.spanStart >= 0 &&
                    hit.spanEnd > hit.spanStart && hit.spanEnd <= record.content.length
        }.sortedByDescending { it.score }.distinctBy { it.recordId }
            .forEachIndexed { rank, vector ->
                val previous = combined[vector.recordId]
                combined[vector.recordId] = if (previous == null) MemoryLocalHit(
                    vector.recordId, vector.revision, vector.spanStart, vector.spanEnd,
                    1.0 / (16.0 + rank), true
                )
                else previous.copy(
                    rankScore = previous.rankScore + 1.0 / (16.0 + rank), currentUserMatch = true,
                    spanStart = if (previous.currentUserMatch) previous.spanStart else vector.spanStart,
                    spanEnd = if (previous.currentUserMatch) previous.spanEnd else vector.spanEnd,
                    requiredStart = if (previous.currentUserMatch) previous.requiredStart else vector.spanStart,
                    requiredEnd = if (previous.currentUserMatch) previous.requiredEnd else vector.spanEnd
                )
            }
        return combined.values.sortedWith(compareByDescending<MemoryLocalHit> { it.currentUserMatch }.thenByDescending { it.rankScore }
            .thenBy { it.recordId })
    }
}
