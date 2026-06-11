package com.example.myapplication.data.local.roleplay.script

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "roleplay_scripts",
    indices = [
        Index(value = ["scope", "ownerId"]),
        Index(value = ["enabled"]),
        Index(value = ["updatedAt"]),
    ],
)
data class RoleplayScriptEntity(
    @PrimaryKey val id: String,
    val name: String,
    val scope: String,
    val ownerId: String,
    val source: String,
    val enabled: Boolean,
    val grantedPermissionsJson: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "roleplay_script_state",
    primaryKeys = ["scriptId", "stateKey"],
    foreignKeys = [
        ForeignKey(
            entity = RoleplayScriptEntity::class,
            parentColumns = ["id"],
            childColumns = ["scriptId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["scriptId"]),
        Index(value = ["updatedAt"]),
    ],
)
data class RoleplayScriptStateEntity(
    val scriptId: String,
    val stateKey: String,
    val stateValue: String,
    val updatedAt: Long,
)
