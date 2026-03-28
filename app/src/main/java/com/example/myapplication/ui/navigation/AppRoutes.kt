package com.example.myapplication.ui.navigation

import android.net.Uri

object AppRoutes {
    const val HOME = "home"
    const val SETTINGS_GRAPH = "settings_graph"
    const val SETTINGS = "settings"
    const val SETTINGS_CONNECTION = "settings/connection"
    const val SETTINGS_MODEL = "settings/model"
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
    const val SETTINGS_WORLD_BOOKS = "settings/worldbook"
    const val SETTINGS_WORLD_BOOK_BOOK = "settings/worldbook/book/{bookName}"
    const val SETTINGS_WORLD_BOOK_EDIT = "settings/worldbook/{entryId}?bookName={bookName}"
    const val SETTINGS_MEMORY = "settings/memory"
    const val SETTINGS_CONTEXT_TRANSFER = "settings/context-transfer"
    const val CHAT = "chat"
    const val TRANSLATOR = "translator"
    const val ROLEPLAY_GRAPH = "roleplay_graph"
    const val ROLEPLAY = "roleplay"
    const val ROLEPLAY_EDIT = "roleplay/edit/{scenarioId}"
    const val ROLEPLAY_PLAY = "roleplay/play/{scenarioId}"
    const val ROLEPLAY_SETTINGS = "roleplay/play/{scenarioId}/settings"
    const val ROLEPLAY_READING = "roleplay/play/{scenarioId}/reading"

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

    fun settingsWorldBookEdit(
        entryId: String,
        bookName: String? = null,
    ): String {
        val encodedBookName = Uri.encode(bookName.orEmpty())
        return "settings/worldbook/$entryId?bookName=$encodedBookName"
    }

    fun settingsWorldBookBook(bookName: String): String {
        return "settings/worldbook/book/${Uri.encode(bookName)}"
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
}
