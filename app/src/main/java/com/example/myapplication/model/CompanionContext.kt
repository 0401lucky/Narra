package com.example.myapplication.model

/** 返回媒介只更新原运行；角色、身份和运行不由导航重新猜测。 */
data class CompanionEntryOrigin(
    val scenarioId: String,
    val conversationId: String,
    val runId: String,
    val characterId: String,
    val personaKey: String,
    val interactionMode: RoleplayInteractionMode,
    val longformModeEnabled: Boolean = false,
    val enableRoleplayProtocol: Boolean = true,
)

data class CompanionSessionSelection(
    val encounter: CompanionEncounter,
    val entryId: String?,
    val origin: CompanionEntryOrigin?,
)

data class CompanionHistoryCursor(val createdAt: Long, val messageId: String) :
    Comparable<CompanionHistoryCursor> {
    override fun compareTo(other: CompanionHistoryCursor): Int =
        compareValuesBy(
            this,
            other,
            CompanionHistoryCursor::createdAt,
            CompanionHistoryCursor::messageId
        )
}

data class CompanionHistoryBinding(
    val conversationId: String,
    val scenarioId: String,
    val runId: String,
    val personaKey: String,
    val purpose: MemoryPurpose,
)

/** 请求授权与阅读窗口分开。空 messages 就是空授权，不能从页面或全库补回。 */
data class AllowedCompanionHistoryScope(
    val context: MemoryRuntimeContext,
    val bindings: List<CompanionHistoryBinding>,
    val messages: List<CompanionMessageRevision>,
    val cutoff: CompanionHistoryCursor,
    val startsAfter: Map<String, CompanionHistoryCursor> = emptyMap(),
    val excludedMessageIds: Set<String> = emptySet(),
    val maxMessages: Int = 8,
)

data class CompanionHistorySelection(
    val maxMessages: Int = 8,
    val allowedConversationIds: Set<String>? = null,
    val startsAfter: Map<String, CompanionHistoryCursor> = emptyMap(),
    val excludedMessageIds: Set<String> = emptySet(),
)

data class CompanionPromptContext(
    val scope: AllowedCompanionHistoryScope,
    val enableTimeAwareness: Boolean,
)

data class CompanionTimePolicy(val enabled: Boolean, val zoneId: String, val requestTime: Long)

/** 仅随本次调用传入网关；不序列化闭包，也不保存凭据。 */
class CompanionRequestGuard(val validate: suspend () -> Boolean)

data class CompanionCharacterSettings(
    val schemaVersion: Int = 1,
    val revision: Long = 0,
    val zoneId: String = "",
    val writing: CompanionWritingSettings = CompanionWritingSettings(),
    val visuals: CompanionVisualSettings = CompanionVisualSettings(),
    val voice: CompanionVoicePreferences = CompanionVoicePreferences(),
) : java.io.Serializable

data class CompanionSettingsPatch(
    val zoneId: String? = null,
    val writing: CompanionWritingSettings? = null,
    val visuals: CompanionVisualSettings? = null,
    val voice: CompanionVoicePreferences? = null,
)

sealed interface CompanionSettingsWriteResult {
    data class Saved(val settings: CompanionCharacterSettings) : CompanionSettingsWriteResult
    data object Conflict : CompanionSettingsWriteResult
    data object UnsupportedVersion : CompanionSettingsWriteResult
    data object CharacterMissing : CompanionSettingsWriteResult
}

fun AppSettings.companionSettings(assistantId: String): CompanionCharacterSettings =
    (companionCharacterSettings as Map<String, CompanionCharacterSettings>?)?.get(assistantId)
        ?: CompanionCharacterSettings()
