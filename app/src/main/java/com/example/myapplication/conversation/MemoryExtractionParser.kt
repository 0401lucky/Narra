package com.example.myapplication.conversation

import com.example.myapplication.model.MemoryCategory
import com.example.myapplication.model.MemoryCommitBatch
import com.example.myapplication.model.MemoryExtractionFormat
import com.example.myapplication.model.MemoryExtractionSnapshot
import com.example.myapplication.model.MemoryRecordCorrection
import com.example.myapplication.model.MemoryRecordDraft
import com.example.myapplication.model.MemoryRecordKind
import com.example.myapplication.model.MemoryRelationDraft
import com.example.myapplication.model.MemorySourceRef
import com.example.myapplication.model.MemoryResidentAction
import com.example.myapplication.model.MemoryResidentCategory
import com.example.myapplication.model.MemoryResidentOperation
import com.example.myapplication.model.MemoryStateAction
import com.example.myapplication.model.MemoryStateKey
import com.example.myapplication.model.MemoryStateOperation
import com.example.myapplication.model.MemorySemanticKind
import com.example.myapplication.model.PromptCompletionResult
import com.example.myapplication.model.PromptCompletionStatus
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.StringReader
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId

/** 失败永远不是空记忆；校验完整动作集后才交给统一事务入口。 */
object MemoryExtractionParser {
    fun parse(
        result: PromptCompletionResult,
        snapshot: MemoryExtractionSnapshot
    ): MemoryCommitBatch {
        require(
            result.status in setOf(
                PromptCompletionStatus.COMPLETED,
                PromptCompletionStatus.UNKNOWN
            )
        ) {
            "模型未完整结束，未提交记忆"
        }
        require(result.content.isNotBlank()) { "模型没有返回记忆正文" }
        val semanticSources =
            snapshot.sources.filter { it.skipReason.isEmpty() && it.text.isNotBlank() }
        require(semanticSources.isNotEmpty()) { "提取结果缺少语义来源" }
        val refs = semanticSources.mapIndexed { index, span -> "M$index" to span.ref }.toMap()
        if (snapshot.format == MemoryExtractionFormat.LEGACY_LIST) {
            return parseLegacy(result.content, refs.values.toList())
        }
        val root = strictJson(result.content).objectValue()
        root.fields(
            "schema_version",
            "memories",
            "corrections",
            "state_updates",
            "resident_candidates"
        )
        require(root.integer("schema_version") == 2L) { "不支持的记忆结构版本" }
        val targets = snapshot.targets.mapIndexed { index, target -> "O$index" to target }.toMap()
        val memories = root.array("memories", 100)
        val corrections = root.array("corrections", 100)
        val localIds = memories.map { it.objectValue().string("local_id") }
        require(localIds.all { it.matches(Regex("N[0-9]{1,4}")) } && localIds.distinct().size == localIds.size) {
            "记忆局部编号无效或重复"
        }
        val relations = mutableListOf<MemoryRelationDraft>()
        val records = memories.map { item ->
            val obj = item.objectValue()
            obj.fields(
                "local_id",
                "kind",
                "category",
                "content",
                "source_refs",
                "related_to",
                "occurred_date",
                optional = setOf("importance")
            )
            val localId = obj.string("local_id")
            val kind = when (obj.string("kind")) {
                "episode" -> MemoryRecordKind.EPISODE
                "fact" -> MemoryRecordKind.FACT
                "opinion" -> MemoryRecordKind.OPINION
                "wish" -> MemoryRecordKind.WISH
                else -> error("记忆种类无效")
            }
            val category =
                MemoryCategory.entries.singleOrNull { it.name.lowercase() == obj.string("category") }
                    ?: error("记忆分类无效")
            val related = obj.array("related_to", 40).map { it.stringValue() }
            require(related.distinct().size == related.size) { "记忆关联重复" }
            related.forEach { targetRef ->
                require(targetRef != localId) { "记忆不能关联自身" }
                when {
                    targetRef in targets -> relations += MemoryRelationDraft(
                        localId,
                        targetRecordId = targets.getValue(targetRef).id
                    )

                    targetRef in localIds -> relations += MemoryRelationDraft(
                        localId,
                        targetLocalId = targetRef
                    )

                    else -> error("记忆关联超出本次目标")
                }
            }
            val date = obj.get("occurred_date").let { value ->
                if (value.isJsonNull) null else {
                    val text = value.stringValue()
                    require(text.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}"))) { "事件日期无效" }
                    LocalDate.parse(text).atStartOfDay(ZoneId.of(snapshot.timeZoneId)).toInstant()
                        .toEpochMilli()
                }
            }
            val importance =
                if (obj.has("importance")) obj.integer("importance").also { require(it in 0..100) }
                    .toInt() else 60
            MemoryRecordDraft(
                localId, obj.content("content"), obj.refs(refs), kind, category, importance,
                date, if (date == null) "unknown" else "day",
            )
        }
        val changes = corrections.map { item ->
            val obj = item.objectValue()
            obj.fields("target_ref", "expected_revision", "replacement", "source_refs")
            val target = targets[obj.string("target_ref")] ?: error("纠正目标超出本次范围")
            require(obj.integer("expected_revision") == target.revision) { "纠正目标版本不符" }
            MemoryRecordCorrection(
                target.id,
                target.revision,
                obj.content("replacement"),
                obj.refs(refs)
            )
        }
        require(changes.map { it.recordId }.distinct().size == changes.size) { "纠正目标重复" }
        val knowledgeTargets = snapshot.knowledgeTargets
        require(knowledgeTargets == null || knowledgeTargets.version == 1) { "不支持的认知目标版本" }
        val stateKeys = mutableSetOf<String>()
        val states = root.array("state_updates", 40).map { item ->
            val obj = item.objectValue()
            obj.fields(
                "state_key",
                "operation",
                "content",
                "source_refs",
                optional = setOf("expected_revision", "expires_at")
            )
            val key =
                MemoryStateKey.entries.singleOrNull { it.name.lowercase() == obj.string("state_key") }
                    ?: error("状态种类无效")
            require(stateKeys.add(obj.string("state_key"))) { "同一状态不能重复更新" }
            val operation = when (obj.string("operation")) {
                "set" -> MemoryStateOperation.SET
                "end" -> MemoryStateOperation.END
                else -> error("状态动作无效")
            }
            val target = knowledgeTargets?.states?.singleOrNull { it.key == key }
            val expected =
                if (obj.has("expected_revision")) obj.integer("expected_revision") else null
            if (knowledgeTargets != null) {
                require((expected ?: 0L) == (target?.revision ?: 0L)) { "状态目标版本不符" }
                if (operation == MemoryStateOperation.END) require(target != null) { "结束状态缺少目标" }
            } else require(expected == null) { "旧快照没有状态目标版本" }
            val expiresAt = if (!obj.has("expires_at") || obj["expires_at"].isJsonNull) null
            else obj.integer("expires_at").also { require(it > 0) { "状态期限无效" } }
            MemoryStateAction(
                key, operation, obj.content("content"), obj.refs(refs),
                if (knowledgeTargets != null) target?.revision ?: 0L else null, expiresAt
            )
        }
        val residentTargetRefs = knowledgeTargets?.residents.orEmpty()
            .mapIndexed { index, target -> "R$index" to target }.toMap()
        val changedResidentIds = mutableSetOf<String>()
        val residents = root.array("resident_candidates", 40).map { item ->
            val obj = item.objectValue()
            obj.fields(
                "category", "content", "subjective", "source_refs",
                optional = setOf("operation", "target_ref", "expected_revision", "durability")
            )
            val category =
                MemoryResidentCategory.entries.singleOrNull { it.name.lowercase() == obj.string("category") }
                    ?: error("认知分类无效")
            require(obj["subjective"].isJsonPrimitive && obj["subjective"].asJsonPrimitive.isBoolean) { "认知主观标记无效" }
            val operation = when (if (obj.has("operation")) obj.string("operation") else "add") {
                "add" -> MemoryResidentOperation.ADD
                "replace" -> MemoryResidentOperation.REPLACE
                "reinforce" -> MemoryResidentOperation.REINFORCE
                "retire" -> MemoryResidentOperation.RETIRE
                else -> error("认知动作无效")
            }
            val target = if (operation == MemoryResidentOperation.ADD) {
                require(!obj.has("target_ref") && !obj.has("expected_revision")) { "新增认知不能指定替代目标" }
                null
            } else {
                val value =
                    residentTargetRefs[obj.string("target_ref")] ?: error("认知目标超出本次范围")
                require(obj.integer("expected_revision") == value.revision && value.category == category) { "认知目标版本或分类不符" }
                require(changedResidentIds.add(value.id)) { "认知目标重复" }
                value
            }
            val durability = if (obj.has("durability")) obj.string("durability") else "stable"
            require(durability in setOf("stable", "temporary")) { "认知时效标记无效" }
            val sourceRefs = obj.refs(refs)
            val subjective = obj["subjective"].asBoolean || semanticSources.any {
                it.ref in sourceRefs && it.semanticKind in setOf(
                    MemorySemanticKind.THOUGHT,
                    MemorySemanticKind.DESCRIPTION
                )
            }
            val content = obj.content("content")
            if (operation == MemoryResidentOperation.REINFORCE) {
                require(target?.content == content && target.subjective == subjective) { "补充来源不能改变已有认知" }
            }
            MemoryResidentAction(
                category, content, subjective, sourceRefs, operation,
                target?.id.orEmpty(), target?.revision ?: 0L,
                durability == "stable" && states.none { it.content == content })
        }
        return MemoryCommitBatch(records, changes, relations, residents, states)
    }

    private fun parseLegacy(content: String, refs: List<MemorySourceRef>): MemoryCommitBatch {
        val trimmed = content.trim()
        val values = if (trimmed.startsWith("[")) {
            val array = strictJson(trimmed)
            require(array.isJsonArray) { "旧模板必须返回文本列表" }
            array.asJsonArray.map { it.stringValue() }
        } else {
            val lines = trimmed.lines().filter { it.isNotBlank() }
            val listItem = Regex("^(?:[-•]|[0-9]+[.)、])\\s+(.+)$")
            lines.map { line ->
                listItem.matchEntire(line.trim())?.groupValues?.get(1)
                    ?: error("旧模板没有返回有效列表")
            }
        }
        require(values.size <= 100 && values.all { it.isNotBlank() && it.length <= 12_000 }) { "旧模板条目无效" }
        return MemoryCommitBatch(records = values.mapIndexed { index, text ->
            MemoryRecordDraft("N$index", text.trim(), refs, MemoryRecordKind.LEGACY_UNCLASSIFIED)
        })
    }

    private fun JsonObject.refs(refs: Map<String, MemorySourceRef>): List<MemorySourceRef> {
        val names = array("source_refs", 200).map { it.stringValue() }
        require(names.isNotEmpty() && names.distinct().size == names.size) { "记忆必须引用有效来源" }
        return names.map { refs[it] ?: error("记忆引用了未提供的来源") }
    }

    private fun JsonObject.fields(vararg required: String, optional: Set<String> = emptySet()) {
        require(keySet().containsAll(required.toList()) && keySet().all { it in required || it in optional }) { "记忆字段缺失或包含越权字段" }
    }

    private fun JsonObject.array(name: String, limit: Int): JsonArray {
        val value = get(name)
        require(value != null && value.isJsonArray && value.asJsonArray.size() <= limit) { "记忆列表无效或过长" }
        return value.asJsonArray
    }

    private fun JsonObject.string(name: String): String =
        (get(name) ?: error("记忆字段缺失")).stringValue()

    private fun JsonObject.content(name: String): String = string(name).also {
        require(it.isNotBlank() && it.length <= 12_000) { "记忆正文为空或过长" }
    }.trim()

    private fun JsonObject.integer(name: String): Long {
        val value = get(name) ?: error("记忆数字缺失")
        require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber) { "记忆数字无效" }
        return value.asBigDecimal.longValueExact()
    }

    private fun JsonElement.stringValue(): String {
        require(isJsonPrimitive && asJsonPrimitive.isString) { "记忆文本类型无效" }
        return asString
    }

    private fun JsonElement.objectValue(): JsonObject {
        require(isJsonObject) { "记忆结构必须为对象" }
        return asJsonObject
    }

    internal fun strictJson(content: String): JsonElement {
        require(content.length <= 256_000) { "记忆响应过长" }
        return JsonReader(StringReader(content)).use { reader ->
            reader.isLenient = false
            fun read(depth: Int): JsonElement {
                require(depth <= 24) { "记忆结构过深" }
                return when (reader.peek()) {
                    JsonToken.BEGIN_OBJECT -> JsonObject().apply {
                        reader.beginObject()
                        while (reader.hasNext()) {
                            val name = reader.nextName()
                            require(!has(name)) { "记忆字段重复" }
                            add(name, read(depth + 1))
                        }
                        reader.endObject()
                    }

                    JsonToken.BEGIN_ARRAY -> JsonArray().apply {
                        reader.beginArray()
                        while (reader.hasNext()) add(read(depth + 1))
                        reader.endArray()
                    }

                    JsonToken.STRING -> JsonPrimitive(reader.nextString())
                    JsonToken.NUMBER -> JsonPrimitive(BigDecimal(reader.nextString()))
                    JsonToken.BOOLEAN -> JsonPrimitive(reader.nextBoolean())
                    JsonToken.NULL -> {
                        reader.nextNull(); JsonNull.INSTANCE
                    }

                    else -> error("记忆 JSON 不完整")
                }
            }
            read(0).also { require(reader.peek() == JsonToken.END_DOCUMENT) { "记忆响应包含多余文本" } }
        }
    }
}
