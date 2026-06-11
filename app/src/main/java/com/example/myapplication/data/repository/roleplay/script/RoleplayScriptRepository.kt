package com.example.myapplication.data.repository.roleplay.script

import com.example.myapplication.data.local.roleplay.script.RoleplayScriptDao
import com.example.myapplication.data.local.roleplay.script.RoleplayScriptEntity
import com.example.myapplication.data.local.roleplay.script.RoleplayScriptStateEntity
import com.example.myapplication.roleplay.script.RoleplayScriptDefinition
import com.example.myapplication.roleplay.script.RoleplayScriptPermission
import com.example.myapplication.roleplay.script.RoleplayScriptPlanner
import com.example.myapplication.roleplay.script.RoleplayScriptScope
import com.example.myapplication.system.json.AppJson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

interface RoleplayScriptRepository {
    fun observeScripts(): Flow<List<RoleplayScriptDefinition>>

    suspend fun listScripts(): List<RoleplayScriptDefinition>

    suspend fun getScript(scriptId: String): RoleplayScriptDefinition?

    suspend fun listRuntimeScripts(
        characterId: String = "",
        scenarioId: String = "",
        sessionId: String = "",
    ): List<RoleplayScriptDefinition>

    suspend fun upsertScript(script: RoleplayScriptDefinition)

    suspend fun deleteScript(scriptId: String)

    suspend fun readState(scriptId: String): Map<String, String>

    suspend fun writeState(scriptId: String, values: Map<String, String>)

    suspend fun deleteStateValue(scriptId: String, stateKey: String)
}

class RoomRoleplayScriptRepository(
    private val scriptDao: RoleplayScriptDao,
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
) : RoleplayScriptRepository {
    override fun observeScripts(): Flow<List<RoleplayScriptDefinition>> {
        return scriptDao.observeScripts().map { scripts -> scripts.map(::toDomain) }
    }

    override suspend fun listScripts(): List<RoleplayScriptDefinition> {
        return scriptDao.listScripts().map(::toDomain)
    }

    override suspend fun getScript(scriptId: String): RoleplayScriptDefinition? {
        return scriptDao.getScript(scriptId.trim())?.let(::toDomain)
    }

    override suspend fun listRuntimeScripts(
        characterId: String,
        scenarioId: String,
        sessionId: String,
    ): List<RoleplayScriptDefinition> {
        val scopedScripts = buildList {
            addAll(scriptDao.listScriptsByOwner(RoleplayScriptScope.GLOBAL.storageValue, ""))
            characterId.trim().takeIf { it.isNotBlank() }?.let { ownerId ->
                addAll(scriptDao.listScriptsByOwner(RoleplayScriptScope.CHARACTER.storageValue, ownerId))
            }
            scenarioId.trim().takeIf { it.isNotBlank() }?.let { ownerId ->
                addAll(scriptDao.listScriptsByOwner(RoleplayScriptScope.SCENARIO.storageValue, ownerId))
            }
            sessionId.trim().takeIf { it.isNotBlank() }?.let { ownerId ->
                addAll(scriptDao.listScriptsByOwner(RoleplayScriptScope.SESSION.storageValue, ownerId))
            }
        }
        return RoleplayScriptPlanner.orderedExecutableScripts(scopedScripts.map(::toDomain))
    }

    override suspend fun upsertScript(script: RoleplayScriptDefinition) {
        val now = nowProvider()
        val normalized = script.normalized(now)
        require(normalized.id.isNotBlank()) { "Roleplay script id must not be blank." }
        val existing = scriptDao.getScript(normalized.id)
        scriptDao.upsertScript(toEntity(normalized, existing?.createdAt ?: now, now))
    }

    override suspend fun deleteScript(scriptId: String) {
        scriptDao.deleteScript(scriptId.trim())
    }

    override suspend fun readState(scriptId: String): Map<String, String> {
        return scriptDao.listState(scriptId.trim()).associate { state ->
            state.stateKey to state.stateValue
        }
    }

    override suspend fun writeState(scriptId: String, values: Map<String, String>) {
        val normalizedScriptId = scriptId.trim()
        if (normalizedScriptId.isBlank()) {
            return
        }
        val now = nowProvider()
        val entities = values.mapNotNull { (key, value) ->
            val normalizedKey = key.trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
            RoleplayScriptStateEntity(
                scriptId = normalizedScriptId,
                stateKey = normalizedKey,
                stateValue = value,
                updatedAt = now,
            )
        }
        if (entities.isNotEmpty()) {
            scriptDao.upsertStateValues(entities)
        }
    }

    override suspend fun deleteStateValue(scriptId: String, stateKey: String) {
        scriptDao.deleteStateValue(scriptId.trim(), stateKey.trim())
    }

    private fun RoleplayScriptDefinition.normalized(now: Long): RoleplayScriptDefinition {
        return copy(
            id = id.trim(),
            name = name.trim().ifBlank { "未命名脚本" },
            ownerId = when (scope) {
                RoleplayScriptScope.GLOBAL -> ""
                else -> ownerId.trim()
            },
            updatedAt = now,
        )
    }

    private fun toDomain(entity: RoleplayScriptEntity): RoleplayScriptDefinition {
        return RoleplayScriptDefinition(
            id = entity.id,
            name = entity.name,
            scope = RoleplayScriptScope.fromStorageValue(entity.scope),
            ownerId = entity.ownerId,
            source = entity.source,
            enabled = entity.enabled,
            grantedPermissions = decodePermissions(entity.grantedPermissionsJson),
            updatedAt = entity.updatedAt,
        )
    }

    private fun toEntity(
        script: RoleplayScriptDefinition,
        createdAt: Long,
        updatedAt: Long,
    ): RoleplayScriptEntity {
        return RoleplayScriptEntity(
            id = script.id,
            name = script.name,
            scope = script.scope.storageValue,
            ownerId = script.ownerId,
            source = script.source,
            enabled = script.enabled,
            grantedPermissionsJson = encodePermissions(script.grantedPermissions),
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    private fun encodePermissions(permissions: Set<RoleplayScriptPermission>): String {
        val values = permissions
            .sortedBy { permission -> permission.ordinal }
            .map { permission -> permission.storageValue }
        return AppJson.gson.toJson(values)
    }

    private fun decodePermissions(rawJson: String): Set<RoleplayScriptPermission> {
        if (rawJson.isBlank()) {
            return emptySet()
        }
        return runCatching {
            AppJson.gson.fromJson<List<String>>(rawJson, stringListType).orEmpty()
        }.getOrDefault(emptyList())
            .mapNotNull(RoleplayScriptPermission::fromStorageValue)
            .toSet()
    }

    private companion object {
        val stringListType = object : TypeToken<List<String>>() {}.type
    }
}

object EmptyRoleplayScriptRepository : RoleplayScriptRepository {
    override fun observeScripts(): Flow<List<RoleplayScriptDefinition>> = flowOf(emptyList())

    override suspend fun listScripts(): List<RoleplayScriptDefinition> = emptyList()

    override suspend fun getScript(scriptId: String): RoleplayScriptDefinition? = null

    override suspend fun listRuntimeScripts(
        characterId: String,
        scenarioId: String,
        sessionId: String,
    ): List<RoleplayScriptDefinition> = emptyList()

    override suspend fun upsertScript(script: RoleplayScriptDefinition) = Unit

    override suspend fun deleteScript(scriptId: String) = Unit

    override suspend fun readState(scriptId: String): Map<String, String> = emptyMap()

    override suspend fun writeState(scriptId: String, values: Map<String, String>) = Unit

    override suspend fun deleteStateValue(scriptId: String, stateKey: String) = Unit
}
