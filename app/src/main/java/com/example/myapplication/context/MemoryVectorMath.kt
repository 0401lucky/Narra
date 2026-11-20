package com.example.myapplication.context

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

object MemoryVectorMath {
    const val MAX_DIMENSIONS = 65_536

    fun normalized(values: FloatArray, dimensions: Int = values.size): FloatArray? {
        if (dimensions !in 1..MAX_DIMENSIONS || values.size != dimensions) return null
        var squaredNorm = 0.0
        for (value in values) {
            if (!value.isFinite()) return null
            squaredNorm += value.toDouble() * value
        }
        if (!squaredNorm.isFinite() || squaredNorm <= 0.0) return null
        val norm = sqrt(squaredNorm)
        return FloatArray(dimensions) { (values[it] / norm).toFloat() }
    }

    fun encode(values: FloatArray): ByteArray {
        val normalized = requireNotNull(normalized(values)) { "向量数据无效" }
        return ByteBuffer.allocate(normalized.size * Float.SIZE_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply { normalized.forEach(::putFloat) }
            .array()
    }

    fun decode(bytes: ByteArray, dimensions: Int): FloatArray? {
        if (dimensions !in 1..MAX_DIMENSIONS || bytes.size != dimensions * Float.SIZE_BYTES) return null
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return normalized(FloatArray(dimensions) { buffer.float }, dimensions)
    }

    fun cosine(left: FloatArray, right: FloatArray): Double? {
        val a = normalized(left) ?: return null
        val b = normalized(right, a.size) ?: return null
        var dot = 0.0
        a.indices.forEach { dot += a[it].toDouble() * b[it] }
        return dot.takeIf { it.isFinite() }?.coerceIn(-1.0, 1.0)
    }
}
