package com.example.myapplication.data.local

import com.example.myapplication.model.*
import com.example.myapplication.system.json.AppJson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.security.MessageDigest

/** CAS 和幂等标记一起生成，调用者须在同一次 DataStore edit 中保存三项。 */
internal object ContextTransferSettingsCodec {
    data class Write(
        val assistantsJson: String, val companionJson: String, val receiptsJson: String,
        val result: ContextTransferSettingsResult,
    )

    fun apply(
        assistantsJson: String, companionJson: String, receiptsJson: String,
        currentAssistants: List<Assistant>, operationId: String, planHash: String,
        target: ContextTransferSettingsTarget,
    ): Write {
        require(
            Regex("[A-Za-z0-9_-]{1,80}").matches(operationId) && Regex("[a-f0-9]{64}").matches(
                planHash
            )
        ) { "导入计划凭证无效" }
        require(target.assistantId.isNotBlank() && target.importedAssistant?.id?.let { it == target.assistantId } != false && target.expectedAssistant?.id?.let { it == target.assistantId } != false) { "导入目标角色不匹配" }
        fun unchanged(result: ContextTransferSettingsResult) =
            Write(assistantsJson, companionJson, receiptsJson, result)

        val receipts =
            if (receiptsJson.isBlank()) JsonObject() else JsonParser.parseString(receiptsJson)
                .also { require(it.isJsonObject) { "设置导入回执不可读取" } }.asJsonObject
        val receiptKey = operationId + "_" + hash(target.assistantId)
        val actionHash = hash(planHash + "\u0000" + AppJson.gson.toJson(target))
        receipts.get(receiptKey)?.let { receipt ->
            require(
                receipt.isJsonObject && receipt.asJsonObject.get("planHash")?.asString == planHash && receipt.asJsonObject.get(
                    "actionHash"
                )?.asString == actionHash
            ) { "同一导入计划的设置目标已改变" }
            return unchanged(ContextTransferSettingsResult.AlreadyApplied)
        }
        val resolved = AppSettings(assistants = currentAssistants).resolvedAssistants()
        if (resolved.firstOrNull { it.id == target.assistantId } != target.expectedAssistant) return unchanged(
            ContextTransferSettingsResult.Conflict
        )
        if (target.importedAssistant == null && target.expectedAssistant == null) return unchanged(
            ContextTransferSettingsResult.Conflict
        )
        var nextCompanion = companionJson
        target.companionPatch?.let { patch ->
            val (json, written) = CompanionSettingsCodec.update(
                companionJson,
                target.assistantId,
                target.expectedCompanionRevision,
                patch
            )
            when (written) {
                is CompanionSettingsWriteResult.Saved -> nextCompanion = json
                CompanionSettingsWriteResult.UnsupportedVersion -> return unchanged(
                    ContextTransferSettingsResult.UnsupportedVersion
                )

                else -> return unchanged(ContextTransferSettingsResult.Conflict)
            }
        }
        val nextAssistants = if (target.importedAssistant == null) assistantsJson else {
            require(!target.importedAssistant.isBuiltin && target.assistantId !in (BUILTIN_ASSISTANTS.map { it.id } + HIDDEN_BUILTIN_ASSISTANT_IDS)) { "导入不能覆盖内置角色" }
            val original =
                if (assistantsJson.isBlank()) JsonArray() else JsonParser.parseString(assistantsJson)
                    .also { require(it.isJsonArray) { "目标角色列表不可读取" } }.asJsonArray
            JsonArray().apply {
                var replaced = false
                original.forEach { item ->
                    if (item.isJsonObject && item.asJsonObject.get("id")?.asString == target.assistantId) {
                        require(!replaced) { "目标角色标识重复" }
                        add(AppJson.gson.toJsonTree(target.importedAssistant.normalizedForContextImport()))
                        replaced = true
                    } else add(item.deepCopy())
                }
                if (!replaced) add(AppJson.gson.toJsonTree(target.importedAssistant.normalizedForContextImport()))
            }.let(AppJson.gson::toJson)
        }
        receipts.add(
            receiptKey,
            JsonObject().apply {
                addProperty("planHash", planHash); addProperty(
                "actionHash",
                actionHash
            )
            })
        return Write(
            nextAssistants,
            nextCompanion,
            AppJson.gson.toJson(receipts),
            ContextTransferSettingsResult.Applied
        )
    }

    private fun hash(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
