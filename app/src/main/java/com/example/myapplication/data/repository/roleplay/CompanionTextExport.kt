package com.example.myapplication.data.repository.roleplay

import com.example.myapplication.model.*
import com.example.myapplication.roleplay.RoleplayMessageFormatSupport
import com.example.myapplication.system.security.SensitiveTextRedactor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.coroutines.coroutineContext

fun interface CompanionTextDocumentWriter {
    suspend fun write(documentUri: String, content: ByteArray)
}

/** TXT 是仓储原文的阅读副本，不使用模型摘要或屏幕窗口。 */
class CompanionTextExporter(
    private val repository: CompanionHistoryManagementRepository,
    private val writer: CompanionTextDocumentWriter,
) {
    suspend fun export(
        scope: CompanionReadingScope,
        query: CompanionHistoryQuery,
        documentUri: String?,
    ): CompanionTextExportResult {
        if (documentUri == null) return CompanionTextExportResult.Cancelled
        return try {
            val records = repository.readAll(scope, query)
            val content = CompanionTextExportContent.encode(records, query)
            coroutineContext.ensureActive()
            checkNotNull(repository.refreshScope(scope)) { "历史授权已失效，未导出原文。请重新打开。" }
            coroutineContext.ensureActive()
            writer.write(documentUri, content)
            CompanionTextExportResult.Saved(records.size)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            CompanionTextExportResult.Failed(
                SensitiveTextRedactor.throwableMessageForUi(
                    failure,
                    "导出失败，请重新选择文件重试。"
                )
            )
        }
    }

    companion object {
        fun fileName(characterName: String, query: CompanionHistoryQuery): String {
            query.validate()
            val sanitized = characterName.replace(Regex("[\\x00-\\x1f<>:\"/\\\\|?*]"), "_")
                .trim(' ', '.').ifBlank { "角色" }
            val range =
                query.date ?: query.month ?: if (query.encounterId != null) "本场" else "全部"
            val prefix = "陪伴记录-"
            val suffix = "-$range.txt"
            // 常见文件提供方以字节限制文件名，按完整码点留出固定前后缀。
            val budget = 255 - (prefix + suffix).toByteArray(Charsets.UTF_8).size
            var end = 0
            var points = 0
            var bytes = 0
            while (end < sanitized.length && points < 60) {
                val next = end + Character.charCount(sanitized.codePointAt(end))
                val size = sanitized.substring(end, next).toByteArray(Charsets.UTF_8).size
                if (bytes + size > budget) break
                bytes += size
                end = next
                points++
            }
            return prefix + sanitized.substring(0, end) + suffix
        }
    }
}

object CompanionTextExportContent {
    fun editableText(record: CompanionReadingRecord): String {
        val message = record.message
        if (message.companionTurnId.isNotBlank() && message.role == MessageRole.ASSISTANT) {
            return RoleplayMessageFormatSupport.resolveAssistantRawContent(message)
        }
        return message.content.ifEmpty {
            message.parts.filter { it.type == ChatMessagePartType.TEXT }
                .joinToString("\n") { it.text }
        }
    }

    fun raw(record: CompanionReadingRecord): String {
        val message = record.message
        if (message.companionTurnId.isNotBlank() && message.role == MessageRole.ASSISTANT) {
            return RoleplayMessageFormatSupport.resolveAssistantRawContent(message)
        }
        if (message.content.isNotEmpty()) return message.content
        return message.parts.joinToString("\n") { part ->
            if (part.type == ChatMessagePartType.TEXT) part.text
            else "【${part.type.name}${
                part.fileName.takeIf(String::isNotBlank)?.let { ": $it" }.orEmpty()
            }】${part.text}"
        }
    }

    fun encode(records: List<CompanionReadingRecord>, query: CompanionHistoryQuery): ByteArray {
        query.validate()
        val zone = ZoneId.of(query.zoneId)
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS XXX")
        val text = buildString {
            append("陪伴原文阅读副本\n")
            append("时区：").append(query.zoneId).append('\n')
            append("记录数：").append(records.size).append("\n\n")
            records.forEach { record ->
                val message = record.message
                val time = if (message.createdAt == 0L) "时间未知（原时间戳：0）"
                else Instant.ofEpochMilli(message.createdAt).atZone(zone).format(formatter)
                val speaker =
                    message.speakerName.ifBlank { if (message.role == MessageRole.USER) "用户" else "角色" }
                append('[').append(time).append("] ").append(message.role.name).append(" · ")
                    .append(speaker)
                if (message.speakerId.isNotBlank()) append(" · speakerId=").append(message.speakerId)
                append(" · 类型=").append(record.turnKind?.name ?: "LEGACY")
                if (!record.readerEligible) append(" · 回合待完成")
                append(" · source=").append(message.id).append('@').append(message.memoryRevision)
                    .append('\n')
                append("会话：").append(record.binding.title).append(" · ")
                    .append(record.encounterId?.let { "场次=$it" } ?: "旧记录，未分场").append('\n')
                append(raw(record)).append("\n\n")
            }
        }
        return byteArrayOf(
            0xEF.toByte(),
            0xBB.toByte(),
            0xBF.toByte()
        ) + text.toByteArray(Charsets.UTF_8)
    }
}
