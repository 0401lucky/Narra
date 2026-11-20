package com.example.myapplication.model

/** 公开包只包含此白名单；不持有角色、人设、原文、声音样本或文件路径。 */
data class CompanionAppearancePackage(
    val version: Int = 1,
    val visuals: CompanionAppearanceData = CompanionAppearanceData(),
    val images: List<CompanionTransferImage> = emptyList(),
)

data class CompanionAppearanceData(
    val preferredMode: CompanionDisplayMode? = null,
    val resources: List<CompanionAppearanceResource> = emptyList(),
    val outfits: List<CompanionOutfit> = emptyList(),
    val selectedOutfitId: String? = null,
    val selectedBackgroundId: String? = null,
    val decorationIds: List<String> = emptyList(),
    val layout: CompanionStageLayout = CompanionStageLayout(),
)

data class CompanionAppearanceResource(
    val id: String,
    val name: String,
    val contentHash: String,
    val kind: CompanionImageKind,
)

/** 内容为去掉附属元数据的有限 PNG；hash 针对实际导出字节。 */
data class CompanionTransferImage(
    val sha256: String,
    val mimeType: String = "image/png",
    val width: Int,
    val height: Int,
    val dataBase64: String,
)

/** 私人资料只扩已有角色偏好域，不含运行、聊天、音频或收藏备份。null 域表示保留目标。 */
data class CompanionContextTransferEntry(
    val assistantId: String,
    val schemaVersion: Int = 1,
    val zoneId: String? = null,
    val writing: CompanionWritingSettings? = null,
    val voice: CompanionVoicePreferences? = null,
    val appearance: CompanionAppearancePackage? = null,
)

enum class ContextTransferOperationKind(val label: String) {
    PRIVATE_LIBRARY("私人资料"), PUBLIC_APPEARANCE("公开装扮")
}

enum class ContextTransferStage(val label: String) {
    PLANNED("待导入"), ROOM_COMMITTED("资料已保存"), FILES_COMMITTED("素材已保存"),
    COMPLETE("导入完成"), CANCELLED("已取消")
}

data class ContextTransferOperationSummary(
    val operationId: String,
    val kind: ContextTransferOperationKind,
    val stage: ContextTransferStage,
    val title: String,
    val completedSettingsTargets: Int = 0,
    val settingsTargetCount: Int = 0,
    val vectorsNeedingRebuild: Int = 0,
    val targetAssistantIds: Set<String> = emptySet(),
) {
    val canResume: Boolean get() = stage != ContextTransferStage.COMPLETE && stage != ContextTransferStage.CANCELLED
}

data class CompanionAppearanceImportPreview(
    val operationId: String,
    val targetName: String,
    val outfitCount: Int,
    val imageCount: Int,
)

/** 单目标写入及回执在同一次 DataStore edit 内完成。 */
data class ContextTransferSettingsTarget(
    val assistantId: String,
    val expectedAssistant: Assistant?,
    val importedAssistant: Assistant? = null,
    val expectedCompanionRevision: Long = 0,
    val companionPatch: CompanionSettingsPatch? = null,
)

sealed interface ContextTransferSettingsResult {
    data object Applied : ContextTransferSettingsResult
    data object AlreadyApplied : ContextTransferSettingsResult
    data object Conflict : ContextTransferSettingsResult
    data object UnsupportedVersion : ContextTransferSettingsResult
}

data class ContextTransferPlanFile(val sha256: String, val created: Boolean = true)

/** 既有 Tavern 图片角色的头像输入，只用于本机冻结计划，不是新的资料备份域。 */
data class ContextTransferAvatarInput(val assistantId: String, val bytes: ByteArray)

data class CompanionPublishedTransferImage(
    val sha256: String,
    val path: String,
    val width: Int,
    val height: Int,
)
