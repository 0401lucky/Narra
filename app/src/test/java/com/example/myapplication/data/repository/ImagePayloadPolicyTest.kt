package com.example.myapplication.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ImagePayloadPolicyTest {
    @Test
    fun estimateDecodedBase64Size_handlesPaddingAndWhitespace() {
        assertEquals(3, estimateDecodedBase64Size(" AQID "))
        assertEquals(2, estimateDecodedBase64Size("AQI="))
        assertEquals(1, estimateDecodedBase64Size("AQ=="))
    }

    @Test
    fun requireImageWithinLimit_acceptsExactMax() {
        requireImageWithinLimit(MAX_IMAGE_BYTES)
    }

    @Test
    fun requireImageWithinLimit_rejectsMaxPlusOne() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            requireImageWithinLimit(MAX_IMAGE_BYTES + 1)
        }

        assertEquals("图片过大，最大支持 20 MB", error.message)
    }

    @Test
    fun detectImageType_returnsNullForUnknownBytes() {
        assertEquals(null, detectImageType(byteArrayOf(0x01, 0x02, 0x03)))
    }

    @Test
    fun detectImageType_detectsPng() {
        val bytes = byteArrayOf(
            0x89.toByte(),
            0x50,
            0x4E,
            0x47,
            0x0D,
            0x0A,
            0x1A,
            0x0A,
        )

        assertTrue(detectImageType(bytes) == DetectedImageType.PNG)
    }
}
