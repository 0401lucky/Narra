package com.example.myapplication.data.repository.context

import com.example.myapplication.model.*
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.StringReader
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType
import java.math.BigDecimal

/** 先验证原始 JSON 类型和必填字段，避免 Gson 给缺失的 Kotlin 字段注入 null/0。 */
internal object MemoryTransferJson {
    const val MAX_BYTES = 16 * 1024 * 1024
    const val MAX_ITEMS = 100_000

    fun parse(raw: String): JsonObject {
        require(raw.length <= MAX_BYTES && raw.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "资料包超过 16 MB 上限" }
        return JsonReader(StringReader(raw)).use { reader ->
            reader.isLenient = false
            var itemCount = 0
            fun read(depth: Int): JsonElement {
                require(depth <= 32 && ++itemCount <= 2_000_000) { "资料包结构过大或过深" }
                return when (reader.peek()) {
                    JsonToken.BEGIN_OBJECT -> JsonObject().apply {
                        reader.beginObject()
                        while (reader.hasNext()) {
                            val key = reader.nextName()
                            require(!has(key)) { "资料包含重复字段" }
                            add(key, read(depth + 1))
                        }
                        reader.endObject()
                    }

                    JsonToken.BEGIN_ARRAY -> JsonArray().apply {
                        reader.beginArray()
                        while (reader.hasNext()) {
                            require(size() < MAX_ITEMS) { "资料包条目过多" }
                            add(read(depth + 1))
                        }
                        reader.endArray()
                    }

                    JsonToken.STRING -> JsonPrimitive(reader.nextString())
                    JsonToken.NUMBER -> JsonPrimitive(BigDecimal(reader.nextString()))
                    JsonToken.BOOLEAN -> JsonPrimitive(reader.nextBoolean())
                    JsonToken.NULL -> {
                        reader.nextNull(); JsonNull.INSTANCE
                    }

                    else -> throw IllegalArgumentException("资料包 JSON 不完整")
                }
            }

            val root = read(0)
            require(root.isJsonObject && reader.peek() == JsonToken.END_DOCUMENT) { "资料包必须是完整对象" }
            root.asJsonObject
        }
    }

    fun integer(value: JsonElement?, label: String): Long {
        require(value?.isJsonPrimitive == true && value.asJsonPrimitive.isNumber) { "$label 必须为整数" }
        return try {
            value.asBigDecimal.longValueExact()
        } catch (_: ArithmeticException) {
            throw IllegalArgumentException("$label 必须为有效整数")
        }
    }

    fun validateArchive(value: JsonElement) = validate(value, MemoryArchiveTransferData::class.java)

    fun validateLegacy(root: JsonObject) {
        fun items(name: String): List<JsonObject> =
            root.get(name)?.takeUnless { it.isJsonNull }?.asJsonArray?.map {
                require(it.isJsonObject) { "旧记忆资料条目必须为对象" }; it.asJsonObject
            }.orEmpty()

        fun stringFields(item: JsonObject, required: Set<String>, optional: Set<String>) {
            for (name in required + optional) {
                val value = item.get(name)
                if (name in required || value != null) require(value?.isJsonPrimitive == true && value.asJsonPrimitive.isString) { "旧记忆资料文字字段无效" }
            }
        }

        fun numbers(item: JsonObject, names: List<String>) = names.forEach { name ->
            item.get(name)?.let {
                val number = integer(it, "旧资料数值")
                require(
                    number >= 0L && (name !in setOf(
                        "importance",
                        "messageCount",
                        "coveredMessageCount"
                    ) || number <= Int.MAX_VALUE)
                ) { "旧资料数值无效" }
            }
        }
        for (item in items("memoryEntries")) {
            stringFields(
                item,
                setOf("id", "content"),
                setOf("scopeId", "characterId", "sourceMessageId")
            )
            item.get("scopeType")?.let { validate(it, MemoryScopeType::class.java) }
            item.get("pinned")?.let { validate(it, Boolean::class.java) }
            numbers(item, listOf("createdAt", "updatedAt", "lastUsedAt", "importance"))
        }
        for (item in items("conversationSummarySegments")) require(item.has("id")) { "旧摘要分段缺少编号" }
        for (item in items("conversationSummaries") + items("conversationSummarySegments")) {
            stringFields(
                item,
                setOf("conversationId"),
                setOf("id", "assistantId", "summary", "startMessageId", "endMessageId")
            )
            numbers(
                item,
                listOf(
                    "coveredMessageCount",
                    "messageCount",
                    "createdAt",
                    "updatedAt",
                    "startCreatedAt",
                    "endCreatedAt"
                )
            )
            item.get("completionStatus")?.let { validate(it, PromptCompletionStatus::class.java) }
            item.get("memoryContext")?.takeUnless { it.isJsonNull }
                ?.let { validate(it, MemoryRuntimeContext::class.java) }
            item.get("sourceSnapshot")?.let { snapshot ->
                require(snapshot.isJsonArray) { "旧摘要来源列表无效" }
                snapshot.asJsonArray.forEach { validate(it, ConversationSummarySource::class.java) }
            }
        }
    }

    private fun validate(value: JsonElement, type: Type) {
        if (type is WildcardType) return validate(value, type.upperBounds.single())
        if (type is ParameterizedType) {
            require(type.rawType == List::class.java && value.isJsonArray) { "记忆档案列表格式无效" }
            value.asJsonArray.forEach { validate(it, type.actualTypeArguments.single()) }
            return
        }
        val klass = type as Class<*>
        when {
            klass == String::class.java -> require(value.isJsonPrimitive && value.asJsonPrimitive.isString) { "记忆档案文字字段无效" }
            klass == Boolean::class.java || klass == java.lang.Boolean::class.java ->
                require(value.isJsonPrimitive && value.asJsonPrimitive.isBoolean) { "记忆档案开关字段无效" }

            klass == Int::class.java || klass == java.lang.Integer::class.java ->
                require(
                    integer(
                        value,
                        "记忆档案数值"
                    ) in Int.MIN_VALUE..Int.MAX_VALUE
                ) { "记忆档案数值越界" }

            klass == Long::class.java || klass == java.lang.Long::class.java -> integer(
                value,
                "记忆档案数值"
            )

            klass == Float::class.java || klass == java.lang.Float::class.java ->
                require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber) { "记忆向量数值无效" }

            klass.isEnum -> require(
                value.isJsonPrimitive && value.asJsonPrimitive.isString &&
                        klass.enumConstants.orEmpty()
                            .any { (it as Enum<*>).name == value.asString }) { "记忆档案枚举值无效" }

            else -> {
                require(value.isJsonObject) { "记忆档案条目必须为对象" }
                val obj = value.asJsonObject
                val fields =
                    klass.declaredFields.filterNot { Modifier.isStatic(it.modifiers) || it.isSynthetic }
                require(
                    obj.keySet()
                        .all { name -> fields.any { it.name == name } }) { "记忆档案含不支持的字段" }
                fields.forEach { field ->
                    val child = obj.get(field.name)
                    if (child == null || child.isJsonNull) {
                        require(field.name in nullableFields[klass].orEmpty()) { "记忆档案缺少必需字段 ${field.name}" }
                    } else validate(child, field.genericType)
                }
            }
        }
    }

    private val nullableFields = mapOf(
        MemoryRecord::class.java to setOf("pinnedUntil", "occurredAt"),
        MemoryTransferEventMember::class.java to setOf("coveredRevision"),
        MemoryResidentEntry::class.java to setOf("supersedesId"),
        MemoryCurrentState::class.java to setOf("expiresAt", "endedAt"),
        MemoryKnowledgeRevision::class.java to setOf("expiresAt", "endedAt"),
        MemoryImportedSummaryMetadata::class.java to setOf("segmentId", "originalMemoryContext"),
        MemoryTransferOrigin::class.java to setOf(
            "originalId",
            "originalSpaceId",
            "originalCharacterId",
            "originalPersonaKey",
            "originalRevision",
            "originalCreatedAt",
            "originalUpdatedAt",
            "originalSourceKind",
            "originalSourceStatus",
            "originalConversationId",
            "originalMessageId",
            "originalSpeakerId",
            "originalSpeakerRole",
            "originalPurpose",
            "originalScenarioId",
            "originalRunId",
            "originalGeneration",
            "originalSpaceStatus",
            "originalLegacyScopeType",
            "originalLegacyScopeId",
            "originalLegacySourceMessageId",
            "legacySummary",
        ),
    )
}
