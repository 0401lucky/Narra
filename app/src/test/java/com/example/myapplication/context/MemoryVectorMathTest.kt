package com.example.myapplication.context

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MemoryVectorMathTest {
    @Test
    fun encodeDecode_usesNormalizedLittleEndianFloat32() {
        val encoded = MemoryVectorMath.encode(floatArrayOf(3f, 4f))
        val buffer = ByteBuffer.wrap(encoded).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0.6f, buffer.float, 0.000001f)
        assertEquals(0.8f, buffer.float, 0.000001f)
        assertArrayEquals(
            floatArrayOf(0.6f, 0.8f),
            MemoryVectorMath.decode(encoded, 2)!!,
            0.000001f
        )
        assertEquals(
            1.0,
            MemoryVectorMath.cosine(floatArrayOf(3f, 4f), floatArrayOf(6f, 8f))!!,
            0.000001
        )
    }

    @Test
    fun malformedVectors_neverParticipateInSimilarity() {
        listOf(
            floatArrayOf(),
            floatArrayOf(0f, 0f),
            floatArrayOf(Float.NaN),
            floatArrayOf(Float.POSITIVE_INFINITY)
        )
            .forEach { assertNull(MemoryVectorMath.normalized(it)) }
        assertNull(MemoryVectorMath.decode(ByteArray(3), 1))
        assertNull(MemoryVectorMath.decode(ByteArray(8), 1))
        assertNull(MemoryVectorMath.cosine(floatArrayOf(1f), floatArrayOf(1f, 0f)))
        assertNull(MemoryVectorMath.cosine(floatArrayOf(0f), floatArrayOf(1f)))
    }

    @Test
    fun largeFiniteComponents_normalizeWithoutFloatOverflow() {
        val vector = floatArrayOf(Float.MAX_VALUE, Float.MAX_VALUE)
        assertNotNull(MemoryVectorMath.normalized(vector))
        assertEquals(1.0, MemoryVectorMath.cosine(vector, vector)!!, 0.000001)
    }
}
