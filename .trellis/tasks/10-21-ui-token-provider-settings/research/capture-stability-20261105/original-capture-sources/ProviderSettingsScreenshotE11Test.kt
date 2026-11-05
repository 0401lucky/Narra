package com.example.myapplication.ui.screenshot

import androidx.compose.ui.test.*
import com.example.myapplication.model.AppColorTheme
import org.junit.Test

class ProviderSettingsScreenshotE11Test : ProviderSettingsRenderTest() {
    @Test
    fun e11_list_matcha_light() = list(AppColorTheme.MATCHA, false)
    @Test
    fun e11_list_matcha_dark() = list(AppColorTheme.MATCHA, true)
    @Test
    fun e11_list_vanilla_light() = list(AppColorTheme.VANILLA, false)
    @Test
    fun e11_list_vanilla_dark() = list(AppColorTheme.VANILLA, true)
    @Test
    fun e11_list_ocean_light() = list(AppColorTheme.OCEAN, false)
    @Test
    fun e11_list_ocean_dark() = list(AppColorTheme.OCEAN, true)
    @Test
    fun e11_config_light() = detail(false, false)
    @Test
    fun e11_config_dark() = detail(false, true)
    @Test
    fun e11_models_light() = detail(true, false)
    @Test
    fun e11_models_dark() = detail(true, true)
    @Test
    fun e11_config_dynamic_light() = dynamic(false)
    @Test
    fun e11_config_dynamic_dark() = dynamic(true)
    @Test
    fun e11_list_narrow_light() = narrow("list", false)
    @Test
    fun e11_list_narrow_dark() = narrow("list", true)
    @Test
    fun e11_config_save_narrow_light() = narrow("config-save", false)
    @Test
    fun e11_config_save_narrow_dark() = narrow("config-save", true)
    @Test
    fun e11_models_tail_narrow_light() = narrow("models-tail", false)
    @Test
    fun e11_models_tail_narrow_dark() = narrow("models-tail", true)
    @Test
    fun e11_template_dialog_light() = dialog("template", false)
    @Test
    fun e11_template_dialog_dark() = dialog("template", true)
    @Test
    fun e11_fetched_sheet_light() = dialog("fetched", false)
    @Test
    fun e11_fetched_sheet_dark() = dialog("fetched", true)
    @Test
    fun e11_manual_error_dialog_light() = dialog("manual-error", false)
    @Test
    fun e11_manual_error_dialog_dark() = dialog("manual-error", true)
    @Test
    fun e11_ability_dialog_light() = dialog("ability", false)
    @Test
    fun e11_ability_dialog_dark() = dialog("ability", true)
    @Test
    fun e11_clear_dialog_light() = dialog("clear", false)
    @Test
    fun e11_clear_dialog_dark() = dialog("clear", true)

    @Test
    fun e11_list_no_result_light() {
        val case = ScreenshotCases.require("E11_list-no-result-light")
        renderProvider(case, list = true)
        pageControl(hasSetTextAction()).performTextInput("不存在的合成服务")
        captureProvider(case, "没有匹配的提供商")
    }

    @Test
    fun e11_models_empty_dark() {
        val case = ScreenshotCases.require("E11_models-empty-dark")
        updateProvider(ProviderId) {
            it.copy(
                models = emptyList(),
                availableModels = emptyList(),
                selectedModel = ""
            )
        }
        renderProvider(case, dark = true)
        selectModels()
        captureProvider(case, "暂无模型")
    }

    @Test
    fun e11_protocol_dialog_light() = dialog("protocol", false)
    @Test
    fun e11_protocol_dialog_dark() = dialog("protocol", true)

    private fun list(theme: AppColorTheme, dark: Boolean) {
        val case = ScreenshotCases.require("E11_list-${theme.storageValue}-${shade(dark)}")
        renderProvider(case, list = true, theme = theme, dark = dark)
        captureProvider(case, "连接正常")
    }

    private fun detail(models: Boolean, dark: Boolean) {
        val case =
            ScreenshotCases.require("E11_${if (models) "models" else "config"}-${shade(dark)}")
        renderProvider(case, dark = dark)
        if (models) selectModels()
        captureProvider(case, if (models) "当前模型" else "是否启用")
    }

    private fun dynamic(dark: Boolean) {
        val case = ScreenshotCases.require("E11_config-dynamic-${shade(dark)}")
        renderProvider(case, dark = dark, dynamic = true)
        captureProvider(case, "是否启用")
    }

    private fun narrow(kind: String, dark: Boolean) {
        val case = ScreenshotCases.require("E11_$kind-narrow-${shade(dark)}")
        renderProvider(case, list = kind == "list", dark = dark)
        if (kind == "models-tail") selectModels()
        scrollToEnd()
        val text = when (kind) {
            "list" -> {
                assertFullyVisible(
                    pageControl(hasText(OtherProviderName) and hasClickAction()),
                    pageScroll(),
                    "lastProvider"
                )
                OtherProviderName
            }

            "config-save" -> {
                assertFullyVisible(
                    pageControl(hasText("保存设置") and hasClickAction()),
                    pageScroll(),
                    "save"
                )
                assertFullyVisible(
                    pageControl(hasContentDescription("删除提供商")),
                    pageScroll(),
                    "delete"
                )
                "保存设置"
            }

            else -> {
                val lastCard = hasText(LastModelName) and isSelectable()
                val edit =
                    pageControl(hasContentDescription("设置模型能力") and hasAnyAncestor(lastCard))
                val remove =
                    pageControl(hasContentDescription("移除模型") and hasAnyAncestor(lastCard))
                val add = pageControl(hasText("添加新模型") and hasClickAction())
                val fetch = pageControl(hasContentDescription("获取模型"))
                val tab = pageControl(hasText("模型") and hasClickAction())
                assertFullyVisible(edit, pageScroll(), "lastModelEdit")
                assertFullyVisible(remove, pageScroll(), "lastModelRemove")
                recordBounds("lastModelCard", pageControl(lastCard))
                recordBounds("addModel", add)
                recordBounds("fetchModels", fetch)
                recordBounds("modelsTab", tab)
                assertAbove(edit, add)
                assertAbove(remove, add)
                assertAbove(add, tab)
                "添加新模型"
            }
        }
        captureProvider(case, text)
    }

    private fun dialog(kind: String, dark: Boolean) {
        val suffix = if (kind == "fetched") "sheet" else "dialog"
        val case = ScreenshotCases.require("E11_$kind-$suffix-${shade(dark)}")
        renderProvider(case, list = kind == "template", dark = dark)
        val title: String
        val operation: String
        when (kind) {
            "template" -> {
                pageControl(hasText("新增") and hasClickAction()).performClick()
                title = "选择提供商模板"
                operation = "取消"
            }

            "protocol" -> {
                reveal(hasContentDescription("接口协议：查看说明")).performClick()
                title = "接口协议"
                operation = "知道了"
            }

            else -> {
                selectModels()
                when (kind) {
                    "fetched" -> {
                        pageControl(hasContentDescription("获取模型")).performClick()
                        title = "选择模型"
                        operation = "确认选择"
                    }

                    "manual-error" -> {
                        pageControl(hasText("添加新模型") and hasClickAction()).performClick()
                        title = "手动添加模型"
                        operation = "添加"
                        windowControl(title, hasText(operation) and hasClickAction()).performClick()
                        inWindow(title, hasText("模型 ID 不能为空")).performScrollTo()
                            .assertIsDisplayed()
                    }

                    "ability" -> {
                        scrollToEnd()
                        val lastCard = hasText(LastModelName) and isSelectable()
                        val edit = pageControl(
                            hasContentDescription("设置模型能力") and hasAnyAncestor(lastCard)
                        )
                        assertFullyVisible(edit, pageScroll(), "abilityEntry")
                        assertAbove(edit, pageControl(hasText("添加新模型") and hasClickAction()))
                        edit.performClick()
                        title = "覆盖模型能力"
                        operation = "保存"
                    }

                    else -> {
                        reveal(hasText("清空已添加")).performClick()
                        title = "清空模型"
                        operation = "确认清空"
                    }
                }
            }
        }
        composeRule.waitForIdle()
        captureProviderWindow(case, title, operation)
    }

    private fun shade(dark: Boolean) = if (dark) "dark" else "light"
}
