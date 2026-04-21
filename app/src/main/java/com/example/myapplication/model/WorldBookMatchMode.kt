package com.example.myapplication.model

enum class WorldBookMatchMode(val storageValue: String, val label: String) {
    CONTAINS("contains", "包含"),
    WORD_CJK("word_cjk", "整词"),
    REGEX("regex", "正则");

    companion object {
        fun fromStorageValue(value: String): WorldBookMatchMode {
            return entries.firstOrNull { it.storageValue == value.trim().lowercase() } ?: WORD_CJK
        }
    }
}

const val DEFAULT_WORLD_BOOK_MATCH_MODE_STORAGE = "word_cjk"
