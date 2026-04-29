package com.example.myapplication.context

object ContextPlaceholderResolver {
    private val userPatterns = listOf(
        Regex("""\{\{\s*user\s*\}\}""", RegexOption.IGNORE_CASE),
        Regex("""<\s*user\s*>""", RegexOption.IGNORE_CASE),
    )
    private val characterPatterns = listOf(
        Regex("""\{\{\s*char\s*\}\}""", RegexOption.IGNORE_CASE),
        Regex("""\{\{\s*character\s*\}\}""", RegexOption.IGNORE_CASE),
        Regex("""<\s*char\s*>""", RegexOption.IGNORE_CASE),
        Regex("""<\s*bot\s*>""", RegexOption.IGNORE_CASE),
    )

    fun resolve(
        text: String,
        userName: String,
        characterName: String,
    ): String {
        if (text.isBlank()) {
            return text
        }
        var resolved = text
        userPatterns.forEach { pattern ->
            resolved = resolved.replace(pattern, userName)
        }
        characterPatterns.forEach { pattern ->
            resolved = resolved.replace(pattern, characterName)
        }
        return resolved
    }

    fun resolveTemplate(
        text: String,
        values: Map<String, String>,
        userName: String,
        characterName: String,
    ): String {
        if (text.isBlank()) {
            return text
        }
        var resolved = resolve(
            text = text,
            userName = userName,
            characterName = characterName,
        )
        values.forEach { (key, value) ->
            val pattern = Regex("""\{\{\s*${Regex.escape(key)}\s*\}\}""", RegexOption.IGNORE_CASE)
            resolved = resolved.replace(pattern, value)
        }
        return resolved
    }

    fun resolveAll(
        values: List<String>,
        userName: String,
        characterName: String,
    ): List<String> {
        return values.map { value ->
            resolve(
                text = value,
                userName = userName,
                characterName = characterName,
            )
        }
    }
}
