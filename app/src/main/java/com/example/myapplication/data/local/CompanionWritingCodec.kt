package com.example.myapplication.data.local

import com.example.myapplication.model.*
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.util.Locale

/** 小映射逐字段容错；写入只覆盖已知字段，并保留同 ID 项上的未知扩展。 */
internal object CompanionWritingCodec {
    fun decode(value: JsonElement?): CompanionWritingSettings {
        val root = value.objectOrNull() ?: return CompanionWritingSettings()
        return CompanionWritingSettings(
            style = enumValue<CompanionWritingStyle>(root, "style"),
            pointOfView = enumValue<CompanionPointOfView>(root, "pointOfView"),
            extraStyle = root.string("extraStyle"),
            templates = root.arrayObjects("templates")
                .take(CompanionWritingValidation.MAX_TEMPLATES)
                .mapNotNull { item ->
                    val id = item.string("id").takeIf { it.isNotBlank() && it.length <= 80 }
                        ?: return@mapNotNull null
                    val name =
                        item.string("name").takeIf(String::isNotBlank) ?: return@mapNotNull null
                    CompanionStyleTemplate(id, name, item.string("text"))
                }.distinctBy { it.id },
            detailHintsEnabled = root.boolean("detailHintsEnabled", true),
            observation = decodeObservation(root.get("observation")),
        )
    }

    fun decodeObservation(value: JsonElement?): CompanionObservationSettings {
        val root = value.objectOrNull() ?: return CompanionObservationSettings()
        val fields = root.arrayObjects("fields").associateBy { it.string("id") }
        return CompanionObservationSettings(
            enabled = root.boolean("enabled", false),
            style = enumValue<CompanionObservationStyle>(root, "style")
                ?: CompanionObservationStyle.CONCISE,
            fields = companionBuiltinObservationFields().map { field ->
                fields[field.id]?.let {
                    field.copy(
                        label = it.string("label").takeIf(String::isNotBlank) ?: field.label,
                        enabled = it.boolean("enabled", true),
                    )
                } ?: field
            },
            customFields = root.arrayObjects("customFields")
                .take(CompanionWritingValidation.MAX_CUSTOM_FIELDS)
                .mapNotNull { item ->
                    val id =
                        item.string("id").takeIf { Regex("obs_[A-Za-z0-9_-]{1,64}").matches(it) }
                            ?: return@mapNotNull null
                    val label =
                        item.string("label").takeIf(String::isNotBlank) ?: return@mapNotNull null
                    CompanionObservationField(id, label, item.boolean("enabled", true))
                }.distinctBy { it.id },
        )
    }

    fun encode(settings: CompanionWritingSettings, previous: JsonElement?): JsonObject =
        (previous.objectOrNull()?.deepCopy() ?: JsonObject()).apply {
            addNullableEnum("style", settings.style?.name)
            addNullableEnum("pointOfView", settings.pointOfView?.name)
            addProperty("extraStyle", settings.extraStyle)
            addProperty("detailHintsEnabled", settings.detailHintsEnabled)
            val oldTemplates = arrayObjects("templates").associateBy { it.string("id") }
            add("templates", com.google.gson.JsonArray().apply {
                settings.templates.forEach { template ->
                    add((oldTemplates[template.id]?.deepCopy() ?: JsonObject()).apply {
                        addProperty("id", template.id)
                        addProperty("name", template.name.trim())
                        addProperty("text", template.text)
                    })
                }
            })
            add("observation", encodeObservation(settings.observation, get("observation")))
        }

    private fun encodeObservation(
        settings: CompanionObservationSettings,
        previous: JsonElement?
    ): JsonObject =
        (previous.objectOrNull()?.deepCopy() ?: JsonObject()).apply {
            addProperty("enabled", settings.enabled)
            addProperty("style", settings.style.name)
            fun saveFields(key: String, fields: List<CompanionObservationField>) {
                val old = arrayObjects(key).associateBy { it.string("id") }
                add(key, com.google.gson.JsonArray().apply {
                    fields.forEach { field ->
                        add((old[field.id]?.deepCopy() ?: JsonObject()).apply {
                            addProperty("id", field.id)
                            addProperty("label", field.label.trim())
                            addProperty("enabled", field.enabled)
                        })
                    }
                })
            }
            saveFields("fields", settings.fields)
            saveFields("customFields", settings.customFields)
        }

    fun decodeSnapshot(value: JsonElement?): CompanionPresentationSnapshot? {
        val root = value.objectOrNull() ?: return null
        val version =
            root.long("snapshotVersion")?.takeIf { it in 1L..Int.MAX_VALUE.toLong() }?.toInt() ?: 0
        val revision = root.long("sourceSettingsRevision")?.takeIf { it >= 0 } ?: 0
        val hint = root.get("detailHint").objectOrNull()?.let {
            val id = it.string("id")
            val text = it.string("text")
            if (id.isNotBlank() && id.length <= 80 && text.isNotBlank() && text.length <= 2_000)
                CompanionDetailHint(id, text) else null
        }
        return CompanionPresentationSnapshot(
            snapshotVersion = version,
            sourceSettingsRevision = revision,
            style = enumValue<CompanionWritingStyle>(root, "style"),
            pointOfView = enumValue<CompanionPointOfView>(root, "pointOfView"),
            extraStyle = root.string("extraStyle"),
            detailHint = hint,
            observation = decodeObservation(root.get("observation")),
        )
    }

    private fun JsonObject.addNullableEnum(name: String, value: String?) {
        if (value == null) remove(name) else addProperty(name, value)
    }

    internal fun JsonElement?.objectOrNull(): JsonObject? =
        this?.takeIf { it.isJsonObject }?.asJsonObject

    internal fun JsonObject.string(name: String): String = get(name)
        ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString.orEmpty()

    internal fun JsonObject.boolean(name: String, fallback: Boolean): Boolean = get(name)
        ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean ?: fallback

    internal fun JsonObject.long(name: String): Long? = get(name)
        ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
        ?.let { runCatching { it.asBigDecimal.longValueExact() }.getOrNull() }

    private fun JsonObject.arrayObjects(name: String): List<JsonObject> = get(name)
        ?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull { it.objectOrNull() }.orEmpty()

    private inline fun <reified T : Enum<T>> enumValue(root: JsonObject, name: String): T? =
        enumValues<T>().firstOrNull { it.name == root.string(name).uppercase(Locale.ROOT) }
}
