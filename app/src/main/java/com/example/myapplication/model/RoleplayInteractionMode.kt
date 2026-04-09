package com.example.myapplication.model

enum class RoleplayInteractionMode(val storageValue: String, val displayName: String) {
    OFFLINE_LONGFORM("offline_longform", "长文线下"),
    OFFLINE_DIALOGUE("offline_dialogue", "普通对白"),
    ONLINE_PHONE("online_phone", "线上模式");

    companion object {
        fun fromStorageValue(value: String): RoleplayInteractionMode {
            return entries.firstOrNull { it.storageValue == value.trim().lowercase() }
                ?: OFFLINE_DIALOGUE
        }
    }
}
