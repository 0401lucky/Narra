package com.example.myapplication.data.repository

import java.io.ByteArrayOutputStream
import java.io.InputStream

internal const val MAX_IMAGE_BYTES = 20 * 1024 * 1024

internal fun requireImageWithinLimit(sizeBytes: Int) {
    require(sizeBytes <= MAX_IMAGE_BYTES) {
        "图片过大，最大支持 20 MB"
    }
}

internal fun requireImageLengthWithinLimit(sizeBytes: Long) {
    require(sizeBytes <= MAX_IMAGE_BYTES) {
        "图片过大，最大支持 20 MB"
    }
}

internal fun estimateDecodedBase64Size(base64Data: String): Int {
    val normalized = base64Data.filterNot { it.isWhitespace() }
    if (normalized.isBlank()) {
        return 0
    }
    val padding = normalized.takeLast(2).count { it == '=' }
    return ((normalized.length * 3) / 4 - padding).coerceAtLeast(0)
}

internal fun readBytesWithImageLimit(input: InputStream): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0
    while (true) {
        val readSize = input.read(buffer)
        if (readSize <= 0) {
            break
        }
        total += readSize
        requireImageWithinLimit(total)
        output.write(buffer, 0, readSize)
    }
    return output.toByteArray()
}

internal fun detectImageType(bytes: ByteArray): DetectedImageType? {
    if (bytes.size >= 8 &&
        bytes[0] == 0x89.toByte() &&
        bytes[1] == 0x50.toByte() &&
        bytes[2] == 0x4E.toByte() &&
        bytes[3] == 0x47.toByte()
    ) {
        return DetectedImageType.PNG
    }
    if (bytes.size >= 3 &&
        bytes[0] == 0xFF.toByte() &&
        bytes[1] == 0xD8.toByte() &&
        bytes[2] == 0xFF.toByte()
    ) {
        return DetectedImageType.JPEG
    }
    if (bytes.size >= 6) {
        val header = bytes.copyOfRange(0, 6).toString(Charsets.US_ASCII)
        if (header == "GIF87a" || header == "GIF89a") {
            return DetectedImageType.GIF
        }
    }
    if (bytes.size >= 12) {
        val riff = bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII)
        val webp = bytes.copyOfRange(8, 12).toString(Charsets.US_ASCII)
        if (riff == "RIFF" && webp == "WEBP") {
            return DetectedImageType.WEBP
        }
    }
    if (bytes.size >= 2 &&
        bytes[0] == 0x42.toByte() &&
        bytes[1] == 0x4D.toByte()
    ) {
        return DetectedImageType.BMP
    }

    val textHeader = bytes.take(128).toByteArray()
        .toString(Charsets.UTF_8)
        .trimStart()
        .lowercase()
    if (textHeader.startsWith("<svg")) {
        return DetectedImageType.SVG
    }
    return null
}

internal enum class DetectedImageType(
    val extension: String,
    val mimeType: String,
) {
    PNG(
        extension = "png",
        mimeType = "image/png",
    ),
    JPEG(
        extension = "jpg",
        mimeType = "image/jpeg",
    ),
    GIF(
        extension = "gif",
        mimeType = "image/gif",
    ),
    WEBP(
        extension = "webp",
        mimeType = "image/webp",
    ),
    BMP(
        extension = "bmp",
        mimeType = "image/bmp",
    ),
    SVG(
        extension = "svg",
        mimeType = "image/svg+xml",
    ),
}
