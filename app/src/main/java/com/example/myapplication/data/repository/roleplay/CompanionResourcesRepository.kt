package com.example.myapplication.data.repository.roleplay

import com.example.myapplication.model.*

interface CompanionResourcesRepository {
    suspend fun importImage(
        uri: String,
        kind: CompanionImageKind,
        name: String
    ): CompanionImageResource

    suspend fun resolve(
        visuals: CompanionVisualSettings,
        scenario: RoleplayScenario,
        assistant: Assistant?
    ): CompanionResolvedResources

    suspend fun save(
        assistantId: String, expectedRevision: Long, visuals: CompanionVisualSettings,
        importedPaths: Set<String> = emptySet()
    ): CompanionSettingsWriteResult

    suspend fun discardImports(paths: Set<String>)
}
