package com.example.myapplication.model

enum class AppColorTheme(
    val storageValue: String,
    val label: String,
) {
    MATCHA(
        storageValue = "matcha",
        label = "治愈抹茶",
    ),
    VANILLA(
        storageValue = "vanilla",
        label = "经典香草",
    ),
    OCEAN(
        storageValue = "ocean",
        label = "深海幽蓝",
    );

    companion object {
        fun fromStorageValue(value: String): AppColorTheme {
            return entries.firstOrNull { it.storageValue == value } ?: MATCHA
        }
    }
}
