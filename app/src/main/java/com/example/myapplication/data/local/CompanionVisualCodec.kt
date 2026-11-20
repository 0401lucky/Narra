package com.example.myapplication.data.local

import com.example.myapplication.data.local.CompanionWritingCodec.long
import com.example.myapplication.data.local.CompanionWritingCodec.objectOrNull
import com.example.myapplication.data.local.CompanionWritingCodec.string
import com.example.myapplication.model.*
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

internal object CompanionVisualCodec {
    fun decode(value: JsonElement?): CompanionVisualSettings {
        val root = value.objectOrNull() ?: return CompanionVisualSettings()
        val version = if (root.has("schemaVersion")) root.long("schemaVersion")
            ?.takeIf { it in 1..Int.MAX_VALUE }?.toInt() ?: 0 else 1
        val images = root.objects("resources").take(CompanionVisualValidation.MAX_RESOURCES)
            .mapNotNull { item ->
                val id = item.string("id").takeIf { it.isNotBlank() && it.length <= 80 }
                    ?: return@mapNotNull null
                val path = item.string("path").takeIf { it.isNotBlank() && it.length <= 4096 }
                    ?: return@mapNotNull null
                val kind = CompanionImageKind.entries.firstOrNull { it.name == item.string("kind") }
                    ?: return@mapNotNull null
                CompanionImageResource(
                    id, item.string("name").ifBlank { kind.label }, path, kind,
                    item.long("width")?.coerceIn(0L, Int.MAX_VALUE.toLong())?.toInt() ?: 0,
                    item.long("height")?.coerceIn(0L, Int.MAX_VALUE.toLong())?.toInt() ?: 0
                )
            }.distinctBy { it.id }
        val imageIds = images.associateBy { it.id }
        val outfits =
            root.objects("outfits").take(CompanionVisualValidation.MAX_OUTFITS).mapNotNull { item ->
                val id = item.string("id").takeIf { it.isNotBlank() && it.length <= 80 }
                    ?: return@mapNotNull null
                val expressions = item.get("expressions").objectOrNull()?.entrySet().orEmpty()
                    .mapNotNull { (key, value) ->
                        val normalized =
                            CompanionExpressionKeys.normalize(key) ?: return@mapNotNull null
                        val ref =
                            value.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
                                ?.takeIf { imageIds[it]?.kind == CompanionImageKind.PORTRAIT }
                                ?: return@mapNotNull null
                        normalized to ref
                    }.take(CompanionVisualValidation.MAX_EXPRESSIONS).toMap()
                CompanionOutfit(
                    id, item.string("name").ifBlank { "角色套装" },
                    item.string("defaultPortraitId")
                        .takeIf { imageIds[it]?.kind == CompanionImageKind.PORTRAIT }, expressions
                )
            }.distinctBy { it.id }
        val layout = root.get("layout").objectOrNull()
        return CompanionVisualSettings(
            schemaVersion = version,
            preferredMode = CompanionDisplayMode.entries.firstOrNull { it.name == root.string("preferredMode") },
            resources = images,
            outfits = outfits,
            selectedOutfitId = root.string("selectedOutfitId")
                .takeIf { id -> outfits.any { it.id == id } },
            selectedBackgroundId = root.string("selectedBackgroundId")
                .takeIf { imageIds[it]?.kind == CompanionImageKind.BACKGROUND },
            decorationIds = root.get("decorationIds")
                ?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull {
                    it.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
                        ?.takeIf { id -> imageIds[id]?.kind == CompanionImageKind.DECOR }
                }.orEmpty().distinct(),
            layout = CompanionStageLayout(
                portraitPosition = CompanionPortraitPosition.entries.firstOrNull {
                    it.name == layout?.string(
                        "portraitPosition"
                    )
                }
                    ?: CompanionPortraitPosition.CENTER,
                portraitWidth = layout?.float("portraitWidth") ?: 0.72f,
                portraitHeight = layout?.float("portraitHeight") ?: 0.45f,
            ).normalized(),
            sceneOverrides = root.get("sceneOverrides").objectOrNull()?.entrySet().orEmpty()
                .take(CompanionVisualValidation.MAX_SCENE_OVERRIDES).mapNotNull { (id, value) ->
                    val choice = value.objectOrNull() ?: return@mapNotNull null
                    if (id.isBlank() || id.length > 200) return@mapNotNull null
                    id to CompanionSceneVisualOverride(
                        choice.string("selectedOutfitId")
                            .takeIf { ref -> outfits.any { it.id == ref } },
                        choice.string("selectedBackgroundId")
                            .takeIf { imageIds[it]?.kind == CompanionImageKind.BACKGROUND },
                        decodeLayout(choice.get("layout")),
                    )
                }.toMap(),
        )
    }

    fun encode(settings: CompanionVisualSettings, previous: JsonElement?): JsonObject =
        (previous.objectOrNull()?.deepCopy() ?: JsonObject()).apply {
            addProperty("schemaVersion", settings.schemaVersion)
            nullable("preferredMode", settings.preferredMode?.name)
            nullable("selectedOutfitId", settings.selectedOutfitId)
            nullable("selectedBackgroundId", settings.selectedBackgroundId)
            val oldImages = objects("resources").associateBy { it.string("id") }
            add("resources", JsonArray().apply {
                settings.resources.forEach { resource ->
                    add((oldImages[resource.id]?.deepCopy() ?: JsonObject()).apply {
                        addProperty("id", resource.id); addProperty("name", resource.name.trim())
                        addProperty("path", resource.path); addProperty("kind", resource.kind.name)
                        addProperty("width", resource.width); addProperty("height", resource.height)
                    })
                }
            })
            val oldOutfits = objects("outfits").associateBy { it.string("id") }
            add("outfits", JsonArray().apply {
                settings.outfits.forEach { outfit ->
                    add((oldOutfits[outfit.id]?.deepCopy() ?: JsonObject()).apply {
                        addProperty("id", outfit.id); addProperty("name", outfit.name.trim())
                        nullable("defaultPortraitId", outfit.defaultPortraitId)
                        add(
                            "expressions",
                            JsonObject().apply {
                                outfit.expressions.forEach { (key, ref) ->
                                    addProperty(
                                        key,
                                        ref
                                    )
                                }
                            })
                    })
                }
            })
            add(
                "decorationIds",
                JsonArray().apply { settings.decorationIds.distinct().forEach(::add) })
            add("layout", (get("layout").objectOrNull()?.deepCopy() ?: JsonObject()).apply {
                addProperty("portraitPosition", settings.layout.portraitPosition.name)
                addProperty("portraitWidth", settings.layout.portraitWidth)
                addProperty("portraitHeight", settings.layout.portraitHeight)
            })
            val previousScenes = get("sceneOverrides").objectOrNull()
            add("sceneOverrides", JsonObject().apply {
                settings.sceneOverrides.forEach { (id, choice) ->
                    add(
                        id,
                        (previousScenes?.get(id).objectOrNull()?.deepCopy() ?: JsonObject()).apply {
                            nullable("selectedOutfitId", choice.selectedOutfitId)
                            nullable("selectedBackgroundId", choice.selectedBackgroundId)
                            add(
                                "layout",
                                (get("layout").objectOrNull()?.deepCopy() ?: JsonObject()).apply {
                                    addProperty(
                                        "portraitPosition",
                                        choice.layout.portraitPosition.name
                                    )
                                    addProperty("portraitWidth", choice.layout.portraitWidth)
                                    addProperty("portraitHeight", choice.layout.portraitHeight)
                                })
                        })
                }
            })
        }

    private fun decodeLayout(value: JsonElement?): CompanionStageLayout {
        val layout = value.objectOrNull()
        return CompanionStageLayout(
            CompanionPortraitPosition.entries.firstOrNull { it.name == layout?.string("portraitPosition") }
                ?: CompanionPortraitPosition.CENTER,
            layout?.float("portraitWidth") ?: 0.72f,
            layout?.float("portraitHeight") ?: 0.45f,
        ).normalized()
    }

    private fun JsonObject.nullable(name: String, value: String?) {
        if (value == null) remove(name) else addProperty(name, value)
    }

    private fun JsonObject.objects(name: String): List<JsonObject> =
        get(name)?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull { it.objectOrNull() }
            .orEmpty()

    private fun JsonObject.float(name: String): Float? = get(name)
        ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
        ?.let { runCatching { it.asFloat.takeIf(Float::isFinite) }.getOrNull() }
}
