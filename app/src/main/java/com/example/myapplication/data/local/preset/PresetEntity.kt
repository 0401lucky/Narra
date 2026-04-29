package com.example.myapplication.data.local.preset

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "presets",
    indices = [
        Index(value = ["builtIn"]),
        Index(value = ["updatedAt"]),
    ],
)
data class PresetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val systemPrompt: String,
    val contextTemplate: String,
    val samplerJson: String,
    val instructJson: String,
    val stopSequencesJson: String,
    val entriesJson: String,
    val renderConfigJson: String,
    val version: Int,
    val builtIn: Boolean,
    val userModified: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)
