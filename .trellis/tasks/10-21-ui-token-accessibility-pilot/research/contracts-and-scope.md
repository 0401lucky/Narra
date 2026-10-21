# 统一方向的源码契约与范围

## 确定事实（2026-10-21）

| 锚点（相对 `app/src/main/java/com/example/myapplication/`） | 事实与影响 |
| --- | --- |
| `ui/theme/UiTokens.kt:8` | `NarraUi` 混合页面、布局和图标尺寸；已有别名不能整体改值影响试点外调用方 |
| `ui/theme/ChatAppTheme.kt:103` | MaterialTheme 尚未显式安装 shapes；新增将影响隐式使用 Material 形状的组件 |
| `ui/theme/ChatAppTheme.kt:111` | `resolveAppColorScheme` 为应用 / 原生窗口共用颜色入口；不能另建系统深色判定替代它 |
| `model/RoleplayNoBackgroundSkinSettings.kt:3` | 设置只有数值与预设，没有手调 / 默认来源字段；旧值等于默认常量不能成为改写依据 |
| `ui/component/roleplay/RoleplayNoBackgroundSkin.kt:46` | 默认玻璃样式使用 22dp 圆角、18 / 16dp padding，可按统一方向修改默认路径 |
| `ui/component/roleplay/RoleplayNoBackgroundSkin.kt:64` | `rememberRoleplayNoBackgroundSkinSpec` 只以 normalized 设置为键，解析还未接收实际 Material scheme |
| `ui/component/roleplay/RoleplayNoBackgroundSkin.kt:131` | 13 套静态预设表起于 Narra；Narra 默认色将改为 Material，其他品牌数据集中保留 |
| `ui/component/roleplay/RoleplayDialoguePanelParts.kt:55` | 已有 `ImmersiveRoleplayColors`；优先扩展现有语义角色，不再新造并存颜色模型 |
| `ui/screen/roleplay/RoleplayOnlinePhoneContent.kt:110` | 五个在线特殊操作固定 Color 常量，应映射到语义角色 / 登记品牌例外 |
| `ui/screen/roleplay/RoleplayOnlinePhoneContent.kt:115` | 顶 / 底跳转按钮可见尺寸为 42dp，应核验并修正最小命中 |
| `ui/screen/roleplay/RoleplayOnlinePhoneContent.kt:1071` | 在线配色 remember 的键未包含 Material scheme，需随主题解析一并修正 |
| `ui/component/roleplay/ImmersiveGlass.kt:126` | 背景解析有本地缓存键；有图键只包含 URI / 尺寸 / highContrast，无图仅部分主题颜色，不能证明完整主题切换正确 |
| `ui/screen/settings/AssistantListScreen.kt:32` | P02 真实入口仍接收 SettingsViewModel；沿用 screenshot 的真实 VM + FakeSettingsStore，不为样式任务重构数据边界 |
| `ui/screen/settings/AssistantListScreen.kt:139` | 角色卡为生产组件，已有 selected 语义，应保留并检查触控 / 菜单 |

上表为本次规划阅读时的行号；实现后更新验收记录，不把行号变化误判为事实失效。

## 截图口径修正

`app/src/test/snapshots/manifest.md` 与 `ScreenshotCases.kt` 是数量事实来源：H01–H04 / B01–B04 / C01 共 76 个登记用例；另有 2 张 `MessageBubbleScreenshotTest` 冒烟基准，总计 78 张 PNG。旧任务个别段落称 76 张，不能据此漏掉两个冒烟文件的哈希保护。

`ScreenshotRenderTest.kt` 的组件舞台目前手写 padding 12 / 14dp、间距 10dp；迁移后应从生产布局契约取得相应值，避免测试舞台继续模拟旧布局。`ScreenshotStageTest.setColorSchemeStage` 直接安装 MaterialTheme，未来也需安装同一 shapes 才能验证固定动态色的生产几何。

## 违规门禁的技术选择

仓库已有 `ProguardRulesTest`、`RoleplayOnlinePhoneMessageListKeyTest` 和 `ScreenshotBaselineGuardTest` 的源码 / 文件守卫模式，没有 custom Lint / detekt 模块。本轮采用有明确词法 / 用途范围的 JVM 守卫，以最小依赖覆盖迁移文件；规则不声称解析完整 Kotlin 类型系统。对比度、缓存和交互另用行为测试，不能让静态匹配代替实际行为。

`app/build.gradle:160` 显式将 `narra.screenshot.snapshotDir` 注入为 `src/test/snapshots`；`:178` 的 Roborazzi outputDir 也固定为该位置。直接 record 会覆盖已确认图，不能在产出候选前这样做。优先使用 verify 的 actual / compare 输出；若新覆盖必须生成候选，输出隔离能力需先实现和验证，再写入专用候选目录。

## 精确例外分类

- 当前 / 历史保存的用户几何：集中由设置解析承接，原值优先。
- 品牌皮肤：非 Narra 的 12 个预设仅对定义内颜色和装饰保留例外；阅读文字仍受对比度检查。
- 形状尾角、可见图标尺寸、边框、系统 Insets、图片尺寸：按几何用途管理，不作为布局间距违规。
- 非试点共享调用方：保留兼容别名，不对整个文件 / 目录放行；必要视觉影响记录并回归。
- P01 / P03 已登记的大字体缺陷纳入 T4 修复；背景加载失败占位属于后续业务状态任务。

## 设备证据

旧安全网登记在 2026-10-14 无物理设备，其性能归档均为不可比合成数据。这是历史事实，不是 2026-10-21 的设备探测结果；实施 Wave 0 需要重新只读检查。有效真机对照缺失时，宿主截图、模拟器交互与性能结论严格分开。
