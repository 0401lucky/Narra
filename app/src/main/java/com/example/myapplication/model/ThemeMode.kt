package com.example.myapplication.model

enum class ThemeMode(
    val storageValue: String,
    val label: String,
) {
    SYSTEM(
        storageValue = "system",
        label = "跟随系统",
    ),
    LIGHT(
        storageValue = "light",
        label = "浅色",
    ),
    DARK(
        storageValue = "dark",
        label = "深色",
    );

    companion object {
        fun fromStorageValue(value: String): ThemeMode {
            return entries.firstOrNull { it.storageValue == value } ?: SYSTEM
        }
    }
}
