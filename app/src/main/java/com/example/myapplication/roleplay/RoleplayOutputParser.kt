package com.example.myapplication.roleplay

import com.example.myapplication.model.RoleplayContentType
import com.example.myapplication.model.RoleplaySpeaker

data class RoleplayParsedSegment(
    val contentType: RoleplayContentType,
    val speaker: RoleplaySpeaker,
    val speakerName: String,
    val content: String,
    val replyToMessageId: String = "",
    val replyToPreview: String = "",
    val replyToSpeakerName: String = "",
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
            return parsePlainTextOutput(
                rawContent = normalized,
                characterName = characterName,
                allowNarration = allowNarration,
            )
        }

        val hasTaggedDialogue = matches.any { it.groups[3]?.value.orEmpty().trim().isNotBlank() }
        val hasTaggedNarration = matches.any { it.groups[1]?.value.orEmpty().trim().isNotBlank() }
        val segments = mutableListOf<RoleplayParsedSegment>()
        var cursor = 0
        matches.forEach { match ->
            if (match.range.first > cursor) {
                segments += parseTaggedGapOutput(
                    rawContent = normalized.substring(cursor, match.range.first),
                    characterName = characterName,
                    allowNarration = allowNarration,
                    hasTaggedDialogue = hasTaggedDialogue,
                    hasTaggedNarration = hasTaggedNarration,
                )
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
                    segments += parseDialogueTagContent(
                        rawContent = dialogueContent,
                        characterName = characterName,
                        allowNarration = allowNarration,
                        replyToMessageId = attributes["reply_to"].orEmpty().trim(),
                        replyToPreview = attributes["reply_preview"].orEmpty().trim(),
                        replyToSpeakerName = attributes["reply_speaker"].orEmpty().trim(),
                        emotion = attributes["emotion"].orEmpty().trim(),
                    )
                }
            }
            cursor = match.range.last + 1
        }

        if (cursor < normalized.length) {
            segments += parseTaggedGapOutput(
                rawContent = normalized.substring(cursor),
                characterName = characterName,
                allowNarration = allowNarration,
                hasTaggedDialogue = hasTaggedDialogue,
                hasTaggedNarration = hasTaggedNarration,
            )
        }

        return mergeAdjacentParsedSegments(
            segments.filter { it.content.isNotBlank() },
        )
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

    private fun parseDialogueTagContent(
        rawContent: String,
        characterName: String,
        allowNarration: Boolean,
        replyToMessageId: String,
        replyToPreview: String,
        replyToSpeakerName: String,
        emotion: String,
    ): List<RoleplayParsedSegment> {
        return parsePlainTextOutput(
            rawContent = rawContent,
            characterName = characterName,
            allowNarration = allowNarration,
            defaultFallbackKind = PlainSegmentKind.DIALOGUE,
        ).map { segment ->
            if (segment.contentType == RoleplayContentType.DIALOGUE) {
                segment.copy(
                    emotion = emotion,
                    replyToMessageId = replyToMessageId,
                    replyToPreview = replyToPreview,
                    replyToSpeakerName = replyToSpeakerName,
                )
            } else {
                segment
            }
        }
    }

    private fun parseTaggedGapOutput(
        rawContent: String,
        characterName: String,
        allowNarration: Boolean,
        hasTaggedDialogue: Boolean,
        hasTaggedNarration: Boolean,
    ): List<RoleplayParsedSegment> {
        val defaultFallbackKind = when {
            hasTaggedDialogue -> PlainSegmentKind.NARRATION
            hasTaggedNarration -> PlainSegmentKind.DIALOGUE
            else -> PlainSegmentKind.DIALOGUE
        }
        return parsePlainTextOutput(
            rawContent = rawContent,
            characterName = characterName,
            allowNarration = allowNarration,
            defaultFallbackKind = defaultFallbackKind,
        )
    }

    private fun parsePlainTextOutput(
        rawContent: String,
        characterName: String,
        allowNarration: Boolean,
        defaultFallbackKind: PlainSegmentKind = PlainSegmentKind.DIALOGUE,
    ): List<RoleplayParsedSegment> {
        val explicitSegments = mutableListOf<PlainSegmentCandidate>()
        var cursor = 0
        while (cursor < rawContent.length) {
            val nextMarker = findNextMarker(rawContent, cursor)
            if (nextMarker == null) {
                explicitSegments += PlainSegmentCandidate(
                    kind = PlainSegmentKind.PLAIN,
                    content = rawContent.substring(cursor),
                )
                break
            }

            if (nextMarker.startIndex > cursor) {
                explicitSegments += PlainSegmentCandidate(
                    kind = PlainSegmentKind.PLAIN,
                    content = rawContent.substring(cursor, nextMarker.startIndex),
                )
            }

            explicitSegments += PlainSegmentCandidate(
                kind = nextMarker.kind,
                content = nextMarker.content,
                rawContent = nextMarker.rawContent,
            )
            cursor = nextMarker.endIndex
        }

        val normalizedCandidates = coalesceAdjacentCandidates(
            normalizeInlineQuotedDialogueCandidates(explicitSegments),
        )
        val hasExplicitDialogue = normalizedCandidates.any { it.kind == PlainSegmentKind.DIALOGUE }
        val hasExplicitNarration = normalizedCandidates.any { it.kind == PlainSegmentKind.NARRATION }
        if (!hasExplicitDialogue && !hasExplicitNarration) {
            val fallbackKind = if (defaultFallbackKind == PlainSegmentKind.NARRATION && allowNarration) {
                RoleplayContentType.NARRATION to RoleplaySpeaker.NARRATOR
            } else {
                RoleplayContentType.DIALOGUE to RoleplaySpeaker.CHARACTER
            }
            return listOf(
                RoleplayParsedSegment(
                    contentType = fallbackKind.first,
                    speaker = fallbackKind.second,
                    speakerName = if (fallbackKind.second == RoleplaySpeaker.NARRATOR) "旁白" else characterName,
                    content = stripMarkup(rawContent),
                ),
            )
        }

        return normalizedCandidates
            .mapNotNull { candidate ->
                val normalizedContent = stripMarkup(candidate.content)
                if (normalizedContent.isBlank()) {
                    return@mapNotNull null
                }
                when (candidate.kind) {
                    PlainSegmentKind.DIALOGUE -> {
                        RoleplayParsedSegment(
                            contentType = RoleplayContentType.DIALOGUE,
                            speaker = RoleplaySpeaker.CHARACTER,
                            speakerName = characterName,
                            content = normalizedContent,
                        )
                    }

                    PlainSegmentKind.NARRATION -> {
                        if (allowNarration) {
                            RoleplayParsedSegment(
                                contentType = RoleplayContentType.NARRATION,
                                speaker = RoleplaySpeaker.NARRATOR,
                                speakerName = "旁白",
                                content = normalizedContent,
                            )
                        } else {
                            RoleplayParsedSegment(
                                contentType = RoleplayContentType.DIALOGUE,
                                speaker = RoleplaySpeaker.CHARACTER,
                                speakerName = characterName,
                                content = normalizedContent,
                            )
                        }
                    }

                    PlainSegmentKind.PLAIN -> {
                        val fallbackKind = when {
                            hasExplicitDialogue -> PlainSegmentKind.NARRATION
                            hasExplicitNarration -> PlainSegmentKind.DIALOGUE
                            else -> defaultFallbackKind
                        }
                        if (fallbackKind == PlainSegmentKind.NARRATION && allowNarration) {
                            RoleplayParsedSegment(
                                contentType = RoleplayContentType.NARRATION,
                                speaker = RoleplaySpeaker.NARRATOR,
                                speakerName = "旁白",
                                content = normalizedContent,
                            )
                        } else {
                            RoleplayParsedSegment(
                                contentType = RoleplayContentType.DIALOGUE,
                                speaker = RoleplaySpeaker.CHARACTER,
                                speakerName = characterName,
                                content = normalizedContent,
                            )
                        }
                    }
                }
            }
            .let(::mergeAdjacentParsedSegments)
    }

    private fun mergeAdjacentParsedSegments(
        segments: List<RoleplayParsedSegment>,
    ): List<RoleplayParsedSegment> {
        return segments.fold(mutableListOf<RoleplayParsedSegment>()) { merged, segment ->
            val previous = merged.lastOrNull()
            if (previous != null &&
                previous.contentType == segment.contentType &&
                previous.speaker == segment.speaker &&
                previous.speakerName == segment.speakerName &&
                previous.replyToMessageId == segment.replyToMessageId &&
                previous.replyToPreview == segment.replyToPreview &&
                previous.replyToSpeakerName == segment.replyToSpeakerName &&
                previous.emotion == segment.emotion
            ) {
                merged[merged.lastIndex] = previous.copy(
                    content = "${previous.content}\n${segment.content}".trim(),
                )
            } else {
                merged += segment
            }
            merged
        }
    }

    private fun normalizeInlineQuotedDialogueCandidates(
        candidates: List<PlainSegmentCandidate>,
    ): List<PlainSegmentCandidate> {
        if (candidates.size < 3) {
            return candidates
        }
        return candidates.mapIndexed { index, candidate ->
            val previous = candidates.getOrNull(index - 1)
            val next = candidates.getOrNull(index + 1)
            if (
                candidate.kind == PlainSegmentKind.DIALOGUE &&
                previous?.kind == PlainSegmentKind.PLAIN &&
                next?.kind == PlainSegmentKind.PLAIN &&
                isInlineQuotedEmphasis(
                    previousText = previous.content,
                    nextText = next.content,
                )
            ) {
                candidate.copy(
                    kind = PlainSegmentKind.PLAIN,
                    content = candidate.rawContent,
                )
            } else {
                candidate
            }
        }
    }

    private fun isInlineQuotedEmphasis(
        previousText: String,
        nextText: String,
    ): Boolean {
        val previousChar = previousText.lastOrNull { !it.isWhitespace() } ?: return false
        val nextChar = nextText.firstOrNull { !it.isWhitespace() } ?: return false
        return previousChar.isLetterOrDigit() && nextChar.isLetterOrDigit()
    }

    private fun coalesceAdjacentCandidates(
        candidates: List<PlainSegmentCandidate>,
    ): List<PlainSegmentCandidate> {
        if (candidates.isEmpty()) {
            return candidates
        }
        return buildList {
            candidates.forEach { candidate ->
                val previous = lastOrNull()
                if (previous != null && previous.kind == candidate.kind) {
                    this[lastIndex] = previous.copy(
                        content = previous.content + candidate.content,
                        rawContent = previous.rawContent + candidate.rawContent,
                    )
                } else {
                    add(candidate)
                }
            }
        }
    }

    private fun findNextMarker(
        value: String,
        startIndex: Int,
    ): MarkerMatch? {
        var bestMatch: MarkerMatch? = null
        fun register(match: MarkerMatch?) {
            if (match == null) {
                return
            }
            if (bestMatch == null || match.startIndex < bestMatch!!.startIndex) {
                bestMatch = match
            }
        }

        register(findWrappedMarker(value, startIndex, '*', '*', PlainSegmentKind.NARRATION))
        register(findWrappedMarker(value, startIndex, '（', '）', PlainSegmentKind.NARRATION))
        register(findWrappedMarker(value, startIndex, '(', ')', PlainSegmentKind.NARRATION))
        register(findWrappedMarker(value, startIndex, '“', '”', PlainSegmentKind.DIALOGUE))
        register(findWrappedMarker(value, startIndex, '"', '"', PlainSegmentKind.DIALOGUE))
        register(findWrappedMarker(value, startIndex, '\'', '\'', PlainSegmentKind.DIALOGUE))
        return bestMatch
    }

    private fun findWrappedMarker(
        value: String,
        startIndex: Int,
        startChar: Char,
        endChar: Char,
        kind: PlainSegmentKind,
    ): MarkerMatch? {
        var markerStart = value.indexOf(startChar, startIndex)
        while (markerStart >= 0) {
            val markerEnd = value.indexOf(endChar, markerStart + 1)
            if (markerEnd > markerStart + 1) {
                val content = value.substring(markerStart + 1, markerEnd)
                if (content.trim().isNotEmpty()) {
                    return MarkerMatch(
                        startIndex = markerStart,
                        endIndex = markerEnd + 1,
                        kind = kind,
                        content = content,
                        rawContent = value.substring(markerStart, markerEnd + 1),
                    )
                }
            }
            markerStart = value.indexOf(startChar, markerStart + 1)
        }
        return null
    }

    private data class PlainSegmentCandidate(
        val kind: PlainSegmentKind,
        val content: String,
        val rawContent: String = content,
    )

    private data class MarkerMatch(
        val startIndex: Int,
        val endIndex: Int,
        val kind: PlainSegmentKind,
        val content: String,
        val rawContent: String,
    )

    private enum class PlainSegmentKind {
        PLAIN,
        NARRATION,
        DIALOGUE,
    }
}
