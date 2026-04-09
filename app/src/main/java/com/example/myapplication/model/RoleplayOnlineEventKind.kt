package com.example.myapplication.model

enum class RoleplayOnlineEventKind(val storageValue: String) {
    NONE("none"),
    SCREENSHOT("screenshot"),
    RECALL("recall"),
    BURST("burst"),
    COMPENSATION_OPENING("compensation_opening");

    companion object {
        fun fromStorageValue(value: String): RoleplayOnlineEventKind {
            return entries.firstOrNull { it.storageValue == value.trim().lowercase() } ?: NONE
        }
    }
}
