package com.example.myapplication.model

/** 模型能力标签，只标注有区分度的特殊能力。 */
enum class ModelAbility(val label: String) {
    /** 支持图片输入。 */
    VISION("视觉"),

    /** 支持将参考图作为生图输入。 */
    IMAGE_EDITING("改图"),

    /** 支持深度推理/思维链。 */
    REASONING("推理"),

    /** 支持函数/工具调用。 */
    TOOL("工具"),

    /** 支持图片生成。 */
    IMAGE_GENERATION("生图"),
}

/** 模型信息，包含 ID、显示名和能力集合。 */
data class ModelInfo(
    val modelId: String,
    val displayName: String = modelId,
    val abilities: Set<ModelAbility> = emptySet(),
    val abilitiesCustomized: Boolean = false,
)

/**
 * 根据模型 ID 结合本地 registry 推断能力集合。
 */
fun inferModelAbilities(modelId: String): Set<ModelAbility> {
    return ModelCapabilityRegistry.inferAbilities(modelId)
}

fun inferredModelInfo(
    modelId: String,
    displayName: String = modelId,
): ModelInfo {
    return ModelInfo(
        modelId = modelId,
        displayName = displayName,
        abilities = inferModelAbilities(modelId),
    )
}

fun ModelInfo.resolvedAbilities(): Set<ModelAbility> {
    return if (abilitiesCustomized) {
        abilities
    } else {
        inferModelAbilities(modelId)
    }
}

fun ModelInfo.withResolvedAbilities(): ModelInfo {
    return copy(abilities = resolvedAbilities())
}

fun ModelInfo.withAbilityOverride(overrideAbilities: Set<ModelAbility>?): ModelInfo {
    return if (overrideAbilities == null) {
        inferredModelInfo(
            modelId = modelId,
            displayName = displayName,
        )
    } else {
        copy(
            abilities = overrideAbilities,
            abilitiesCustomized = true,
        )
    }
}

/** 判断模型是否为图片生成模型。 */
fun isImageGenerationModel(modelId: String): Boolean {
    return ModelAbility.IMAGE_GENERATION in inferModelAbilities(modelId)
}

fun mergeModelInfosPreservingOverrides(
    fetchedModels: List<ModelInfo>,
    previousModels: List<ModelInfo>,
): List<ModelInfo> {
    if (previousModels.isEmpty()) {
        return fetchedModels
    }

    val previousById = previousModels.associateBy(ModelInfo::modelId)
    return fetchedModels.map { fetched ->
        val previous = previousById[fetched.modelId]
        if (previous?.abilitiesCustomized == true) {
            fetched.copy(
                abilities = previous.abilities,
                abilitiesCustomized = true,
            )
        } else {
            fetched
        }
    }
}
