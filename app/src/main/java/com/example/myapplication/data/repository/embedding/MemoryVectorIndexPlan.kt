package com.example.myapplication.data.repository.embedding

import com.example.myapplication.context.MemoryArchiveIdentity
import com.example.myapplication.context.MemoryEmbeddingChunker
import com.example.myapplication.context.MemoryVectorMath
import com.example.myapplication.data.local.memory.MemoryEmbeddingEntity
import com.example.myapplication.data.local.memory.MemoryEmbeddingIndexEntity
import com.example.myapplication.model.MemoryRecord
import com.example.myapplication.system.json.AppJson
import com.example.myapplication.system.logging.AppLogger

internal data class MemoryVectorRecordRef(
    val recordId: String,
    val revision: Long,
    val fingerprint: String,
    val chunkCount: Int,
)

internal data class MemoryVectorChunkRef(
    val recordId: String,
    val revision: Long,
    val chunkIndex: Int,
    val start: Int,
    val end: Int,
) {
    val key: Pair<String, Int> get() = recordId to chunkIndex
}

internal data class PlannedMemoryVectorChunk(val ref: MemoryVectorChunkRef, val text: String)

internal class MemoryVectorIndexPlan(
    val records: List<MemoryVectorRecordRef>,
    val chunks: List<PlannedMemoryVectorChunk>,
) {
    val recordsJson: String = AppJson.gson.toJson(records)
    val revisions: Map<String, Long> = records.associate { it.recordId to it.revision }
    val chunksByKey = chunks.associateBy { it.ref.key }

    fun accepts(embedding: MemoryEmbeddingEntity, dimensions: Int): Boolean {
        val expected = chunksByKey[embedding.recordId to embedding.chunkIndex]?.ref ?: return false
        return embedding.recordRevision == expected.revision && embedding.spanStart == expected.start &&
                embedding.spanEnd == expected.end && embedding.dimensions == dimensions &&
                embedding.status == "READY" && MemoryVectorMath.decode(
            embedding.vector,
            dimensions
        ) != null
    }

    fun isComplete(index: MemoryEmbeddingIndexEntity, rows: List<MemoryEmbeddingEntity>): Boolean =
        index.totalChunks == chunks.size && index.recordsJson == recordsJson &&
                index.dimensions in 1..MemoryVectorMath.MAX_DIMENSIONS &&
                rows.filter { accepts(it, index.dimensions) }
                    .map { it.recordId to it.chunkIndex }.toSet().size == chunks.size

    companion object {
        fun create(
            records: List<MemoryRecord>,
            chunker: MemoryEmbeddingChunker
        ): MemoryVectorIndexPlan {
            val refs = mutableListOf<MemoryVectorRecordRef>()
            val chunks = mutableListOf<PlannedMemoryVectorChunk>()
            records.sortedBy { it.id }.forEach { record ->
                val parts = chunker.split(record.content)
                if (parts.isNotEmpty()) {
                    refs += MemoryVectorRecordRef(
                        record.id,
                        record.revision,
                        MemoryArchiveIdentity.fingerprint(record.content),
                        parts.size,
                    )
                    chunks += parts.map {
                        PlannedMemoryVectorChunk(
                            MemoryVectorChunkRef(
                                record.id,
                                record.revision,
                                it.index,
                                it.start,
                                it.end
                            ),
                            it.text,
                        )
                    }
                }
            }
            return MemoryVectorIndexPlan(refs, chunks)
        }

        fun parseRecords(json: String): List<MemoryVectorRecordRef>? = try {
            AppJson.gson.fromJson(json, Array<MemoryVectorRecordRef>::class.java)?.toList()
                ?.takeIf { refs ->
                    refs.all { it.recordId.isNotBlank() && it.revision > 0 && it.fingerprint.length == 64 && it.chunkCount > 0 } &&
                            refs.map { it.recordId }.distinct().size == refs.size
                }
        } catch (_: Exception) {
            AppLogger.w("MemoryVectorIndex", "Invalid index record metadata")
            null
        }

        fun parsePending(json: String): List<MemoryVectorChunkRef>? = try {
            AppJson.gson.fromJson(json, Array<MemoryVectorChunkRef>::class.java)?.toList()
                ?.takeIf { refs ->
                    refs.all { it.recordId.isNotBlank() && it.revision > 0 && it.chunkIndex >= 0 && it.start >= 0 && it.end > it.start }
                }
        } catch (_: Exception) {
            AppLogger.w("MemoryVectorIndex", "Invalid pending index metadata")
            null
        }
    }
}
