package com.example.myapplication.ui.screenshot

import com.example.myapplication.model.AppColorTheme
import com.example.myapplication.model.RoleplayNoBackgroundSkinPreset

/**
 * 单个截图用例的登记信息：用例 ID、基准文件相对路径、归属测试类与舞台参数。
 *
 * 用例 ID 就是基准文件名去掉 `.png`；文件名前缀必须包含矩阵 ID（H01..C01）。
 * 测试只允许通过 [ScreenshotCases.require] 取用例，不允许自造路径。
 */
data class ScreenshotCase(
    val id: String,
    val relativePath: String,
    val testClass: String,
    val description: String,
    val stageWidthDp: Int = ScreenshotCases.NormalWidthDp,
    val stageHeightDp: Int = ScreenshotCases.NormalHeightDp,
    val fontScale: Float = ScreenshotFixture.NormalFontScale,
) {
    /** 矩阵 ID，例如 H01 / B03 / C01。 */
    val family: String get() = id.substringBefore('_')

    val fileName: String get() = relativePath.substringAfterLast('/')
}

/**
 * 截图矩阵的单一登记表：H01–H04、B01–B04、C01 及扩面 E04 / E05 / E10 / E11。
 *
 * 测试、基准计数守卫与运行清单都从这里取数据；新增/删除用例必须同步更新
 * [expectedFamilyCounts]，守卫会在数量不符时失败。
 */
object ScreenshotCases {

    const val NormalWidthDp = 360
    const val NormalHeightDp = 800
    const val NarrowWidthDp = 320
    const val NarrowHeightDp = 640

    /** 常规与窄屏舞台的固定像素（density 2）。 */
    const val NormalWidthPx = NormalWidthDp * 2
    const val NormalHeightPx = NormalHeightDp * 2
    const val NarrowWidthPx = NarrowWidthDp * 2
    const val NarrowHeightPx = NarrowHeightDp * 2

    val themeTokens: List<String> = AppColorTheme.entries.map { it.storageValue }
    val shadeTokens: List<String> = listOf("light", "dark")
    val skinTokens: List<String> = RoleplayNoBackgroundSkinPreset.entries.map { it.storageValue }

    private const val H01Class = "PageScreenshotH01Test"
    private const val H02Class = "PageScreenshotH02Test"
    private const val H03Class = "PageScreenshotH03Test"
    private const val H04Class = "PageScreenshotH04Test"
    private const val B01Class = "SkinScreenshotB01Test"
    private const val B02Class = "BubbleScreenshotB02Test"
    private const val B03Class = "BubbleScreenshotB03Test"
    private const val B04Class = "BubbleScreenshotB04Test"
    private const val C01Class = "ThemeScreenshotC01Test"
    private const val E04Class = "OfflineRoleplayScreenshotE04Test"

    private val h01Cases: List<ScreenshotCase> = buildList {
        themeTokens.forEach { theme ->
            shadeTokens.forEach { shade ->
                add(
                    case(
                        testClass = H01Class,
                        fileName = "H01_p01-messages-$theme-$shade.png",
                        description = "P01 消息首页 / 手机主壳 · $theme · $shade",
                    ),
                )
                add(
                    case(
                        testClass = H01Class,
                        fileName = "H01_p02-assistants-$theme-$shade.png",
                        description = "P02 角色管理列表 · $theme · $shade",
                    ),
                )
                add(
                    case(
                        testClass = H01Class,
                        fileName = "H01_p03-online-roleplay-$theme-$shade.png",
                        description = "P03 线上扮演聊天 · $theme · $shade",
                    ),
                )
            }
        }
    }

    private val h02Cases: List<ScreenshotCase> = listOf(
        case(H02Class, "H02_p01-messages-empty.png", "P01 现有空态：还没有会话"),
        case(H02Class, "H02_p02-assistants-no-search-result.png", "P02 现有空态：搜索无匹配角色"),
        case(H02Class, "H02_p03-online-empty-session.png", "P03 现有空态：空会话"),
    )

    private val h03Cases: List<ScreenshotCase> = listOf(
        case(H03Class, "H03_p03-online-long-reply.png", "P03 跨多屏长回复"),
        case(H03Class, "H03_p03-online-streaming-frame.png", "P03 固定流式中间帧"),
        case(H03Class, "H03_p03-online-error-message.png", "P03 回合生成失败消息"),
    )

    private val h04Cases: List<ScreenshotCase> = buildList {
        shadeTokens.forEach { shade ->
            add(
                case(
                    testClass = H04Class,
                    fileName = "H04_p01-messages-narrow-$shade.png",
                    description = "P01 窄屏 320x640dp + 200% 字体 · $shade",
                    stageWidthDp = NarrowWidthDp,
                    stageHeightDp = NarrowHeightDp,
                    fontScale = ScreenshotFixture.NarrowFontScale,
                ),
            )
            add(
                case(
                    testClass = H04Class,
                    fileName = "H04_p02-assistants-narrow-$shade.png",
                    description = "P02 窄屏 320x640dp + 200% 字体 · $shade",
                    stageWidthDp = NarrowWidthDp,
                    stageHeightDp = NarrowHeightDp,
                    fontScale = ScreenshotFixture.NarrowFontScale,
                ),
            )
            add(
                case(
                    testClass = H04Class,
                    fileName = "H04_p03-online-narrow-$shade.png",
                    description = "P03 窄屏 320x640dp + 200% 字体 · $shade",
                    stageWidthDp = NarrowWidthDp,
                    stageHeightDp = NarrowHeightDp,
                    fontScale = ScreenshotFixture.NarrowFontScale,
                ),
            )
        }
    }

    private val b01Cases: List<ScreenshotCase> = buildList {
        skinTokens.forEach { skin ->
            shadeTokens.forEach { shade ->
                add(
                    case(
                        testClass = B01Class,
                        fileName = "B01_$skin-bubbles-$shade.png",
                        description = "无背景皮肤 $skin · 用户/角色/旁白气泡 · $shade",
                    ),
                )
            }
        }
    }

    private val b02Cases: List<ScreenshotCase> = listOf(
        case(B02Class, "B02_p03-no-background.png", "P03 未配置背景：皮肤气泡"),
        case(B02Class, "B02_p03-background-glass-on.png", "P03 背景加载成功 + 线上玻璃开"),
        case(B02Class, "B02_p03-background-glass-off.png", "P03 背景加载成功 + 线上玻璃关"),
        case(B02Class, "B02_p03-background-load-failed.png", "P03 已配置背景但未加载成功（回退皮肤气泡）"),
    )

    private val b03Cases: List<ScreenshotCase> = listOf(
        case(B03Class, "B03_radius-min.png", "气泡圆角下限 4dp"),
        case(B03Class, "B03_radius-max.png", "气泡圆角上限 28dp"),
        case(B03Class, "B03_width-min.png", "气泡最大宽度下限 64%"),
        case(B03Class, "B03_width-max.png", "气泡最大宽度上限 92%"),
        case(B03Class, "B03_padding-horizontal-min.png", "横向内边距下限 8dp"),
        case(B03Class, "B03_padding-horizontal-max.png", "横向内边距上限 20dp"),
        case(B03Class, "B03_padding-vertical-min.png", "纵向内边距下限 6dp"),
        case(B03Class, "B03_padding-vertical-max.png", "纵向内边距上限 18dp"),
        case(B03Class, "B03_tail-on.png", "气泡尾角开启"),
        case(B03Class, "B03_tail-off.png", "气泡尾角关闭"),
    )

    private val b04Cases: List<ScreenshotCase> = listOf(
        case(B04Class, "B04_online-glass-on.png", "线上模式 + 有背景 + 玻璃开关开（页面级）"),
        case(B04Class, "B04_online-glass-off.png", "线上模式 + 有背景 + 玻璃开关关（页面级）"),
        case(B04Class, "B04_offline-background.png", "线下模式 + 有背景（组件级玻璃气泡）"),
        case(B04Class, "B04_offline-no-background.png", "线下模式 + 无背景（组件级皮肤气泡）"),
    )

    private val c01Cases: List<ScreenshotCase> = listOf(
        case(C01Class, "C01_dynamic-light.png", "固定动态 ColorScheme · 浅色解析"),
        case(C01Class, "C01_dynamic-dark.png", "固定动态 ColorScheme · 深色解析"),
    )

    private val e04Cases: List<ScreenshotCase> = buildList {
        themeTokens.forEach { theme -> shadeTokens.forEach { shade ->
            add(case(E04Class, "E04_scene-$theme-$shade.png", "P04 线下对白整页 · $theme · $shade"))
        } }
        add(case(E04Class, "E04_scene-empty.png", "P04 线下空态 · 固定动画帧"))
        add(case(E04Class, "E04_scene-narrow-light.png", "P04 长名与展开建议 · 200%",
            NarrowWidthDp, NarrowHeightDp, ScreenshotFixture.NarrowFontScale))
        add(case(E04Class, "E04_scene-narrow-dark.png", "P04 长文发送与立绘 · 200%",
            NarrowWidthDp, NarrowHeightDp, ScreenshotFixture.NarrowFontScale))
        add(case(E04Class, "E04_scene-background.png", "P04 实际场景遮罩与玻璃"))
        add(case(E04Class, "E04_scene-suggestions.png", "P04 常规长建议与标签"))
        add(case(E04Class, "E04_reading-mixed-background.png", "会话阅读混排与实际背景"))
        add(case(E04Class, "E04_reading-empty-dark.png", "会话阅读深色空态"))
        add(case(E04Class, "E04_reading-narrow-dark.png", "会话阅读长标题长文 · 200%",
            NarrowWidthDp, NarrowHeightDp, ScreenshotFixture.NarrowFontScale))
    }

    private val e05Cases: List<ScreenshotCase> = buildList {
        themeTokens.forEach { theme -> shadeTokens.forEach { shade ->
            add(case("SessionSidebarScreenshotE05Test", "E05_main-$theme-$shade.png", "P05 会话设置 · $theme · $shade"))
        } }
        listOf("main-dynamic-light", "main-dynamic-dark").forEach {
            add(case("SessionSidebarScreenshotE05Test", "E05_$it.png", "P05 固定动态色 · $it"))
        }
        listOf("main-narrow-light", "main-narrow-dark", "theme-narrow-light", "theme-narrow-dark",
            "preset-sheet-light", "mask-sheet-dark", "help-dialog-dark", "model-sheet-light",
            "reset-dialog-dark", "group-narrow-light").forEach {
            add(case("SessionSidebarScreenshotE05Test", "E05_$it.png", "P05 真实面板 / 窗口 · $it",
                NarrowWidthDp, NarrowHeightDp, ScreenshotFixture.NarrowFontScale))
        }
    }

    private val e10Cases: List<ScreenshotCase> = buildList {
        themeTokens.forEach { theme ->
            shadeTokens.forEach { shade ->
                add(case("SettingsHomeScreenshotE10Test", "E10_home-$theme-$shade.png",
                    "P10 设置首页与固定长摘要 · $theme · $shade"))
            }
        }
        shadeTokens.forEach { shade ->
            add(case("SettingsHomeScreenshotE10Test", "E10_home-dynamic-$shade.png",
                "P10 设置首页 · 固定动态色 · $shade"))
            listOf("home-narrow", "theme-sheet", "mode-sheet", "display-sheet").forEach { scene ->
                add(case("SettingsHomeScreenshotE10Test", "E10_$scene-$shade.png",
                    "P10 真实首页 / 外观窗口 · $scene · $shade · 200%",
                    NarrowWidthDp, NarrowHeightDp, ScreenshotFixture.NarrowFontScale))
            }
        }
    }

    private val e11Cases: List<ScreenshotCase> = buildList {
        themeTokens.forEach { theme ->
            shadeTokens.forEach { shade ->
                add(case("ProviderSettingsScreenshotE11Test", "E11_list-$theme-$shade.png",
                    "P11 提供商列表、长名称与连接状态 · $theme · $shade"))
            }
        }
        shadeTokens.forEach { shade ->
            listOf("config", "models", "config-dynamic").forEach { scene ->
                add(case("ProviderSettingsScreenshotE11Test", "E11_$scene-$shade.png",
                    "P11 提供商详情 · $scene · $shade"))
            }
            listOf("list-narrow", "config-save-narrow", "models-tail-narrow",
                "template-dialog", "fetched-sheet", "manual-error-dialog", "ability-dialog",
                "clear-dialog", "protocol-dialog").forEach { scene ->
                add(case("ProviderSettingsScreenshotE11Test", "E11_$scene-$shade.png",
                    "P11 真实页面 / 独立窗口 · $scene · $shade · 200%",
                    NarrowWidthDp, NarrowHeightDp, ScreenshotFixture.NarrowFontScale))
            }
        }
        add(case("ProviderSettingsScreenshotE11Test", "E11_list-no-result-light.png",
            "P11 提供商搜索无结果 · light"))
        add(case("ProviderSettingsScreenshotE11Test", "E11_models-empty-dark.png",
            "P11 提供商模型空态 · dark"))
    }

    val all: List<ScreenshotCase> = h01Cases + h02Cases + h03Cases + h04Cases +
        b01Cases + b02Cases + b03Cases + b04Cases + c01Cases + e04Cases + e05Cases + e10Cases + e11Cases

    /** 冻结的分族数量：缺场景、零测试或静默删减都会让守卫失败。 */
    val expectedFamilyCounts: Map<String, Int> = mapOf(
        "H01" to 18,
        "H02" to 3,
        "H03" to 3,
        "H04" to 6,
        "B01" to 26,
        "B02" to 4,
        "B03" to 10,
        "B04" to 4,
        "C01" to 2,
        "E04" to 14,
        "E05" to 18,
        "E10" to 16,
        "E11" to 32,
    )

    private val byId: Map<String, ScreenshotCase> = all.associateBy { it.id }

    fun require(id: String): ScreenshotCase =
        byId[id] ?: error("未登记的截图用例 id：$id")

    fun casesOf(testClass: String): List<ScreenshotCase> = all.filter { it.testClass == testClass }

    private fun case(
        testClass: String,
        fileName: String,
        description: String,
        stageWidthDp: Int = NormalWidthDp,
        stageHeightDp: Int = NormalHeightDp,
        fontScale: Float = ScreenshotFixture.NormalFontScale,
    ) = ScreenshotCase(
        id = fileName.removeSuffix(".png"),
        relativePath = "$testClass/$fileName",
        testClass = testClass,
        description = description,
        stageWidthDp = stageWidthDp,
        stageHeightDp = stageHeightDp,
        fontScale = fontScale,
    )
}
