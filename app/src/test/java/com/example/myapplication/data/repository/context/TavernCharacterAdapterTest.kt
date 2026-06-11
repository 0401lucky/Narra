package com.example.myapplication.data.repository.context

import com.example.myapplication.model.CONTEXT_IMPORT_MAX_ASSISTANT_EXAMPLE_DIALOGUES
import com.example.myapplication.model.CONTEXT_IMPORT_MAX_ASSISTANT_EXAMPLE_DIALOGUE_CHARS
import com.example.myapplication.model.CONTEXT_IMPORT_MAX_ASSISTANT_NAME_CHARS
import com.example.myapplication.model.CONTEXT_IMPORT_MAX_ASSISTANT_TAGS
import com.example.myapplication.model.CONTEXT_IMPORT_MAX_ASSISTANT_TAG_CHARS
import com.example.myapplication.model.CONTEXT_IMPORT_MAX_ASSISTANT_TEXT_CHARS
import com.example.myapplication.model.CONTEXT_IMPORT_MAX_ASSISTANT_WORLD_BOOK_MAX_ENTRIES
import com.example.myapplication.model.CONTEXT_IMPORT_MAX_WORLD_BOOK_CONTENT_CHARS
import com.example.myapplication.model.CONTEXT_IMPORT_MAX_WORLD_BOOK_ENTRIES
import com.example.myapplication.model.CONTEXT_IMPORT_MAX_WORLD_BOOK_TITLE_CHARS
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

    @Test
    fun decodeAsBundle_limitsCharacterFieldsListsAndEmbeddedWorldBook() {
        val longName = "名".repeat(CONTEXT_IMPORT_MAX_ASSISTANT_NAME_CHARS + 20)
        val longText = "文".repeat(CONTEXT_IMPORT_MAX_ASSISTANT_TEXT_CHARS + 20)
        val longDialogue = "对话".repeat(CONTEXT_IMPORT_MAX_ASSISTANT_EXAMPLE_DIALOGUE_CHARS)
        val examples = (0 until CONTEXT_IMPORT_MAX_ASSISTANT_EXAMPLE_DIALOGUES + 2)
            .joinToString("\n\n") { index -> "用户：$index\n助手：$longDialogue" }
        val escapedExamples = examples
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
        val tags = (0 until CONTEXT_IMPORT_MAX_ASSISTANT_TAGS + 2)
            .joinToString(",") { index -> """"标签$index${"长".repeat(CONTEXT_IMPORT_MAX_ASSISTANT_TAG_CHARS)}"""" }
        val longTitle = "题".repeat(CONTEXT_IMPORT_MAX_WORLD_BOOK_TITLE_CHARS + 20)
        val longContent = "设".repeat(CONTEXT_IMPORT_MAX_WORLD_BOOK_CONTENT_CHARS + 20)
        val entries = (0 until CONTEXT_IMPORT_MAX_WORLD_BOOK_ENTRIES + 2)
            .joinToString(",") { index ->
                if (index == 0) {
                    """{"id":$index,"name":"$longTitle","keys":["关键词"],"content":"$longContent"}"""
                } else {
                    """{"id":$index,"name":"条目$index","keys":["k$index"],"content":"正文$index"}"""
                }
            }
        val rawJson = """
            {
              "spec": "chara_card_v2",
              "data": {
                "name": "$longName",
                "description": "$longText",
                "personality": "$longText",
                "scenario": "$longText",
                "first_mes": "$longText",
                "mes_example": "$escapedExamples",
                "creator_notes": "$longText",
                "tags": [$tags],
                "character_book": {
                  "name": "超长角色世界书",
                  "entries": [$entries]
                }
              }
            }
        """.trimIndent()

        val bundle = adapter.decodeAsBundle(rawJson)!!
        val assistant = bundle.assistants.single()
        val firstEntry = bundle.worldBookEntries.first()

        assertEquals(CONTEXT_IMPORT_MAX_ASSISTANT_NAME_CHARS, assistant.name.length)
        assertEquals(CONTEXT_IMPORT_MAX_ASSISTANT_TEXT_CHARS, assistant.description.length)
        assertEquals(CONTEXT_IMPORT_MAX_ASSISTANT_TEXT_CHARS, assistant.systemPrompt.length)
        assertEquals(CONTEXT_IMPORT_MAX_ASSISTANT_TEXT_CHARS, assistant.scenario.length)
        assertEquals(CONTEXT_IMPORT_MAX_ASSISTANT_TEXT_CHARS, assistant.greeting.length)
        assertEquals(CONTEXT_IMPORT_MAX_ASSISTANT_TEXT_CHARS, assistant.creatorNotes.length)
        assertEquals(CONTEXT_IMPORT_MAX_ASSISTANT_EXAMPLE_DIALOGUES, assistant.exampleDialogues.size)
        assertTrue(assistant.exampleDialogues.all { it.length <= CONTEXT_IMPORT_MAX_ASSISTANT_EXAMPLE_DIALOGUE_CHARS })
        assertEquals(CONTEXT_IMPORT_MAX_ASSISTANT_TAGS, assistant.tags.size)
        assertTrue(assistant.tags.all { it.length <= CONTEXT_IMPORT_MAX_ASSISTANT_TAG_CHARS })
        assertTrue(assistant.worldBookMaxEntries <= CONTEXT_IMPORT_MAX_ASSISTANT_WORLD_BOOK_MAX_ENTRIES)
        assertEquals(CONTEXT_IMPORT_MAX_WORLD_BOOK_ENTRIES, bundle.worldBookEntries.size)
        assertEquals(CONTEXT_IMPORT_MAX_WORLD_BOOK_TITLE_CHARS, firstEntry.title.length)
        assertEquals(CONTEXT_IMPORT_MAX_WORLD_BOOK_CONTENT_CHARS, firstEntry.content.length)
    }
}
