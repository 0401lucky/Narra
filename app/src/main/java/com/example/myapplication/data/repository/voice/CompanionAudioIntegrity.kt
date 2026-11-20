package com.example.myapplication.data.repository.voice

import java.security.MessageDigest

internal data class CompanionAudioFormat(val mimeType: String, val extension: String)

/** 文件头只是起点；校验完整容器/采样帧，残缺响应不能发布为成功缓存。 */
internal object CompanionAudioIntegrity {
    const val MAX_AUDIO_BYTES = 10 * 1024 * 1024

    fun format(bytes: ByteArray): CompanionAudioFormat {
        require(bytes.isNotEmpty() && bytes.size <= MAX_AUDIO_BYTES) { "音频数据为空或过大" }
        return when {
            bytes.matches(0, "RIFF") -> {
                validateWave(bytes)
                CompanionAudioFormat("audio/wav", "wav")
            }

            else -> {
                validateMp3(bytes)
                CompanionAudioFormat("audio/mpeg", "mp3")
            }
        }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    private fun validateWave(bytes: ByteArray) {
        require(
            bytes.size >= 44 && bytes.matches(
                8,
                "WAVE"
            ) && bytes.u32(4) + 8 == bytes.size.toLong()
        ) {
            "WAV 音频不完整"
        }
        var offset = 12
        var alignment: Int? = null
        val dataSizes = mutableListOf<Long>()
        while (offset < bytes.size) {
            require(offset <= bytes.size - 8) { "WAV 音频块不完整" }
            val size = bytes.u32(offset + 4)
            val end = offset.toLong() + 8 + size
            require(end <= bytes.size && size <= Int.MAX_VALUE) { "WAV 音频块不完整" }
            if (bytes.matches(offset, "fmt ")) {
                require(size >= 16 && alignment == null) { "WAV 格式信息无效" }
                val start = offset + 8
                var encoding = bytes.u16(start)
                val channels = bytes.u16(start + 2)
                val sampleRate = bytes.u32(start + 4)
                val byteRate = bytes.u32(start + 8)
                val blockAlign = bytes.u16(start + 12)
                val bits = bytes.u16(start + 14)
                if (encoding == 0xfffe) {
                    require(size >= 40 && bytes.u16(start + 16) >= 22) { "WAV 扩展格式不完整" }
                    encoding = bytes.u16(start + 24)
                }
                require(encoding == 1 || encoding == 3) { "WAV 编码格式不受支持" }
                require(
                    channels in 1..32 && sampleRate in 1..384_000 && bits in listOf(
                        8,
                        16,
                        24,
                        32,
                        64
                    )
                ) {
                    "WAV 采样格式无效"
                }
                require(encoding != 3 || bits == 32 || bits == 64) { "WAV 浮点采样格式无效" }
                require(blockAlign == channels * bits / 8 && byteRate == sampleRate * blockAlign) {
                    "WAV 采样帧信息无效"
                }
                alignment = blockAlign
            }
            if (bytes.matches(offset, "data")) dataSizes += size
            val padded = end + (size and 1)
            require(padded <= bytes.size) { "WAV 音频填充不完整" }
            offset = padded.toInt()
        }
        val frameSize = requireNotNull(alignment) { "WAV 缺少格式信息" }
        require(dataSizes.isNotEmpty() && dataSizes.sum() > 0 && dataSizes.all { it % frameSize == 0L }) {
            "WAV 缺少完整采样帧"
        }
    }

    private fun validateMp3(bytes: ByteArray) {
        var offset = 0
        if (bytes.matches(0, "ID3")) {
            require(bytes.size >= 10 && bytes[3].toInt() in 2..4) { "MP3 标签不完整" }
            var tagSize = 0
            for (index in 6..9) {
                val value = bytes[index].toInt() and 255
                require(value and 128 == 0) { "MP3 标签长度无效" }
                tagSize = (tagSize shl 7) or value
            }
            offset =
                10 + tagSize + if (bytes[3].toInt() == 4 && bytes[5].toInt() and 16 != 0) 10 else 0
            require(offset <= bytes.size) { "MP3 标签不完整" }
        }
        var frames = 0
        var format: Pair<Int, Int>? = null
        while (offset < bytes.size) {
            if (bytes.size - offset == 128 && bytes.matches(offset, "TAG")) {
                offset = bytes.size
                break
            }
            require(offset <= bytes.size - 4) { "MP3 音频帧不完整" }
            val header = ((bytes[offset].toInt() and 255) shl 24) or
                    ((bytes[offset + 1].toInt() and 255) shl 16) or
                    ((bytes[offset + 2].toInt() and 255) shl 8) or (bytes[offset + 3].toInt() and 255)
            val version = (header ushr 19) and 3
            val layer = (header ushr 17) and 3
            val bitrateIndex = (header ushr 12) and 15
            val rateIndex = (header ushr 10) and 3
            require(header ushr 21 == 0x7ff && version != 1 && layer == 1 && bitrateIndex in 1..14 && rateIndex < 3) {
                "MP3 音频帧无效"
            }
            val rates = intArrayOf(44_100, 48_000, 32_000)
            val sampleRate = rates[rateIndex] / when (version) {
                3 -> 1; 2 -> 2; else -> 4
            }
            val bitrates = if (version == 3) intArrayOf(
                0,
                32,
                40,
                48,
                56,
                64,
                80,
                96,
                112,
                128,
                160,
                192,
                224,
                256,
                320
            )
            else intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160)
            val length =
                (if (version == 3) 144_000 else 72_000) * bitrates[bitrateIndex] / sampleRate + ((header ushr 9) and 1)
            val mono = ((header ushr 6) and 3) == 3
            val sideInfo = if (version == 3) (if (mono) 17 else 32) else (if (mono) 9 else 17)
            val crcBytes = if ((header ushr 16) and 1 == 0) 2 else 0
            require(length > 4 + crcBytes + sideInfo && offset.toLong() + length <= bytes.size) {
                "MP3 音频负载不完整"
            }
            val currentFormat = version to sampleRate
            require(format == null || format == currentFormat) { "MP3 音频帧格式不一致" }
            format = currentFormat
            frames++
            offset += length
        }
        require(frames > 0 && offset == bytes.size) { "MP3 缺少完整音频帧" }
    }

    private fun ByteArray.matches(offset: Int, value: String): Boolean =
        offset >= 0 && offset + value.length <= size && value.indices.all { this[offset + it].toInt() == value[it].code }

    private fun ByteArray.u16(offset: Int): Int =
        (this[offset].toInt() and 255) or ((this[offset + 1].toInt() and 255) shl 8)

    private fun ByteArray.u32(offset: Int): Long = (0..3).fold(0L) { result, index ->
        result or ((this[offset + index].toLong() and 255) shl (index * 8))
    }
}
