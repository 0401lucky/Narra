package com.example.myapplication.roleplay

import com.example.myapplication.model.ChatActionType
import com.example.myapplication.model.ChatMessagePartType
import com.example.myapplication.model.ChatSpecialType
import com.example.myapplication.model.TransferDirection
import com.example.myapplication.model.TransferStatus
import com.example.myapplication.model.isOnlineThoughtPart
import com.example.myapplication.model.onlineThoughtContent
import com.example.myapplication.model.specialMetadataValue
import com.example.myapplication.model.voiceMessageDurationSeconds
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
    fun parse_acceptsHyphenatedPhotoAndSpecialPlayObjects() {
        val result = OnlineActionProtocolParser.parse(
            rawContent = """
                [
                  {"type":"ai-photo","description":"厨房台面上的三菜一汤。"},
                  {"type":"invite","target":"用户","place":"家里","time":"现在","note":"菜要凉了"},
                  {"type":"gift","target":"用户","item":"围巾","note":"外面冷"},
                  {"type":"task","title":"带伞","objective":"出门前把伞拿上","reward":"少淋雨","deadline":"出门前"},
                  {"type":"punish","method":"今晚早点睡","count":"1","intensity":"low","reason":"昨天熬夜了"}
                ]
            """.trimIndent(),
            characterName = "沈砚清",
        )!!

        assertEquals(ChatActionType.AI_PHOTO, result.parts[0].actionType)
        assertEquals(ChatSpecialType.INVITE, result.parts[1].specialType)
        assertEquals("家里", result.parts[1].specialMetadataValue("place"))
        assertEquals(ChatSpecialType.GIFT, result.parts[2].specialType)
        assertEquals(ChatSpecialType.TASK, result.parts[3].specialType)
        assertEquals(ChatSpecialType.PUNISH, result.parts[4].specialType)
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
    fun parse_voiceMessageAcceptsOptionalDurationKeys() {
        val snakeCaseResult = OnlineActionProtocolParser.parse(
            rawContent = """[{"type":"voice_message","content":"晚点再说","duration_seconds":9}]""",
            characterName = "沈砚清",
        )!!
        val camelCaseResult = OnlineActionProtocolParser.parse(
            rawContent = """[{"type":"voice_message","content":"我在听","durationSeconds":"7"}]""",
            characterName = "沈砚清",
        )!!

        assertEquals(9, snakeCaseResult.parts.single().voiceMessageDurationSeconds())
        assertEquals(7, camelCaseResult.parts.single().voiceMessageDurationSeconds())
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

    @Test
    fun parse_stringElementWithBracketThoughtPrefix_createsThoughtPart() {
        val result = OnlineActionProtocolParser.parse(
            rawContent = """["【心声】谁也没你重要","你在干嘛"]""",
            characterName = "角色",
        )!!

        assertEquals(2, result.parts.size)
        assertTrue(result.parts[0].isOnlineThoughtPart())
        assertEquals("谁也没你重要", result.parts[0].onlineThoughtContent())
        assertEquals("你在干嘛", result.parts[1].text)
    }

    @Test
    fun parse_stringElementWithColonThoughtPrefix_createsThoughtPart() {
        val result = OnlineActionProtocolParser.parse(
            rawContent = """["心声：其实很想见你","晚安"]""",
            characterName = "角色",
        )!!

        assertEquals(2, result.parts.size)
        assertTrue(result.parts[0].isOnlineThoughtPart())
        assertEquals("其实很想见你", result.parts[0].onlineThoughtContent())
        assertEquals("晚安", result.parts[1].text)
    }

    @Test
    fun parse_stripsReferenceIdPrefixFromDialogueText() {
        val result = OnlineActionProtocolParser.parse(
            rawContent = """["ID:1010 刚才班长看你的眼神","你要是真想玩真心话","ID:1004 我问你答，或者你问我答"]""",
            characterName = "角色",
        )!!

        assertEquals(3, result.parts.size)
        assertEquals("刚才班长看你的眼神", result.parts[0].text)
        assertEquals("你要是真想玩真心话", result.parts[1].text)
        assertEquals("我问你答，或者你问我答", result.parts[2].text)
    }

    @Test
    fun parse_stripsReferenceIdPrefixFromReplyToContent() {
        val result = OnlineActionProtocolParser.parse(
            rawContent = """[{"type":"reply_to","message_id":"1007","content":"ID:1007 这句话我看见了"}]""",
            characterName = "角色",
        )!!

        assertEquals(1, result.parts.size)
        assertEquals("这句话我看见了", result.parts[0].text)
    }

    @Test
    fun parse_narrationBracketTextPreservedAsIs() {
        val result = OnlineActionProtocolParser.parse(
            rawContent = """["【她低下头，视线落在他手上。】","你怎么了"]""",
            characterName = "角色",
        )!!

        assertEquals(2, result.parts.size)
        // 旁白检测由 RoleplayMessageUiMapper 负责，解析器只保留原文
        assertEquals("【她低下头，视线落在他手上。】", result.parts[0].text)
        assertEquals("你怎么了", result.parts[1].text)
    }
    @Test
    fun parse_pokeWithTargetAndSuffixExtractsMetadata() {
        val result = OnlineActionProtocolParser.parse(
            rawContent = """[{"type":"poke","target":"用户","suffix":"的脑袋"}]""",
            characterName = "望汐",
        )!!

        assertEquals(1, result.parts.size)
        assertEquals(ChatActionType.POKE, result.parts[0].actionType)
        assertEquals("用户", result.parts[0].actionMetadata["poke_target"])
        assertEquals("的脑袋", result.parts[0].actionMetadata["poke_suffix"])
    }

    @Test
    fun parse_pokeWithSelfTargetExtractsMetadata() {
        val result = OnlineActionProtocolParser.parse(
            rawContent = """[{"type":"poke","target":"自己","suffix":"说你别生气了"}]""",
            characterName = "望汐",
        )!!

        assertEquals(1, result.parts.size)
        assertEquals(ChatActionType.POKE, result.parts[0].actionType)
        assertEquals("自己", result.parts[0].actionMetadata["poke_target"])
        assertEquals("说你别生气了", result.parts[0].actionMetadata["poke_suffix"])
    }

    @Test
    fun parse_pokeLegacyNoFieldsStillValid() {
        val result = OnlineActionProtocolParser.parse(
            rawContent = """[{"type":"poke"}]""",
            characterName = "角色",
        )!!

        assertEquals(1, result.parts.size)
        assertEquals(ChatActionType.POKE, result.parts[0].actionType)
        // 无 target/suffix 时 metadata 中对应值为空
        assertEquals("", result.parts[0].actionMetadata["poke_target"].orEmpty())
        assertEquals("", result.parts[0].actionMetadata["poke_suffix"].orEmpty())
    }

    @Test
    fun parseWithFallback_stripsSpecialPlayTagsFromPlainText() {
        val result = OnlineActionProtocolParser.parseWithFallback(
            rawContent = "去吧。<play id='task_lunch_99' type='task' title='牛骨汤令' objective='帮园汇报午餐内容，不许胡嗦。' reward='晚上的惩罚豁免令' deadline='13:00' />记得准时。",
            characterName = "角色",
        )!!

        assertEquals(2, result.parts.size)
        assertEquals("去吧。", result.parts[0].text)
        assertEquals("记得准时。", result.parts[1].text)
        assertTrue(result.parts.none { it.text.contains("<play") || it.text.contains("task_lunch_99") })
    }

    @Test
    fun parseWithFallback_stripsMalformedProtocolFragmentsFromPlainText() {
        val result = OnlineActionProtocolParser.parseWithFallback(
            rawContent = "thought\",\"快点回好吧\",\"ai-photo\",\"厨房台面上摆着三菜一汤\",\"play id='meal_invitation_001' type='invite' target='用户' place='家里' time='现在' note='菜要凉了'/>",
            characterName = "角色",
        )!!

        val text = result.parts.joinToString("\n") { it.text }
        assertTrue(text.contains("快点回好吧"))
        assertTrue(text.contains("厨房台面上摆着三菜一汤"))
        assertFalse(text.contains("thought", ignoreCase = true))
        assertFalse(text.contains("ai-photo", ignoreCase = true))
        assertFalse(text.contains("play id", ignoreCase = true))
        assertFalse(text.contains("meal_invitation_001", ignoreCase = true))
    }

    @Test
    fun parseWithFallback_dropsVariablePatchArtifactOnly() {
        val result = OnlineActionProtocolParser.parseWithFallback(
            rawContent = """
                - Time passed: negligible.
                - Dramatic updates: no.
                - Variables update: NTK intensity maintained.
                [
                  {"op":"replace","path":"/世界环境/当前日期时间","value":"2026-05-04 14:40"},
                  {"op":"delta","path":"/User/NTK累计强度","value":3}
                ]
            """.trimIndent(),
            characterName = "角色",
        )

        assertEquals(null, result)
    }

    @Test
    fun parseWithFallback_keepsVisibleTextBeforeVariablePatchArtifact() {
        val result = OnlineActionProtocolParser.parseWithFallback(
            rawContent = """
                你到底在等什么？是觉得我迟早会露出底牌吗？

                <UpdateVariable>
                <Analysis>- Time passed: negligible.</Analysis>
                <JSONPatch>[{"op":"delta","path":"/User/NTK累计强度","value":3}]</JSONPatch>
                </UpdateVariable>
            """.trimIndent(),
            characterName = "沈鸦",
        )!!

        assertEquals(1, result.parts.size)
        assertEquals("你到底在等什么？是觉得我迟早会露出底牌吗？", result.parts.single().text)
    }

    @Test
    fun extractFallbackStreamingPreview_returnsEmptyForJsonPatchPrefix() {
        val preview = OnlineActionProtocolParser.extractFallbackStreamingPreview(
            rawContent = """[{"op":"delta","path":"/User/NTK累计强度","value":3}]""",
        )

        assertEquals("", preview)
    }

    @Test
    fun parseWithFallback_dropsBareNtkAnalysisTail() {
        val result = OnlineActionProtocolParser.parseWithFallback(
            rawContent = """
                视线扫过屏幕上的 IDE 界面。
                NTK detected (unprovoked friendliness) but too early to quantify.
                [{"op":"replace","path":"/世界环境/当前状态","value":"CONFUSION"}]
            """.trimIndent(),
            characterName = "沈鸦",
        )!!

        assertEquals(1, result.parts.size)
        assertEquals("视线扫过屏幕上的 IDE 界面。", result.parts.single().text)
    }

    @Test
    fun parseGroupTextOnlyWithFallback_keepsVoiceAndPhotoButDropsThoughts() {
        val result = OnlineActionProtocolParser.parseGroupTextOnlyWithFallback(
            rawContent = """
                [
                  {"type":"thought","content":"不想让群里看出来"},
                  "群里说一句就行。",
                  {"type":"ai_photo","description":"窗外夜色"},
                  {"type":"voice_message","content":"别闹"}
                ]
            """.trimIndent(),
            characterName = "角色",
        )!!

        assertEquals(3, result.parts.size)
        assertEquals(ChatMessagePartType.TEXT, result.parts[0].type)
        assertEquals("群里说一句就行。", result.parts[0].text)
        assertEquals(ChatActionType.AI_PHOTO, result.parts[1].actionType)
        assertEquals(ChatActionType.VOICE_MESSAGE, result.parts[2].actionType)
    }
}
