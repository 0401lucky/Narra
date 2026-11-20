package com.example.myapplication.roleplay.companion

import com.example.myapplication.model.TranslationRequestKey

/** 当前仅缓存完整译文；音频和译文各自有限，失败/取消结果不由此自动发布。 */
class CompanionTranslationCache(
    private val maxEntries: Int = 128,
    private val maxCharacters: Int = 256_000
) {
    private val entries = LinkedHashMap<TranslationRequestKey, String>(16, 0.75f, true)

    init {
        require(maxEntries > 0 && maxCharacters > 0)
    }

    @Synchronized
    fun get(key: TranslationRequestKey): String? = entries[key]

    @Synchronized
    fun put(key: TranslationRequestKey, text: String) {
        if (text.isBlank() || text.length > maxCharacters) return
        entries[key] = text
        while (entries.size > maxEntries || entries.values.sumOf { it.length } > maxCharacters) {
            entries.remove(entries.keys.first())
        }
    }

    @Synchronized
    fun clear() = entries.clear()
}
