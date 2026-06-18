package com.example.myapplication.viewmodel

import com.example.myapplication.data.repository.ParsedAssistantSpecialOutput
import com.example.myapplication.model.ChatActionType
import com.example.myapplication.model.RoleplayInteractionMode
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.model.TransferDirection
import com.example.myapplication.model.isTransferPart
import com.example.myapplication.model.transferMessagePart
import com.example.myapplication.roleplay.OnlineActionProtocolParser
import org.junit.Assert.assertEquals
import org.junit.Test

class RoleplayCompletedPartsTest {

    /**
     * 线上单聊一轮回复里只有一张 ai_photo 时，
     * onlineProtocolResult 与 parsedOutput 解析的是同一段模型输出，
     * 合并落库时不应把这张照片计成两份。
     */
    @Test
    fun onlinePhoto_isNotDuplicatedWhenMergedWithParsedOutput() {
        val rawContent = """
            [
              {"type":"ai_photo","description":"窗边的自拍"},
              "剩下的一会儿自己解",
              "司机已经到你学校门口了"
            ]
        """.trimIndent()
        val onlineResult = OnlineActionProtocolParser.parseWithFallback(
            rawContent = rawContent,
            characterName = "角色",
        )
        // runner 用同一段输出再解析一次：parseAssistantSpecialOutput 现在也会产出 ai_photo
        val parsedOutput = ParsedAssistantSpecialOutput(
            content = "",
            parts = OnlineActionProtocolParser.parse(rawContent, "角色")!!.parts,
        )
        val scenario = RoleplayScenario(
            id = "scene-1",
            title = "线上模式",
            interactionMode = RoleplayInteractionMode.ONLINE_PHONE,
        )

        val parts = resolveRoleplayCompletedParts(
            onlineProtocolResult = onlineResult,
            parsedOutput = parsedOutput,
            scenario = scenario,
            referenceCandidates = emptyList(),
        )

        val photoCount = parts.count { it.actionType == ChatActionType.AI_PHOTO }
        assertEquals(1, photoCount)
    }

    /**
     * 方案 B 排除的是 ai_photo，转账等 XML 特殊玩法卡仍需从 parsedOutput 合并进来，不能误删。
     */
    @Test
    fun onlineSpecialPlayCard_isPreservedFromParsedOutput() {
        val onlineResult = OnlineActionProtocolParser.parseWithFallback(
            rawContent = """["在的，怎么了？"]""",
            characterName = "角色",
        )
        val parsedOutput = ParsedAssistantSpecialOutput(
            content = "",
            parts = listOf(
                transferMessagePart(
                    direction = TransferDirection.ASSISTANT_TO_USER,
                    counterparty = "用户",
                    amount = "88.00",
                ),
            ),
        )
        val scenario = RoleplayScenario(
            id = "scene-1",
            title = "线上模式",
            interactionMode = RoleplayInteractionMode.ONLINE_PHONE,
        )

        val parts = resolveRoleplayCompletedParts(
            onlineProtocolResult = onlineResult,
            parsedOutput = parsedOutput,
            scenario = scenario,
            referenceCandidates = emptyList(),
        )

        assertEquals(1, parts.count { it.isTransferPart() })
    }
}
