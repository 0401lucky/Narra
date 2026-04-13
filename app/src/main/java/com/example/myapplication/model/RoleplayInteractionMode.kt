package com.example.myapplication.model

enum class RoleplayInteractionMode(val storageValue: String, val displayName: String) {
    OFFLINE_LONGFORM("offline_longform", "长文线下"),
    OFFLINE_DIALOGUE("offline_dialogue", "普通对白"),
    ONLINE_PHONE("online_phone", "线上模式");

    companion object {
        fun fromStorageValueOrNull(value: String): RoleplayInteractionMode? {
            val normalized = value.trim().lowercase()
            if (normalized.isBlank()) {
                return null
            }
            return entries.firstOrNull { it.storageValue == normalized }
        }

        fun fromStorageValue(value: String): RoleplayInteractionMode {
            return fromStorageValueOrNull(value)
                ?: OFFLINE_DIALOGUE
        }
    }
}
