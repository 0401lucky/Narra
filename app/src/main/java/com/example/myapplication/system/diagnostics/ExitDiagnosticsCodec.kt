package com.example.myapplication.system.diagnostics

import com.example.myapplication.system.json.AppJson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

internal data class DecodedDiagnosticsFile(
    val events: List<LocalExitDiagnosticEvent>,
    val discardedEntryCount: Int,
)

internal class ExitDiagnosticsCodec {
    fun encodeStore(events: List<LocalExitDiagnosticEvent>): ByteArray {
        val root = JsonObject().apply {
            addProperty("schemaVersion", DIAGNOSTICS_SCHEMA_VERSION)
            add("events", encodeEvents(events))
        }
        return AppJson.gson.toJson(root).toByteArray(Charsets.UTF_8)
    }

    fun decodeStore(bytes: ByteArray): DecodedDiagnosticsFile {
        val root = JsonParser.parseString(bytes.toString(Charsets.UTF_8))
        check(root.isJsonObject) { "诊断文件根节点无效" }
        val rootObject = root.asJsonObject
        check(rootObject.intOrNull("schemaVersion") == DIAGNOSTICS_SCHEMA_VERSION) {
            "诊断文件版本无效"
        }
        val eventElements = rootObject.get("events")
        check(eventElements != null && eventElements.isJsonArray) { "诊断事件列表无效" }
        val decoded = eventElements.asJsonArray.mapNotNull(::decodeEvent)
        return DecodedDiagnosticsFile(
            events = decoded.sortedWith(EVENT_ORDER),
            discardedEntryCount = eventElements.asJsonArray.size() - decoded.size,
        )
    }

    fun encodeExport(
        events: List<LocalExitDiagnosticEvent>,
        generatedAtEpochMillis: Long,
        appVersion: DiagnosticsAppVersion,
    ): ByteArray {
        val safeVersion = normalizeDiagnosticsAppVersion(appVersion)
        val root = JsonObject().apply {
            addProperty("schemaVersion", DIAGNOSTICS_SCHEMA_VERSION)
            addProperty("generatedAtEpochMillis", generatedAtEpochMillis.coerceAtLeast(0L))
            add(
                "appVersion",
                JsonObject().apply {
                    addProperty("versionName", safeVersion.versionName)
                    addProperty("versionCode", safeVersion.versionCode)
                },
            )
            addProperty(
                "privacyNotice",
                "仅包含有限的本地异常退出元数据；不包含聊天内容、提示词、密钥、日志或系统跟踪文件，也不会自动联网。",
            )
            add("events", encodeEvents(events))
        }
        return AppJson.gson.toJson(root).toByteArray(Charsets.UTF_8)
    }

    private fun encodeEvents(events: List<LocalExitDiagnosticEvent>): JsonArray {
        return JsonArray().apply {
            events
                .mapNotNull(::normalizeDiagnosticEvent)
                .distinctBy(LocalExitDiagnosticEvent::eventId)
                .sortedWith(EVENT_ORDER)
                .forEach { event -> add(encodeEvent(event)) }
        }
    }

    private fun encodeEvent(event: LocalExitDiagnosticEvent): JsonObject {
        return JsonObject().apply {
            addProperty("schemaVersion", DIAGNOSTICS_SCHEMA_VERSION)
            addProperty("eventId", event.eventId)
            addProperty("exitTimestampEpochMillis", event.exitTimestampEpochMillis)
            addProperty("observedTimestampEpochMillis", event.observedTimestampEpochMillis)
            addProperty("reasonCategory", event.reasonCategory.name)
            addProperty("importance", event.importance)
            addProperty("status", event.status)
            addProperty("appVersionName", event.appVersionName)
            addProperty("appVersionCode", event.appVersionCode)
            addProperty("pssKb", event.pssKb)
            addProperty("rssKb", event.rssKb)
        }
    }

    private fun decodeEvent(element: JsonElement): LocalExitDiagnosticEvent? {
        if (!element.isJsonObject) return null
        val json = element.asJsonObject
        if (json.intOrNull("schemaVersion") != DIAGNOSTICS_SCHEMA_VERSION) return null
        val category = json.stringOrNull("reasonCategory")
            ?.let { stored -> ExitReasonCategory.entries.firstOrNull { it.name == stored } }
            ?: return null
        val event = LocalExitDiagnosticEvent(
            schemaVersion = DIAGNOSTICS_SCHEMA_VERSION,
            eventId = json.stringOrNull("eventId") ?: return null,
            exitTimestampEpochMillis = json.longOrNull("exitTimestampEpochMillis") ?: return null,
            observedTimestampEpochMillis = json.longOrNull("observedTimestampEpochMillis") ?: return null,
            reasonCategory = category,
            importance = json.intOrNull("importance") ?: return null,
            status = json.intOrNull("status") ?: return null,
            appVersionName = json.stringOrNull("appVersionName") ?: return null,
            appVersionCode = json.longOrNull("appVersionCode") ?: return null,
            pssKb = json.longOrNull("pssKb") ?: return null,
            rssKb = json.longOrNull("rssKb") ?: return null,
        )
        return normalizeDiagnosticEvent(event)
    }

    private fun JsonObject.stringOrNull(name: String): String? {
        return runCatching { get(name)?.takeIf { it.isJsonPrimitive }?.asString }.getOrNull()
    }

    private fun JsonObject.intOrNull(name: String): Int? {
        return runCatching { get(name)?.takeIf { it.isJsonPrimitive }?.asInt }.getOrNull()
    }

    private fun JsonObject.longOrNull(name: String): Long? {
        return runCatching { get(name)?.takeIf { it.isJsonPrimitive }?.asLong }.getOrNull()
    }

    private companion object {
        val EVENT_ORDER = compareBy<LocalExitDiagnosticEvent>(
            LocalExitDiagnosticEvent::exitTimestampEpochMillis,
            LocalExitDiagnosticEvent::eventId,
        )
    }
}
