package com.example.myapplication.ui.screen.roleplay

import com.example.myapplication.ui.screenshot.SessionSidebarRenderTest
import com.example.myapplication.ui.screenshot.ScreenshotCases

import org.junit.Assert.assertThrows
import org.junit.Test

class SessionSidebarWindowGuardTest : SessionSidebarRenderTest() {
    @Test fun unopenedWindow_failsInsteadOfCapturingStage() {
        renderSidebar(ScreenshotCases.require("E05_main-matcha-light"))
        assertThrows(AssertionError::class.java) { windowRoot("本会话预设") }
    }
}
