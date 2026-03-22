package com.example.myapplication.data.repository.context

import com.example.myapplication.model.ContextDataBundle
import com.google.gson.Gson

class ContextTransferCodec(
    private val gson: Gson = Gson(),
) {
    fun encode(bundle: ContextDataBundle): String {
        return gson.toJson(bundle)
    }

    fun decode(rawJson: String): ContextDataBundle {
        val bundle = runCatching {
            gson.fromJson(rawJson, ContextDataBundle::class.java)
        }.getOrNull() ?: throw IllegalArgumentException("导入文件格式无效")
        return ContextDataBundle(
            version = bundle.version,
            exportedAt = bundle.exportedAt,
            assistants = bundle.assistants.orEmpty(),
            worldBookEntries = bundle.worldBookEntries.orEmpty(),
            memoryEntries = bundle.memoryEntries.orEmpty(),
            conversationSummaries = bundle.conversationSummaries.orEmpty(),
        )
    }
}
