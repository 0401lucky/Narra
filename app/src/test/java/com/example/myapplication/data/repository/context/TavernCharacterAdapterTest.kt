package com.example.myapplication.data.repository.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TavernCharacterAdapterTest {
    private val adapter = TavernCharacterAdapter()

    @Test
    fun decodeAsBundle_mapsTavernCharacterToAssistant() {
        val rawJson = """
            {
              "spec": "chara_card_v2",
              "spec_version": "2.0",
              "data": {
                "name": "白塔侦探",
                "description": "一名擅长调查失窃案的侦探。",
                "personality": "冷静、克制、重视证据。",
                "scenario": "你正在白塔城调查商会失窃案。",
                "first_mes": "把现场情况告诉我，我先梳理线索。",
                "mes_example": "<START>\n用户：你怎么看嫌疑人？\n助手：先核对他的时间线。 ",
                "creator_notes": "适合悬疑推理剧情。",
                "tags": ["推理", "悬疑"]
              }
            }
        """.trimIndent()

        val bundle = adapter.decodeAsBundle(rawJson)
        val assistant = bundle?.assistants?.single()

        requireNotNull(assistant)
        assertEquals("白塔侦探", assistant.name)
        assertTrue(assistant.systemPrompt.contains("【角色描述】"))
        assertTrue(assistant.systemPrompt.contains("【性格特点】"))
        assertEquals("你正在白塔城调查商会失窃案。", assistant.scenario)
        assertEquals("把现场情况告诉我，我先梳理线索。", assistant.greeting)
        assertEquals(listOf("推理", "悬疑"), assistant.tags)
        assertTrue(assistant.memoryEnabled)
    }

    @Test
    fun decodeAsBundle_importsCharacterBookEntries() {
        val rawJson = """
            {
              "spec": "chara_card_v2",
              "spec_version": "2.0",
              "data": {
                "name": "璃珠侦探",
                "description": "擅长夜间调查。",
                "character_book": {
                  "name": "璃珠都市设定",
                  "entries": [
                    {
                      "id": 1,
                      "name": "璃珠都市",
                      "keys": ["/璃珠(都|城)市/i"],
                      "content": "璃珠都市是一座昼夜反差极强的海港城市。",
                      "priority": 9,
                      "insertion_order": 120
                    },
                    {
                      "id": 2,
                      "keys": ["夜巡"],
                      "secondary_keys": ["午夜", "钟楼"],
                      "selective": true,
                      "content": "夜巡期间必须避开旧钟楼北侧。",
                      "constant": false
                    }
                  ]
                }
              }
            }
        """.trimIndent()

        val bundle = adapter.decodeAsBundle(rawJson)
        val assistant = bundle?.assistants?.single()
        val worldBookEntries = bundle?.worldBookEntries.orEmpty()

        requireNotNull(assistant)
        assertEquals(2, worldBookEntries.size)
        assertEquals("璃珠都市", worldBookEntries[0].title)
        assertEquals("璃珠都市设定", worldBookEntries[0].sourceBookName)
        assertEquals(120, worldBookEntries[0].insertionOrder)
        assertEquals(assistant.id, worldBookEntries[0].scopeId)
        assertEquals(listOf("/璃珠(都|城)市/i"), worldBookEntries[0].keywords)
        assertTrue(worldBookEntries[1].selective)
        assertEquals(listOf("午夜", "钟楼"), worldBookEntries[1].secondaryKeywords)
        assertTrue(assistant.worldBookMaxEntries >= 8)
    }

    @Test
    fun decodeAsBundle_findsNestedCharacterBookNode() {
        val rawJson = """
            {
              "spec": "chara_card_v3",
              "data": {
                "name": "深港夜巡者",
                "description": "擅长在雨夜追踪线索。",
                "extensions": {
                  "lore": {
                    "character_book": {
                      "name": "深港异闻录",
                      "entries": [
                        {
                          "name": "旧钟楼",
                          "keys": ["钟楼", "旧塔"],
                          "content": "旧钟楼地下埋着一条废弃运货通道。"
                        }
                      ]
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val bundle = adapter.decodeAsBundle(rawJson)
        val assistant = bundle?.assistants?.single()
        val worldBookEntries = bundle?.worldBookEntries.orEmpty()

        requireNotNull(assistant)
        assertEquals(1, worldBookEntries.size)
        assertEquals("旧钟楼", worldBookEntries.single().title)
        assertEquals("深港异闻录", worldBookEntries.single().sourceBookName)
        assertEquals(listOf("钟楼", "旧塔"), worldBookEntries.single().keywords)
        assertEquals(assistant.id, worldBookEntries.single().scopeId)
    }
}
