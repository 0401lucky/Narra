package com.example.myapplication.data.local

import com.example.myapplication.model.CompanionRequestDescriptor
import com.example.myapplication.system.json.AppJson
import com.google.gson.JsonParser
import com.example.myapplication.data.local.CompanionWritingCodec.long

internal object CompanionDescriptorCodec {
    fun decode(raw: String): CompanionRequestDescriptor {
        val root = JsonParser.parseString(raw).asJsonObject
        val protocol = if (!root.has("protocolVersion")) 1 else
            root.long("protocolVersion")?.takeIf { it in 1L..Int.MAX_VALUE.toLong() }?.toInt() ?: 0
        return AppJson.gson.fromJson(root, CompanionRequestDescriptor::class.java).copy(
            protocolVersion = protocol,
            presentation = CompanionWritingCodec.decodeSnapshot(root.get("presentation")),
        )
    }
}
