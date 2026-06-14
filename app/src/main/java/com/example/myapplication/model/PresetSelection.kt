package com.example.myapplication.model

fun resolveActivePresetId(
    globalDefaultPresetId: String,
    assistantDefaultPresetId: String?,
): String {
    val globalPresetId = globalDefaultPresetId.trim().ifBlank { DEFAULT_PRESET_ID }
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
