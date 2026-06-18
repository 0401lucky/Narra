# 发现区 UI 重排设计稿（发现页 / 摇一摇 / 角色图工作台）

- 日期：2026-06-18
- 分支：`feat/discover-ui-redesign`
- 状态：已确认，待实现

## 背景与问题

沉浸式手机壳（`ImmersivePhoneShell.kt`）的"发现"Tab 当前是一条龙竖直列表：今日动态卡 + 摇一摇入口 + 角色图工作台入口 + 5 个功能行（朋友圈 / 查手机 / 日记本 / 信箱 / 视频通话）。它用 Material 系统默认配色、8dp 小圆角，观感偏"系统朴素风"。

而角色图工作台（`CharacterArtStudioScreen.kt`）用自定义 `rememberSettingsPalette()` + 渐变 Hero + 大圆角（18~28dp），明显更精致。两块视觉语言不统一。

关键发现：`rememberSettingsPalette()` 本质是把 `MaterialTheme.colorScheme` 重命名为语义色（accent=primary、body=onSurfaceVariant…），**两块底层是同一套配色**。所以"割裂"不在颜色，而在形态（圆角 / 渐变 / 留白 / 层次）。

## 设计目标

力度：**重排布局 + 信息架构**，统一到精致风。

1. 发现页从线性列表改为**分区式**：动态概览 / AI 创作 / 角色空间。
2. 摇一摇 Sheet 重排，做出**视觉仪式感**（保留按钮触发，不接传感器）。
3. 角色图工作台改为**单页分段**结构。
4. 三块统一视觉语言。

## 非目标（明确不做）

- 不改配色方案（仅复用现有 `colorScheme` / `rememberSettingsPalette()`），保证暗色模式零风险。
- 不接入重力传感器 / 真实摇晃手势（保持按钮触发）。
- 不改 `CharacterShakeViewModel`、`CharacterArtStudioViewModel` 及任何数据层逻辑。
- 不做无关重构。

## 共用视觉语言

- 圆角：卡片 / 磁贴 `20~24dp`，chip `12~14dp`（替换现 8dp）。
- 渐变点缀：磁贴、概览卡、摇一摇主区用 `palette.accent` 柔和线性渐变，与工作台 Hero 一致。
- 留白：分区之间加大间距，区内 `Arrangement.spacedBy`。
- 新增可复用小组件 `DiscoverSectionHeader`（图标 + 分区标题），发现页内三处复用。

## 1. 发现页 · 分区式（`ImmersiveDiscoverPage`）

```
今日动态（渐变概览卡，保留 未读/动态/通话 统计）

✨ AI 创作
  [ 🎲 摇一摇 ]  [ 🎨 角色图工作台 ]   ← 等高双磁贴，渐变背景

🪪 角色空间
  [💬朋友圈] [📱查手机] [📖日记]        ← 3 列图标网格
  [✉信箱]   [📹视频通话]
```

- 今日动态：数据来源不变（`RoleplayPhoneEcosystemSnapshot`），外壳升级为大圆角 + 渐变。
- 双磁贴：图标 + 标题 + 副标题；摇一摇用 accent 系、角色图用 secondary 系区分。
- 角色空间：`DiscoverTarget.entries`（5 项）改为图标网格，弱化为次级功能。

## 2. 摇一摇 Sheet · 视觉仪式感（`CharacterShakeSheet`）

```
"摇"主区（渐变背景）
  [ 🎲 摇一摇 ] 大按钮，点击时骰子图标摇晃动效 + 震动反馈
  当前：完全随机 / 已选 N 项

偏好设置 ⌄        ← 默认折叠，点开才显示 6 组筛选 chip

生成结果卡        ← 上移 + 放大，大头像 + 渐变边框
```

- "摇"做成主视觉：醒目大按钮，点击触发骰子图标摇晃动效 + `HapticFeedback` 震动。
- 默认一键随机摇；6 组偏好（`CharacterShakeFilterGroups`）默认折叠（"偏好设置 ⌄"），展开后才显示。
- 生成结果卡上移、放大，强化"摇到了"的成就感。
- 折叠状态为 Sheet 内 `remember` 本地 UI 状态，ViewModel 不改。

## 3. 角色图工作台 · 单页分段（`CharacterArtStudioScreen`）

```
[ Hero 区：头像 + 图片预览 ]   ← 保留

① 角色 & 风格 —— 角色横滑选择 + 风格 chip
② 视觉提示词 —— 修改意见 + 提取按钮 + 生图提示词 + 避免元素(⌄ 折叠)
③ 生成 & 应用 —— 生成角色图 + 设为头像
```

- 加 `①②③` 编号分段标题，把现有内容分成三段，结构一眼可见。
- "避免元素"（负向提示词）默认折叠，减少一屏信息量。
- 保持单页可自由滚动，反复改提示词重生成不受打断。

## 改动范围

| 文件 | 改动 |
|------|------|
| `ui/screen/immersive/ImmersivePhoneShell.kt` | 重写 `ImmersiveDiscoverPage` 分区化；`CharacterShakeEntry` / `CharacterArtStudioEntry` 改双磁贴；`DiscoverTarget` 功能行改图标网格；`CharacterShakeSheet` 加折叠 + 动效 + 震动；新增 `DiscoverSectionHeader` |
| `ui/screen/settings/CharacterArtStudioScreen.kt` | 加 `①②③` 编号分段标题、"避免元素"折叠 |
| ViewModel / 数据层 | 不改 |

## 验证

- `./gradlew.bat app:compileDebugKotlin` 编译通过。
- `./gradlew.bat app:testDebugUnitTest` 现有单测全绿（本次为纯 UI 改动，不应触发逻辑测试回归）。
- 人工走查：浅色 / 深色模式下发现页三个分区、摇一摇折叠展开与动效、工作台分段与折叠显示正常。
