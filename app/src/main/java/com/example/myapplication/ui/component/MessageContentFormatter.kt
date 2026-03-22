package com.example.myapplication.ui.component

private val OrderedListLineRegex = Regex("""\d+\.\s+.+""")
private val CommandHeadingRegex = Regex(""".*[:：]\s*$""")
private val CommandContinuationRegex = Regex("""^(--?[A-Za-z0-9][\w./:\\-]*|[A-Za-z0-9_.:/\\-]+)$""")
private val ShellPromptRegex = Regex("""^(?:PS [^>]+>|[A-Z]:\\.*>|[$>#])\s*.+""")
private val MarkdownImageRegex = Regex(
    """!\[[^\]]*]\((?:<)?([^)>\n]+)(?:>)?(?:\s+(?:"[^"]*"|'[^']*'))?\)""",
)
private val HtmlImageRegex = Regex(
    """(?is)<img\b[^>]*?\bsrc\s*=\s*(['"])(.*?)\1[^>]*>""",
)
private val StandaloneImageUrlRegex = Regex(
    """(?i)^(?:https?://|file://|content://)\S+\.(?:png|jpe?g|webp|gif|bmp|svg|avif)(?:[?#]\S*)?$""",
)
private val DataImageUrlRegex = Regex(
    """(?is)^data:image/[a-z0-9.+-]+;base64,[a-z0-9+/=\r\n]+$""",
)
internal data class AssistantVisualContent(
    val text: String,
    val imageSources: List<String>,
)

internal fun normalizeAssistantMarkdownForDisplay(content: String): String {
    val normalized = content.replace("\r\n", "\n").trim()
    if (normalized.isBlank()) {
        return normalized
    }

    if ("```" in normalized) {
        return normalized
    }

    val normalizedWithCodeBlocks = normalizeCommandBlocks(normalized)
    if (normalizedWithCodeBlocks != normalized || "\n\n" in normalizedWithCodeBlocks) {
        return normalizedWithCodeBlocks
    }

    if ("\n\n" in normalized) {
        return normalized
    }

    val nonBlankLines = normalized.lines()
        .map(String::trim)
        .filter(String::isNotBlank)
    if (nonBlankLines.size < 2 || nonBlankLines.any(::looksLikeStructuredMarkdownLine)) {
        return normalized
    }

    return nonBlankLines.joinToString(separator = "\n\n")
}

private fun normalizeCommandBlocks(content: String): String {
    val lines = content.lines()
    val result = mutableListOf<String>()
    var index = 0

    while (index < lines.size) {
        val line = lines[index]
        val trimmed = line.trim()
        val normalizedCandidate = unwrapWholeLineInlineCode(trimmed)
        if (trimmed.isBlank()) {
            result += ""
            index += 1
            continue
        }

        val previousNonBlank = result.asReversed().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        if (!looksLikeCommandLine(normalizedCandidate) &&
            !looksLikeCommandBlockStart(normalizedCandidate, previousNonBlank)
        ) {
            result += line
            index += 1
            continue
        }

        val block = mutableListOf<String>()
        var cursor = index
        while (cursor < lines.size) {
            val candidate = lines[cursor].trim()
            val normalizedBlockCandidate = unwrapWholeLineInlineCode(candidate)
            if (candidate.isBlank()) {
                break
            }
            val canInclude = if (block.isEmpty()) {
                looksLikeCommandLine(normalizedBlockCandidate) ||
                    looksLikeCommandBlockStart(normalizedBlockCandidate, previousNonBlank)
            } else {
                looksLikeCommandLine(normalizedBlockCandidate)
            }
            if (!canInclude && !looksLikeCommandContinuation(normalizedBlockCandidate, block.lastOrNull().orEmpty())) {
                break
            }
            block += normalizedBlockCandidate
            cursor += 1
        }

        val shouldWrap = block.isNotEmpty()
        if (!shouldWrap) {
            result += line
            index += 1
            continue
        }

        val language = inferCommandLanguage(block)
        result += "```$language"
        result += block
        result += "```"
        index = cursor
    }

    return result.joinToString(separator = "\n").trim()
}

private fun unwrapWholeLineInlineCode(line: String): String {
    val trimmed = line.trim()
    if (trimmed.length >= 2 && trimmed.startsWith("`") && trimmed.endsWith("`")) {
        val inner = trimmed.removePrefix("`").removeSuffix("`").trim()
        if (inner.isNotBlank() && '`' !in inner) {
            return inner
        }
    }
    return trimmed
}

private fun looksLikeCommandBlockStart(
    line: String,
    previousNonBlank: String,
): Boolean {
    return CommandHeadingRegex.containsMatchIn(previousNonBlank) && looksLikeCommandLine(line)
}

private fun looksLikeCommandLine(line: String): Boolean {
    val trimmed = line.trim()
    if (trimmed.isBlank()) {
        return false
    }
    if (looksLikeStructuredMarkdownLine(trimmed)) {
        return false
    }
    if (trimmed.contains("http://") || trimmed.contains("https://")) {
        return false
    }
    if (trimmed.any { it in "。！？；，、（）【】“”" }) {
        return false
    }
    if (trimmed.any { it.code in 0x4E00..0x9FFF }) {
        return false
    }
    if (ShellPromptRegex.matches(trimmed)) {
        return true
    }

    val lowercase = trimmed.lowercase()
    return listOf(
        "powershell",
        "pwsh",
        "winget",
        "git",
        "adb",
        "npm",
        "pnpm",
        "yarn",
        "bun",
        "python",
        "python3",
        "pip",
        "pip3",
        "uv",
        "poetry",
        "conda",
        "mamba",
        "curl",
        "wget",
        "ssh",
        "scp",
        "kubectl",
        "docker",
        "gradle",
        "./gradlew",
        "gradlew",
        "java",
        "node",
        "npx",
        "cargo",
        "go",
        "bash",
        "sh",
        "zsh",
        "cmd",
        "reg",
        "netsh",
        "sfc",
        "dism",
        "choco",
        "scoop",
        "ls",
        "cd",
        "mkdir",
        "rm",
        "cp",
        "mv",
        "cat",
        "echo",
        "export",
        "set ",
        "get-",
        "set-",
        "new-",
        "remove-",
        "copy-",
        "move-",
        "\$env:",
        "\$psversiontable",
    ).any { token ->
        lowercase == token.trim() || lowercase.startsWith("$token ")
    }
}

private fun looksLikeCommandContinuation(
    line: String,
    previousCommandLine: String,
): Boolean {
    if (previousCommandLine.isBlank()) {
        return false
    }
    if (line.any { it in "。！？；，、（）【】“”" }) {
        return false
    }
    return CommandContinuationRegex.matches(line)
}

private fun inferCommandLanguage(lines: List<String>): String {
    val lowercaseLines = lines.map { it.lowercase() }
    return if (
        lowercaseLines.any { line ->
            "powershell" in line ||
                "winget" in line ||
                "\$psversiontable" in line ||
                line.startsWith("get-") ||
                line.startsWith("set-") ||
                line.startsWith("\$env:") ||
                Regex("""^[a-z]:\\""").containsMatchIn(line)
        }
    ) {
        "powershell"
    } else {
        "bash"
    }
}

private fun looksLikeStructuredMarkdownLine(line: String): Boolean {
    return line.startsWith("#") ||
        line.startsWith("> ") ||
        line.startsWith("- ") ||
        line.startsWith("* ") ||
        line.startsWith("+ ") ||
        line.startsWith("|") ||
        line.startsWith("```") ||
        OrderedListLineRegex.matches(line)
}

internal fun extractAssistantVisualContent(content: String): AssistantVisualContent {
    if (content.isBlank()) {
        return AssistantVisualContent(text = "", imageSources = emptyList())
    }

    val imageSources = mutableListOf<String>()
    var remaining = content.replace("\r\n", "\n")

    fun registerImageSource(raw: String?) {
        val normalized = raw
            ?.trim()
            ?.trim('<', '>')
            ?.takeIf { it.isNotBlank() }
            ?: return
        if (normalized !in imageSources) {
            imageSources += normalized
        }
    }

    remaining = MarkdownImageRegex.replace(remaining) { match ->
        registerImageSource(match.groupValues.getOrNull(1))
        ""
    }
    remaining = HtmlImageRegex.replace(remaining) { match ->
        registerImageSource(match.groupValues.getOrNull(2))
        ""
    }

    val trimmedRemaining = remaining.trim()
    if (
        imageSources.isEmpty() &&
        looksLikeStandaloneImageSource(trimmedRemaining) &&
        !trimmedRemaining.contains('\n')
    ) {
        registerImageSource(trimmedRemaining)
        remaining = ""
    }

    val textLines = mutableListOf<String>()
    remaining.lines().forEach { line ->
        val trimmed = line.trim()
        when {
            trimmed.isBlank() -> textLines += ""
            DataImageUrlRegex.matches(trimmed) -> registerImageSource(trimmed)
            StandaloneImageUrlRegex.matches(trimmed) -> registerImageSource(trimmed)
            else -> textLines += line
        }
    }

    val normalizedText = textLines.joinToString(separator = "\n")
        .replace(Regex("""\n{3,}"""), "\n\n")
        .trim()

    return AssistantVisualContent(
        text = normalizedText,
        imageSources = imageSources,
    )
}

private fun looksLikeStandaloneImageSource(value: String): Boolean {
    val normalized = value.trim()
    if (normalized.isBlank()) {
        return false
    }
    if (DataImageUrlRegex.matches(normalized) || StandaloneImageUrlRegex.matches(normalized)) {
        return true
    }
    if (!normalized.startsWith("http://", ignoreCase = true) &&
        !normalized.startsWith("https://", ignoreCase = true)
    ) {
        return false
    }

    val lower = normalized.lowercase()
    val hasImageHint = listOf(
        "/generated/",
        "/image",
        "/images/",
        "/img/",
        "/media/",
        "/render/",
        "/asset/",
        "cdn.",
    ).any { hint -> hint in lower }
    val hasSignedQueryHint = listOf(
        "token=",
        "sig=",
        "signature=",
        "expires=",
        "x-amz-",
    ).any { hint -> hint in lower }

    return hasImageHint && hasSignedQueryHint
}
