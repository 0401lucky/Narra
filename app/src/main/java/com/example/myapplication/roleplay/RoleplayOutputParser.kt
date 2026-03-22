package com.example.myapplication.roleplay

import com.example.myapplication.model.RoleplayContentType
import com.example.myapplication.model.RoleplaySpeaker

data class RoleplayParsedSegment(
    val contentType: RoleplayContentType,
    val speaker: RoleplaySpeaker,
    val speakerName: String,
    val content: String,
    val emotion: String = "",
)

class RoleplayOutputParser {
    private val tagPattern = Regex(
        """(?s)<narration>(.*?)</narration>|<dialogue\b([^>]*)>(.*?)</dialogue>""",
    )
    private val attributePattern = Regex("(\\w+)=\"([^\"]*)\"")
    private val stripTagPattern = Regex("""<[^>]+>""")
    private val danglingOpenTagPattern = Regex("""<[^>]*$""")

    fun parseAssistantOutput(
        rawContent: String,
        characterName: String,
        allowNarration: Boolean,
    ): List<RoleplayParsedSegment> {
        val normalized = rawContent.trim()
        if (normalized.isBlank()) {
            return emptyList()
        }

        val matches = tagPattern.findAll(normalized).toList()
        if (matches.isEmpty()) {
            return listOf(
                RoleplayParsedSegment(
                    contentType = RoleplayContentType.DIALOGUE,
                    speaker = RoleplaySpeaker.CHARACTER,
                    speakerName = characterName,
                    content = stripMarkup(normalized),
                ),
            )
        }

        val segments = mutableListOf<RoleplayParsedSegment>()
        var cursor = 0
        matches.forEach { match ->
            if (match.range.first > cursor) {
                val prefix = normalized.substring(cursor, match.range.first).trim()
                if (prefix.isNotBlank()) {
                    segments += RoleplayParsedSegment(
                        contentType = RoleplayContentType.DIALOGUE,
                        speaker = RoleplaySpeaker.CHARACTER,
                        speakerName = characterName,
                        content = stripMarkup(prefix),
                    )
                }
            }

            val narrationContent = match.groups[1]?.value.orEmpty().trim()
            val dialogueAttributes = match.groups[2]?.value.orEmpty()
            val dialogueContent = match.groups[3]?.value.orEmpty().trim()
            when {
                narrationContent.isNotBlank() && allowNarration -> {
                    segments += RoleplayParsedSegment(
                        contentType = RoleplayContentType.NARRATION,
                        speaker = RoleplaySpeaker.NARRATOR,
                        speakerName = "旁白",
                        content = stripMarkup(narrationContent),
                    )
                }

                narrationContent.isNotBlank() -> {
                    segments += RoleplayParsedSegment(
                        contentType = RoleplayContentType.DIALOGUE,
                        speaker = RoleplaySpeaker.CHARACTER,
                        speakerName = characterName,
                        content = stripMarkup(narrationContent),
                    )
                }

                dialogueContent.isNotBlank() -> {
                    val attributes = parseAttributes(dialogueAttributes)
                    segments += RoleplayParsedSegment(
                        contentType = RoleplayContentType.DIALOGUE,
                        speaker = RoleplaySpeaker.CHARACTER,
                        speakerName = characterName,
                        content = stripMarkup(dialogueContent),
                        emotion = attributes["emotion"].orEmpty().trim(),
                    )
                }
            }
            cursor = match.range.last + 1
        }

        if (cursor < normalized.length) {
            val suffix = normalized.substring(cursor).trim()
            if (suffix.isNotBlank()) {
                segments += RoleplayParsedSegment(
                    contentType = RoleplayContentType.DIALOGUE,
                    speaker = RoleplaySpeaker.CHARACTER,
                    speakerName = characterName,
                    content = stripMarkup(suffix),
                )
            }
        }

        return segments.filter { it.content.isNotBlank() }
    }

    fun stripMarkup(rawContent: String): String {
        return rawContent
            .replace(danglingOpenTagPattern, "")
            .replace(stripTagPattern, "")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .trim()
    }

    private fun parseAttributes(rawAttributes: String): Map<String, String> {
        return attributePattern.findAll(rawAttributes)
            .associate { match ->
                match.groupValues[1] to match.groupValues[2]
            }
    }
}
