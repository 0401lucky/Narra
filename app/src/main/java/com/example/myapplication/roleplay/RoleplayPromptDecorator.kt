package com.example.myapplication.roleplay

import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.DEFAULT_ROLEPLAY_LONGFORM_TARGET_CHARS
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
        val sections = buildList {
            baseSystemPrompt.trim()
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
                        append(scenario.title.trim())
                    }
                    if (scenario.description.isNotBlank()) {
                        append("\n场景描述：")
                        append(scenario.description.trim())
                    }
                    if (includeOpeningNarrationReference && scenario.openingNarration.isNotBlank()) {
                        append("\n开场旁白参考：")
                        append(scenario.openingNarration.trim())
                    }
                },
            )

            if (scenario.longformModeEnabled) {
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
                        append("3. 每次回复写成 4 到 8 个自然段，保留段落换行\n")
                        append("4. 动作和环境描写直接融入叙事正文\n")
                        append("5. 对白优先使用中文引号“”包裹\n")
                        append("6. 心理描写优先使用全角括号（……）表达\n")
                        append("7. 继续保持角色口吻稳定，并让剧情至少推进一项：局势、信息或关系\n")
                        append("8. 仅在角色本人直接说出口的话外层使用 <char>“……”</char> 包裹，只包裹台词本身\n")
                        append("9. 仅在角色本人的心理活动外层使用 <thought>（……）</thought> 包裹，只包裹心声本身\n")
                        append("10. 其他人物对白、环境描写和普通叙述不要添加任何标记\n")
                        append("11. 不要嵌套这些标记，也不要解释标记用途；这些标记只供客户端内部渲染，用户最终不会看到")
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
                        append("5. 保持角色口吻稳定，不要跳出设定解释规则")
                    },
                )
            } else {
                add(
                    "【输出要求】\n保持沉浸式角色口吻，默认以角色对白为主，不要跳出设定解释自己是模型。",
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
