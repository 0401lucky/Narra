package com.example.myapplication.system.diagnostics

import com.example.myapplication.system.logging.AppLogger
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.CancellationException

fun interface DiagnosticsFileWriter {
    fun write(target: File, bytes: ByteArray)
}

data class DiagnosticsStoreUpdateResult(
    val persisted: Boolean,
    val storedCount: Int,
)

private data class PreparedDiagnosticsStore(
    val events: List<LocalExitDiagnosticEvent>,
    val encoded: ByteArray,
)

class LocalExitDiagnosticsStore(
    directory: File,
    private val maxRecords: Int = DEFAULT_DIAGNOSTICS_MAX_RECORDS,
    private val maxBytes: Int = DEFAULT_DIAGNOSTICS_MAX_BYTES,
    private val fileWriter: DiagnosticsFileWriter = AtomicDiagnosticsFileWriter,
) {
    val diagnosticsFile: File = File(directory, DIAGNOSTICS_FILE_NAME)
    val quarantineFile: File = File(directory, QUARANTINE_FILE_NAME)

    private val lock = Any()
    private val codec = ExitDiagnosticsCodec()

    init {
        require(maxRecords > 0) { "诊断记录上限必须大于 0" }
        require(maxBytes >= codec.encodeStore(emptyList()).size) {
            "诊断文件上限不足以保存空文档"
        }
    }

    fun readEvents(): List<LocalExitDiagnosticEvent> = synchronized(lock) {
        readEventsLocked()
    }

    fun merge(events: List<LocalExitDiagnosticEvent>): DiagnosticsStoreUpdateResult = synchronized(lock) {
        return@synchronized try {
            val prepared = prepareStore(readEventsLocked() + events)
            fileWriter.write(diagnosticsFile, prepared.encoded)
            DiagnosticsStoreUpdateResult(
                persisted = true,
                storedCount = prepared.events.size,
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            AppLogger.w(TAG, "诊断文件原子写入失败，type=${throwable.javaClass.simpleName}")
            DiagnosticsStoreUpdateResult(
                persisted = false,
                storedCount = readEventsLocked().size,
            )
        }
    }

    private fun readEventsLocked(): List<LocalExitDiagnosticEvent> {
        if (!diagnosticsFile.exists()) return emptyList()
        return try {
            if (diagnosticsFile.length() > recoveryReadLimitBytes()) {
                quarantineCorruptFile()
                return emptyList()
            }
            val originalBytes = diagnosticsFile.readBytes()
            val decoded = codec.decodeStore(originalBytes)
            if (decoded.discardedEntryCount > 0) {
                AppLogger.w(
                    TAG,
                    "诊断文件丢弃损坏条目，count=${decoded.discardedEntryCount}",
                )
            }
            val prepared = prepareStore(decoded.events)
            if (
                decoded.discardedEntryCount > 0 ||
                !originalBytes.contentEquals(prepared.encoded)
            ) {
                rewriteRecoveredStore(prepared.encoded)
            }
            prepared.events
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            AppLogger.w(TAG, "诊断文件读取失败并隔离，type=${throwable.javaClass.simpleName}")
            quarantineCorruptFile()
            emptyList()
        }
    }

    private fun recoveryReadLimitBytes(): Long {
        return maxOf(maxBytes.toLong() * 4L, MIN_RECOVERY_READ_BYTES)
    }

    private fun prepareStore(events: List<LocalExitDiagnosticEvent>): PreparedDiagnosticsStore {
        val firstObservedEvents = linkedMapOf<String, LocalExitDiagnosticEvent>()
        events.mapNotNull(::normalizeDiagnosticEvent).forEach { event ->
            firstObservedEvents.putIfAbsent(event.eventId, event)
        }
        val preparedEvents = firstObservedEvents.values
            .sortedWith(EVENT_ORDER)
            .takeLast(maxRecords)
            .toMutableList()
        var encoded = codec.encodeStore(preparedEvents)
        while (encoded.size > maxBytes && preparedEvents.isNotEmpty()) {
            preparedEvents.removeAt(0)
            encoded = codec.encodeStore(preparedEvents)
        }
        return PreparedDiagnosticsStore(
            events = preparedEvents.toList(),
            encoded = encoded,
        )
    }

    private fun rewriteRecoveredStore(encoded: ByteArray) {
        try {
            fileWriter.write(diagnosticsFile, encoded)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            AppLogger.w(TAG, "诊断恢复文件安全回写失败，type=${throwable.javaClass.simpleName}")
        }
    }

    private fun quarantineCorruptFile() {
        if (!diagnosticsFile.exists()) return
        val removed = runCatching {
            Files.deleteIfExists(diagnosticsFile.toPath())
        }.onFailure { throwable ->
            AppLogger.w(TAG, "诊断损坏文件删除失败，type=${throwable.javaClass.simpleName}")
        }.getOrDefault(false)
        if (!removed) return
        runCatching {
            AtomicDiagnosticsFileWriter.write(quarantineFile, QUARANTINE_MARKER_BYTES)
        }.onFailure { throwable ->
            AppLogger.w(TAG, "诊断安全隔离标记写入失败，type=${throwable.javaClass.simpleName}")
        }
    }

    private object AtomicDiagnosticsFileWriter : DiagnosticsFileWriter {
        override fun write(target: File, bytes: ByteArray) {
            val parent = checkNotNull(target.parentFile) { "诊断目录无效" }
            check(parent.exists() || parent.mkdirs()) { "无法创建诊断目录" }
            val temporaryFile = File(parent, "${target.name}.tmp")
            try {
                FileOutputStream(temporaryFile).use { output ->
                    output.write(bytes)
                    output.flush()
                    output.fd.sync()
                }
                try {
                    Files.move(
                        temporaryFile.toPath(),
                        target.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(
                        temporaryFile.toPath(),
                        target.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                }
            } finally {
                if (temporaryFile.exists()) {
                    temporaryFile.delete()
                }
            }
        }
    }

    private companion object {
        const val TAG = "ExitDiagnosticsStore"
        const val DIAGNOSTICS_FILE_NAME = "exit-diagnostics.json"
        const val QUARANTINE_FILE_NAME = "exit-diagnostics.corrupt.json"
        const val MIN_RECOVERY_READ_BYTES = 4L * 1024L
        val QUARANTINE_MARKER_BYTES =
            "{\"schemaVersion\":1,\"status\":\"discarded_corrupt_diagnostics\"}\n"
                .toByteArray(Charsets.UTF_8)

        val EVENT_ORDER = compareBy<LocalExitDiagnosticEvent>(
            LocalExitDiagnosticEvent::exitTimestampEpochMillis,
            LocalExitDiagnosticEvent::eventId,
        )
    }
}
