package com.example.myapplication.ui.navigation

import android.net.Uri

object AppRoutes {
    const val HOME = "home"
    const val SETTINGS_GRAPH = "settings_graph"
    const val SETTINGS = "settings"
    const val SETTINGS_CONNECTION = "settings/connection"
    const val SETTINGS_MODEL = "settings/model"
    const val SETTINGS_SEARCH_TOOLS = "settings/search-tools"
    const val SETTINGS_PROVIDERS = "settings/providers"
    const val SETTINGS_UPDATES = "settings/updates"
    const val SETTINGS_SCREEN_TRANSLATION = "settings/screen-translation"
    const val SETTINGS_PROVIDER_DETAIL = "settings/providers/{providerId}"
    const val SETTINGS_ASSISTANTS = "settings/assistants"
    const val SETTINGS_ASSISTANT_DETAIL = "settings/assistants/{assistantId}"
    const val SETTINGS_ASSISTANT_BASIC = "settings/assistants/{assistantId}/basic"
    const val SETTINGS_ASSISTANT_PROMPT = "settings/assistants/{assistantId}/prompt"
    const val SETTINGS_ASSISTANT_EXTENSIONS = "settings/assistants/{assistantId}/extensions"
    const val SETTINGS_ASSISTANT_MEMORY = "settings/assistants/{assistantId}/memory"
    const val SETTINGS_ASSISTANT_MEMORY_SIMPLE = "settings/assistants/{assistantId}/memory/simple?conversationId={conversationId}"
    const val SETTINGS_WORLD_BOOKS = "settings/worldbook"
    const val SETTINGS_WORLD_BOOK_BOOK = "settings/worldbook/book/{bookId}"
    const val SETTINGS_WORLD_BOOK_EDIT = "settings/worldbook/{entryId}?bookName={bookName}"
    const val SETTINGS_MEMORY = "settings/memory"
    const val SETTINGS_CONTEXT_TRANSFER = "settings/context-transfer"
    const val SETTINGS_CONTEXT_LOG = "settings/context-log"
    const val CHAT = "chat"
    const val TRANSLATOR = "translator"
    const val ROLEPLAY_GRAPH = "roleplay_graph"
    const val ROLEPLAY = "roleplay"
    const val ROLEPLAY_MANAGE = "roleplay/manage"
    const val ROLEPLAY_EDIT = "roleplay/edit/{scenarioId}"
    const val ROLEPLAY_PLAY = "roleplay/play/{scenarioId}"
    const val ROLEPLAY_SETTINGS = "roleplay/play/{scenarioId}/settings"
    const val ROLEPLAY_READING = "roleplay/play/{scenarioId}/reading"
    const val ROLEPLAY_DIARY = "roleplay/play/{scenarioId}/diary"
    const val ROLEPLAY_DIARY_DETAIL = "roleplay/play/{scenarioId}/diary/{entryId}"
    const val ROLEPLAY_VIDEO_CALL = "roleplay/play/{scenarioId}/video-call"
    const val PHONE_CHECK = "phone-check/{conversationId}?scenarioId={scenarioId}&ownerType={ownerType}"
    const val MOMENTS = "moments/{conversationId}?scenarioId={scenarioId}&ownerType={ownerType}"

    fun settingsProviderDetail(providerId: String): String {
        return "settings/providers/$providerId"
    }

    fun settingsAssistantDetail(assistantId: String): String {
        return "settings/assistants/$assistantId"
    }

    fun settingsAssistantBasic(assistantId: String): String {
        return "settings/assistants/$assistantId/basic"
    }

    fun settingsAssistantPrompt(assistantId: String): String {
        return "settings/assistants/$assistantId/prompt"
    }

    fun settingsAssistantExtensions(assistantId: String): String {
        return "settings/assistants/$assistantId/extensions"
    }

    fun settingsAssistantMemory(assistantId: String): String {
        return "settings/assistants/$assistantId/memory"
    }

    fun settingsAssistantMemorySimple(assistantId: String, conversationId: String? = null): String {
        val base = "settings/assistants/$assistantId/memory/simple"
        val trimmed = conversationId?.trim().orEmpty()
        return if (trimmed.isEmpty()) base else "$base?conversationId=${Uri.encode(trimmed)}"
    }

    fun settingsWorldBookEdit(
        entryId: String,
        bookName: String? = null,
    ): String {
        val encodedBookName = Uri.encode(bookName.orEmpty())
        return "settings/worldbook/$entryId?bookName=$encodedBookName"
    }

    fun settingsWorldBookBook(bookId: String): String {
        return "settings/worldbook/book/${Uri.encode(bookId)}"
    }

    fun roleplayEdit(scenarioId: String): String {
        return "roleplay/edit/${Uri.encode(scenarioId)}"
    }

    fun roleplayPlay(scenarioId: String): String {
        return "roleplay/play/${Uri.encode(scenarioId)}"
    }

    fun roleplaySettings(scenarioId: String): String {
        return "roleplay/play/${Uri.encode(scenarioId)}/settings"
    }

    fun roleplayReading(scenarioId: String): String {
        return "roleplay/play/${Uri.encode(scenarioId)}/reading"
    }

    fun roleplayDiary(scenarioId: String): String {
        return "roleplay/play/${Uri.encode(scenarioId)}/diary"
    }

    fun roleplayDiaryDetail(scenarioId: String, entryId: String): String {
        return "roleplay/play/${Uri.encode(scenarioId)}/diary/${Uri.encode(entryId)}"
    }

    fun roleplayVideoCall(scenarioId: String): String {
        return "roleplay/play/${Uri.encode(scenarioId)}/video-call"
    }

    fun phoneCheck(
        conversationId: String,
        scenarioId: String? = null,
        ownerType: com.example.myapplication.model.PhoneSnapshotOwnerType = com.example.myapplication.model.PhoneSnapshotOwnerType.CHARACTER,
    ): String {
        val encodedConversationId = Uri.encode(conversationId)
        val encodedScenarioId = Uri.encode(scenarioId.orEmpty())
        return "phone-check/$encodedConversationId?scenarioId=$encodedScenarioId&ownerType=${ownerType.storageValue}"
    }

    fun moments(
        conversationId: String,
        scenarioId: String? = null,
        ownerType: com.example.myapplication.model.PhoneSnapshotOwnerType = com.example.myapplication.model.PhoneSnapshotOwnerType.CHARACTER,
    ): String {
        val encodedConversationId = Uri.encode(conversationId)
        val encodedScenarioId = Uri.encode(scenarioId.orEmpty())
        return "moments/$encodedConversationId?scenarioId=$encodedScenarioId&ownerType=${ownerType.storageValue}"
    }
}
