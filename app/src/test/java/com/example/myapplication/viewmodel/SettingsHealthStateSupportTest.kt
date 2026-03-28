package com.example.myapplication.viewmodel

import com.example.myapplication.model.ConnectionHealth
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsHealthStateSupportTest {
    @Test
    fun markChecking_setsProviderToChecking() {
        val updated = SettingsHealthStateSupport.markChecking(
            current = SettingsUiState(),
            providerId = "provider-1",
        )

        assertEquals(ConnectionHealth.CHECKING, updated.connectionHealthMap["provider-1"])
    }

    @Test
    fun markResult_setsProviderToFinalHealth() {
        val updated = SettingsHealthStateSupport.markResult(
            current = SettingsUiState(
                connectionHealthMap = mapOf("provider-1" to ConnectionHealth.CHECKING),
            ),
            providerId = "provider-1",
            health = ConnectionHealth.HEALTHY,
        )

        assertEquals(ConnectionHealth.HEALTHY, updated.connectionHealthMap["provider-1"])
    }
}
