package com.example.myapplication.data.local.chat

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class ContextTransferSchemaContractTest {
    @Test
    fun schema57_onlyAddsOperationReceiptAndKeepsFrozen56Definitions() {
        val source =
            appFile("schemas/com.example.myapplication.data.local.chat.ChatDatabase/56.json")
        assertEquals(
            "d5e2dff049818be64c7ab4b3bf2b767b99f1bc0546756f10a333ca05e903eab7",
            sha256(source.readBytes())
        )
        val previous =
            JsonParser.parseString(source.readText()).asJsonObject.getAsJsonObject("database")
        val current =
            JsonParser.parseString(appFile("schemas/com.example.myapplication.data.local.chat.ChatDatabase/57.json").readText()).asJsonObject.getAsJsonObject(
                "database"
            )
        assertEquals(56, previous["version"].asInt)
        assertEquals(57, current["version"].asInt)
        val old = previous.getAsJsonArray("entities")
            .associate { it.asJsonObject["tableName"].asString to it }
        val next = current.getAsJsonArray("entities")
            .associate { it.asJsonObject["tableName"].asString to it }
        assertEquals(setOf("context_transfer_operations"), next.keys - old.keys)
        old.forEach { (name, definition) ->
            assertEquals(
                "旧表 $name 定义必须保持",
                definition,
                next[name]
            )
        }
        assertEquals(previous.getAsJsonArray("views"), current.getAsJsonArray("views"))
    }

    private fun appFile(name: String): File =
        sequenceOf(File(name), File("app", name)).first { it.isFile }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
