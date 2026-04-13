package com.example.myapplication.roleplay

import com.example.myapplication.model.ChatMessagePart
import com.example.myapplication.model.textMessagePart
import com.example.myapplication.model.thoughtMessagePart

internal object OnlineInlineThoughtFallback {
    private val thoughtPrefixes = listOf("【心声】", "心声：", "心声:")
    private val dialogueMarkers = listOf("【对白】", "【发出】", "【说出口】")
    private val ellipsisSeparator = Regex("""\s+(?:\.{4,}|…{2,}|……+)\s+""")
    private val blankLineSeparator = Regex("""\n\s*\n+""")
    private val sentenceBoundarySeparator = Regex("""(?<=[。！？!?])\s+""")

    private const val MaxAutoDialogueSegments = 5
    private const val MaxAutoDialogueSegmentChars = 28
    private const val MaxAutoDialogueTotalChars = 120

    fun splitToParts(rawContent: String): List<ChatMessagePart>? {
        val trimmed = rawContent.trim()
        val prefix = thoughtPrefixes.firstOrNull { trimmed.startsWith(it) } ?: return null
        val content = trimmed.removePrefix(prefix).trim()
        if (content.isBlank()) {
            return null
        }

        val markerSplit = dialogueMarkers.firstNotNullOfOrNull { marker ->
            content.indexOf(marker).takeIf { it >= 0 }?.let { index ->
                content.substring(0, index).trim() to content.substring(index + marker.length).trim()
            }
        }
        val blankLineSplit = content.split(blankLineSeparator, limit = 2)
            .takeIf { it.size == 2 }
            ?.let { it[0].trim() to it[1].trim() }
        val ellipsisSplit = ellipsisSeparator.split(content, limit = 2)
            .takeIf { it.size == 2 }
            ?.let { it[0].trim() to it[1].trim() }

        val (thoughtText, dialogueText) = markerSplit ?: blankLineSplit ?: ellipsisSplit ?: (content to "")
        if (thoughtText.isBlank()) {
            return null
        }

        return buildList {
            add(thoughtMessagePart(thoughtText))
            addAll(splitDialogueParts(dialogueText))
        }
    }

    fun splitDialogueOnlyToParts(rawContent: String): List<ChatMessagePart>? {
        val parts = splitDialogueParts(rawContent)
        return parts.takeIf { it.size >= 2 }
    }

    private fun splitDialogueParts(rawContent: String): List<ChatMessagePart> {
        val trimmed = rawContent.trim()
        if (trimmed.isBlank()) {
            return emptyList()
        }
        val segments = splitDialogueText(trimmed)
        return if (segments.size >= 2) {
            segments.map(::textMessagePart)
        } else {
            listOf(textMessagePart(trimmed))
        }
    }

    private fun splitDialogueText(rawContent: String): List<String> {
        val normalized = rawContent.replace("\r\n", "\n").trim()
        if (normalized.isBlank()) {
            return emptyList()
        }

        val blankLineSegments = normalized.split(blankLineSeparator)
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (isReasonableDialogueSegments(blankLineSegments)) {
            return blankLineSegments
        }

        val lineSegments = normalized.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (isReasonableDialogueSegments(lineSegments)) {
            return lineSegments
        }

        val sentenceSegments = normalized.split(sentenceBoundarySeparator)
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (isReasonableDialogueSegments(sentenceSegments)) {
            return sentenceSegments
        }

        return listOf(normalized)
    }

    private fun isReasonableDialogueSegments(segments: List<String>): Boolean {
        if (segments.size < 2 || segments.size > MaxAutoDialogueSegments) {
            return false
        }
        val totalLength = segments.sumOf(String::length)
        if (totalLength > MaxAutoDialogueTotalChars) {
            return false
        }
        return segments.all { segment ->
            segment.length <= MaxAutoDialogueSegmentChars
        }
    }
}
