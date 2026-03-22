package com.example.myapplication.data.repository.context

import android.util.Base64
import com.example.myapplication.model.ContextDataBundle
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.zip.Inflater

class TavernCharacterImageAdapter(
    private val tavernCharacterAdapter: TavernCharacterAdapter = TavernCharacterAdapter(),
) {
    fun decodeAsBundle(
        bytes: ByteArray,
        fileName: String = "",
        mimeType: String = "",
    ): ContextDataBundle? {
        if (!looksLikeSupportedImage(bytes, fileName, mimeType)) {
            return null
        }
        val charaPayload = extractPngCharaPayload(bytes) ?: return null
        val decodedJson = decodeBase64Payload(charaPayload) ?: return null
        return tavernCharacterAdapter.decodeAsBundle(decodedJson)
    }

    private fun looksLikeSupportedImage(
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
    ): Boolean {
        if (mimeType.equals("image/png", ignoreCase = true)) {
            return true
        }
        if (fileName.endsWith(".png", ignoreCase = true)) {
            return true
        }
        return bytes.size >= PNG_SIGNATURE.size &&
            bytes.copyOfRange(0, PNG_SIGNATURE.size).contentEquals(PNG_SIGNATURE)
    }

    private fun extractPngCharaPayload(bytes: ByteArray): String? {
        if (bytes.size < PNG_SIGNATURE.size || !bytes.copyOfRange(0, PNG_SIGNATURE.size).contentEquals(PNG_SIGNATURE)) {
            return null
        }

        var offset = PNG_SIGNATURE.size
        while (offset + 8 <= bytes.size) {
            val length = readInt(bytes, offset) ?: return null
            val typeStart = offset + 4
            val dataStart = typeStart + 4
            val dataEnd = dataStart + length
            val crcEnd = dataEnd + 4
            if (length < 0 || crcEnd > bytes.size) {
                return null
            }
            val chunkType = bytes.toString(typeStart, typeStart + 4)
            val chunkData = bytes.copyOfRange(dataStart, dataEnd)
            when (chunkType) {
                "tEXt" -> parseTextChunk(chunkData)
                "zTXt" -> parseCompressedTextChunk(chunkData)
                "iTXt" -> parseInternationalTextChunk(chunkData)
                else -> null
            }?.let { payload ->
                return payload
            }
            offset = crcEnd
            if (chunkType == "IEND") {
                break
            }
        }
        return null
    }

    private fun parseTextChunk(data: ByteArray): String? {
        extractBracketWrappedPayload(data.toString(0, data.size))?.let { return it }
        val separatorIndex = data.indexOf(0)
        if (separatorIndex <= 0) return null
        val key = data.toString(0, separatorIndex)
        val value = data.toString(separatorIndex + 1, data.size)
        return resolveCharaPayload(key, value)
    }

    private fun parseCompressedTextChunk(data: ByteArray): String? {
        val separatorIndex = data.indexOf(0)
        if (separatorIndex <= 0 || separatorIndex + 2 > data.size) return null
        val key = data.toString(0, separatorIndex)
        val compressedBytes = data.copyOfRange(separatorIndex + 2, data.size)
        val value = inflateToString(compressedBytes) ?: return null
        return resolveCharaPayload(key, value)
    }

    private fun parseInternationalTextChunk(data: ByteArray): String? {
        val keywordEnd = data.indexOf(0)
        if (keywordEnd <= 0 || keywordEnd + 5 > data.size) return null
        val key = data.toString(0, keywordEnd)
        val compressionFlag = data[keywordEnd + 1].toInt()
        var cursor = keywordEnd + 3
        val languageEnd = data.indexOf(0, cursor)
        if (languageEnd == -1) return null
        cursor = languageEnd + 1
        val translatedEnd = data.indexOf(0, cursor)
        if (translatedEnd == -1) return null
        cursor = translatedEnd + 1
        if (cursor > data.size) return null
        val textBytes = data.copyOfRange(cursor, data.size)
        val value = if (compressionFlag == 1) {
            inflateToString(textBytes)
        } else {
            textBytes.toString(StandardCharsets.UTF_8)
        } ?: return null
        return resolveCharaPayload(key, value)
    }

    private fun resolveCharaPayload(
        key: String,
        value: String,
    ): String? {
        if (key.equals("chara", ignoreCase = true)) {
            return value
        }
        return extractBracketWrappedPayload(value)
    }

    private fun extractBracketWrappedPayload(value: String): String? {
        return CHARA_WRAPPED_REGEX.find(value)?.groupValues?.getOrNull(1)?.trim()
    }

    private fun inflateToString(bytes: ByteArray): String? {
        return runCatching {
            val inflater = Inflater()
            inflater.setInput(bytes)
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count == 0 && inflater.needsInput()) {
                    break
                }
                output.write(buffer, 0, count)
            }
            inflater.end()
            output.toString(StandardCharsets.UTF_8.name())
        }.getOrNull()
    }

    private fun decodeBase64Payload(value: String): String? {
        val sanitized = value.trim().filterNot(Char::isWhitespace)
        return runCatching {
            val decodedBytes = Base64.decode(sanitized, Base64.DEFAULT)
            String(decodedBytes, StandardCharsets.UTF_8)
        }.getOrNull()
    }

    private fun readInt(bytes: ByteArray, offset: Int): Int? {
        if (offset + 4 > bytes.size) return null
        return ByteBuffer.wrap(bytes, offset, 4)
            .order(ByteOrder.BIG_ENDIAN)
            .int
    }

    private fun ByteArray.indexOf(target: Int, startIndex: Int = 0): Int {
        for (index in startIndex until size) {
            if (this[index].toInt() == target) {
                return index
            }
        }
        return -1
    }

    private fun ByteArray.toString(start: Int, end: Int, charset: Charset = StandardCharsets.ISO_8859_1): String {
        return String(this, start, end - start, charset)
    }

    private companion object {
        val CHARA_WRAPPED_REGEX = Regex(
            pattern = """\[\s*chara\s*:\s*(.+?)]""",
            options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(),
            0x50,
            0x4E,
            0x47,
            0x0D,
            0x0A,
            0x1A,
            0x0A,
        )
    }
}
