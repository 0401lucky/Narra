# 试点源码守卫

实现位于 `app/src/test/java/com/example/myapplication/ui/theme/UiTokenSourceGuard.kt`，
绑定清单为同目录 `UiTokenSourceInventory.kt`，由 `UiTokenSourceGuardTest` 在普通 JVM 单测中执行。
不引入 detekt、编译器插件或新的 Android Lint 模块。

## 检查范围

以下路径相对生产 `ui/`，整文件扫描包括顶层默认属性及后来移入的声明：

- `theme/UiTokens.kt`、`theme/ChatAppTheme.kt`、`theme/RoleplaySkinPalette.kt`。
- `component/NarraButton.kt`、`component/roleplay/RoleplayUiTokens.kt`。
- `component/roleplay/RoleplayNoBackgroundSkin.kt`、`RoleplayDialoguePanelParts.kt`、
  `RoleplayDialogueBubbles.kt`、`RoleplayMessageBubbles.kt`、`RoleplayInputBarComponents.kt`、
  `RoleplayOnlinePhoneBubbles.kt`（此行后五项沿用相同组件目录）。
- `screen/immersive/ImmersivePhoneShell.kt`、`screen/settings/AssistantListScreen.kt`、
  `screen/roleplay/RoleplayOnlinePhoneContent.kt`。

两个共享大文件只检查实际涉及的声明；其他声明逐个具名列在 `legacyDeclarations`，
新增 / 删除 / 改名均使声明范围检查失败，不对整个文件或目录豁免：

| 路径 | 扫描声明 | 未扫描的既有范围与理由 |
| --- | --- | --- |
| `component/roleplay/ImmersiveGlass.kt` | `paletteCache`、`ImmersivePaletteCacheKey`、`immersivePaletteCacheKey`、`rememberImmersiveBackdropState`、`readPaletteCache`、`writePaletteCache`、`ImmersiveReadingGlassSurface`、`roleplayDialogueReadingSurfaces` | 其余既有背景绘制、模糊、对比处理和 HSL / 图片采样算法不在本轮几何迁移中。包括 `ImmersiveBackdrop` 的 Matcha 装饰、`ImmersiveGlassSurface` 的 28dp 默认值、图片解码边界和 `ImmersiveGlassChip` 的历史形状 / 间距；准确的全部声明名保存在绑定清单中。 |
| `screen/settings/SettingsComponents.kt` | `SettingsTopBar` | 仅 P02 顶栏参数和实际顶栏涉及迁移。其他设置页面的组、行、头像、提示卡、弹窗等既有组件保留历史布局；准确的全部声明名保存在绑定清单中。 |

以上未扫描部分是后续扩面清单，不计作完成令牌统一。`NarraButton.kt`、
`RoleplayUiTokens.kt` 作为共享依赖额外检查，并不宣称其全部调用方已改版。
每个登记路径都以原文件 stem 约束新 Kotlin 文件，例如 `RoleplaySkinPaletteHelpers.kt` 和
`RoleplayDialogueBubblesColors.kt` 拆出后必须登记；固定入口 / 部分扫描声明须存在且唯一。

`RoleplayDialoguePanelParts.kt` 仍整文件扫描，但两个线下专用声明的 15 处原始几何匹配逐表达式冻结。
`RoleplaySuggestionSection` 与 `EmptyDialogueState` 的唯一调用在 `RoleplayDialoguePanel.kt`；
P03 使用独立线上 Helper 和空会话路径。这两处布局未迁移，不能写成线下整页完成；
新增表达式、修改旧值或增加相同调用次数仍会失败。对应 diff 已核对只改共享颜色解析与数据模型。

## 规则和精确例外

- 检查 `RoundedCornerShape` 的字面量 dp 或百分比，防止默认形状移入文件级属性后绕过。
- 检查固定 `Color(0x...)`、十进制 ARGB、字面量 RGB（命名 / 位置参数）及 `Color.Red` 等命名固定色。
  用户输入的 `Color(userArgb)`、`Color(color = settings.userColor)` 和用户 RGB 通道变量合法。
- 检查 `padding`、`absolutePadding`、`PaddingValues`、`spacedBy`、`Spacer` 调用中的非零
  dp 字面量；支持多行、命名参数、括号嵌套、负数 / 小数和算术组合。零布局间距保持中性。
- 普通 `size` / `widthIn` / `offset`、边框、头像、图标和系统 Insets 本身不作为布局间距；
  `Spacer` 中的宽高仍属于间距，特殊分隔线需要准确表达式例外。

例外键是“路径 + 顶层声明 + 规则 + 去空白的完整调用表达式”；每个例外另有用途和理由。
每个准确键的出现次数也必须一致，删除、改值、跨声明移动、增加相同表达式或新匹配都会失败。
主题拥有者的固定 `ColorScheme` 基色、十二套品牌皮肤和共享 pill 定义按准确声明 / 值登记，
不允许主题包整体放行。品牌调色函数中 Narra 返回空，由语义解析接手；该行为还需主题测试验证。
黑 / 白对比校正端点、真实透明渐变 / 无填充背景也分别按用途和准确次数登记。
其他几何例外为 `PhoneDestinationRow` 的 23dp 图标 + 16dp 间距对齐、P02 不使用的共享副标题 2dp 间距，
以及共享 `ImmersiveReadingGlassSurface` 对非试点保留的 28dp 默认形状（试点消息显式传统一形状）。

## 明确的词法边界

屏蔽行注释、嵌套块注释、普通 / raw 字符串和字符字面量，保留行号。
当前顶层声明识别遵循本仓库零缩进的函数 / 属性 / 类 / 对象格式，支持限定调用名和扩展函数名。
这不是完整 Kotlin 解析器：不追踪变量别名、import alias、常量算术、自定义包装函数或字符串模板中的表达式，
也不分析未登记目录。部分扫描文件若把逻辑塞入已有的旧声明，仍需人工 diff 审查。
颜色配对、实际对比度、用户配置、缓存和交互由行为测试负责。

## 定向验证

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.myapplication.ui.theme.UiTokenSourceGuardTest" --console=plain
```

规则正反例覆盖：默认形状 / 配色 / 间距、多行和算术、合法用户几何 / 图标 / Insets、注释与字符串、
准确品牌例外、陈旧 / 重复例外、新旧声明 / 文件漂移，以及一个几何例外不能放行同声明内的其他布局。
首轮实际运行 10 项测试，9 项规则正反例通过，生产绑定因未登记的准确兼容边界失败；
此次修订后为 12 项（增加固定 RGB / 用户通道及组件 / 主题拆文件覆盖），待主会话串行复验。
实际执行结果由主会话统一登记；编写测试不等于通过验证。
