package com.example.myapplication.data.repository.voice

import com.example.myapplication.data.repository.normalizeBase64AudioData
import com.example.myapplication.data.repository.roleplay.CompanionVoiceFilePublisher
import com.example.myapplication.model.CompanionAudioAttachmentState
import com.example.myapplication.model.CompanionAudioClaim
import com.example.myapplication.model.CompanionAudioReceipt
import com.example.myapplication.model.CompanionVoiceAudio
import com.example.myapplication.model.CompanionVoiceClip
import com.example.myapplication.system.json.AppJson
import com.example.myapplication.system.logging.logFailure
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okio.ByteString.Companion.decodeBase64

class PrivateCompanionVoiceFilePublisher(
    private val directory: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
    private val afterAudioPublished: suspend () -> Unit = {},
) : CompanionVoiceFilePublisher {
    override suspend fun publish(
        claim: CompanionAudioClaim,
        b64Audio: String,
        clip: CompanionVoiceClip,
        isCurrent: suspend () -> Boolean,
    ): CompanionVoiceAudio {
        requireId(claim.audioId)
        require(Regex("[a-f0-9]{64}").matches(claim.key.value)) { "语音缓存标识无效" }
        var created = false
        try {
            return withContext(ioDispatcher) {
                checkCurrent(isCurrent)
                val existing = readReceipt(receiptFile(claim.audioId))
                if (existing != null && existing.receipt.key == claim.key && validateNow(existing.receipt)) {
                    checkCurrent(isCurrent)
                    return@withContext existing
                }
                ensureDirectory()
                val bytes = normalizeBase64AudioData(b64Audio).decodeBase64()?.toByteArray()
                    ?: throw IllegalArgumentException("音频 Base64 数据无效")
                val format = CompanionAudioIntegrity.format(bytes)
                val temporary = child("${claim.audioId}.audio.part")
                val finalFile = child("${claim.audioId}.${format.extension}")
                created = true
                try {
                    writeSynced(temporary, bytes)
                    checkCurrent(isCurrent)
                    val written = temporary.readBytes()
                    require(written.contentEquals(bytes)) { "语音临时文件校验失败" }
                    checkCurrent(isCurrent)
                    movePrivate(temporary, finalFile)
                    afterAudioPublished()
                    checkCurrent(isCurrent)
                    val receipt = CompanionAudioReceipt(
                        claim.audioId, claim.key, finalFile.absolutePath, format.mimeType,
                        CompanionAudioIntegrity.sha256(written), written.size.toLong(), clock(),
                        CompanionAudioAttachmentState.PENDING,
                    )
                    val audio = CompanionVoiceAudio(receipt, clip)
                    writeReceipt(audio)
                    checkCurrent(isCurrent)
                    audio
                } finally {
                    temporary.delete()
                    child("${claim.audioId}.receipt.part").delete()
                }
            }
        } catch (cancellation: CancellationException) {
            if (created) cleanupFailedPublication(claim.audioId)
            throw cancellation
        } catch (throwable: Throwable) {
            if (created) cleanupFailedPublication(claim.audioId)
            throw throwable
        }
    }

    override suspend fun validate(receipt: CompanionAudioReceipt): Boolean =
        withContext(ioDispatcher) {
            validateNow(receipt)
        }

    override suspend fun publishedReceipts(): List<CompanionVoiceAudio> =
        withContext(ioDispatcher) {
            if (!directory.exists()) return@withContext emptyList()
            directory.listFiles().orEmpty().filter { it.isFile && it.name.endsWith(".json") }
                .mapNotNull { file ->
                    currentCoroutineContext().ensureActive()
                    readReceipt(file)?.takeIf { validateNow(it.receipt) }
                }
        }

    override suspend fun cleanupOrphans(retainedAudioIds: Set<String>) = withContext(ioDispatcher) {
        if (!directory.exists()) return@withContext
        // 引用由 Room 所有者提供；坏 sidecar 不能让仍被收藏的实际音频被误删。
        val cutoff = clock() - ORPHAN_RETENTION_MILLIS
        directory.listFiles().orEmpty().filter { file ->
            file.isFile && file.lastModified() < cutoff &&
                    file.name.substringBefore('.') !in retainedAudioIds && isOwnedFileName(file.name)
        }.forEach { file ->
            currentCoroutineContext().ensureActive()
            if (!file.delete() && file.exists()) throw IOException("无法清理过期语音临时文件")
        }
    }

    override suspend fun markAttached(audio: CompanionVoiceAudio) = withContext(ioDispatcher) {
        val existing = readReceipt(receiptFile(audio.receipt.audioId))
            ?: throw IOException("语音持久回执不可用")
        require(
            existing.receipt.key == audio.receipt.key && existing.receipt.sha256 == audio.receipt.sha256 &&
                    existing.receipt.sizeBytes == audio.receipt.sizeBytes && validateNow(existing.receipt)
        ) {
            "语音持久回执校验失败"
        }
        if (existing.receipt.attachmentState != CompanionAudioAttachmentState.ATTACHED) {
            writeReceipt(existing.copy(receipt = existing.receipt.copy(attachmentState = CompanionAudioAttachmentState.ATTACHED)))
        }
    }

    override suspend fun delete(audioId: String) = withContext(ioDispatcher) { deleteNow(audioId) }

    private suspend fun cleanupFailedPublication(audioId: String) =
        withContext(NonCancellable + ioDispatcher) {
            runCatching { deleteNow(audioId) }.logFailure("CompanionVoiceFiles") { "audio publication cleanup failed" }
            Unit
        }

    private suspend fun checkCurrent(isCurrent: suspend () -> Boolean) {
        currentCoroutineContext().ensureActive()
        if (!isCurrent()) throw CancellationException("旧语音任务已取消")
        currentCoroutineContext().ensureActive()
    }

    private fun readReceipt(file: File): CompanionVoiceAudio? {
        if (!file.exists()) return null
        return runCatching {
            require(file.canonicalFile.parentFile == directory.canonicalFile && file.length() in 1..MAX_RECEIPT_BYTES)
            val audio = AppJson.gson.fromJson(
                file.readText(Charsets.UTF_8),
                CompanionVoiceAudio::class.java
            )
            requireId(audio.receipt.audioId)
            require(file.name == "${audio.receipt.audioId}.json")
            require(Regex("[a-f0-9]{64}").matches(audio.receipt.key.value))
            require(audio.clip.spokenText.isNotBlank())
            audio
        }.logFailure("CompanionVoiceFiles") { "audio receipt read failed" }.getOrNull()
    }

    private fun validateNow(receipt: CompanionAudioReceipt): Boolean = runCatching {
        requireId(receipt.audioId)
        require(receipt.sizeBytes in 1..CompanionAudioIntegrity.MAX_AUDIO_BYTES.toLong())
        require(Regex("[a-f0-9]{64}").matches(receipt.sha256))
        val file = File(receipt.path).canonicalFile
        require(file.parentFile == directory.canonicalFile && file.isFile && file.length() == receipt.sizeBytes)
        val bytes = file.readBytes()
        val format = CompanionAudioIntegrity.format(bytes)
        file.name == "${receipt.audioId}.${format.extension}" && receipt.mimeType == format.mimeType &&
                CompanionAudioIntegrity.sha256(bytes) == receipt.sha256
    }.getOrDefault(false)

    private fun writeReceipt(audio: CompanionVoiceAudio) {
        val bytes = AppJson.gson.toJson(audio).toByteArray(Charsets.UTF_8)
        require(bytes.size.toLong() <= MAX_RECEIPT_BYTES) { "语音来源快照过大" }
        val temporary = child("${audio.receipt.audioId}.receipt.part")
        try {
            writeSynced(temporary, bytes)
            movePrivate(temporary, receiptFile(audio.receipt.audioId))
        } finally {
            temporary.delete()
        }
    }

    private fun writeSynced(file: File, bytes: ByteArray) {
        FileOutputStream(file).use { output ->
            output.write(bytes)
            output.fd.sync()
        }
    }

    private fun movePrivate(source: File, destination: File) {
        Files.move(
            source.toPath(),
            destination.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING
        )
    }

    private fun ensureDirectory() {
        if (!directory.exists() && !directory.mkdirs()) throw IOException("无法创建语音私有目录")
        require(directory.isDirectory) { "语音私有目录不可用" }
    }

    private fun receiptFile(audioId: String): File {
        requireId(audioId)
        return child("$audioId.json")
    }

    private fun child(name: String): File {
        val file = File(directory, name)
        require(file.canonicalFile.parentFile == directory.canonicalFile) { "语音文件位置无效" }
        return file
    }

    private fun deleteNow(audioId: String) {
        requireId(audioId)
        listOf("wav", "mp3", "json", "audio.part", "receipt.part").forEach { extension ->
            val file = child("$audioId.$extension")
            if (file.exists() && !file.delete()) throw IOException("无法清理语音私有文件")
        }
    }

    private fun requireId(audioId: String) {
        require(Regex("[A-Za-z0-9_-]{1,100}").matches(audioId)) { "语音资源标识无效" }
    }

    private fun isOwnedFileName(name: String): Boolean =
        Regex("[A-Za-z0-9_-]{1,100}\\.(wav|mp3|json|audio\\.part|receipt\\.part)").matches(name)

    companion object {
        private const val MAX_RECEIPT_BYTES = 2 * 1024 * 1024L
        private const val ORPHAN_RETENTION_MILLIS = 24 * 60 * 60 * 1000L
    }
}
