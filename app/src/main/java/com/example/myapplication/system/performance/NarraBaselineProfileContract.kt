package com.example.myapplication.system.performance

data class NarraBaselineProfileValidation(
    val isValid: Boolean,
    val applicationRuleCount: Int,
    val reason: String?,
)

object NarraBaselineProfileContract {
    const val APPLICATION_RULE_MARKER = "Lcom/example/myapplication/"

    fun validate(profileText: String): NarraBaselineProfileValidation {
        val applicationRuleCount = profileText.lineSequence()
            .count { line -> line.contains(APPLICATION_RULE_MARKER) }
        return if (applicationRuleCount > 0) {
            NarraBaselineProfileValidation(
                isValid = true,
                applicationRuleCount = applicationRuleCount,
                reason = null,
            )
        } else {
            NarraBaselineProfileValidation(
                isValid = false,
                applicationRuleCount = 0,
                reason = "Baseline Profile 不包含 Narra 应用命名空间规则",
            )
        }
    }
}
