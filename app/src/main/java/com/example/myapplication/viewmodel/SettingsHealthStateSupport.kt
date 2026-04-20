package com.example.myapplication.viewmodel

import com.example.myapplication.model.ConnectionHealth

object SettingsHealthStateSupport {
    fun markChecking(
        current: SettingsUiState,
        providerId: String,
    ): SettingsUiState {
        return current.copy(
            connectionHealthMap = current.connectionHealthMap + (providerId to ConnectionHealth.CHECKING),
        )
    }

    fun markResult(
        current: SettingsUiState,
        providerId: String,
        health: ConnectionHealth,
    ): SettingsUiState {
        return current.copy(
            connectionHealthMap = current.connectionHealthMap + (providerId to health),
        )
    }

    fun clearProviderHealth(
        current: SettingsUiState,
        providerId: String,
    ): SettingsUiState {
        if (providerId !in current.connectionHealthMap) return current
        return current.copy(
            connectionHealthMap = current.connectionHealthMap - providerId,
        )
    }
}
