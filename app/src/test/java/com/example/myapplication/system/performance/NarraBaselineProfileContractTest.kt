package com.example.myapplication.system.performance

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NarraBaselineProfileContractTest {

    @Test
    fun validate_emptyOrDependencyOnlyProfile_fails() {
        assertFalse(NarraBaselineProfileContract.validate("").isValid)
        assertFalse(
            NarraBaselineProfileContract.validate(
                "HSPLandroidx/compose/runtime/Composer;->startRestartGroup(I)Landroidx/compose/runtime/Composer;",
            ).isValid,
        )
    }

    @Test
    fun validate_profileContainingApplicationNamespace_passes() {
        val result = NarraBaselineProfileContract.validate(
            "HSPLcom/example/myapplication/ui/AppRootKt;->AppRoot()V",
        )

        assertTrue(result.isValid)
        assertTrue(result.applicationRuleCount >= 1)
    }
}
