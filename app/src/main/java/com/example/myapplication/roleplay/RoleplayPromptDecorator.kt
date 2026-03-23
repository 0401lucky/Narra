package com.example.myapplication.roleplay

import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
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

            if (scenario.enableRoleplayProtocol) {
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
