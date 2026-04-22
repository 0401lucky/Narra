package com.example.myapplication.model

internal enum class ModelFeature {
    VISION,
    IMAGE_EDITING,
    REASONING,
    TOOL,
    OPENAI_REASONING_EFFORT,
    ANTHROPIC_THINKING,
    GROK_OPAQUE_REASONING,
    IMAGE_GENERATION,
}

internal object ModelCapabilityRegistry {
    private val rules = listOf(
        rule(
            features = setOf(
                ModelFeature.VISION,
                ModelFeature.REASONING,
                ModelFeature.TOOL,
                ModelFeature.OPENAI_REASONING_EFFORT,
            ),
            """(?:^|[^a-z0-9])o(?:1|3|4)(?:$|[-_.])""",
        ),
        rule(
            features = setOf(
                ModelFeature.VISION,
                ModelFeature.REASONING,
                ModelFeature.TOOL,
                ModelFeature.OPENAI_REASONING_EFFORT,
            ),
            """\bgpt[-_.]?5(?:$|[-_.])""",
            excludes = listOf("""\bgpt[-_.]?5[-_.]?chat\b"""),
        ),
        rule(
            features = setOf(
                ModelFeature.VISION,
                ModelFeature.TOOL,
            ),
            """\bgpt[-_.]?4(?:$|[-_.])""",
            """\bgpt[-_.]?4o(?:$|[-_.])""",
        ),
        rule(
            features = setOf(
                ModelFeature.REASONING,
                ModelFeature.TOOL,
            ),
            """\bgpt[-_.]?oss(?:$|[-_.])""",
        ),
        rule(
            features = setOf(ModelFeature.TOOL),
            """\bgpt(?:$|[-_.])""",
        ),
        rule(
            features = setOf(
                ModelFeature.VISION,
                ModelFeature.TOOL,
            ),
            """\bclaude(?:[-_.](?:3|4|opus|sonnet|haiku))""",
        ),
        rule(
            features = setOf(
                ModelFeature.REASONING,
                ModelFeature.ANTHROPIC_THINKING,
            ),
            """\bclaude[-_.]?3[-_.]?7\b.*\bsonnet\b""",
            """\bclaude\b.*[-_.]4(?:$|[-_.])""",
        ),
        rule(
            features = setOf(
                ModelFeature.VISION,
                ModelFeature.TOOL,
            ),
            """\bgemini\b""",
        ),
        rule(
            features = setOf(
                ModelFeature.REASONING,
                ModelFeature.OPENAI_REASONING_EFFORT,
            ),
            """\bgemini[-_.]?(?:2[-_.]?5|3)(?:$|[-_.])""",
            """\bgemini[-_.](?:flash|pro)[-_.]latest\b""",
            """\bgemini\b.*(?:think|deep)""",
        ),
        rule(
            features = setOf(ModelFeature.TOOL),
            """\bdeepseek\b""",
        ),
        rule(
            features = setOf(ModelFeature.REASONING),
            """\bdeepseek\b.*(?:r[-_.]?1|reasoner|v[-_.]?3[-_.]?1|v[-_.]?3[-_.]?2)""",
        ),
        rule(
            features = setOf(ModelFeature.VISION),
            """\bdeepseek\b.*(?:vision|janus)""",
        ),
        rule(
            features = setOf(ModelFeature.TOOL),
            """\bqwen(?:$|[-_.]|\d)""",
            """\bqwq\b""",
            """\bqvq\b""",
        ),
        rule(
            features = setOf(ModelFeature.REASONING),
            """\bqwq\b""",
            """\bqvq\b""",
            """\bqwen[-_.]?3(?:[-_.]?5)?(?:$|[-_.])""",
        ),
        rule(
            features = setOf(ModelFeature.VISION),
            """\bqwen\b.*(?:vl|vision)""",
            """\bqwen[-_.]?(?:max|plus|turbo)\b""",
            """\bqwen[-_.]?3(?:$|[-_.])""",
            """\bqvq\b""",
        ),
        rule(
            features = setOf(ModelFeature.TOOL),
            """\bglm\b""",
        ),
        rule(
            features = setOf(ModelFeature.REASONING),
            """\bglm\b.*(?:4[-_.]?5|5)(?:$|[-_.])""",
        ),
        rule(
            features = setOf(ModelFeature.VISION),
            """\bglm[-_.]?4v\b""",
            """\bglm\b.*(?:vision|[-_.]v(?:$|[-_.]|\d))""",
        ),
        rule(
            features = setOf(ModelFeature.TOOL),
            """\bdoubao\b""",
        ),
        rule(
            features = setOf(
                ModelFeature.VISION,
                ModelFeature.REASONING,
            ),
            """\bdoubao\b.*(?:1[-_.]?6|1[-_.]?8)""",
        ),
        rule(
            features = setOf(ModelFeature.TOOL),
            """\bgrok\b""",
        ),
        rule(
            features = setOf(ModelFeature.REASONING),
            """\bgrok[-_.]?(?:3|4)(?:$|[-_.])""",
        ),
        rule(
            features = setOf(ModelFeature.VISION),
            """\bgrok[-_.]?4(?:$|[-_.])""",
        ),
        rule(
            features = setOf(ModelFeature.OPENAI_REASONING_EFFORT),
            """\bgrok[-_.]?3[-_.]?mini(?:$|[-_.])""",
        ),
        rule(
            features = setOf(ModelFeature.GROK_OPAQUE_REASONING),
            """\bgrok[-_.]?4(?:$|[-_.])""",
            """\bgrok[-_.]?3(?:$|[-_.])""",
            excludes = listOf("""mini"""),
        ),
        rule(
            features = setOf(ModelFeature.TOOL),
            """\b(?:moonshot|kimi)\b""",
        ),
        rule(
            features = setOf(ModelFeature.REASONING),
            """\bkimi\b.*k[-_.]?2(?:[-_.]?5)?""",
        ),
        rule(
            features = setOf(ModelFeature.VISION),
            """\bkimi\b.*k[-_.]?2[-_.]?5""",
        ),
        rule(
            features = setOf(ModelFeature.TOOL),
            """\bmistral\b""",
            """\bmixtral\b""",
            """\bmagistral\b""",
        ),
        rule(
            features = setOf(ModelFeature.REASONING),
            """\bmagistral\b""",
        ),
        rule(
            features = setOf(ModelFeature.TOOL),
            """\bllama[-_.]?(?:3|4)\b""",
            """\bminimax\b""",
            """\bstep[-_.]?3\b""",
            """\bintern[-_.]?s[-_.]?1\b""",
        ),
        rule(
            features = setOf(ModelFeature.REASONING),
            """\bminimax\b.*m[-_.]?2[-_.]?5""",
            """\bstep[-_.]?3\b""",
            """\bintern[-_.]?s[-_.]?1\b""",
        ),
        rule(
            features = setOf(ModelFeature.IMAGE_GENERATION),
            """\bgrok[-_.]?(?:2[-_.]?)?image\b""",
            """\bgrok[-_.]?imagine[-_.]?image""",
            """\bgrok[-_.]?imagine(?:[-_.]?\d+(?:\.\d+)*)?\b""",
            """\bdall[-_.]?e""",
            """\bgpt[-_.]?image""",
            """\bgpt(?:[-_.]?\d+(?:\.\d+)*)?[-_.]image\b""",
            """\bgemini\b.*\bimage(?:[-_.]preview)?\b""",
            """\bstable[-_.]?diffusion\b""",
            """\bsdxl\b""",
            """\bflux\b""",
            """\bmidjourney\b""",
        ),
        rule(
            features = setOf(ModelFeature.IMAGE_EDITING),
            """\bgpt[-_.]?image(?:$|[-_.]|\d)""",
            """\bgpt(?:[-_.]?\d+(?:\.\d+)*)?[-_.]image(?:$|[-_.]|\d)""",
        ),
    )

    fun inferAbilities(modelId: String): Set<ModelAbility> {
        val features = resolveFeatures(modelId)
        return buildSet {
            if (ModelFeature.VISION in features) add(ModelAbility.VISION)
            if (ModelFeature.IMAGE_EDITING in features) add(ModelAbility.IMAGE_EDITING)
            if (ModelFeature.REASONING in features) add(ModelAbility.REASONING)
            if (ModelFeature.TOOL in features) add(ModelAbility.TOOL)
            if (ModelFeature.IMAGE_GENERATION in features) add(ModelAbility.IMAGE_GENERATION)
        }
    }

    fun hasFeature(modelId: String, feature: ModelFeature): Boolean {
        return feature in resolveFeatures(modelId)
    }

    private fun resolveFeatures(modelId: String): Set<ModelFeature> {
        val normalizedId = modelId.trim().lowercase()
        if (normalizedId.isBlank()) {
            return emptySet()
        }

        return buildSet {
            rules
                .filter { it.matches(normalizedId) }
                .forEach { addAll(it.features) }
        }
    }
}

internal fun hasModelFeature(
    modelId: String,
    feature: ModelFeature,
): Boolean {
    return ModelCapabilityRegistry.hasFeature(modelId, feature)
}

private data class ModelFeatureRule(
    val features: Set<ModelFeature>,
    val patterns: List<Regex>,
    val excludes: List<Regex>,
) {
    fun matches(modelId: String): Boolean {
        return patterns.any { it.containsMatchIn(modelId) } &&
            excludes.none { it.containsMatchIn(modelId) }
    }
}

private fun rule(
    features: Set<ModelFeature>,
    vararg patterns: String,
    excludes: List<String> = emptyList(),
): ModelFeatureRule {
    return ModelFeatureRule(
        features = features,
        patterns = patterns.map(::featureRegex),
        excludes = excludes.map(::featureRegex),
    )
}

private fun featureRegex(pattern: String): Regex {
    return Regex(pattern, setOf(RegexOption.IGNORE_CASE))
}
