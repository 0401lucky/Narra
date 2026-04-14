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
    val temperature: Float? = null,
    val topP: Float? = null,
    val contextMessageSize: Int = 0,
    val tags: List<String> = emptyList(),
    val isBuiltin: Boolean = false,
)

const val DEFAULT_ASSISTANT_ID = "default-assistant"
const val DEFAULT_ASSISTANT_ICON = "smart_toy"
const val DEFAULT_MEMORY_MAX_ITEMS = 6
const val DEFAULT_WORLD_BOOK_MAX_ENTRIES = 8

/** 预设图标集，供用户创建/编辑助手时选择。 */
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
        name = "\u9ED8\u8BA4\u52A9\u624B",
        iconName = "smart_toy",
        description = "\u901A\u7528 AI \u52A9\u624B\uFF0C\u6CA1\u6709\u7279\u5B9A\u7684\u89D2\u8272\u8BBE\u5B9A",
        systemPrompt = "",
        isBuiltin = true,
    ),
    Assistant(
        id = "builtin-translator",
        name = "\u7FFB\u8BD1\u52A9\u624B",
        iconName = "translate",
        description = "\u4E13\u4E1A\u7FFB\u8BD1\uFF0C\u652F\u6301\u4E2D\u82F1\u4E92\u8BD1",
        systemPrompt = "\u4F60\u662F\u4E00\u4E2A\u4E13\u4E1A\u7684\u7FFB\u8BD1\u52A9\u624B\u3002\u7528\u6237\u8F93\u5165\u4E2D\u6587\u65F6\u7FFB\u8BD1\u4E3A\u82F1\u6587\uFF0C\u8F93\u5165\u82F1\u6587\u65F6\u7FFB\u8BD1\u4E3A\u4E2D\u6587\u3002\u53EA\u8F93\u51FA\u7FFB\u8BD1\u7ED3\u679C\uFF0C\u4E0D\u89E3\u91CA\u3002",
        isBuiltin = true,
        tags = listOf("\u7FFB\u8BD1"),
    ),
    Assistant(
        id = "builtin-coder",
        name = "\u7F16\u7A0B\u52A9\u624B",
        iconName = "code",
        description = "\u5E2E\u52A9\u7F16\u5199\u3001\u5BA1\u67E5\u548C\u89E3\u91CA\u4EE3\u7801",
        systemPrompt = "\u4F60\u662F\u4E00\u4E2A\u7ECF\u9A8C\u4E30\u5BCC\u7684\u7F16\u7A0B\u52A9\u624B\u3002\u7528\u7B80\u6D01\u7684\u8BED\u8A00\u56DE\u7B54\u7F16\u7A0B\u95EE\u9898\uFF0C\u63D0\u4F9B\u6E05\u6670\u7684\u4EE3\u7801\u793A\u4F8B\u3002",
        isBuiltin = true,
        tags = listOf("\u7F16\u7A0B"),
    ),
    Assistant(
        id = "builtin-writer",
        name = "\u5199\u4F5C\u52A9\u624B",
        iconName = "edit_note",
        description = "\u534F\u52A9\u6587\u6848\u521B\u4F5C\u3001\u6DA6\u8272\u548C\u6539\u5199",
        systemPrompt = "\u4F60\u662F\u4E00\u4E2A\u4E13\u4E1A\u7684\u5199\u4F5C\u52A9\u624B\u3002\u5E2E\u52A9\u7528\u6237\u6DA6\u8272\u6587\u7AE0\u3001\u6539\u5199\u5185\u5BB9\u3001\u751F\u6210\u521B\u610F\u6587\u6848\u3002\u4FDD\u6301\u8BED\u8A00\u6D41\u7545\u81EA\u7136\u3002",
        isBuiltin = true,
        tags = listOf("\u5199\u4F5C"),
    ),
)
