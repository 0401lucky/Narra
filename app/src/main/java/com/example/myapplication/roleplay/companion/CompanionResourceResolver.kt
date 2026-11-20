package com.example.myapplication.roleplay.companion

import com.example.myapplication.model.*

/** 套装是显式选择；旧场景图只作回退，不永久压住新表情图。 */
object CompanionResourceResolver {
    fun resolve(
        visuals: CompanionVisualSettings,
        scenario: RoleplayScenario,
        assistant: Assistant?,
        isReadable: (CompanionResolvedImage) -> Boolean,
    ): CompanionResolvedResources {
        val selected = visuals.forScene(scenario.id)
        val supported = selected.schemaVersion == 1
        val images = if (supported) selected.resources.associateBy { it.id } else emptyMap()
        fun image(id: String?, kind: CompanionImageKind): CompanionResolvedImage? =
            images[id]?.takeIf { it.kind == kind }
                ?.let { CompanionResolvedImage(it.id, it.path, kind = kind) }
                ?.takeIf(isReadable)

        fun legacy(
            uri: String,
            url: String = "",
            kind: CompanionImageKind = CompanionImageKind.PORTRAIT
        ) =
            CompanionResolvedImage(null, uri.trim(), url.trim(), kind).takeIf {
                (it.uri.isNotBlank() || it.url.isNotBlank()) && isReadable(it)
            }

        val outfit =
            selected.outfits.firstOrNull { supported && it.id == selected.selectedOutfitId }
        val oldPortrait = legacy(scenario.characterPortraitUri)
        val oldRemotePortrait = legacy("", scenario.characterPortraitUrl)
        val avatar = legacy(assistant?.avatarUri.orEmpty())
        fun candidates(targetId: String?): List<CompanionResolvedImage> = listOfNotNull(
            image(targetId, CompanionImageKind.PORTRAIT),
            image(outfit?.defaultPortraitId, CompanionImageKind.PORTRAIT),
            oldPortrait,
            oldRemotePortrait,
            avatar,
        ).distinctBy { Triple(it.resourceId, it.uri, it.url) }
        return CompanionResolvedResources(
            defaultPortraits = candidates(null),
            expressionPortraits = outfit?.expressions?.mapValues { candidates(it.value) }.orEmpty(),
            backgrounds = listOfNotNull(
                image(
                    selected.selectedBackgroundId.takeIf { supported },
                    CompanionImageKind.BACKGROUND
                ),
                legacy(scenario.backgroundUri, kind = CompanionImageKind.BACKGROUND),
            ).distinctBy { it.uri to it.url },
            decorations = selected.decorationIds.takeIf { supported }.orEmpty().mapNotNull {
                image(it, CompanionImageKind.DECOR)
            },
        )
    }
}
