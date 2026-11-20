package com.example.myapplication.ui.navigation

import android.net.Uri

object AppRoutes {
    const val HOME = "home"
    const val SETTINGS_GRAPH = "settings_graph"
    const val SETTINGS = "settings"
    const val SETTINGS_MODEL = "settings/model"
    const val SETTINGS_VOICE_SYNTHESIS = "settings/voice-synthesis"
    const val SETTINGS_VOICE_RECOGNITION = "settings/voice-recognition"
    const val SETTINGS_VOICE_RECOGNITION_API = "settings/voice-recognition/api"
    const val SETTINGS_PRESETS = "settings/presets"
    const val SETTINGS_PRESET_EDIT = "settings/presets/{presetId}"
    const val SETTINGS_PROVIDERS = "settings/providers"
    const val SETTINGS_UPDATES = "settings/updates"
    const val SETTINGS_COMPLIANCE = "settings/compliance"
    const val SETTINGS_USER_MASKS = "settings/user-masks"
    const val SETTINGS_ROLEPLAY_SCRIPTS = "settings/roleplay-scripts"
    const val SETTINGS_PROVIDER_DETAIL = "settings/providers/{providerId}"
    const val SETTINGS_ASSISTANTS = "settings/assistants"
    const val SETTINGS_ASSISTANT_DETAIL = "settings/assistants/{assistantId}"
    const val SETTINGS_ASSISTANT_BASIC = "settings/assistants/{assistantId}/basic"
    const val SETTINGS_ASSISTANT_ART_STUDIO = "settings/assistants/{assistantId}/art-studio"
    const val SETTINGS_ASSISTANT_PROMPT = "settings/assistants/{assistantId}/prompt"
    const val SETTINGS_ASSISTANT_EXTENSIONS = "settings/assistants/{assistantId}/extensions"
    const val SETTINGS_ASSISTANT_MEMORY =
        "settings/assistants/{assistantId}/memory?conversationId={conversationId}"
    const val SETTINGS_ASSISTANT_MEMORY_SIMPLE =
        "settings/assistants/{assistantId}/memory/simple?conversationId={conversationId}"
    const val SETTINGS_MEMORY_ORIGINAL =
        "settings/assistants/{assistantId}/memory/archive/{spaceId}/read/{conversationId}?personaKey={personaKey}"
    const val SETTINGS_WORLD_BOOKS = "settings/worldbook"
    const val SETTINGS_WORLD_BOOK_BOOK = "settings/worldbook/book/{bookId}"
    const val SETTINGS_WORLD_BOOK_EDIT = "settings/worldbook/{entryId}?bookName={bookName}"
    const val SETTINGS_MEMORY = "settings/memory"
    const val SETTINGS_CONTEXT_TRANSFER = "settings/context-transfer"
    const val SETTINGS_CONTEXT_LOG = "settings/context-log"
    const val SETTINGS_DIAGNOSTICS = "settings/diagnostics"
    const val CHAT = "chat"
    const val TRANSLATOR = "translator"
    const val ROLEPLAY_GRAPH = "roleplay_graph"
    const val ROLEPLAY = "roleplay"
    const val ROLEPLAY_MANAGE = "roleplay/manage"
    const val ROLEPLAY_EDIT = "roleplay/edit/{scenarioId}"
    const val ROLEPLAY_PLAY = "roleplay/play/{scenarioId}"
    const val ROLEPLAY_COMPANION = "roleplay/companion/{scenarioId}/{companionEntryId}"
    const val ROLEPLAY_COMPANION_SETTINGS =
        "roleplay/play/{scenarioId}/companion-settings/{assistantId}"
    const val ROLEPLAY_SETTINGS = "roleplay/play/{scenarioId}/settings"
    const val ROLEPLAY_READING = "roleplay/play/{scenarioId}/reading"
    const val ROLEPLAY_COMPANION_READING =
        "roleplay/play/{scenarioId}/companion-reading/{assistantId}?messageId={messageId}&archiveSpaceId={archiveSpaceId}&archiveRunId={archiveRunId}"
    const val ROLEPLAY_COMPANION_HISTORY = "roleplay/play/{scenarioId}/companion-history/{assistantId}"
    const val ROLEPLAY_COMPANION_APPEARANCE_TRANSFER =
        "roleplay/play/{scenarioId}/companion-appearance-transfer/{assistantId}"
    const val ROLEPLAY_COMPANION_VISUALS = "roleplay/play/{scenarioId}/companion-visuals/{assistantId}"
    const val ROLEPLAY_COMPANION_VOICE = "roleplay/companion-voice/{assistantId}"
    const val ROLEPLAY_DIARY = "roleplay/play/{scenarioId}/diary"
    const val ROLEPLAY_DIARY_DETAIL = "roleplay/play/{scenarioId}/diary/{entryId}"
    const val ROLEPLAY_VIDEO_CALL = "roleplay/play/{scenarioId}/video-call"
    const val ROLEPLAY_MAILBOX = "roleplay/play/{scenarioId}/mailbox"
    const val ROLEPLAY_WALLET = "roleplay/play/{scenarioId}/wallet"
    const val PHONE_CHECK =
        "phone-check/{conversationId}?scenarioId={scenarioId}&ownerType={ownerType}"
    const val MOMENTS_GLOBAL = "moments"
    const val MOMENTS = "moments/{conversationId}?scenarioId={scenarioId}&ownerType={ownerType}"

    fun settingsProviderDetail(providerId: String): String {
        return "settings/providers/$providerId"
    }

    fun settingsPresetEdit(presetId: String): String {
        return "settings/presets/${Uri.encode(presetId)}"
    }

    fun settingsAssistantDetail(assistantId: String): String {
        return "settings/assistants/$assistantId"
    }

    fun settingsAssistantBasic(assistantId: String): String {
        return "settings/assistants/${Uri.encode(assistantId)}/basic"
    }

    fun settingsAssistantArtStudio(assistantId: String): String {
        return "settings/assistants/${Uri.encode(assistantId)}/art-studio"
    }

    fun settingsAssistantPrompt(assistantId: String): String {
        return "settings/assistants/${Uri.encode(assistantId)}/prompt"
    }

    fun settingsAssistantExtensions(assistantId: String): String {
        return "settings/assistants/$assistantId/extensions"
    }

    fun settingsAssistantMemory(assistantId: String, conversationId: String? = null): String {
        val base = "settings/assistants/${Uri.encode(assistantId)}/memory"
        return conversationId?.takeIf { it.isNotBlank() }
            ?.let { "$base?conversationId=${Uri.encode(it)}" } ?: base
    }

    fun settingsAssistantMemorySimple(assistantId: String, conversationId: String? = null): String {
        val base = "settings/assistants/${Uri.encode(assistantId)}/memory/simple"
        val trimmed = conversationId?.trim().orEmpty()
        return if (trimmed.isEmpty()) base else "$base?conversationId=${Uri.encode(trimmed)}"
    }

    fun settingsMemoryOriginal(
        assistantId: String,
        spaceId: String,
        conversationId: String,
        personaKey: String
    ): String =
        "settings/assistants/${Uri.encode(assistantId)}/memory/archive/${Uri.encode(spaceId)}/read/${
            Uri.encode(
                conversationId
            )
        }?personaKey=${Uri.encode(personaKey)}"

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

    fun roleplayCompanion(
        scenarioId: String,
        entryId: String = java.util.UUID.randomUUID().toString()
    ): String =
        "roleplay/companion/${Uri.encode(scenarioId)}/${Uri.encode(entryId)}"

    fun roleplaySettings(scenarioId: String): String {
        return "roleplay/play/${Uri.encode(scenarioId)}/settings"
    }

    fun roleplayCompanionSettings(scenarioId: String, assistantId: String): String =
        "roleplay/play/${Uri.encode(scenarioId)}/companion-settings/${Uri.encode(assistantId)}"

    @JvmOverloads
    fun roleplayCompanionReading(
        scenarioId: String,
        assistantId: String,
        messageId: String? = null,
        archiveSpaceId: String? = null,
        archiveRunId: String? = null,
    ): String {
        val base = "roleplay/play/${Uri.encode(scenarioId)}/companion-reading/${Uri.encode(assistantId)}"
        val query = listOf(
            "messageId" to messageId,
            "archiveSpaceId" to archiveSpaceId,
            "archiveRunId" to archiveRunId,
        ).mapNotNull { (name, value) -> value?.let { "$name=${Uri.encode(it)}" } }
        return if (query.isEmpty()) base else "$base?${query.joinToString("&")}"
    }

    fun roleplayCompanionHistory(scenarioId: String, assistantId: String): String =
        "roleplay/play/${Uri.encode(scenarioId)}/companion-history/${Uri.encode(assistantId)}"

    fun roleplayCompanionAppearanceTransfer(scenarioId: String, assistantId: String): String =
        "roleplay/play/${Uri.encode(scenarioId)}/companion-appearance-transfer/${Uri.encode(assistantId)}"

    fun roleplayCompanionVisuals(scenarioId: String, assistantId: String): String =
        "roleplay/play/${Uri.encode(scenarioId)}/companion-visuals/${Uri.encode(assistantId)}"

    fun roleplayCompanionVoice(assistantId: String): String =
        "roleplay/companion-voice/${Uri.encode(assistantId)}"

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

    fun roleplayMailbox(scenarioId: String): String {
        return "roleplay/play/${Uri.encode(scenarioId)}/mailbox"
    }

    fun roleplayWallet(scenarioId: String): String {
        return "roleplay/play/${Uri.encode(scenarioId)}/wallet"
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

    fun moments(): String {
        return MOMENTS_GLOBAL
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
