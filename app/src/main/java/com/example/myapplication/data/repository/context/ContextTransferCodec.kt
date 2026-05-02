package com.example.myapplication.data.repository.context

import com.example.myapplication.model.ContextDataBundle
import com.example.myapplication.system.json.AppJson
import com.example.myapplication.system.logging.logFailure
import com.google.gson.Gson

class ContextTransferCodec(
    private val gson: Gson = AppJson.gson,
) {
    fun encode(bundle: ContextDataBundle): String {
        return gson.toJson(bundle)
    }

    fun decode(rawJson: String): ContextDataBundle {
        val bundle = runCatching {
            gson.fromJson(rawJson, ContextDataBundle::class.java)
        }.logFailure("CtxTransferCodec") { "decode fromJson failed, raw.len=${rawJson.length}" }
            .getOrNull() ?: throw IllegalArgumentException("导入文件格式无效")
        return ContextDataBundle(
            version = bundle.version,
            exportedAt = bundle.exportedAt,
            assistants = bundle.assistants.orEmpty(),
            worldBookEntries = bundle.worldBookEntries.orEmpty(),
            memoryEntries = bundle.memoryEntries.orEmpty(),
            conversationSummaries = bundle.conversationSummaries.orEmpty(),
            conversationSummarySegments = bundle.conversationSummarySegments.orEmpty(),
            presets = bundle.presets.orEmpty(),
        )
    }
}
