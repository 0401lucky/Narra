package com.example.myapplication.data.repository.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.Base64

class TavernCharacterImageAdapterTest {
    private val adapter = TavernCharacterImageAdapter()

    @Test
    fun decodeAsBundle_readsPngCharaChunk() {
        val json = """
            {
              "name": "白塔侦探",
              "description": "一名沉着的侦探。",
              "scenario": "你正在白塔城破案。",
              "first_mes": "把线索整理给我。"
            }
        """.trimIndent()
        val pngBytes = buildPngCharacterCard(json)

        val bundle = adapter.decodeAsBundle(
            bytes = pngBytes,
            fileName = "detective.png",
            mimeType = "image/png",
        )

        val assistant = bundle?.assistants?.singleOrNull()
        assertNotNull(assistant)
        assertEquals("白塔侦探", assistant?.name)
        assertEquals("你正在白塔城破案。", assistant?.scenario)
        assertEquals("把线索整理给我。", assistant?.greeting)
        assertTrue(assistant?.memoryEnabled == true)
    }

    @Test
    fun decodeAsBundle_readsBracketWrappedPngTextChunk() {
        val json = """
            {
              "spec": "chara_card_v2",
              "data": {
                "name": "夜巡者",
                "description": "负责守夜的调查员。",
                "scenario": "你正在旧港追查失踪案。"
              }
            }
        """.trimIndent()
        val pngBytes = buildWrappedPngCharacterCard(json)

        val bundle = adapter.decodeAsBundle(
            bytes = pngBytes,
            fileName = "night-watch.png",
            mimeType = "image/png",
        )

        val assistant = bundle?.assistants?.singleOrNull()
        assertNotNull(assistant)
        assertEquals("夜巡者", assistant?.name)
        assertEquals("你正在旧港追查失踪案。", assistant?.scenario)
    }

    private fun buildPngCharacterCard(json: String): ByteArray {
        val base64 = Base64.getEncoder().encodeToString(json.toByteArray(StandardCharsets.UTF_8))
        val output = ByteArrayOutputStream()
        output.write(
            byteArrayOf(
                0x89.toByte(),
                0x50,
                0x4E,
                0x47,
                0x0D,
                0x0A,
                0x1A,
                0x0A,
            ),
        )
        writeChunk(output, "IHDR", ByteArray(13))
        writeChunk(output, "tEXt", "chara\u0000$base64".toByteArray(StandardCharsets.ISO_8859_1))
        writeChunk(output, "IEND", ByteArray(0))
        return output.toByteArray()
    }

    private fun buildWrappedPngCharacterCard(json: String): ByteArray {
        val base64 = Base64.getEncoder().encodeToString(json.toByteArray(StandardCharsets.UTF_8))
        val output = ByteArrayOutputStream()
        output.write(
            byteArrayOf(
                0x89.toByte(),
                0x50,
                0x4E,
                0x47,
                0x0D,
                0x0A,
                0x1A,
                0x0A,
            ),
        )
        writeChunk(output, "IHDR", ByteArray(13))
        writeChunk(output, "tEXt", "Comment\u0000[chara:$base64]".toByteArray(StandardCharsets.ISO_8859_1))
        writeChunk(output, "IEND", ByteArray(0))
        return output.toByteArray()
    }

    private fun writeChunk(
        output: ByteArrayOutputStream,
        type: String,
        data: ByteArray,
    ) {
        output.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(data.size).array())
        output.write(type.toByteArray(StandardCharsets.ISO_8859_1))
        output.write(data)
        output.write(ByteArray(4))
    }
}
