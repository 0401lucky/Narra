package com.example.myapplication.viewmodel

import com.example.myapplication.data.repository.ai.RoleplaySuggestionParser

internal object RoleplaySuggestionDraftGuard {
    fun isUsableDraft(rawDraft: String): Boolean {
        val normalized = rawDraft.trim()
        if (normalized.isBlank()) {
            return false
        }
        return !RoleplaySuggestionParser.looksLikeStructuredPayload(normalized)
    }
}
