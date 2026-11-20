package com.example.myapplication.model

/** 位置相对该 revision 的完整正文，使用 UTF-16 半开区间。 */
data class MemoryVectorHit(
    val recordId: String,
    val revision: Long,
    val chunkIndex: Int,
    val spanStart: Int,
    val spanEnd: Int,
    val score: Double,
)

data class MemoryEmbeddingChunk(
    val index: Int,
    val start: Int,
    val end: Int,
    val text: String,
)

enum class MemoryEmbeddingPurpose { QUERY, INDEX }

data class MemoryEmbeddingBatch(
    val vectors: List<FloatArray>,
    val dimensions: Int,
)

data class MemoryVectorIndexProgress(
    val generation: Long,
    val status: String,
    val active: Boolean,
    val totalChunks: Int,
    val completedChunks: Int,
    val requiresManualRetry: Boolean,
    val failureCode: String,
    val pendingImportedRecords: Int = 0,
)

sealed interface MemoryVectorBuildResult {
    data object Disabled : MemoryVectorBuildResult
    data object Unavailable : MemoryVectorBuildResult
    data class Ready(
        val generation: Long,
        val records: Int,
        val chunks: Int,
        val pendingImportedRecords: Int = 0
    ) :
        MemoryVectorBuildResult

    data class NeedsReview(val generation: Long, val completedChunks: Int) : MemoryVectorBuildResult
    data class Failed(val generation: Long, val code: String) : MemoryVectorBuildResult
}
