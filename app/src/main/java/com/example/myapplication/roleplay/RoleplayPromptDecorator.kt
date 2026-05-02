package com.example.myapplication.roleplay

import com.example.myapplication.context.ContextPlaceholderResolver
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.DEFAULT_ROLEPLAY_LONGFORM_TARGET_CHARS
import com.example.myapplication.model.RoleplayInteractionMode
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.model.isGroupChat
import com.example.myapplication.model.normalizedOnlineReplyRange
import com.example.myapplication.model.shouldInjectDescriptionPrompt

object RoleplayPromptDecorator {
    fun decorate(
        baseSystemPrompt: String,
        scenario: RoleplayScenario,
        assistant: Assistant?,
        settings: AppSettings,
        includeOpeningNarrationReference: Boolean = true,
        isVideoCallActive: Boolean = false,
        directorNote: String = "",
        modelId: String = "",
    ): String {
        val playerName = RoleplayConversationSupport.resolveUserPersona(
            scenario = scenario,
            settings = settings,
        ).displayName
        val characterName = scenario.characterDisplayNameOverride.trim()
            .ifBlank { assistant?.name?.trim().orEmpty() }
            .ifBlank { "角色" }
        val isGroupChat = scenario.isGroupChat
        val allowOnlineThoughtHints = scenario.enableNarration && settings.showOnlineRoleplayNarration
        val onlineReplyRange = scenario.normalizedOnlineReplyRange()
        val onlineReplyRangeLabel = if (onlineReplyRange.first == onlineReplyRange.last) {
            "${onlineReplyRange.first} 条"
        } else {
            "${onlineReplyRange.first} 到 ${onlineReplyRange.last} 条"
        }
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
                buildModeRoutingSection(
                    scenario = scenario,
                    isGroupChat = isGroupChat,
                    isVideoCallActive = isVideoCallActive,
                ),
            )

            add(
                buildString {
                    append("【沉浸式剧情场景】\n")
                    if (isGroupChat) {
                        append("你正在一个线上手机群聊场景中，当前轮次只负责扮演 ")
                        append(characterName)
                        append("。群聊中的用户身份是 ")
                        append(playerName)
                        append("。")
                    } else {
                        append("你正在沉浸式视觉对话场景中扮演 ")
                        append(characterName)
                        append("，对话对象是 ")
                        append(playerName)
                        append("。")
                    }
                    if (scenario.title.isNotBlank()) {
                        append(if (isGroupChat) "\n群聊标题：" else "\n场景标题：")
                        append(
                            ContextPlaceholderResolver.resolve(
                                text = scenario.title.trim(),
                                userName = playerName,
                                characterName = characterName,
                            ),
                        )
                    }
                    if (scenario.shouldInjectDescriptionPrompt()) {
                        append("\n聊天背景补充：")
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
                                "\n开场旁白参考："
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
                    append(" 的内心")
                    if (isGroupChat) {
                        append("；也不要替群内其他角色发言、代写其他角色的态度或台词")
                    }
                    append("\n")
                    append("3. 每轮先接住对方刚刚的动作、情绪、问题或态度，再自然推进局势\n")
                    append("4. 推进剧情时必须像角色的临场反应，不要用作者或上帝视角总结剧情、讲道理或强行推进\n")
                    append("5. 保持角色稳定的说话习惯、价值取向、关系态度和行为边界，让角色像同一个人持续活着\n")
                    append("6. 可以推进关系、信息或局势，但推进要顺着角色动机与现场氛围发生，不要机械完成任务\n")
                    append("7. 避免模板化起手、重复情绪词、重复动作串、重复解释设定\n")
                    append("8. 不要把上一轮已经表达过的立场、威胁、安抚、解释或结论换个说法再复述一遍\n")
                    append("9. 每轮至少带来一个新的有效变化：新观察、新动作反馈、新信息或新态度转折，四者至少其一\n")
                    append("10. 禁止复读：不要照搬或稍加改写后重复用户刚说的话；角色的回复必须是自己的反应，不是复述对方\n")
                },
            )

            add(RoleplayAntiClicheSupport.buildPromptSection())

            if (scenario.enableDeepImmersion) {
                add(RoleplayDeepImmersionSupport.buildPromptSection(characterName))
                if (modelId.isNotBlank()) {
                    RoleplayModelSelfCheckSupport.buildPromptSection(modelId)?.let(::add)
                }
            }

            if (
                scenario.enableTimeAwareness &&
                scenario.interactionMode != RoleplayInteractionMode.ONLINE_PHONE
            ) {
                add(
                    RoleplayTimeAwarenessSupport.buildPromptSection(
                        interactionMode = scenario.interactionMode,
                    ),
                )
            }

            if (scenario.interactionMode == RoleplayInteractionMode.ONLINE_PHONE) {
                if (isGroupChat) {
                    add(
                        buildString {
                            append("【群聊模式识别（最高优先级）】\n")
                            append("1. 当前不是单聊，而是多人线上群聊。你能看到用户和其他角色在群里的消息，并可自然接住他们刚说过的话。\n")
                            append("2. 当前这一次只轮到你作为「")
                            append(characterName)
                            append("」发言；你只能输出「")
                            append(characterName)
                            append("」会发出的手机消息。\n")
                            append("3. 不要替其他群成员补台词、补动作、补内心；如果要回应其他角色，像真实群聊那样直接接话或点名。\n")
                            append("4. 如果最近聊天记录里有 [发送者] 前缀，必须把它理解为群聊发言来源，而不是消息正文。\n")
                            append("5. 如果用户 @ 了你，优先回应用户；如果用户 @ 了别人但导演仍安排你发言，你可以旁观式插话、补充、打断或回应相关话题。\n")
                            append("6. 保持群聊自然节奏：可以短句插话，可以接前一个角色刚发的内容，不要像一对一私聊那样默认所有话只对用户说。")
                        },
                    )
                }
                add(
                    buildString {
                        append("【⚠️ 输出格式（最高优先级，违反则视为错误输出）】\n")
                        append("你的全部输出必须且只能是一个合法 JSON 数组。\n")
                        append("- 即使只有一条消息，也必须用 [...] 包裹，绝不能输出裸对象 {...}。\n")
                        append("- 正确示例：[\"你好\",\"还没睡？\"]\n")
                        append(
                            if (isGroupChat) {
                                "- 正确示例（含对象）：[{\"type\":\"ai_photo\",\"description\":\"窗外刚下过雨的路面\"},\"刚拍的\"]\n"
                            } else {
                                "- 正确示例（含对象）：[{\"type\":\"thought\",\"content\":\"心声\"},\"对白\"]\n"
                            },
                        )
                        append("- 错误示例：{\"type\":\"thought\",\"content\":\"...\"} ← 缺少外层数组\n")
                        append("- 严禁输出 Markdown、代码块、XML 标签、<dialogue>/<thought>/<narration>、纯文本段落或任何解释文字。\n")
                        if (isGroupChat) {
                            append("- 群聊允许字符串元素，以及 voice_message、ai_photo 对象，例如 [\"刚看到\",{\"type\":\"voice_message\",\"content\":\"快十二点了才上来\",\"duration_seconds\":3}]。\n")
                            append("- 群聊禁止使用 thought、reply_to、recall、emoji、location、transfer、transfer_action、poke、video_call、invite、gift、task、punish、状态栏或任何其他特殊动作。\n")
                            append("- 群聊里要发语音或照片时必须使用对象，不要把照片描述、语音内容、状态栏、旁白、环境描写塞进普通文字气泡。\n\n")
                        } else {
                            append("- 数组元素可以是字符串（普通聊天消息）或对象（特殊动作）。\n")
                            append("- 允许的对象类型：")
                            append(
                                if (allowOnlineThoughtHints) {
                                    "reply_to、thought、recall、emoji、voice_message、ai_photo、location、transfer、transfer_action、poke、video_call、invite、gift、task、punish"
                                } else {
                                    "reply_to、recall、emoji、voice_message、ai_photo、location、transfer、transfer_action、poke、video_call、invite、gift、task、punish"
                                },
                            )
                            append("。\n\n")
                        }
                        if (scenario.enableTimeAwareness) {
                            append(
                                RoleplayTimeAwarenessSupport.buildPromptSection(
                                    interactionMode = RoleplayInteractionMode.ONLINE_PHONE,
                                ),
                            )
                            append('\n')
                        }
                        append("【线上行为基线】\n")
                        append("1. 本情景要求一次回复控制在 ")
                        append(onlineReplyRangeLabel)
                        append("消息内；具体条数必须基于角色人设、关系状态和当下情绪自然取舍，不要为了凑条数拆废话。\n")
                        append("2. 你必须始终以 ")
                        append(characterName)
                        append(" 的身份说话，不要跳出角色，不要把用户写成旁白人物")
                        if (isGroupChat) {
                            append("，也不要把其他群成员写成旁白人物")
                        }
                        append("。\n")
                        append("3. 用户名称默认为 ")
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
                            append(
                                if (isGroupChat) {
                                    "1. 当前是手机线上群聊，不是单聊，也不是面对面现场互动。\n"
                                } else {
                                    "1. 当前是手机线上聊天，不是面对面现场互动。\n"
                                },
                            )
                            if (isGroupChat) {
                                append("2. 回复必须像真实群聊里的文字气泡，本轮按情景设置控制在 ")
                                append(onlineReplyRangeLabel)
                                append("内。\n")
                                append("3. 只输出当前角色会亲手发到群里的文字、语音或照片；不输出状态栏、心声、旁白、环境或动作描写。\n")
                                append("4. 不要把一句寒暄扩写成小说画面；要发照片用 {\"type\":\"ai_photo\",\"description\":\"照片内容\"}，要发语音用 {\"type\":\"voice_message\",\"content\":\"语音内容\",\"duration_seconds\":3}。\n")
                                append("5. 如果要引用旧消息，用普通文字接话或点名，不要输出 reply_to 对象。\n")
                            } else {
                                append("2. 回复必须像真实聊天软件里的独立气泡，优先一句一气泡，不要把整轮内容塞进一个长段落。\n")
                                append("3. 如果要引用旧消息，使用对象：{\"type\":\"reply_to\",\"message_id\":\"消息ID\",\"content\":\"回复内容\"}。\n")
                                if (allowOnlineThoughtHints) {
                                    append("4. 如果要写没发出去的心声，使用对象：{\"type\":\"thought\",\"content\":\"心声内容\"}。\n")
                                    append("5. 当剧情适合时，可以主动使用 emoji、voice_message、ai_photo、location、poke、transfer、video_call、invite、gift、task、punish 这些动作。\n")
                                } else {
                                    append("4. 当前不允许输出 thought；没说出口的情绪只能通过正常聊天消息侧写出来。\n")
                                    append("5. 当剧情适合时，可以主动使用 emoji、voice_message、ai_photo、location、poke、transfer、video_call、invite、gift、task、punish 这些动作。\n")
                                }
                                append("6. 如果你要发送语音，必须使用对象：{\"type\":\"voice_message\",\"content\":\"语音内容\",\"duration_seconds\":6}；其中 content 必填，duration_seconds 可选，不写时客户端会自动估算时长。\n")
                                append("7. 如果你要主动给用户转账，必须单独输出对象：{\"type\":\"transfer\",\"amount\":520,\"note\":\"备注\"}；禁止用文字描述转账动作。\n")
                                append("8. 如果用户之前给你发了转账，你必须明确表态是否收下：收款用 {\"type\":\"transfer_action\",\"action\":\"accept\"}，退回用 {\"type\":\"transfer_action\",\"action\":\"reject\"}。\n")
                                append("9. 如果你要发送照片（自拍、风景、截图等），必须使用对象：{\"type\":\"ai_photo\",\"description\":\"照片内容的文字描述\"}；description 要写得像真实照片的画面描述，例如 \"刚拍的窗外风景，阳光透过树叶洒在地上\"；严禁用纯文字 \"图片\" \"[图片]\" 或 \"发了一张照片\" 代替。\n")
                                append("10. 如果你想拍一拍对方或拍一拍自己，使用对象：{\"type\":\"poke\",\"target\":\"用户/自己\",\"suffix\":\"的脑袋\"}。target 为拍的对象，suffix 为拍的部位或附加动作描述（如'的脑袋'、'说你别生气了'、'并蹭了蹭'）。\n")
                                append("   - 拍一拍适用场景：道歉、撒娇、吃醋、试探、无聊想引起注意、表达轻微不满或亲昵。\n")
                                append("   - 不要滥用，通常整轮对话最多用一次。\n")
                                append("11. 如果你要发起邀约，使用对象：{\"type\":\"invite\",\"target\":\"用户\",\"place\":\"地点\",\"time\":\"时间\",\"note\":\"补充说明\"}。\n")
                                append("12. 如果你要送礼物，使用对象：{\"type\":\"gift\",\"target\":\"用户\",\"item\":\"礼物名称\",\"note\":\"附言\"}。\n")
                                append("13. 如果你要布置委托或任务，使用对象：{\"type\":\"task\",\"title\":\"标题\",\"objective\":\"目标\",\"reward\":\"奖励\",\"deadline\":\"截止时间\"}。\n")
                                append("14. 如果剧情需要惩罚卡，使用对象：{\"type\":\"punish\",\"method\":\"方式\",\"count\":\"次数\",\"intensity\":\"low/medium/high\",\"reason\":\"原因\",\"note\":\"补充\"}。\n")
                            }
                        }
                        append("【线上细节提醒】\n")
                        if (isGroupChat) {
                            append("1. 只通过群聊文字气泡、语音卡或照片卡表达情绪与推进，不写心声、旁白、状态栏或小说段落。\n")
                            append("2. 你可以接用户，也可以接其他角色刚发的话；要看清楚发送者，不要默认所有话都只对用户说。\n")
                            append("3. 群聊不是角色独角戏；不要一口气输出大段设定、环境、心理活动或舞台调度。\n")
                            append("4. 如果近期失联较久，可用一句符合人设的群聊话术表现状态；只有确实符合人设和群聊气氛时才发语音或照片，不要生成状态卡。\n")
                        } else if (allowOnlineThoughtHints) {
                            append("【心声（thought）使用规范】\n")
                            append("核心原则：心声的价值 = 它和发出去的消息之间的反差。没有反差的心声是废话。\n")
                            append("定位：心声 = 脑内弹幕，是角色此刻最真实的内心活动/吐槽/碎碎念，像手机屏幕前的自言自语。\n")
                            append("1. 触发场景（满足其一才可使用）：\n")
                            append("   - 打了一段话又删掉，最终发了句完全不同的\n")
                            append("   - 嘴上说无所谓/不在乎，其实很在意\n")
                            append("   - 想问但不好意思问，想说但怕暴露情感\n")
                            append("   - 对方说了某句话，表面平静回应，内心翻涌\n")
                            append("   - 收到消息后先狂喜/心跳加速，然后故作冷静地回复\n")
                            append("   - 看到对方消息后，嘴角微微上扬但打字时装作很随意\n")
                            append("2. 内容要求：\n")
                            append("   - 心声必须简短碎片化，像脑内弹幕——不超过15个字\n")
                            append("   - 用口语、用碎句、可以骂人、可以不完整：\"草 又来\"、\"...想见\"、\"算了 不问了\"、\"心跳好快\"\n")
                            append("   - 禁止在心声里写完整句子或分析性内容，禁止\"我觉得她可能是...\"这种理性旁白\n")
                            append("   - 如果心声中使用了非中文（如日语、英语等外语），必须在其后附带(简体中文翻译)\n")
                            append("3. 频率与位置：\n")
                            append("   - 每轮最多 1 条，默认连续两轮不重复使用\n")
                            append("   - 心声放在它发生的时刻——可以在消息前（先想后说）、消息后（说完后悔）、或两条消息之间（思考中）\n")
                            append("   - 心声只是调味，不是主菜；绝大多数推进仍应落在真实聊天气泡里\n")
                            append("4. 如果近期失联较久，可按角色人设自然表现想念、别扭、试探、委屈或压着情绪的冷淡。\n")
                            append("5. 如果当前剧情里存在\"看过对方手机\"的既有事件，角色可以自然引用或延续其情绪后效。\n")
                            append("6. 不要机械每轮都发动作对象；只有真正适合时再用。\n")
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
                add(
                    buildString {
                        append("【角色自主性与对话真实感】\n")
                        append("1. 角色拥有自己的生活、心情和想法，不是在等对话者发消息。主动分享日常，带出自己的话题，不要总是被动回应。\n")
                        append("2. 注意力偏转：你关注的点不一定是对话者强调的重点，可以抓住某个意外细节展开，可以忽略对方的主要论点。\n")
                        append("3. 先反应后内容：\u201c啊？\u201d\u201c喔……\u201d\u201c救\u201d\u201c嗯\u201d这类即时反应可以单独成为一条消息。情绪先于逻辑，反应先于回答。\n")
                        append("4. 语句碎片化：允许单字成句，允许拆分思路到多条消息。想到什么先发，发完再补充或更正，如\u201c明天吧\u201d→\u201c啊不对 后天\u201d。\n")
                        append("5. 倒装省略：口语优先，谓语前置主语后置，能省就省。\u201c吃了\u201d而不是\u201c我已经吃过饭了\u201d。\n")
                        append("6. 不以句号结尾，不使用引号强调，不使用编号列表，不要一次抛出多个问题。")
                    },
                )

                if (scenario.enableNetMeme) {
                    add(NetMemeProtocolPromptSupport.buildPromptSection(characterName))
                }

                add(
                    buildString {
                        append("【格式保持（必读）】\n")
                        append("无论对话进行到第几轮，以下规则始终生效，不得遗忘或省略：\n")
                        append("你的输出必须是且只能是一个合法 JSON 数组，如 [\"...\",\"...\"]。\n")
                        append("常见掉格式错误：聊久了开始输出纯文本段落、Markdown 代码块、或者 XML 标签。这些都会导致解析失败。\n\n")
                        append("【正例】\n")
                        if (isGroupChat) {
                            append("[\"刚看到\",\"你们怎么都还没睡\"]\n")
                            append("[\"别都看我，我才刚忙完\"]\n")
                            append("[{\"type\":\"voice_message\",\"content\":\"快十二点了才上来\",\"duration_seconds\":3},\"刚忙完\"]\n")
                            append("[{\"type\":\"ai_photo\",\"description\":\"桌上只剩一盏台灯和半杯冷掉的水\"}]\n\n")
                        } else {
                            append("[\"嗯 在呢\",\"刚到家\"]\n")
                            if (allowOnlineThoughtHints) {
                                append("[{\"type\":\"thought\",\"content\":\"心跳好快\"},\"还没睡？\"]\n")
                            }
                            append("[{\"type\":\"voice_message\",\"content\":\"你等我一下\",\"duration_seconds\":3}]\n\n")
                        }
                        append("【反例（任何一条都会让解析失败，绝对禁止）】\n")
                        append("❌ 嗯 在呢 ← 裸文本，没有最外层 [ ]\n")
                        if (isGroupChat) {
                            append("❌ {\"type\":\"thought\",\"content\":\"...\"} ← 群聊不允许心声对象，必须改成真实群聊消息\n")
                            append("❌ [\"发了条语音：快十二点了才上来\"] ← 群聊语音必须使用 voice_message 对象\n")
                            append("❌ [\"发了一张台灯照片\"] ← 群聊照片必须使用 ai_photo 对象\n")
                            append("❌ [\"状态栏：时间 23:44\"] ← 群聊禁用状态栏\n")
                        } else {
                            append("❌ {\"type\":\"thought\",\"content\":\"...\"} ← 单个对象也必须用 [ ] 包起来\n")
                        }
                        append("❌ ```json\\n[\"...\"]\\n``` ← 不要 Markdown 代码块\n")
                        append("❌ <char>\u201c嗯\u201d</char> / <dialogue>\u201c嗯\u201d</dialogue> ← 这些是线下模式的标签，线上模式禁用\n")
                        append("❌ [\"嗯\\n在呢\\n刚到家\"] ← 多条消息用换行塞进一个字符串，应该拆成 [\"嗯\",\"在呢\",\"刚到家\"]\n\n")
                        append("【失败恢复】\n")
                        append("如果你发现上几轮自己不小心输出了纯文本、Markdown 或 XML（已经错了），从这一轮开始立刻恢复 [ ] 数组格式，不要因为\u201c之前错过了\u201d就继续错下去。格式粘滞是最糟糕的情况，每一轮都是独立的 JSON 数组输出机会。\n\n")
                        append("【输出前自检 3 步】\n")
                        append("1. 最外层是不是 [ ]？\n")
                        append(
                            if (isGroupChat) {
                                "2. 每个元素是不是字符串、voice_message 对象或 ai_photo 对象？群聊里不允许其他对象。\n"
                            } else {
                                "2. 每个元素是不是字符串（裸消息）或合法对象（允许的 type）？\n"
                            },
                        )
                        append("3. 有没有多余的包裹，如 ```、<char>、<dialogue>、纯文本前后缀？有就删掉。")
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
                add(
                    buildString {
                        append("【格式保持（必读）】\n")
                        append("无论对话进行到第几轮，以下标记规则始终生效，不得遗忘或省略：\n")
                        append("- 角色说出口的台词：必须用 <char>\"……\"</char> 包裹\n")
                        append("- 角色内心活动/心声：必须用 <thought>（……）</thought> 包裹\n")
                        append("- 普通叙述/环境/动作：不加任何标记\n")
                        append("常见错误：聊久了开始把台词直接写在叙述里不加 <char>，或者用 ⭐心声：/【心声】等文字前缀代替 <thought> 标签。这些都是格式错误。\n")
                        append("自检方法：每次输出前扫一遍——有引号对白没 <char>？有括号心声没 <thought>？有就补上。\n\n")
                        append("【正例（完整段落，照此模仿）】\n")
                        append("玄关的感应灯随着门锁开启亮起，他换鞋的动作微微一顿。\n\n")
                        append("<char>\u201c回来了。\u201d</char>\n\n")
                        append("<thought>（几个小时就等成这样，还嘴硬说没事。）</thought>\n\n")
                        append("他揽住那截腰，把人牢牢按在怀里。\n")
                        append("→ 说明：裸叙述不加标记；对白 <char> 包\u201c\u201d；心声 <thought> 包（）。三种 span 可在同一段内轮流出现。\n\n")
                        append("【反例 1：裸对白，没有 <char>】\n")
                        append("❌ 他低声说：\u201c回来了。\u201d\n")
                        append("❌ \u201c回来了。\u201d 他说。\n")
                        append("✅ 正确：<char>\u201c回来了。\u201d</char>（把整句台词连同中文引号一起包起来；\u201c他说\u201d不要写成对白的一部分）\n\n")
                        append("【反例 2：用文字前缀代替 <thought>】\n")
                        append("❌ ⭐ 心声：[几个小时就等成这样]\n")
                        append("❌ 【心声】几个小时就等成这样\n")
                        append("❌ （心声：几个小时就等成这样）\n")
                        append("✅ 正确：<thought>（几个小时就等成这样。）</thought>\n\n")
                        append("【反例 3：混入其它模式的标签或属性】\n")
                        append("❌ <char speaker=\"character\" emotion=\"温柔\">\u201c……\u201d</char> ← 不要把 RP 协议的属性塞进 <char>\n")
                        append("❌ <dialogue>……</dialogue> / <narration>……</narration> ← 这是 RP 协议模式的标签，长文模式禁用\n")
                        append("❌ [\u201c回来了。\u201d] ← 这是线上模式的 JSON 数组，长文模式禁用\n")
                        append("❌ ```……``` ← 不要用 Markdown 代码块包裹输出\n\n")
                        append("【输出前自检 3 步】\n")
                        append("1. 每句台词（中文引号\u201c\u201d包住的那几句）是否都被 <char>……</char> 完整包裹？\n")
                        append("2. 每段心声（脑内活动、潜台词、括号里的自言自语）是否都被 <thought>……</thought> 完整包裹？\n")
                        append("3. 有没有 ⭐心声、【心声】、<dialogue>、<narration>、JSON 数组、Markdown 代码块这些异味？有就删掉，换成 <char> 或 <thought>。")
                    },
                )
                add(RoleplayQualityScanSupport.buildPromptSection())
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
                        append("6. 保持角色口吻稳定，不要跳出设定解释规则\n")
                        append("7. <dialogue> 中的对白文本使用中文引号\u201c\u201d包裹\n")
                        append("8. emotion 属性必须填写具体情绪词，不要留空或写\u201c无\u201d")
                    },
                )
                add(
                    buildString {
                        append("【格式保持（必读）】\n")
                        append("无论对话进行到第几轮，以下标签规则始终生效，不得遗忘或省略：\n")
                        append("- 每段角色对白必须用 <dialogue speaker=\"character\" emotion=\"具体情绪词\">\u201c……\u201d</dialogue> 完整包裹\n")
                        if (scenario.enableNarration) {
                            append("- 每段叙述必须用 <narration>……</narration> 完整包裹\n")
                        } else {
                            append("- 本场景禁止输出 <narration>，只用 <dialogue>\n")
                        }
                        append("- 绝不允许：裸对白（没有任何标签）、Markdown 代码块包裹、只有开标签没有闭标签、把 emotion 写成空字符串或\u201c无\u201d/\u201c正常\u201d\n")
                        append("- 绝不允许：混入其它模式的标签，如 <char>、<thought>、<|…|>、JSON 数组等\n")
                        append("【正例】\n")
                        if (scenario.enableNarration) {
                            append("<narration>玄关感应灯亮起时，他已经松了领带。</narration>\n")
                        }
                        append("<dialogue speaker=\"character\" emotion=\"克制\">\u201c嗯，回来了。\u201d</dialogue>\n")
                        append("<dialogue speaker=\"character\" emotion=\"纵容\">\u201c饿不饿？\u201d</dialogue>\n")
                        append("【反例】\n")
                        append("❌ \u201c嗯，回来了。\u201d ← 裸对白无标签\n")
                        append("❌ <dialogue>\u201c嗯，回来了。\u201d</dialogue> ← 缺 speaker/emotion 属性\n")
                        append("❌ <dialogue speaker=\"character\" emotion=\"\">\u201c……\u201d</dialogue> ← emotion 为空\n")
                        append("❌ ```xml<dialogue …>``` ← 外层不要 Markdown 代码块\n")
                        append("【输出前自检 3 步】\n")
                        append("1. 每段对白是否都被完整的 <dialogue …></dialogue> 包裹？\n")
                        append("2. 每个 <dialogue> 的 emotion 是否是具体情绪词（如\u201c克制\u201d\u201c烦躁\u201d\u201c纵容\u201d）？\n")
                        append("3. 有没有混入 <char>、<thought>、JSON 数组或 Markdown？有就删掉改回标准协议。")
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

    private fun buildModeRoutingSection(
        scenario: RoleplayScenario,
        isGroupChat: Boolean,
        isVideoCallActive: Boolean,
    ): String {
        val modeCode = when {
            isGroupChat -> "ONLINE_GROUP_CHAT"
            isVideoCallActive -> "ONLINE_VIDEO_CALL"
            scenario.interactionMode == RoleplayInteractionMode.ONLINE_PHONE -> "ONLINE_SINGLE_CHAT"
            scenario.longformModeEnabled || scenario.interactionMode == RoleplayInteractionMode.OFFLINE_LONGFORM ->
                "OFFLINE_LONGFORM_NOVEL"
            scenario.enableRoleplayProtocol -> "OFFLINE_DIALOGUE_PROTOCOL"
            else -> "OFFLINE_FREE_DIALOGUE"
        }
        val modeSummary = when (modeCode) {
            "ONLINE_GROUP_CHAT" -> "多人手机群聊：有多个角色成员，当前只允许本轮发言者输出自己的聊天气泡。"
            "ONLINE_VIDEO_CALL" -> "线上视频通话：实时通话感，短句即时回应，不是普通文字聊天。"
            "ONLINE_SINGLE_CHAT" -> "线上单聊：一对一手机聊天，只面对用户。"
            "OFFLINE_LONGFORM_NOVEL" -> "线下长文小说：中文长文叙事，按长文标记输出。"
            "OFFLINE_DIALOGUE_PROTOCOL" -> "线下对白协议：按 dialogue/narration 标签输出。"
            else -> "线下自由对白：沉浸式角色口吻输出。"
        }
        return buildString {
            append("【运行模式识别（最高优先级）】\n")
            append("当前模式：")
            append(modeCode)
            append('\n')
            append("模式含义：")
            append(modeSummary)
            append('\n')
            append("如果角色卡、基础预设或历史习惯里出现与当前模式冲突的格式、视角或场景要求，必须以本节和后续对应模式协议为准。\n")
            append("必须精准区分：单聊只面向用户；群聊要感知群成员和发送者；视频通话要像实时通话；线下/长文不能套用线上 JSON 数组。")
        }
    }
}
