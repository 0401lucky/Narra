package com.example.myapplication.context

import com.example.myapplication.model.MemoryRecord
import com.example.myapplication.model.MemoryRecordStatus
import com.example.myapplication.model.MemoryRuntimeContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.ln

data class MemoryLocalHit(
    val recordId: String,
    val revision: Long,
    val spanStart: Int,
    val spanEnd: Int,
    val rankScore: Double,
    val currentUserMatch: Boolean,
    val requiredStart: Int = spanStart,
    val requiredEnd: Int = spanEnd,
)

fun interface MemoryLocalSearch {
    suspend fun search(
        context: MemoryRuntimeContext,
        records: List<MemoryRecord>,
        queries: List<MemoryRecallQuery>
    ): List<MemoryLocalHit>
}

/** 每个合法空间/身份单独索引。只重建变化记录的分段词项，绝不在主线程分词。 */
class MemoryLocalIndex(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val allowLegacy: Boolean = false,
) : MemoryLocalSearch {
    private data class Document(
        val revision: Long,
        val fingerprint: String,
        val chunks: List<Chunk>
    )

    private data class Chunk(
        val start: Int,
        val end: Int,
        val text: String,
        val terms: Map<String, Int>,
        val dates: Set<String>
    ) {
        val length: Int = terms.values.sum().coerceAtLeast(1)
    }

    private data class Key(
        val character: String,
        val space: String,
        val persona: String,
        val generation: Long
    )

    private val mutex = Mutex()
    private val indexes = linkedMapOf<Key, MutableMap<String, Document>>()

    override suspend fun search(
        context: MemoryRuntimeContext,
        records: List<MemoryRecord>,
        queries: List<MemoryRecallQuery>,
    ): List<MemoryLocalHit> = withContext(dispatcher) {
        mutex.withLock {
            val key =
                Key(context.characterId, context.spaceId, context.personaKey, context.generation)
            val index = indexes.remove(key) ?: linkedMapOf()
            indexes[key] = index
            while (indexes.size > 8) indexes.remove(indexes.keys.first())
            val legal = records.filter { record ->
                record.status in setOf(MemoryRecordStatus.ACTIVE, MemoryRecordStatus.ARCHIVED) &&
                        ((record.spaceId == context.spaceId && record.characterId == context.characterId && record.personaKey == context.personaKey) ||
                                (allowLegacy && record.legacyScopeType.isNotBlank() && record.characterId in setOf(
                                    context.characterId,
                                    com.example.myapplication.model.MEMORY_UNASSIGNED_CHARACTER
                                )))
            }
            index.keys.retainAll(legal.map { it.id }.toSet())
            legal.forEach { record ->
                coroutineContext.ensureActive()
                val fingerprint = MemoryArchiveIdentity.fingerprint(record.content)
                val old = index[record.id]
                if (old?.revision != record.revision || old.fingerprint != fingerprint) {
                    index[record.id] = Document(
                        record.revision,
                        fingerprint,
                        MemoryRecallText.chunks(record.content).map { span ->
                            val text = record.content.substring(span.first, span.last + 1)
                            Chunk(
                                span.first,
                                span.last + 1,
                                text,
                                MemoryRecallText.terms(text).groupingBy { it }.eachCount(),
                                MemoryRecallText.datesAndNumbers(text)
                            )
                        })
                }
            }
            rank(index, queries)
        }
    }

    private suspend fun rank(
        index: Map<String, Document>,
        queries: List<MemoryRecallQuery>
    ): List<MemoryLocalHit> {
        if (index.isEmpty() || queries.isEmpty()) return emptyList()
        val chunks = index.flatMap { (id, document) ->
            document.chunks.map {
                Triple(
                    id,
                    document.revision,
                    it
                )
            }
        }
        val frequency = mutableMapOf<String, Int>()
        chunks.forEach { (_, _, chunk) ->
            chunk.terms.keys.forEach {
                frequency[it] = (frequency[it] ?: 0) + 1
            }
        }
        val averageLength = chunks.map { it.third.length }.average().coerceAtLeast(1.0)
        val combined = mutableMapOf<String, MemoryLocalHit>()
        val bestSpanScore = mutableMapOf<String, Double>()
        queries.forEach { query ->
            coroutineContext.ensureActive()
            val local = mutableMapOf<String, Pair<Double, Triple<String, Long, Chunk>>>()
            val entity = mutableMapOf<String, Pair<Double, Triple<String, Long, Chunk>>>()
            chunks.forEach { candidate ->
                val chunk = candidate.third
                var score = 0.0
                var exact = 0.0
                query.terms.forEach { term ->
                    val tf = chunk.terms[term] ?: return@forEach
                    val df = frequency[term] ?: 0
                    val idf = ln(1.0 + (chunks.size - df + 0.5) / (df + 0.5))
                    score += idf * tf * 2.2 / (tf + 1.2 * (0.25 + 0.75 * chunk.length / averageLength))
                    if (term.length >= 2 && df <= maxOf(1, chunks.size / 4)) exact += idf
                }
                exact += query.datesAndNumbers.count { it in chunk.dates } * 5.0
                if (score > (local[candidate.first]?.first ?: 0.0)) local[candidate.first] =
                    score to candidate
                if (exact > (entity[candidate.first]?.first ?: 0.0)) entity[candidate.first] =
                    exact to candidate
            }
            listOf(local to 1.0, entity to 1.3).forEach { (channel, channelWeight) ->
                channel.values.sortedWith(compareByDescending<Pair<Double, Triple<String, Long, Chunk>>> { it.first }.thenBy { it.second.first })
                    .forEachIndexed { rank, (rawScore, candidate) ->
                        val (id, revision, chunk) = candidate
                        val contribution = query.weight * channelWeight / (16.0 + rank)
                        val prior = combined[id]
                        val spanScore =
                            rawScore * query.weight * if (query.currentUser) 10.0 else 1.0
                        val best = spanScore > (bestSpanScore[id] ?: -1.0)
                        if (best) bestSpanScore[id] = spanScore
                        val matchedTerm =
                            query.terms.filter { it in chunk.terms }.maxByOrNull { term ->
                                (if (term in query.datesAndNumbers) 100.0 else 0.0) + ln(
                                    1.0 + chunks.size.toDouble() / (frequency[term] ?: 1)
                                ) + term.length / 1000.0
                            }
                        val matchOffset =
                            matchedTerm?.let { chunk.text.indexOf(it, ignoreCase = true) }
                                ?.coerceAtLeast(0) ?: 0
                        val focusedStart = MemoryRecallText.safeBoundary(
                            chunk.text,
                            (matchOffset - 24).coerceAtLeast(0)
                        ) + chunk.start
                        val focusedEnd = MemoryRecallText.safeBoundary(
                            chunk.text,
                            matchOffset + maxOf(matchedTerm?.length ?: 0, 280)
                        ) + chunk.start
                        combined[id] = MemoryLocalHit(
                            id, revision,
                            if (best) focusedStart else prior?.spanStart ?: focusedStart,
                            if (best) focusedEnd else prior?.spanEnd ?: focusedEnd,
                            (prior?.rankScore ?: 0.0) + contribution,
                            prior?.currentUserMatch == true || query.currentUser,
                            if (best) chunk.start + matchOffset else prior?.requiredStart
                                ?: chunk.start + matchOffset,
                            if (best) chunk.start + matchOffset + (matchedTerm?.length
                                ?: 1) else prior?.requiredEnd
                                ?: chunk.start + matchOffset + (matchedTerm?.length ?: 1),
                        )
                    }
            }
        }
        return combined.values.sortedWith(compareByDescending<MemoryLocalHit> { it.currentUserMatch }.thenByDescending { it.rankScore }
            .thenBy { it.recordId })
    }
}
