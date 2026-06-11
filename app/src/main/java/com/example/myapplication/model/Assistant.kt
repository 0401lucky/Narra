package com.example.myapplication.model

import java.util.UUID

data class Assistant(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val iconName: String = DEFAULT_ASSISTANT_ICON,
    val avatarUri: String = "",
    val description: String = "",
    val systemPrompt: String = "",
    val scenario: String = "",
    val greeting: String = "",
    val exampleDialogues: List<String> = emptyList(),
    val creatorNotes: String = "",
    val linkedWorldBookIds: List<String> = emptyList(),
    val linkedWorldBookBookIds: List<String> = emptyList(),
    val memoryEnabled: Boolean = false,
    val useGlobalMemory: Boolean = false,
    val memoryMaxItems: Int = DEFAULT_MEMORY_MAX_ITEMS,
    val worldBookMaxEntries: Int = DEFAULT_WORLD_BOOK_MAX_ENTRIES,
    val worldBookScanDepth: Int = DEFAULT_WORLD_BOOK_SCAN_DEPTH,
    val temperature: Float? = null,
    val topP: Float? = null,
    val contextMessageSize: Int = 0,
    val defaultPresetId: String = DEFAULT_PRESET_ID,
    val tags: List<String> = emptyList(),
    val momentAutoCommentEnabled: Boolean = true,
    val momentAutoPostEnabled: Boolean = false,
    val momentAutoImageEnabled: Boolean = false,
    val momentAutoPostFrequency: MomentAutoPostFrequency = MomentAutoPostFrequency.STANDARD,
    val momentCommentStyle: MomentCommentStyle = MomentCommentStyle.NATURAL,
    val momentLastAutoPostAt: Long = 0L,
    val isBuiltin: Boolean = false,
)

const val DEFAULT_ASSISTANT_ID = "default-assistant"
const val DEFAULT_ASSISTANT_ICON = "smart_toy"
const val DEFAULT_MEMORY_MAX_ITEMS = 6
const val DEFAULT_WORLD_BOOK_MAX_ENTRIES = 8
const val DEFAULT_WORLD_BOOK_SCAN_DEPTH = 2

/** 预设图标集，供用户创建/编辑角色时选择。 */
val PRESET_ASSISTANT_ICONS: List<PresetIcon> = listOf(
    PresetIcon("smart_toy", "\u673A\u5668\u4EBA"),
    PresetIcon("psychology", "\u5FC3\u7406"),
    PresetIcon("translate", "\u7FFB\u8BD1"),
    PresetIcon("code", "\u4EE3\u7801"),
    PresetIcon("edit_note", "\u5199\u4F5C"),
    PresetIcon("school", "\u5B66\u4E60"),
    PresetIcon("science", "\u79D1\u5B66"),
    PresetIcon("calculate", "\u8BA1\u7B97"),
    PresetIcon("palette", "\u8BBE\u8BA1"),
    PresetIcon("music_note", "\u97F3\u4E50"),
    PresetIcon("restaurant", "\u7F8E\u98DF"),
    PresetIcon("fitness_center", "\u5065\u8EAB"),
    PresetIcon("travel_explore", "\u65C5\u884C"),
    PresetIcon("local_hospital", "\u533B\u7597"),
    PresetIcon("gavel", "\u6CD5\u5F8B"),
    PresetIcon("auto_stories", "\u6545\u4E8B"),
)

data class PresetIcon(
    val name: String,
    val label: String,
)

val BUILTIN_ASSISTANTS: List<Assistant> = listOf(
    Assistant(
        id = DEFAULT_ASSISTANT_ID,
        name = "默认角色",
        iconName = "auto_stories",
        description = "用于快速开始 RP 会话的基础角色卡，可按剧情需要继续编辑。",
        systemPrompt = "你正在参与角色扮演。请保持当前角色口吻和关系边界，围绕剧情自然回应。",
        isBuiltin = true,
        tags = listOf("RP"),
    ),
)

val HIDDEN_BUILTIN_ASSISTANT_IDS: Set<String> = setOf(
    "builtin-translator",
    "builtin-coder",
    "builtin-writer",
)
