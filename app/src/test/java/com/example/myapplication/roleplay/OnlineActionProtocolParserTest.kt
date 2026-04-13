package com.example.myapplication.roleplay

import com.example.myapplication.model.ChatActionType
import com.example.myapplication.model.TransferDirection
import com.example.myapplication.model.TransferStatus
import com.example.myapplication.model.isOnlineThoughtPart
import com.example.myapplication.model.onlineThoughtContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineActionProtocolParserTest {
    @Test
    fun parse_mixedJsonArrayBuildsOrderedParts() {
        val result = OnlineActionProtocolParser.parse(
            rawContent = """
                [
                  "嗯，还没睡？",
                  {"type":"reply_to","message_id":"1701","content":"刚才那句我看到了。"},
                  {"type":"emoji","description":"若有所思"},
                  {"type":"voice_message","content":"你先说，我在听。"},
                  {"type":"ai_photo","description":"书桌边的一盏暖灯。"},
                  {"type":"location","locationName":"文学楼","coordinates":"120E,30N"},
                  {"type":"transfer","amount":520,"note":"给你的晚安奖励"},
                  {"type":"poke"},
                  {"type":"video_call","reason":"想看看你现在的样子"}
                ]
            """.trimIndent(),
            characterName = "沈砚清",
        )!!

        assertEquals(9, result.parts.size)
        assertEquals("嗯，还没睡？", result.parts[0].text)
        assertEquals("1701", result.parts[1].replyToMessageId)
        assertEquals(ChatActionType.EMOJI, result.parts[2].actionType)
        assertEquals(ChatActionType.VOICE_MESSAGE, result.parts[3].actionType)
        assertEquals(ChatActionType.AI_PHOTO, result.parts[4].actionType)
        assertEquals(ChatActionType.LOCATION, result.parts[5].actionType)
        assertEquals(TransferDirection.ASSISTANT_TO_USER, result.parts[6].specialDirection)
        assertEquals(ChatActionType.POKE, result.parts[7].actionType)
        assertEquals(ChatActionType.VIDEO_CALL, result.parts[8].actionType)
    }

    @Test
    fun parse_recallPreviousProducesDirective() {
        val result = OnlineActionProtocolParser.parse(
            rawContent = """[{"type":"recall","target":"previous"}]""",
            characterName = "沈砚清",
        )!!

        assertTrue(result.parts.isEmpty())
        assertEquals(listOf(OnlineActionDirective.RecallPreviousAssistant), result.directives)
    }

    @Test
    fun parse_transferActionProducesReceiptDirective() {
        val result = OnlineActionProtocolParser.parse(
            rawContent = """[{"type":"transfer_action","action":"reject"}]""",
            characterName = "沈砚清",
        )!!

        assertTrue(result.parts.isEmpty())
        assertEquals(
            listOf(
                OnlineActionDirective.UpdateTransferStatus(
                    status = TransferStatus.REJECTED,
                    refId = "",
                ),
            ),
            result.directives,
        )
    }

    @Test
    fun parse_invalidJsonReturnsNull() {
        val result = OnlineActionProtocolParser.parse(
            rawContent = """{"type":"emoji"}""",
            characterName = "沈砚清",
        )

        assertEquals(null, result)
    }

    @Test
    fun parse_supportsThoughtObjectAndTrailingCommaRepair() {
        val result = OnlineActionProtocolParser.parse(
            rawContent = """
                ```json
                [
                  {"type":"thought","content":"其实已经盯着这条消息看了很久"},
                  {"type":"reply_to","message_id":1007,"content":"我刚才不是不回你。"},
                ]
                ```
            """.trimIndent(),
            characterName = "沈砚清",
        )!!

        assertEquals(2, result.parts.size)
        assertTrue(result.parts[0].isOnlineThoughtPart())
        assertEquals("其实已经盯着这条消息看了很久", result.parts[0].onlineThoughtContent())
        assertEquals("1007", result.parts[1].replyToMessageId)
    }

    @Test
    fun extractStreamingPreview_readsCompletedPrefixBeforeWholeArrayCloses() {
        val preview = OnlineActionProtocolParser.extractStreamingPreview(
            rawContent = """
                [
                  {"type":"reply_to","message_id":"1007","content":"这句我已经看见了"},
                  {"type":"thought","content":"差一点就想直接拨过去"}
            """.trimIndent(),
        )

        assertTrue(preview.contains("这句我已经看见了"))
        assertTrue(preview.contains("心声：差一点就想直接拨过去"))
    }

    @Test
    fun extractStreamingPreview_includesTransferActionDirectiveText() {
        val preview = OnlineActionProtocolParser.extractStreamingPreview(
            rawContent = """[{"type":"transfer_action","action":"accept"}]""",
        )

        assertEquals("已收款", preview)
    }

    @Test
    fun extractStreamingPreview_returnsEmptyForIncompleteFirstItem() {
        val preview = OnlineActionProtocolParser.extractStreamingPreview(
            rawContent = "[{\"type\":\"reply_to\",\"message_id\":\"1007\",\"content\":\"这句我还没说完\"",
        )

        assertFalse(preview.isNotBlank())
    }
}
