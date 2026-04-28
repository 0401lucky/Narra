package com.example.myapplication.model

data class FunctionModelProviderIds(
    val titleSummaryProviderId: String = "",
    val chatSuggestionProviderId: String = "",
    val memoryProviderId: String = "",
    val translationProviderId: String = "",
    val phoneSnapshotProviderId: String = "",
    val searchProviderId: String = "",
    val giftImageProviderId: String = "",
) {
    fun providerIdFor(function: ProviderFunction): String {
        return when (function) {
            ProviderFunction.CHAT -> ""
            ProviderFunction.TITLE_SUMMARY -> (titleSummaryProviderId as String?).orEmpty().trim()
            ProviderFunction.CHAT_SUGGESTION -> (chatSuggestionProviderId as String?).orEmpty().trim()
            ProviderFunction.MEMORY -> (memoryProviderId as String?).orEmpty().trim()
            ProviderFunction.TRANSLATION -> (translationProviderId as String?).orEmpty().trim()
            ProviderFunction.PHONE_SNAPSHOT -> (phoneSnapshotProviderId as String?).orEmpty().trim()
            ProviderFunction.SEARCH -> (searchProviderId as String?).orEmpty().trim()
            ProviderFunction.GIFT_IMAGE -> (giftImageProviderId as String?).orEmpty().trim()
        }
    }

    fun withProviderId(function: ProviderFunction, providerId: String): FunctionModelProviderIds {
        val normalizedProviderId = providerId.trim()
        return when (function) {
            ProviderFunction.CHAT -> this
            ProviderFunction.TITLE_SUMMARY -> copy(titleSummaryProviderId = normalizedProviderId)
            ProviderFunction.CHAT_SUGGESTION -> copy(chatSuggestionProviderId = normalizedProviderId)
            ProviderFunction.MEMORY -> copy(memoryProviderId = normalizedProviderId)
            ProviderFunction.TRANSLATION -> copy(translationProviderId = normalizedProviderId)
            ProviderFunction.PHONE_SNAPSHOT -> copy(phoneSnapshotProviderId = normalizedProviderId)
            ProviderFunction.SEARCH -> copy(searchProviderId = normalizedProviderId)
            ProviderFunction.GIFT_IMAGE -> copy(giftImageProviderId = normalizedProviderId)
        }
    }

    fun normalized(validProviderIds: Set<String> = emptySet()): FunctionModelProviderIds {
        fun keepIfValid(providerId: String): String {
            val normalizedProviderId = providerId.trim()
            return normalizedProviderId
                .takeIf { it.isNotBlank() && (validProviderIds.isEmpty() || it in validProviderIds) }
                .orEmpty()
        }
        return copy(
            titleSummaryProviderId = keepIfValid((titleSummaryProviderId as String?).orEmpty()),
            chatSuggestionProviderId = keepIfValid((chatSuggestionProviderId as String?).orEmpty()),
            memoryProviderId = keepIfValid((memoryProviderId as String?).orEmpty()),
            translationProviderId = keepIfValid((translationProviderId as String?).orEmpty()),
            phoneSnapshotProviderId = keepIfValid((phoneSnapshotProviderId as String?).orEmpty()),
            searchProviderId = keepIfValid((searchProviderId as String?).orEmpty()),
            giftImageProviderId = keepIfValid((giftImageProviderId as String?).orEmpty()),
        )
    }
}
