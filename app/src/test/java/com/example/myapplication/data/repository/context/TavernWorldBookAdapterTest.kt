package com.example.myapplication.data.repository.context

import com.example.myapplication.model.CONTEXT_IMPORT_MAX_WORLD_BOOK_CONTENT_CHARS
import com.example.myapplication.model.CONTEXT_IMPORT_MAX_WORLD_BOOK_ENTRIES
import com.example.myapplication.model.CONTEXT_IMPORT_MAX_WORLD_BOOK_EXTRAS_CHARS
import com.example.myapplication.model.CONTEXT_IMPORT_MAX_WORLD_BOOK_SOURCE_NAME_CHARS
import com.example.myapplication.model.CONTEXT_IMPORT_MAX_WORLD_BOOK_TITLE_CHARS
import com.example.myapplication.system.json.AppJson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TavernWorldBookAdapterTest {
    private val adapter = TavernWorldBookAdapter()
    private val gson = AppJson.gson

    @Test
    fun decodeAsBundle_capturesUnknownFieldsIntoExtrasJson() {
        val raw = """
            {
              "name": "测试书",
              "entries": [
                {
                  "uid": "u-1",
                  "comment": "标题",
                  "content": "正文",
                  "key": ["关键词"],
                  "depth": 3,
                  "position_override": 1,
                  "role": "system",
                  "logic": "AND"
                }
              ]
            }
        """.trimIndent()

        val bundle = adapter.decodeAsBundle(raw)!!
        val entry = bundle.worldBookEntries.single()
        val extras = gson.fromJson(entry.extrasJson, JsonObject::class.java)
        assertEquals(3, extras.get("depth").asInt)
        assertEquals(1, extras.get("position_override").asInt)
        assertEquals("system", extras.get("role").asString)
        assertEquals("AND", extras.get("logic").asString)
        assertFalse("extrasJson 不应包含已映射的 content", extras.has("content"))
        assertFalse("extrasJson 不应包含已映射的 uid", extras.has("uid"))
    }

    @Test
    fun decodeAsBundle_mapsProbabilityToDedicatedField() {
        val raw = """
            {
              "name": "测试书",
              "entries": [
                { "uid":"u-1", "comment":"t", "content":"c", "key":["k"], "probability": 80 }
              ]
            }
        """.trimIndent()

        val entry = adapter.decodeAsBundle(raw)!!.worldBookEntries.single()
        assertEquals(80, entry.probability)
        val extras = gson.fromJson(entry.extrasJson, JsonObject::class.java)
        assertFalse("probability 应成为独立字段，不再保留在 extrasJson", extras.has("probability"))
    }

    @Test
    fun decodeAsBundle_missingProbabilityDefaultsTo100() {
        val raw = """
            {
              "name": "测试书",
              "entries": [
                { "uid":"u-1", "comment":"t", "content":"c", "key":["k"] }
              ]
            }
        """.trimIndent()

        val entry = adapter.decodeAsBundle(raw)!!.worldBookEntries.single()
        assertEquals(100, entry.probability)
    }

    @Test
    fun decodeAsBundle_leavesExtrasEmptyWhenAllFieldsRecognized() {
        val raw = """
            {
              "name": "书",
              "entries": [
                { "uid":"1","comment":"a","content":"b","key":["k"] }
              ]
            }
        """.trimIndent()

        val bundle = adapter.decodeAsBundle(raw)!!
        assertEquals("{}", bundle.worldBookEntries.single().extrasJson)
    }

    @Test
    fun decodeAsBundle_limitsEntriesFieldsAndOversizedExtrasSafely() {
        val longTitle = "t".repeat(CONTEXT_IMPORT_MAX_WORLD_BOOK_TITLE_CHARS + 20)
        val longContent = "c".repeat(CONTEXT_IMPORT_MAX_WORLD_BOOK_CONTENT_CHARS + 20)
        val longBookName = "b".repeat(CONTEXT_IMPORT_MAX_WORLD_BOOK_SOURCE_NAME_CHARS + 20)
        val hugeExtras = "x".repeat(CONTEXT_IMPORT_MAX_WORLD_BOOK_EXTRAS_CHARS + 20)
        val entries = (0 until CONTEXT_IMPORT_MAX_WORLD_BOOK_ENTRIES + 2)
            .joinToString(",") { index ->
                if (index == 0) {
                    """
                    {
                      "uid":"u-$index",
                      "comment":"$longTitle",
                      "content":"$longContent",
                      "key":["关键词"],
                      "safe":"保留",
                      "huge":"$hugeExtras"
                    }
                    """.trimIndent()
                } else {
                    """{"uid":"u-$index","comment":"标题$index","content":"正文$index","key":["k$index"]}"""
                }
            }
        val raw = """{"name":"$longBookName","entries":[$entries]}"""

        val bundle = adapter.decodeAsBundle(raw)!!
        val first = bundle.worldBookEntries.first()
        val extras = JsonParser.parseString(first.extrasJson).asJsonObject

        assertEquals(CONTEXT_IMPORT_MAX_WORLD_BOOK_ENTRIES, bundle.worldBookEntries.size)
        assertEquals(CONTEXT_IMPORT_MAX_WORLD_BOOK_TITLE_CHARS, first.title.length)
        assertEquals(CONTEXT_IMPORT_MAX_WORLD_BOOK_CONTENT_CHARS, first.content.length)
        assertEquals(CONTEXT_IMPORT_MAX_WORLD_BOOK_SOURCE_NAME_CHARS, first.sourceBookName.length)
        assertEquals("保留", extras.get("safe").asString)
        assertFalse("超限 extras 字段应被丢弃，而不是截断成非法 JSON", extras.has("huge"))
        assertTrue(first.extrasJson.length <= CONTEXT_IMPORT_MAX_WORLD_BOOK_EXTRAS_CHARS)
    }

    @Test
    fun decodeAsBundle_sameUidProducesSameStableIdAcrossMinorEdits() {
        val raw1 = """{"name":"书","entries":[{"uid":"42","comment":"t","content":"老正文","key":["k"]}]}"""
        val raw2 = """{"name":"书","entries":[{"uid":"42","comment":"t","content":"改过的正文","key":["k"]}]}"""

        val id1 = adapter.decodeAsBundle(raw1)!!.worldBookEntries.single().id
        val id2 = adapter.decodeAsBundle(raw2)!!.worldBookEntries.single().id

        assertEquals("uid 一致时 stableId 应保持不变", id1, id2)
    }

    @Test
    fun decodeAsBundle_differentUidProducesDifferentStableId() {
        val raw1 = """{"name":"书","entries":[{"uid":"42","comment":"t","content":"a","key":["k"]}]}"""
        val raw2 = """{"name":"书","entries":[{"uid":"99","comment":"t","content":"a","key":["k"]}]}"""

        val id1 = adapter.decodeAsBundle(raw1)!!.worldBookEntries.single().id
        val id2 = adapter.decodeAsBundle(raw2)!!.worldBookEntries.single().id

        if (id1 == id2) {
            throw AssertionError("不同 uid 必须产生不同 stableId，但都是 $id1")
        }
    }

    @Test
    fun decodeAsBundle_missingUidFallsBackToHashAndStaysDeterministic() {
        val raw1 = """{"name":"书","entries":[{"comment":"t","content":"a","key":["k"]}]}"""
        val raw2 = """{"name":"书","entries":[{"comment":"t","content":"a","key":["k"]}]}"""

        val id1 = adapter.decodeAsBundle(raw1)!!.worldBookEntries.single().id
        val id2 = adapter.decodeAsBundle(raw2)!!.worldBookEntries.single().id

        assertEquals("无 uid 时依赖 hash：相同内容的条目应产生相同 stableId", id1, id2)
    }
}
