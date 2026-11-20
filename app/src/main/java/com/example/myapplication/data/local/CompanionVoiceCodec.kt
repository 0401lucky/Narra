package com.example.myapplication.data.local

import com.example.myapplication.data.local.CompanionWritingCodec.boolean
import com.example.myapplication.data.local.CompanionWritingCodec.long
import com.example.myapplication.data.local.CompanionWritingCodec.objectOrNull
import com.example.myapplication.data.local.CompanionWritingCodec.string
import com.example.myapplication.model.CompanionVoicePreferences
import com.example.myapplication.model.CompanionVoicePreferences.Companion.MAX_LANGUAGE_LENGTH
import com.example.myapplication.model.CompanionVoiceReadMode
import com.google.gson.JsonElement
import com.google.gson.JsonObject

/** 语音小映射使用自己的版本守卫，更新其它陪伴字段不覆盖未来声音配置。 */
internal object CompanionVoiceCodec {
    fun decode(value: JsonElement?): CompanionVoicePreferences {
        val root = value.objectOrNull() ?: return CompanionVoicePreferences()
        val version = if (root.has("schemaVersion")) root.long("schemaVersion")
            ?.takeIf { it in 1..Int.MAX_VALUE }?.toInt() ?: 0 else 1
        return CompanionVoicePreferences(
            schemaVersion = version,
            enabled = root.boolean("enabled", false),
            autoPlay = root.boolean("autoPlay", false),
            readMode = CompanionVoiceReadMode.entries.firstOrNull { it.name == root.string("readMode") }
                ?: CompanionVoiceReadMode.ORIGINAL,
            sourceLanguage = root.string("sourceLanguage")
                .takeIf { it.length <= MAX_LANGUAGE_LENGTH }
                .orEmpty(),
            targetLanguage = root.string("targetLanguage")
                .takeIf { it.length <= MAX_LANGUAGE_LENGTH }
                .orEmpty(),
            cacheLimitBytes = root.long("cacheLimitBytes")
                ?: CompanionVoicePreferences.DEFAULT_CACHE_LIMIT_BYTES,
        ).normalized()
    }

    fun validate(settings: CompanionVoicePreferences) {
        require(settings.schemaVersion == 1) { "声音设置版本不受支持，请读取最新设置" }
        require(settings.sourceLanguage.length <= MAX_LANGUAGE_LENGTH && settings.targetLanguage.length <= MAX_LANGUAGE_LENGTH) {
            "语言名称最多 $MAX_LANGUAGE_LENGTH 字"
        }
    }

    fun encode(settings: CompanionVoicePreferences, previous: JsonElement?): JsonObject =
        (previous.objectOrNull()?.deepCopy() ?: JsonObject()).apply {
            addProperty("schemaVersion", settings.schemaVersion)
            addProperty("enabled", settings.enabled)
            addProperty("autoPlay", settings.autoPlay)
            addProperty("readMode", settings.readMode.name)
            addProperty("sourceLanguage", settings.sourceLanguage)
            addProperty("targetLanguage", settings.targetLanguage)
            addProperty("cacheLimitBytes", settings.cacheLimitBytes)
        }
}
