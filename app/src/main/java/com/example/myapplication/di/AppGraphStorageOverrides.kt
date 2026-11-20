package com.example.myapplication.di

import com.example.myapplication.data.local.AppSettingsStore
import com.example.myapplication.data.local.ComplianceConsentRepository
import com.example.myapplication.data.local.chat.ChatDatabase

/** 隔离宿主可提供自己的存储；未提供的域仍使用原应用私有实现。 */
data class AppGraphStorageOverrides(
    val database: ChatDatabase? = null,
    val settingsStore: AppSettingsStore? = null,
    val complianceConsentStore: ComplianceConsentRepository? = null,
)
