package com.example.myapplication.conversation

import com.example.myapplication.model.MemoryEventSummaryOutput
import com.example.myapplication.model.MemoryEventSummarySnapshot
import com.example.myapplication.model.MemoryRecordVersion
import com.example.myapplication.model.PromptCompletionResult
import com.example.myapplication.model.PromptCompletionStatus
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.io.IOException
import java.math.BigDecimal
import java.util.concurrent.CancellationException

/** 结构通过不代表允许归档；调用方仍须保存原完成状态并只发布 COMPLETED 产物。 */
object MemoryEventSummaryParser {
    fun parse(
        result: PromptCompletionResult,
        snapshot: MemoryEventSummarySnapshot,
    ): MemoryEventSummaryOutput {
        require(result.status == PromptCompletionStatus.COMPLETED || result.status == PromptCompletionStatus.UNKNOWN) {
            "模型未完整结束，不能发布事件摘要"
        }
        require(result.content.isNotBlank()) { "模型没有返回事件摘要正文" }
        MemoryEventSummaryFormatter.validate(snapshot)
        val root = strictObject(result.content)
        require(
            root.keySet() == setOf(
                "schema_version",
                "summary",
                "current_state",
                "covered_refs"
            )
        ) {
            "事件摘要字段缺失或包含未授权字段"
        }
        val version = root.get("schema_version")
        require(
            version.isJsonPrimitive && version.asJsonPrimitive.isNumber && version.asBigDecimal.compareTo(
                BigDecimal.ONE
            ) == 0
        ) {
            "事件摘要结构版本无效"
        }
        val summary = root.requiredContent("summary")
        val currentState = root.requiredContent("current_state")
        val refs = root.get("covered_refs")
        require(refs.isJsonArray && refs.asJsonArray.size() == snapshot.members.size) {
            "事件摘要没有覆盖本次全部成员"
        }
        val coveredRefs = refs.asJsonArray.map { value -> value.requiredString() }
        val expectedRefs = snapshot.members.indices.map { "M$it" }.toSet()
        require(coveredRefs.toSet() == expectedRefs && coveredRefs.distinct().size == coveredRefs.size) {
            "事件摘要成员引用无效、重复或越界"
        }
        return MemoryEventSummaryOutput(
            summary = summary,
            currentState = currentState,
            coveredRecords = snapshot.members.map {
                MemoryRecordVersion(
                    it.record.id,
                    it.record.revision
                )
            },
        )
    }

    private fun strictObject(content: String): JsonObject {
        val value = try {
            MemoryExtractionParser.strictJson(content)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: IOException) {
            throw IllegalArgumentException("事件摘要 JSON 不完整或格式无效")
        } catch (_: IllegalStateException) {
            throw IllegalArgumentException("事件摘要 JSON 不完整或格式无效")
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("事件摘要 JSON 不完整或格式无效")
        }
        require(value.isJsonObject) { "事件摘要必须是完整 JSON 对象" }
        return value.asJsonObject
    }

    private fun JsonObject.requiredContent(name: String): String {
        val value = get(name).requiredString()
        require(value.isNotBlank() && value.length <= MemoryEventSummaryFormatter.MAX_SUMMARY_CHARACTERS) {
            "事件摘要正文为空或过长"
        }
        return value.trim()
    }

    private fun JsonElement.requiredString(): String {
        require(isJsonPrimitive && asJsonPrimitive.isString) { "事件摘要文本类型无效" }
        return asString
    }
}
