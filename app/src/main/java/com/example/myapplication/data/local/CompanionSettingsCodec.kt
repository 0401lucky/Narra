package com.example.myapplication.data.local

import com.example.myapplication.model.CompanionCharacterSettings
import com.example.myapplication.model.CompanionSettingsPatch
import com.example.myapplication.model.CompanionSettingsWriteResult
import com.example.myapplication.model.CompanionWritingValidation
import com.example.myapplication.model.CompanionVisualValidation
import com.example.myapplication.data.local.CompanionWritingCodec.long
import com.example.myapplication.data.local.CompanionWritingCodec.string
import com.example.myapplication.system.json.AppJson
import com.example.myapplication.system.logging.logFailure
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.time.ZoneId

/** 保留未识别字段与高版本原 JSON，旧角色整表保存不能覆盖这份独立小映射。 */
internal object CompanionSettingsCodec {
    private fun root(raw: String): JsonObject = if (raw.isBlank()) JsonObject() else
        JsonParser.parseString(raw).also { require(it.isJsonObject) }.asJsonObject

    fun decode(raw: String): Map<String, CompanionCharacterSettings> = runCatching {
        root(raw).entrySet().mapNotNull { (id, value) ->
            if (!value.isJsonObject) return@mapNotNull null
            val settings = runCatching {
                val entry = value.asJsonObject
                val schema = if (entry.has("schemaVersion")) entry.long("schemaVersion")
                    ?.takeIf { it in 1..Int.MAX_VALUE }?.toInt() ?: 0 else 1
                val revision = if (entry.has("revision")) entry.long("revision") ?: -1 else 0
                require(revision >= 0)
                val zone = entry.string("zoneId")
                CompanionCharacterSettings(
                    schema,
                    revision,
                    validZoneOrDefault(zone),
                    CompanionWritingCodec.decode(entry.get("writing")),
                    CompanionVisualCodec.decode(entry.get("visuals")),
                    CompanionVoiceCodec.decode(entry.get("voice")),
                )
            }.getOrElse { CompanionCharacterSettings(schemaVersion = 0) }
            id to settings
        }.toMap()
    }.logFailure("CompanionSettings") { "settings decode failed, raw.len=${raw.length}" }
        .getOrDefault(emptyMap())

    fun update(
        raw: String,
        assistantId: String,
        expectedRevision: Long,
        patch: CompanionSettingsPatch,
    ): Pair<String, CompanionSettingsWriteResult> {
        val root = runCatching { root(raw) }.getOrNull()
            ?: return raw to CompanionSettingsWriteResult.UnsupportedVersion
        val existing = root.get(assistantId)
        if (existing != null && !existing.isJsonObject) return raw to CompanionSettingsWriteResult.UnsupportedVersion
        val entry = existing?.asJsonObject ?: JsonObject()
        val current =
            decode(AppJson.gson.toJson(JsonObject().apply { add(assistantId, entry) }))[assistantId]
                ?: return raw to CompanionSettingsWriteResult.UnsupportedVersion
        if (current.schemaVersion != 1) return raw to CompanionSettingsWriteResult.UnsupportedVersion
        if (current.revision != expectedRevision || expectedRevision == Long.MAX_VALUE) {
            return raw to CompanionSettingsWriteResult.Conflict
        }
        val zone =
            patch.zoneId?.trim()?.also { if (it.isNotBlank()) ZoneId.of(it) } ?: current.zoneId
        val writing = patch.writing ?: current.writing
        if (patch.visuals != null && current.visuals.schemaVersion != 1)
            return raw to CompanionSettingsWriteResult.UnsupportedVersion
        val visuals = patch.visuals ?: current.visuals
        if (patch.voice != null && current.voice.schemaVersion != 1)
            return raw to CompanionSettingsWriteResult.UnsupportedVersion
        val voice = patch.voice?.normalized() ?: current.voice
        if (patch.voice != null) CompanionVoiceCodec.validate(voice)
        if (patch.visuals != null) require(CompanionVisualValidation.error(visuals) == null) {
            CompanionVisualValidation.error(visuals).orEmpty()
        }
        if (patch.writing != null) require(CompanionWritingValidation.error(writing) == null) {
            CompanionWritingValidation.error(writing).orEmpty()
        }
        val next = current.copy(revision = current.revision + 1, zoneId = zone, writing = writing, visuals = visuals, voice = voice)
        entry.addProperty("schemaVersion", next.schemaVersion)
        entry.addProperty("revision", next.revision)
        entry.addProperty("zoneId", next.zoneId)
        if (patch.writing != null) entry.add(
            "writing",
            CompanionWritingCodec.encode(writing, entry.get("writing"))
        )
        if (patch.visuals != null) entry.add("visuals", CompanionVisualCodec.encode(visuals, entry.get("visuals")))
        if (patch.voice != null) entry.add("voice", CompanionVoiceCodec.encode(voice, entry.get("voice")))
        root.add(assistantId, entry)
        val json = AppJson.gson.toJson(root)
        return json to CompanionSettingsWriteResult.Saved(requireNotNull(decode(json)[assistantId]))
    }

    fun retainCharacters(raw: String, ids: Set<String>): String = runCatching {
        val root = root(raw)
        root.keySet().toList().filterNot { it in ids }.forEach(root::remove)
        AppJson.gson.toJson(root)
    }.getOrDefault(raw)

    fun copyCharacter(raw: String, sourceId: String, targetId: String): String {
        val root = root(raw)
        require(!root.has(targetId)) { "目标角色已有陪伴设置，未覆盖" }
        val source = root.get(sourceId) ?: return raw
        require(source.isJsonObject && decode(raw)[sourceId]?.schemaVersion == 1) {
            "原角色的陪伴设置版本不受支持，未创建副本"
        }
        root.add(targetId, source.asJsonObject.deepCopy().apply { addProperty("revision", 0L) })
        return AppJson.gson.toJson(root)
    }

    private fun validZoneOrDefault(zone: String): String =
        zone.trim().takeIf { it.isNotBlank() && runCatching { ZoneId.of(it) }.isSuccess }.orEmpty()
}
