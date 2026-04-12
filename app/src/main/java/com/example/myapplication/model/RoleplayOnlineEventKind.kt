package com.example.myapplication.model

enum class RoleplayOnlineEventKind(val storageValue: String) {
    NONE("none"),
    SCREENSHOT("screenshot"),
    RECALL("recall"),
    BURST("burst"),
    COMPENSATION_OPENING("compensation_opening"),
    VIDEO_CALL_CONNECTED("video_call_connected"),
    VIDEO_CALL_ENDED("video_call_ended");

    companion object {
        fun fromStorageValue(value: String): RoleplayOnlineEventKind {
            return entries.firstOrNull { it.storageValue == value.trim().lowercase() } ?: NONE
        }
    }
}
