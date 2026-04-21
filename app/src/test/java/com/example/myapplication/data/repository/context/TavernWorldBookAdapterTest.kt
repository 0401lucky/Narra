package com.example.myapplication.data.repository.context

import com.example.myapplication.system.json.AppJson
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
                  "probability": 80,
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
        assertEquals(80, extras.get("probability").asInt)
        assertEquals(3, extras.get("depth").asInt)
        assertEquals(1, extras.get("position_override").asInt)
        assertEquals("system", extras.get("role").asString)
        assertEquals("AND", extras.get("logic").asString)
        assertFalse("extrasJson 不应包含已映射的 content", extras.has("content"))
        assertFalse("extrasJson 不应包含已映射的 uid", extras.has("uid"))
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
}
