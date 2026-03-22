package com.example.myapplication.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() {
        rule.collect(
            packageName = InstrumentationRegistry.getArguments().getString("targetAppId")
                ?: error("targetAppId not passed as instrumentation runner arg"),
            includeInStartupProfile = true,
        ) {
            pressHome()
            startActivityAndWait()

            device.wait(Until.hasObject(By.textContains("欢迎")), 3_000)
            device.wait(Until.hasObject(By.descContains("历史")), 3_000)

            device.findObject(By.descContains("历史"))?.click()
            device.wait(Until.hasObject(By.textContains("聊天历史")), 3_000)
            device.pressBack()

            device.findObject(By.text("设置"))?.click()
            device.wait(Until.hasObject(By.textContains("设置")), 3_000)
            device.pressBack()
        }
    }
}
