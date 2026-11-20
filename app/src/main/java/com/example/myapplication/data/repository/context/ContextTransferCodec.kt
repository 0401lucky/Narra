package com.example.myapplication.data.repository.context

import com.example.myapplication.model.ContextDataBundle
import com.example.myapplication.model.PromptCompletionStatus
import com.example.myapplication.system.json.AppJson
import com.example.myapplication.system.logging.logFailure
import com.google.gson.Gson

class ContextTransferCodec(
    private val gson: Gson = AppJson.gson,
) {
    fun encode(bundle: ContextDataBundle): String {
        if (bundle.companions != null) {
            require(bundle.version == 3) { "陪伴偏好需要 v3 私人资料包" }
            CompanionAppearanceTransferCodec().privateEntries(gson.toJsonTree(bundle.companions))
        }
        return gson.toJson(bundle).also { if (bundle.version == 3) MemoryTransferJson.parse(it) }
    }

    fun decode(rawJson: String): ContextDataBundle {
        val root = try {
            MemoryTransferJson.parse(rawJson)
        } catch (failure: Exception) {
            throw IllegalArgumentException("导入文件格式无效", failure)
        }
        val version = root.get("version")?.let { MemoryTransferJson.integer(it, "资料版本") } ?: 1L
        require(version in 1L..3L) { "不支持的资料包版本" }
        if (version >= 2L) {
            require(root.has("exportedAt")) { "资料包缺少导出时间" }
            require(
                MemoryTransferJson.integer(
                    root.get("exportedAt"),
                    "导出时间"
                ) >= 0L
            ) { "导出时间无效" }
        }
        root.get("memoryArchive")?.takeUnless { it.isJsonNull }?.let {
            require(version >= 2L) { "v1 资料包不能包含 v2 记忆档案" }
            MemoryTransferJson.validateArchive(it)
        }
        listOf(
            "assistants",
            "worldBookEntries",
            "memoryEntries",
            "conversationSummaries",
            "conversationSummarySegments",
            "presets"
        ).forEach { name ->
            root.get(name)?.takeUnless { it.isJsonNull }
                ?.let { require(it.isJsonArray) { "资料列表格式无效" } }
        }
        MemoryTransferJson.validateLegacy(root)
        val companions = root.get("companions")?.takeUnless { it.isJsonNull }?.let {
            require(version == 3L) { "旧资料包不能包含 v3 陪伴偏好" }
            CompanionAppearanceTransferCodec().privateEntries(it)
        }
        val bundle = runCatching {
            gson.fromJson(root, ContextDataBundle::class.java)
        }.logFailure("CtxTransferCodec") { "decode fromJson failed, raw.len=${rawJson.length}" }
            .getOrNull() ?: throw IllegalArgumentException("导入文件格式无效")
        return ContextDataBundle(
            version = version.toInt(),
            exportedAt = bundle.exportedAt,
            assistants = bundle.assistants.orEmpty(),
            worldBookEntries = bundle.worldBookEntries.orEmpty(),
            memoryEntries = bundle.memoryEntries.orEmpty(),
            conversationSummaries = bundle.conversationSummaries.orEmpty().map {
                it.copy(
                    assistantId = it.assistantId.orEmpty(),
                    summary = it.summary.orEmpty(),
                    sourceSnapshot = it.sourceSnapshot.orEmpty(),
                    completionStatus = completion(it.completionStatus),
                )
            },
            conversationSummarySegments = bundle.conversationSummarySegments.orEmpty().map {
                it.copy(
                    assistantId = it.assistantId.orEmpty(),
                    summary = it.summary.orEmpty(),
                    startMessageId = it.startMessageId.orEmpty(),
                    endMessageId = it.endMessageId.orEmpty(),
                    sourceSnapshot = it.sourceSnapshot.orEmpty(),
                    completionStatus = completion(it.completionStatus),
                )
            },
            presets = bundle.presets.orEmpty(),
            memoryArchive = bundle.memoryArchive,
            companions = companions,
        ).also { restored ->
            restored.memoryArchive?.let(MemoryArchiveTransferValidation::validate)
            MemoryArchiveTransferValidation.validateLegacy(restored)
        }
    }

    private fun completion(status: PromptCompletionStatus?): PromptCompletionStatus =
        status ?: PromptCompletionStatus.UNKNOWN
}
