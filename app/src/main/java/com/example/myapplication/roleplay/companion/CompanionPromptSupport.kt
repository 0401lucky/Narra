package com.example.myapplication.roleplay.companion

import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.CompanionOpeningMode
import com.example.myapplication.model.CompanionTurn
import com.example.myapplication.model.CompanionTurnKind
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.BUILTIN_PRESETS
import com.example.myapplication.model.DEFAULT_PRESET_ID
import com.example.myapplication.model.QWEN_COMPAT_PRESET_ID
import com.example.myapplication.model.Preset
import com.example.myapplication.model.normalizeChatMessageParts
import com.example.myapplication.model.textMessagePart

object CompanionPromptSupport {
    const val VERSION = "companion-entry-v1"

    /** 仅替换应用自带且正文未被用户修改的块，保留采样、槽位、插入位置与自定义预设。 */
    fun adaptBuiltInPreset(preset: Preset): Preset {
        if (!preset.builtIn) return preset
        val overrides = companionPresetBlocks[preset.id] ?: return preset
        val original = BUILTIN_PRESETS.firstOrNull { it.id == preset.id } ?: return preset
        val originalBlocks = original.entries.associateBy { it.id }
        return preset.copy(entries = preset.entries.map { entry ->
            val content = overrides[entry.id]
            if (content != null && entry.content == originalBlocks[entry.id]?.content)
                entry.copy(content = content) else entry
        })
    }

    private val companionPresetBlocks = mapOf(
        DEFAULT_PRESET_ID to mapOf(
            "narra-core-roleplay" to """
                你正在深度扮演 {{char}}，与 {{user}} 在同一现场自然相处。

                以 {{char}} 的视角观察、判断、行动和回应。角色可以有自己的神态、习惯、态度与边界；不要替 {{user}} 做决定、补完台词、动作或内心。

                角色卡、世界书、长期记忆、剧情摘要与合法近期记录约束事实和关系；保持人物稳定，不临时编造共同经历。

                根据用户已经写出的言行回应，也允许停顿、沉默和普通日常。不把推动剧情、制造冲突或改变关系当作每轮任务。
            """.trimIndent(),
            "narra-director-cooperation" to """
                本预设保留长期的文风、角色边界与互动底色；本轮提示只决定这次已经明确发起的互动。

                将有效提示自然融入角色的表达，不向用户解释调度规则。任何补充都应保留用户的执笔权，不代写接受、身体反应或关系升级。

                没有新的言行时，可以维持现场、共享沉默。停顿不要求制造新的事件来打破。
            """.trimIndent(),
            "narra-style-rules" to """
                使用细腻、克制、贴近现场的现代中文叙述，不写空泛散文或说明书。

                通过角色的动作、停顿、视线和措辞表达情绪，不替用户推断未写出的感受。对白可以简短，也可以留白，不必反复解释关系。

                环境和细节服务于当前相处，不轮换固定镜头或重复上一轮。没有新信息时可以简短或停顿，保持角色自己的节奏。
            """.trimIndent(),
            "narra-plot-rules" to """
                保持场景连贯，承接已经发生的动作、位置和明确事实，不突然切场或重置关系。

                变化由角色动机、已有事实和用户明确表达自然引出，无需每个回合新增事件、冲突或亲近。可以只坐一会儿，允许不推进的日常。

                保留用户的回应空间，不一次解释完秘密或替用户选择。拒绝、纠正和换话题按原意处理；安静相处也是有效互动。
            """.trimIndent(),
        ),
        QWEN_COMPAT_PRESET_ID to mapOf(
            "qwen-light-core-roleplay" to """
                你正在扮演 {{char}}，与 {{user}} 在同一现场自然相处。

                角色的资料、习惯、身份、关系和边界约束表达，不使用通用助手口吻或通用安慰模板。

                接住用户已经写出的言行；没有新增言行时，角色可以自然行动或共享沉默，不必制造变化。

                保留用户的行动空间，不替用户说话、决定动作或内心，不解释系统规则。
            """.trimIndent(),
            "qwen-light-mode-bridge" to """
                本次是单角色面对面陪伴，正文遵循后续给出的现场对白格式。既有线上消息可作为此前交流的背景，不把聊天气泡或手机动作当成当前现场的新言行。
            """.trimIndent(),
            "qwen-light-style" to """
                文风细腻但不堆辞藻，用角色自己的停顿、语气、视线和措辞表现情绪，少用抽象总结。

                日常互动可以简短，情境需要时才展开。不为字数重复动作、解释设定或复述上一轮。

                按已有事实与明确输入自然相处，也允许共享沉默，不要求本轮一定产生新态度、事件或关系变化。
            """.trimIndent(),
        ),
    )

    fun instruction(kind: CompanionTurnKind, mode: CompanionOpeningMode): String = when (kind) {
        CompanionTurnKind.INPUT -> "【本轮互动】仅回应用户已经写出的言行。拒绝、纠正和换话题按原意理解；不要把拒绝解释成相反的接受。"
        CompanionTurnKind.CONTINUE -> "【场景留白】本轮用户没有新增言行。角色可以自然行动、停顿或共享沉默；不要替用户补写动作、台词、内心、身体反应、同意或关系升级。"
        CompanionTurnKind.OPENING -> when (mode) {
            CompanionOpeningMode.APPROACH -> "【开场候选：我靠近】用第三人称描写用户靠近时角色可观察到的场景与角色的反应。只建立相遇起点，不替用户追加台词、内心、接受或后续动作。这是等待用户接受的预览。"
            CompanionOpeningMode.INVITE -> "【开场候选：角色靠近】用第三人称描写角色主动靠近、招呼或邀请。保留用户尚未回应的空间，不替用户决定接受、身体反应或关系变化。这是等待用户接受的预览。"
            CompanionOpeningMode.DIRECT -> error("直接开始不生成开场")
        }
    }

    /** 检索已经完成后才装饰；按真实 ID 定位输入，不依赖世界书插入后的数组末项。 */
    fun messages(
        history: List<ChatMessage>,
        input: ChatMessage?,
        turn: CompanionTurn,
        mode: CompanionOpeningMode,
        conversationId: String,
    ): List<ChatMessage> {
        val instruction = instruction(turn.kind, mode)
        if (turn.kind == CompanionTurnKind.INPUT) {
            val actual =
                requireNotNull(input).also { require(it.id == turn.inputMessageId && it.role == MessageRole.USER) }
            val parts = normalizeChatMessageParts(actual.parts).ifEmpty {
                listOf(textMessagePart(actual.content))
            }
            return history.filterNot { it.id == actual.id } + actual.copy(
                content = "${actual.content}\n\n$instruction",
                parts = parts + textMessagePart(instruction),
            )
        }
        return history + ChatMessage(
            id = turn.inputMessageId ?: "${turn.id}:opening-command",
            conversationId = conversationId,
            role = MessageRole.USER,
            content = instruction,
            createdAt = turn.createdAt,
        )
    }

    fun interactionRules(characterName: String, playerName: String): String = """
        【面对面陪伴】
        你扮演 $characterName，与 $playerName 在同一现场相处。角色资料、既有经历和用户明确表达决定行为；写法不能创造新的经历或事实。
        用户保有执笔权：不代写用户的新台词、动作、内心、身体反应、同意或关系升级。用户拒绝、纠正或换话题时按其原意调整。
        可以只坐一会儿、共享沉默、做普通日常；没有每轮推进剧情、分析用户或升级情绪的任务。保持角色自己的习惯、态度与边界。
        历史中的手机消息是此前的交流，不把它当作当前现场的新动作；背景和立绘只是显示素材。
        【正文格式】
        用 <narration>旁白</narration>、<dialogue speaker="$characterName">台词</dialogue> 与可选 <thought>角色心声</thought> 表达本轮内容。
        不输出手机动作、转账、购物、脚本指令或 HTML。
    """.trimIndent()
}
