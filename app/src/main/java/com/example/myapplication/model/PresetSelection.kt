package com.example.myapplication.model

fun resolveActivePresetId(
    globalDefaultPresetId: String,
    assistantDefaultPresetId: String?,
    scenarioPresetId: String? = null,
): String {
    val globalPresetId = globalDefaultPresetId.trim().ifBlank { DEFAULT_PRESET_ID }
    val scenarioId = scenarioPresetId?.trim().orEmpty()
    if (scenarioId.isNotEmpty()) return scenarioId
    val assistantPresetId = assistantDefaultPresetId?.trim().orEmpty()
    return when {
        assistantPresetId.isBlank() -> globalPresetId
        else -> assistantPresetId
    }
}

fun isAssistantPresetFollowingGlobal(
    globalDefaultPresetId: String,
    assistantDefaultPresetId: String?,
): Boolean {
    val globalPresetId = globalDefaultPresetId.trim().ifBlank { DEFAULT_PRESET_ID }
    val assistantPresetId = assistantDefaultPresetId?.trim().orEmpty()
    return assistantPresetId.isBlank() ||
        assistantPresetId == globalPresetId
}

fun isScenarioPresetFollowingAssistant(scenarioPresetId: String?): Boolean =
    scenarioPresetId?.trim().isNullOrEmpty()

/**
 * 侧栏「本会话预设」摘要：本会话 / 跟随角色 / 跟随全局；已删预设显示失效文案。
 */
fun scenarioPresetSummary(
    scenarioPresetId: String?,
    assistantDefaultPresetId: String?,
    globalDefaultPresetId: String,
    presets: List<Preset>,
): String {
    val activeId = resolveActivePresetId(
        globalDefaultPresetId = globalDefaultPresetId,
        assistantDefaultPresetId = assistantDefaultPresetId,
        scenarioPresetId = scenarioPresetId,
    )
    val activeName = presets.firstOrNull { it.id == activeId }?.name?.trim().orEmpty()
    val scenarioId = scenarioPresetId?.trim().orEmpty()
    if (scenarioId.isNotEmpty()) {
        val scenarioPreset = presets.firstOrNull { it.id == scenarioId }
        return if (scenarioPreset != null) {
            "${scenarioPreset.name.trim().ifBlank { "未命名预设" }} · 本会话"
        } else {
            "已失效，回退默认"
        }
    }
    val source = if (
        isAssistantPresetFollowingGlobal(
            globalDefaultPresetId = globalDefaultPresetId,
            assistantDefaultPresetId = assistantDefaultPresetId,
        )
    ) {
        "跟随全局"
    } else {
        "跟随角色"
    }
    return if (activeName.isNotBlank()) {
        "$activeName · $source"
    } else {
        source
    }
}
