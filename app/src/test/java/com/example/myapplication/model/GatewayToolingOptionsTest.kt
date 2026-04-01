package com.example.myapplication.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayToolingOptionsTest {
    @Test
    fun searchOnly_preservesSearchEnabledCompatibility() {
        assertTrue(GatewayToolingOptions.searchOnly(true).searchEnabled)
        assertFalse(GatewayToolingOptions.searchOnly(false).searchEnabled)
    }
}
