package com.example.myapplication.model

data class CharacterShakeFilters(
    val gender: String = "",
    val ageRange: String = "",
    val personality: String = "",
    val identity: String = "",
    val relationship: String = "",
    val personalTrait: String = "",
    val zodiacSign: String = "",
    val mbti: String = "",
) {
    fun hasSelectedOption(): Boolean {
        return gender.isNotBlank() ||
            ageRange.isNotBlank() ||
            personality.isNotBlank() ||
            identity.isNotBlank() ||
            relationship.isNotBlank() ||
            personalTrait.isNotBlank() ||
            zodiacSign.isNotBlank() ||
            mbti.isNotBlank()
    }

    fun selectedSummary(): String {
        val parts = buildList {
            if (gender.isNotBlank()) add(gender)
            if (ageRange.isNotBlank()) add(ageRange)
            if (personality.isNotBlank()) add(personality)
            if (identity.isNotBlank()) add(identity)
            if (relationship.isNotBlank()) add(relationship)
            if (personalTrait.isNotBlank()) add(personalTrait)
            if (zodiacSign.isNotBlank()) add(zodiacSign)
            if (mbti.isNotBlank()) add(mbti)
        }
        return parts.joinToString(" · ").ifBlank { "完全随机" }
    }
}
