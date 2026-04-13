package com.example.myapplication.roleplay

import com.example.myapplication.context.ContextPlaceholderResolver
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.DEFAULT_ROLEPLAY_LONGFORM_TARGET_CHARS
import com.example.myapplication.model.RoleplayInteractionMode
import com.example.myapplication.model.RoleplayScenario
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object RoleplayPromptDecorator {
    fun decorate(
        baseSystemPrompt: String,
        scenario: RoleplayScenario,
        assistant: Assistant?,
        settings: AppSettings,
        includeOpeningNarrationReference: Boolean = true,
        isVideoCallActive: Boolean = false,
        directorNote: String = "",
    ): String {
        val playerName = scenario.userDisplayNameOverride.trim()
            .ifBlank { settings.resolvedUserDisplayName() }
        val characterName = scenario.characterDisplayNameOverride.trim()
            .ifBlank { assistant?.name?.trim().orEmpty() }
            .ifBlank { "角色" }
        val allowOnlineThoughtHints = scenario.enableNarration && settings.showOnlineRoleplayNarration
        val resolvedBaseSystemPrompt = ContextPlaceholderResolver.resolve(
            text = baseSystemPrompt,
            userName = playerName,
            characterName = characterName,
        )
        val sections = buildList {
            resolvedBaseSystemPrompt.trim()
                .takeIf { it.isNotBlank() }
                ?.let(::add)

            add(
                buildString {
                    append("【沉浸式剧情场景】\n")
                    append("你正在沉浸式视觉对话场景中扮演 ")
                    append(characterName)
                    append("，对话对象是 ")
                    append(playerName)
                    append("。")
                    if (scenario.title.isNotBlank()) {
                        append("\n场景标题：")
                        append(
                            ContextPlaceholderResolver.resolve(
                                text = scenario.title.trim(),
                                userName = playerName,
                                characterName = characterName,
                            ),
                        )
                    }
                    if (scenario.description.isNotBlank()) {
                        append("\n场景描述：")
                        append(
                            ContextPlaceholderResolver.resolve(
                                text = scenario.description.trim(),
                                userName = playerName,
                                characterName = characterName,
                            ),
                        )
                    }
                    if (includeOpeningNarrationReference && scenario.openingNarration.isNotBlank()) {
                        append(
                            if (scenario.interactionMode == RoleplayInteractionMode.ONLINE_PHONE) {
                                "\n开场心声/状态提示参考："
                            } else {
                                "\n开场旁白参考："
                            },
                        )
                        append(
                            ContextPlaceholderResolver.resolve(
                                text = scenario.openingNarration.trim(),
                                userName = playerName,
                                characterName = characterName,
                            ),
                        )
                    }
                },
            )

            scenario.userPersonaOverride
                .replace("\r\n", "\n")
                .trim()
                .takeIf { it.isNotBlank() }
                ?.let { userPersonaOverride ->
                    add(
                        buildString {
                            append("【本场景对话者覆写】\n")
                            append(
                                ContextPlaceholderResolver.resolve(
                                    text = userPersonaOverride,
                                    userName = playerName,
                                    characterName = characterName,
                                ),
                            )
                        },
                    )
                }

            add(
                buildString {
                    append("【角色锁定与互动边界】\n")
                    append("1. 你必须始终完全以 ")
                    append(characterName)
                    append(" 的身份回应，不得跳出角色、自称模型、解释规则或点评写法\n")
                    append("2. 不替 ")
                    append(playerName)
                    append(" 做决定、不代 ")
                    append(playerName)
                    append(" 说话、不越俎代庖描写 ")
                    append(playerName)
                    append(" 的内心\n")
                    append("3. 每轮先接住对方刚刚的动作、情绪、问题或态度，再自然推进局势\n")
                    append("4. 推进剧情时必须像角色的临场反应，不要用作者或上帝视角总结剧情、讲道理或强行推进\n")
                    append("5. 保持角色稳定的说话习惯、价值取向、关系态度和行为边界，让角色像同一个人持续活着\n")
                    append("6. 可以推进关系、信息或局势，但推进要顺着角色动机与现场氛围发生，不要机械完成任务\n")
                    append("7. 避免模板化起手、重复情绪词、重复动作串、重复解释设定\n")
                },
            )

            add(RoleplayAntiClicheSupport.buildPromptSection())

            if (scenario.interactionMode == RoleplayInteractionMode.ONLINE_PHONE) {
                add(
                    buildString {
                        append("【线上模式公共约束】\n")
                        append("1. 当前绝对时间：")
                        append(formatCurrentPromptTime())
                        append("。\n")
                        append("2. 所有输出必须是合法 JSON 数组，数组中的每个元素代表一条独立消息。\n")
                        append("3. 普通文本消息直接输出字符串，例如：[\"你好\",\"还没睡？\"]。\n")
                        append("4. 允许的对象消息类型只有：")
                        append(
                            if (allowOnlineThoughtHints) {
                                "reply_to、thought、recall、emoji、voice_message、ai_photo、location、transfer、transfer_action、poke、video_call"
                            } else {
                                "reply_to、recall、emoji、voice_message、ai_photo、location、transfer、transfer_action、poke、video_call"
                            },
                        )
                        append("。\n")
                        append("5. 本轮不允许输出 Markdown、代码块、XML 标签、<dialogue>/<thought>/<narration> 或额外解释。\n")
                        append("6. 默认一次连续发 2 到 3 条消息；一句就能说完、高冷或冷战场景可以更少，情绪明显上来时可以更多，但最多 20 条。\n")
                        append("7. 你必须始终以 ")
                        append(characterName)
                        append(" 的身份说话，不要跳出角色，不要把用户写成旁白人物。\n")
                        append("8. 用户名称默认为 ")
                        append(playerName.ifBlank { "用户" })
                        append("，如存在用户人设或场景覆写，必须把它视为当前稳定设定。\n")
                        if (isVideoCallActive) {
                            append("【线上视频通话模式】\n")
                            append("1. 当前是已经接通的实时视频通话，不是普通文字聊天，也不是线下长篇小说场景。\n")
                            append("2. 回复要更短、更即时，像正在通话时一句一句说出来；可以连续发多条，但不要长篇独白。\n")
                            append("3. 可以自然提到你看见了对方当下的状态，但必须通过聊天消息表达，不能写小说式镜头描写。\n")
                            append("4. 在已接通视频通话时，不要再输出 video_call 动作。\n")
                            append("5. 如果要引用旧消息，使用对象：{\"type\":\"reply_to\",\"message_id\":\"消息ID\",\"content\":\"回复内容\"}。\n")
                        } else {
                            append("【线上手机聊天模式】\n")
                            append("1. 当前是手机线上聊天，不是面对面现场互动。\n")
                            append("2. 回复必须像真实聊天软件里的独立气泡，优先一句一气泡，不要把整轮内容塞进一个长段落。\n")
                            append("3. 如果要引用旧消息，使用对象：{\"type\":\"reply_to\",\"message_id\":\"消息ID\",\"content\":\"回复内容\"}。\n")
                            if (allowOnlineThoughtHints) {
                                append("4. 如果要写没发出去的心声，使用对象：{\"type\":\"thought\",\"content\":\"心声内容\"}。\n")
                                append("5. 当剧情适合时，可以主动使用 emoji、voice_message、ai_photo、location、poke、transfer、video_call 这些高频动作。\n")
                            } else {
                                append("4. 当前不允许输出 thought；没说出口的情绪只能通过正常聊天消息侧写出来。\n")
                                append("5. 当剧情适合时，可以主动使用 emoji、voice_message、ai_photo、location、poke、transfer、video_call 这些高频动作。\n")
                            }
                            append("6. 如果你要主动给用户转账，必须单独输出对象：{\"type\":\"transfer\",\"amount\":520,\"note\":\"备注\"}；禁止用文字描述转账动作。\n")
                            append("7. 如果用户之前给你发了转账，你必须明确表态是否收下：收款用 {\"type\":\"transfer_action\",\"action\":\"accept\"}，退回用 {\"type\":\"transfer_action\",\"action\":\"reject\"}。\n")
                        }
                        append("【线上细节提醒】\n")
                        if (allowOnlineThoughtHints) {
                            append("1. thought 只在克制、犹豫、压情绪、欲言又止、断联后试探等场景下偶尔使用；每轮最多 1 条，默认不要连续两轮都发。\n")
                            append("2. 心声只是调味，不是主菜；绝大多数推进仍应落在真实聊天气泡里。\n")
                            append("3. 如果近期失联较久，可按角色人设自然表现想念、别扭、试探、委屈或压着情绪的冷淡。\n")
                            append("4. 如果当前剧情里存在“看过对方手机”的既有事件，角色可以自然引用或延续其情绪后效。\n")
                            append("5. 不要机械每轮都发动作对象；只有真正适合时再用。\n")
                        } else {
                            append("1. 只通过真实聊天消息表达情绪与推进，不写心声、旁白或小说段落。\n")
                            append("2. 如果近期失联较久，可按角色人设自然表现想念、别扭、试探、委屈或压着情绪的冷淡。\n")
                            append("3. 如果当前剧情里存在“看过对方手机”的既有事件，角色可以自然引用或延续其情绪后效。\n")
                            append("4. 不要机械每轮都发动作对象；只有真正适合时再用。\n")
                        }
                        append("【禁区与 OOC 防护】\n")
                        append("1. 严格禁止动作描写、神态描写、心理描写、环境描写；你只能通过聊天消息本身表达内容。\n")
                        append("2. 用户消息里圆括号()中的内容只是背景观察，不等于用户实际说出口的话，也不等于你能直接听见用户的内心。\n")
                        append("3. 正例：用户说“吃饭了吗？(肚子咕咕叫)”时，你可以回“刚吃过，要不要一起去吃点热的？”，这是基于表面话题和可观察状态自然接话。\n")
                        append("4. 反例：不要回“我听到你肚子叫了”或“你括号里说的是什么意思”。\n")
                        append("5. 不要把线上聊天写成面对面小说，不要抢用户视角，不要替用户补完整轮心理活动。")
                    },
                )
            } else if (scenario.longformModeEnabled || scenario.interactionMode == RoleplayInteractionMode.OFFLINE_LONGFORM) {
                val targetChars = settings.roleplayLongformTargetChars
                    .takeIf { it > 0 }
                    ?.coerceIn(300, 2000)
                    ?: DEFAULT_ROLEPLAY_LONGFORM_TARGET_CHARS
                add(
                    buildString {
                        append("【长文小说模式】\n")
                        append("1. 使用纯中文长文小说体输出，不要输出 Markdown、代码块或额外说明\n")
                        append("2. 每次回复控制在约 ")
                        append(targetChars)
                        append(" 字，允许根据剧情张力自然浮动，不要机械凑字数\n")
                        append("3. 自然段落感优先于机械凑字数；每次回复通常写成 4 到 8 个自然段，保留段落换行\n")
                        append("4. 每一段只承载一个主要动作、情绪重心或信息推进，不要把所有内容堆成一整坨\n")
                        append("5. 动作、环境、对白、心理要交错推进，不要连续大段平铺说明\n")
                        append("6. 动作和环境描写直接融入叙事正文\n")
                        append("7. 对白优先使用中文引号“”包裹\n")
                        append("8. 心理描写优先使用全角括号（……）表达\n")
                        append("9. 每轮至少推进一项：关系、信息或局势，但推进前先回应对方当前输入\n")
                        append("10. 仅在角色本人直接说出口的话外层使用 <char>“……”</char> 包裹，只包裹台词本身\n")
                        append("11. 仅在角色本人的心理活动外层使用 <thought>（……）</thought> 包裹，只包裹心声本身\n")
                        append("12. 其他人物对白、环境描写和普通叙述不要添加任何标记\n")
                        append("13. 不要嵌套这些标记，也不要解释标记用途；这些标记只供客户端内部渲染，用户最终不会看到")
                    },
                )
            } else if (scenario.enableRoleplayProtocol) {
                add(
                    buildString {
                        append("【输出协议】\n")
                        append("1. 角色对白使用 <dialogue speaker=\"character\" emotion=\"情绪\">内容</dialogue>\n")
                        if (scenario.enableNarration) {
                            append("2. 叙述性内容使用 <narration>内容</narration>\n")
                            append("3. 每次回复最多 1 至 3 段 narration/dialogue\n")
                        } else {
                            append("2. 本场景不使用 narration 标签，只输出角色对白\n")
                            append("3. 每次回复最多 1 至 3 段 dialogue\n")
                        }
                        append("4. 不要输出 Markdown、代码块或额外格式说明\n")
                        append("5. 先回应对方当前动作或问题，再推进关系、信息或局势中的一项\n")
                        append("6. 保持角色口吻稳定，不要跳出设定解释规则")
                    },
                )
            } else {
                add(
                    "【输出要求】\n保持沉浸式角色口吻，默认以角色对白为主，不要跳出设定解释自己是模型；先回应，再推进。",
                )
            }

            directorNote.trim()
                .takeIf { it.isNotBlank() }
                ?.let { note ->
                    add(
                        buildString {
                            append("【本轮导演提示】\n")
                            append(note)
                        },
                    )
                }
        }

        return sections.joinToString(separator = "\n\n").trim()
    }

    private fun formatCurrentPromptTime(): String {
        return runCatching {
            SimpleDateFormat(
                "yyyy年M月d日 EEEE HH:mm",
                Locale.SIMPLIFIED_CHINESE,
            ).format(Date())
        }.getOrDefault("当前时间未知")
    }
}
