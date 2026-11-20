package com.example.myapplication.model

import java.io.Serializable

enum class CompanionDisplayMode(val label: String) { LIST("消息列表"), STAGE("立绘演出") }
enum class CompanionImageKind(val label: String) {
    PORTRAIT("角色立绘"), BACKGROUND("场景背景"), DECOR(
        "装饰图片"
    )
}

enum class CompanionPortraitPosition(val label: String) { LEFT("靠左"), CENTER("居中"), RIGHT("靠右") }

/** ID 是配置引用；图片始终留在私有文件中，不将二进制复制到回复或书签。 */
data class CompanionImageResource(
    val id: String,
    val name: String,
    val path: String,
    val kind: CompanionImageKind,
    val width: Int = 0,
    val height: Int = 0,
) : Serializable

data class CompanionOutfit(
    val id: String,
    val name: String,
    val defaultPortraitId: String? = null,
    val expressions: Map<String, String> = emptyMap(),
) : Serializable

data class CompanionStageLayout(
    val portraitPosition: CompanionPortraitPosition = CompanionPortraitPosition.CENTER,
    val portraitWidth: Float = 0.72f,
    val portraitHeight: Float = 0.45f,
) : Serializable {
    fun normalized() = copy(
        portraitWidth = portraitWidth.takeIf { it.isFinite() }?.coerceIn(0.3f, 1f) ?: 0.72f,
        portraitHeight = portraitHeight.takeIf { it.isFinite() }?.coerceIn(0.15f, 0.65f) ?: 0.45f,
    )
}

/** 存在此项即为用户明确的本场选择；空资源表示本场沿用旧场景图。 */
data class CompanionSceneVisualOverride(
    val selectedOutfitId: String? = null,
    val selectedBackgroundId: String? = null,
    val layout: CompanionStageLayout = CompanionStageLayout(),
) : Serializable

data class CompanionVisualSettings(
    val schemaVersion: Int = 1,
    /** null 表示旧用户尚未主动选择，原列表继续作为默认。 */
    val preferredMode: CompanionDisplayMode? = null,
    val resources: List<CompanionImageResource> = emptyList(),
    val outfits: List<CompanionOutfit> = emptyList(),
    val selectedOutfitId: String? = null,
    val selectedBackgroundId: String? = null,
    val decorationIds: List<String> = emptyList(),
    val layout: CompanionStageLayout = CompanionStageLayout(),
    val sceneOverrides: Map<String, CompanionSceneVisualOverride> = emptyMap(),
) : Serializable {
    val displayMode: CompanionDisplayMode get() = preferredMode ?: CompanionDisplayMode.LIST

    fun forScene(scenarioId: String): CompanionVisualSettings = sceneOverrides[scenarioId]?.let {
        copy(
            selectedOutfitId = it.selectedOutfitId,
            selectedBackgroundId = it.selectedBackgroundId,
            layout = it.layout
        )
    } ?: this
}

object CompanionVisualValidation {
    const val MAX_RESOURCES = 100
    const val MAX_OUTFITS = 20
    const val MAX_NAME = 60
    const val MAX_EXPRESSIONS = 40
    const val MAX_SCENE_OVERRIDES = 40

    fun error(visuals: CompanionVisualSettings): String? {
        if (visuals.schemaVersion != 1) return "此演出设置版本暂不支持编辑"
        if (visuals.resources.size > MAX_RESOURCES) return "最多保存 $MAX_RESOURCES 张演出图片"
        if (visuals.outfits.size > MAX_OUTFITS) return "最多保存 $MAX_OUTFITS 套角色图集"
        if (visuals.resources.any { it.id.isBlank() || it.id.length > 80 || it.path.isBlank() || it.path.length > 4096 })
            return "图片引用无效，请重新导入"
        if (visuals.resources.map { it.id }
                .distinct().size != visuals.resources.size) return "图片标识不能重复"
        if (visuals.outfits.map { it.id }.distinct().size != visuals.outfits.size ||
            visuals.outfits.any { it.id.isBlank() || it.id.length > 80 }
        ) return "套装标识无效"
        if ((visuals.resources.map { it.name } + visuals.outfits.map { it.name })
                .any { it.isBlank() || it.trim().length > MAX_NAME }
        ) return "请填写不超过 $MAX_NAME 字的名称"
        val images = visuals.resources.associateBy { it.id }
        if (visuals.outfits.any { outfit ->
                outfit.expressions.size > MAX_EXPRESSIONS ||
                        outfit.expressions.keys.any { CompanionExpressionKeys.normalize(it) != it } ||
                        (outfit.expressions.values + listOfNotNull(outfit.defaultPortraitId))
                            .any { images[it]?.kind != CompanionImageKind.PORTRAIT }
            }) return "套装表情只能使用角色立绘，表情键请用英文小写、数字、下划线或短横线"
        if (visuals.selectedOutfitId != null && visuals.outfits.none { it.id == visuals.selectedOutfitId })
            return "所选套装不存在"
        if (visuals.selectedBackgroundId != null && images[visuals.selectedBackgroundId]?.kind != CompanionImageKind.BACKGROUND)
            return "背景只能使用场景背景图片"
        if (visuals.decorationIds.any { images[it]?.kind != CompanionImageKind.DECOR }) return "装饰引用无效"
        if (visuals.layout != visuals.layout.normalized()) return "立绘布局超出可用范围"
        if (visuals.sceneOverrides.size > MAX_SCENE_OVERRIDES || visuals.sceneOverrides.keys.any { it.isBlank() || it.length > 200 })
            return "本场外观选择过多或标识无效"
        if (visuals.sceneOverrides.values.any { choice ->
                (choice.selectedOutfitId != null && visuals.outfits.none { it.id == choice.selectedOutfitId }) ||
                        (choice.selectedBackgroundId != null && images[choice.selectedBackgroundId]?.kind != CompanionImageKind.BACKGROUND) ||
                        choice.layout != choice.layout.normalized()
            }) return "本场外观的套装、背景或布局已失效"
        return null
    }
}

data class CompanionResolvedImage(
    val resourceId: String?,
    val uri: String = "",
    val url: String = "",
    val kind: CompanionImageKind = CompanionImageKind.PORTRAIT,
)

/** 有序候选也保留运行时解码失败的回退；网络旧图失败不会压住头像。 */
data class CompanionResolvedResources(
    val defaultPortraits: List<CompanionResolvedImage> = emptyList(),
    val expressionPortraits: Map<String, List<CompanionResolvedImage>> = emptyMap(),
    val backgrounds: List<CompanionResolvedImage> = emptyList(),
    val decorations: List<CompanionResolvedImage> = emptyList(),
) {
    fun portraits(spriteKey: String?): List<CompanionResolvedImage> =
        CompanionExpressionKeys.normalize(spriteKey)?.let(expressionPortraits::get)
            ?: defaultPortraits
}
