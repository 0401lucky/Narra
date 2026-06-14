package com.example.myapplication.model

data class CharacterShakeFilters(
    val gender: String = "",
    val ageRange: String = "",
    val personality: String = "",
    val identity: String = "",
    val personalTrait: String = "",
) {
    fun hasSelectedOption(): Boolean {
        return gender.isNotBlank() ||
            ageRange.isNotBlank() ||
            personality.isNotBlank() ||
            identity.isNotBlank() ||
            personalTrait.isNotBlank()
    }

    fun selectedSummary(): String {
        val parts = buildList {
            if (gender.isNotBlank()) add(gender)
            if (ageRange.isNotBlank()) add(ageRange)
            if (personality.isNotBlank()) add(personality)
            if (identity.isNotBlank()) add(identity)
            if (personalTrait.isNotBlank()) add(personalTrait)
        }
        return parts.joinToString(" · ").ifBlank { "完全随机" }
    }
}
