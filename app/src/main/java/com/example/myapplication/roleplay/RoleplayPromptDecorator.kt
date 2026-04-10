package com.example.myapplication.roleplay

import com.example.myapplication.context.ContextPlaceholderResolver
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.DEFAULT_ROLEPLAY_LONGFORM_TARGET_CHARS
import com.example.myapplication.model.RoleplayInteractionMode
import com.example.myapplication.model.RoleplayScenario

object RoleplayPromptDecorator {
    fun decorate(
        baseSystemPrompt: String,
        scenario: RoleplayScenario,
        assistant: Assistant?,
        settings: AppSettings,
        includeOpeningNarrationReference: Boolean = true,
        directorNote: String = "",
    ): String {
        val playerName = scenario.userDisplayNameOverride.trim()
            .ifBlank { settings.resolvedUserDisplayName() }
        val characterName = scenario.characterDisplayNameOverride.trim()
            .ifBlank { assistant?.name?.trim().orEmpty() }
            .ifBlank { "角色" }
        val allowOnlineNarration = scenario.enableNarration && settings.showOnlineRoleplayNarration
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
                        append("\n开场旁白参考：")
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

            if (scenario.interactionMode == RoleplayInteractionMode.ONLINE_PHONE) {
                add(
                    buildString {
                        append("【线上手机聊天模式】\n")
                        append("1. 当前是手机线上聊天，不是面对面现场互动。\n")
                        if (allowOnlineNarration) {
                            append("2. 角色回复应以短消息、多气泡为主，但允许自然穿插旁白/叙述性插段。\n")
                        } else {
                            append("2. 角色回复应以短消息、多气泡为主，不使用 narration 标签，不写独立旁白段。\n")
                        }
                        append("3. 角色对白继续使用 <dialogue speaker=\"character\" emotion=\"情绪\">内容</dialogue>。\n")
                        append("4. speaker 属性固定写 character，不要写角色真实名字；emotion 属性只有确定时再写，拿不准就省略。\n")
                        append("5. 所有属性必须完整写在 opening tag 内，绝不能把 speaker=、emotion= 或半截标签输出到正文里；如果协议快写错了，宁可直接输出纯中文内容，也不要输出坏掉的标签。\n")
                        append("6. 如果要引用旧消息，可在 dialogue 标签上附加属性：reply_to=\"消息ID\" reply_speaker=\"名字\" reply_preview=\"预览\"。\n")
                        if (allowOnlineNarration) {
                            append("7. 叙述性内容继续使用 <narration>内容</narration>，用于状态条、聊天中的动作/停顿/情绪铺垫。\n")
                            append("8. 不要把整轮写成长篇散文；本轮输出多少段 dialogue/narration 由当前话题、情绪强度、上下文压力和记忆线索自然决定，能一句说完就少发，需要追发时再连续多发。\n")
                            append("9. 聊天语境要有时间感、等待感和手机互动感，但不要丢掉正常强度的旁白表现。\n")
                            append("10. 如果近期失联较久，可按角色人设自然表现委屈、生气、阴阳怪气、克制或想念。\n")
                            append("11. 如果当前剧情里存在“看过对方手机”的既有事件，角色可以自然引用或延续其情绪后效。\n")
                            append("12. 不要输出 Markdown、代码块或额外格式说明。")
                        } else {
                            append("7. 输出多少段 dialogue 由当前话题、情绪强度、上下文压力和记忆线索自然决定，能一句说完就少发，需要追发时再连续多发，整体保持真实聊天软件里的连续气泡感。\n")
                            append("8. 聊天语境要有时间感、等待感和手机互动感，但通过对白本身表达，不额外插入旁白。\n")
                            append("9. 如果近期失联较久，可按角色人设自然表现委屈、生气、阴阳怪气、克制或想念。\n")
                            append("10. 如果当前剧情里存在“看过对方手机”的既有事件，角色可以自然引用或延续其情绪后效。\n")
                            append("11. 不要输出 Markdown、代码块或额外格式说明。")
                        }
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
}
