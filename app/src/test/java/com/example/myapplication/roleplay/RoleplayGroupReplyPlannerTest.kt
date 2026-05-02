package com.example.myapplication.roleplay

import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ChatActionType
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.ChatMessagePartType
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.RoleplayGroupParticipant
import com.example.myapplication.model.RoleplayGroupReplyMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoleplayGroupReplyPlannerTest {
    @Test
    fun natural_prefersMentionedMembersAndAllowsMultipleReplies() {
        val plan = RoleplayGroupReplyPlanner.plan(
            mode = RoleplayGroupReplyMode.NATURAL,
            members = listOf(
                member("a", "陆宴清"),
                member("b", "萧悍"),
                member("c", "谢海"),
            ),
            latestUserInput = "@陆宴清 @谢海 你们两个怎么看？",
            maxTurns = 3,
        )

        assertEquals(listOf("a", "c"), plan.turns.map { it.assistantId })
    }

    @Test
    fun natural_excludesMutedMembers() {
        val plan = RoleplayGroupReplyPlanner.plan(
            mode = RoleplayGroupReplyMode.NATURAL,
            members = listOf(
                member("a", "陆宴清", muted = true),
                member("b", "萧悍"),
            ),
            latestUserInput = "@陆宴清 你说句话",
            maxTurns = 3,
        )

        assertEquals(listOf("b"), plan.turns.map { it.assistantId })
    }

    @Test
    fun allMembers_usesOrderAndExcludesMutedMembers() {
        val plan = RoleplayGroupReplyPlanner.plan(
            mode = RoleplayGroupReplyMode.ALL_MEMBERS,
            members = listOf(
                member("c", "谢海", sortOrder = 2),
                member("a", "陆宴清", sortOrder = 0),
                member("b", "萧悍", sortOrder = 1, muted = true),
            ),
            latestUserInput = "都说说看",
            maxTurns = 6,
        )

        assertEquals(listOf("a", "c"), plan.turns.map { it.assistantId })
    }

    @Test
    fun manualOnly_requiresMention() {
        val plan = RoleplayGroupReplyPlanner.plan(
            mode = RoleplayGroupReplyMode.MANUAL_ONLY,
            members = listOf(member("a", "陆宴清")),
            latestUserInput = "有人在吗",
            maxTurns = 3,
        )

        assertTrue(plan.turns.isEmpty())
        assertEquals("指定发言模式下请先 @角色", plan.notice)
    }

    @Test
    fun speakerDirectorNote_includesMemberProfilesAndGroupMode() {
        val note = buildRoleplayGroupSpeakerDirectorNote(
            turn = RoleplayGroupReplyTurn(
                participantId = "participant-a",
                assistantId = "a",
                displayName = "陆宴清",
                intent = "接住用户的问题，也可以回应谢海刚才的质疑",
                reason = "用户明确 @ 了陆宴清",
            ),
            members = listOf(
                member(
                    assistantId = "a",
                    name = "陆宴清",
                    description = "克制、照顾型，说话不急。",
                ),
                member(
                    assistantId = "b",
                    name = "谢海",
                    description = "锋利、毒舌，喜欢旁观后插刀。",
                    systemPrompt = "谢海表面冷淡，实际很会观察人。",
                ),
            ),
            recentMessages = listOf(
                ChatMessage(id = "m1", role = MessageRole.USER, content = "@陆宴清 你怎么看？"),
                ChatMessage(id = "m2", role = MessageRole.ASSISTANT, content = "别急着下结论", speakerName = "谢海"),
            ),
        )

        assertTrue(note.contains("当前模式：ONLINE_GROUP_CHAT"))
        assertTrue(note.contains("陆宴清（你，本轮发言者）"))
        assertTrue(note.contains("谢海"))
        assertTrue(note.contains("毒舌"))
        assertTrue(note.contains("实际很会观察人"))
        assertTrue(note.contains("[谢海] 别急着下结论"))
        assertTrue(note.contains("你可以回应用户，也可以回应其他角色刚发的消息"))
        assertTrue(note.contains("禁止原样或近义重复"))
        assertTrue(note.contains("多人排队复读“晚上好”"))
        assertTrue(note.contains("群聊输出协议硬规则"))
        assertTrue(note.contains("数组元素只能是字符串、voice_message 对象或 ai_photo 对象"))
        assertTrue(note.contains("允许语音"))
        assertTrue(note.contains("允许图片"))
    }

    @Test
    fun groupParser_keepsTextVoiceAndPhotoBubbles() {
        val result = OnlineActionProtocolParser.parseGroupTextOnlyWithFallback(
            rawContent = """[{"type":"ai_photo","description":"台灯暖光笼罩书桌"},{"type":"voice_message","content":"快十二点了才上来"},"晚上好"]""",
            characterName = "沈宴清",
        )!!

        assertEquals(ChatActionType.AI_PHOTO, result.parts[0].actionType)
        assertEquals(ChatActionType.VOICE_MESSAGE, result.parts[1].actionType)
        assertEquals(ChatMessagePartType.TEXT, result.parts[2].type)
        assertEquals("晚上好", result.parts[2].text)
    }

    @Test
    fun groupParser_acceptsOnlyPhotoOutput() {
        val result = OnlineActionProtocolParser.parseGroupTextOnlyWithFallback(
            rawContent = """[{"type":"ai_photo","description":"台灯暖光笼罩书桌"}]""",
            characterName = "沈宴清",
        )!!

        assertEquals(ChatActionType.AI_PHOTO, result.parts.single().actionType)
    }

    @Test
    fun groupParser_rejectsOnlyForbiddenActionOutput() {
        val result = OnlineActionProtocolParser.parseGroupTextOnlyWithFallback(
            rawContent = """[{"type":"transfer","amount":66,"note":"打车"}]""",
            characterName = "沈宴清",
        )

        assertNull(result)
    }

    @Test
    fun groupStreamingPreview_includesVoiceAndText() {
        val preview = OnlineActionProtocolParser.extractGroupTextStreamingPreview(
            """[{"type":"voice_message","content":"快十二点了才上来"},"刚看到"]""",
        )

        assertEquals("语音消息：快十二点了才上来\n刚看到", preview)
    }

    private fun member(
        assistantId: String,
        name: String,
        sortOrder: Int = 0,
        muted: Boolean = false,
        description: String = "",
        systemPrompt: String = "",
    ): RoleplayGroupMemberContext {
        return RoleplayGroupMemberContext(
            participant = RoleplayGroupParticipant(
                id = "participant-$assistantId",
                scenarioId = "scenario",
                assistantId = assistantId,
                sortOrder = sortOrder,
                isMuted = muted,
            ),
            assistant = Assistant(
                id = assistantId,
                name = name,
                description = description,
                systemPrompt = systemPrompt,
            ),
        )
    }
}
