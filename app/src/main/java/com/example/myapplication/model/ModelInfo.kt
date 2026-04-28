package com.example.myapplication.model

import java.util.Locale

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

fun List<ModelInfo>.sortedForModelListDisplay(): List<ModelInfo> {
    if (size <= 1) {
        return this
    }
    val familyFirstIndex = linkedMapOf<String, Int>()
    forEachIndexed { index, model ->
        familyFirstIndex.putIfAbsent(model.modelFamilySortKey(), index)
    }
    return withIndex()
        .sortedWith { left, right ->
            val leftFamily = left.value.modelFamilySortKey()
            val rightFamily = right.value.modelFamilySortKey()
            val familyCompare = familyFirstIndex.getValue(leftFamily)
                .compareTo(familyFirstIndex.getValue(rightFamily))
            if (familyCompare != 0) {
                familyCompare
            } else {
                val naturalCompare = compareModelNamesNaturally(
                    left.value.modelSortName(),
                    right.value.modelSortName(),
                )
                if (naturalCompare != 0) {
                    naturalCompare
                } else {
                    left.index.compareTo(right.index)
                }
            }
        }
        .map { it.value }
}

private fun ModelInfo.modelSortName(): String {
    return modelId.trim()
        .substringAfterLast('/')
        .lowercase(Locale.ROOT)
}

private fun ModelInfo.modelFamilySortKey(): String {
    val normalized = modelId.trim().lowercase(Locale.ROOT)
    return when {
        "deepseek" in normalized -> "deepseek"
        "qwen" in normalized || QWQ_MODEL_PATTERN.containsMatchIn(normalized) -> "qwen"
        "claude" in normalized -> "claude"
        "gemini" in normalized -> "gemini"
        "gpt" in normalized || OPENAI_REASONING_MODEL_PATTERN.containsMatchIn(normalized) -> "openai"
        "grok" in normalized -> "grok"
        "glm" in normalized -> "glm"
        "kimi" in normalized || "moonshot" in normalized -> "kimi"
        "llama" in normalized -> "llama"
        "mistral" in normalized -> "mistral"
        else -> normalized
            .substringAfterLast('/')
            .substringBefore(':')
            .substringBefore('-')
            .substringBefore('_')
            .substringBefore('.')
            .ifBlank { normalized }
    }
}

private fun compareModelNamesNaturally(left: String, right: String): Int {
    val leftTokens = MODEL_SORT_TOKEN.findAll(left).map { it.value }.toList()
    val rightTokens = MODEL_SORT_TOKEN.findAll(right).map { it.value }.toList()
    val maxSize = minOf(leftTokens.size, rightTokens.size)
    for (index in 0 until maxSize) {
        val leftToken = leftTokens[index]
        val rightToken = rightTokens[index]
        val compare = if (leftToken.firstOrNull()?.isDigit() == true &&
            rightToken.firstOrNull()?.isDigit() == true
        ) {
            compareNumericText(leftToken, rightToken)
        } else {
            leftToken.compareTo(rightToken)
        }
        if (compare != 0) {
            return compare
        }
    }
    return leftTokens.size.compareTo(rightTokens.size)
}

private fun compareNumericText(left: String, right: String): Int {
    val normalizedLeft = left.trimStart('0').ifBlank { "0" }
    val normalizedRight = right.trimStart('0').ifBlank { "0" }
    val lengthCompare = normalizedLeft.length.compareTo(normalizedRight.length)
    if (lengthCompare != 0) {
        return lengthCompare
    }
    val valueCompare = normalizedLeft.compareTo(normalizedRight)
    if (valueCompare != 0) {
        return valueCompare
    }
    return left.length.compareTo(right.length)
}

private val MODEL_SORT_TOKEN = Regex("\\d+|\\D+")
private val QWQ_MODEL_PATTERN = Regex("(^|[/_.:-])qwq($|[/_.:-])")
private val OPENAI_REASONING_MODEL_PATTERN = Regex("(^|[/_.:-])o[1-9]($|[/_.:-])")
