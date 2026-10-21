# 试点现状证据

## 来源

- `.trellis/tasks/archive/2026-10/10-14-ui-regression-safety-net/research/screenshot-matrix.md`
- `.trellis/tasks/archive/2026-10/10-14-ui-regression-safety-net/research/performance-baseline.md`
- `.trellis/tasks/archive/2026-10/10-14-ui-regression-safety-net/verification.md`
- `.trellis/tasks/archive/2026-10/10-14-narra-experience-consistency/research/current-state.md`
- `app/src/main/java/com/example/myapplication/ui/theme/UiTokens.kt`
- `app/src/main/java/com/example/myapplication/ui/theme/ChatAppTheme.kt`
- `app/src/main/java/com/example/myapplication/ui/component/roleplay/RoleplayUiTokens.kt`
- `.trellis/spec/backend/ui-state-guidelines.md`
- `.trellis/spec/backend/quality-guidelines.md`

## Baseline status

P01 / P02 / P03 的 H01–H04、B01–B04、C01 共 76 个矩阵用例及 2 张冒烟图（78 张 PNG）已于 2026-10-14 完成人工确认，并由 CI run 38573095548 零阈值复验。D01 真机效果和 A6 性能仍未执行；现有记录不能给出设备或性能通过结论。

已登记的现状缺陷包括：P03 窄屏 200% 字体首条消息被头部遮挡、输入区提示换行溢出；P01 窄屏底栏文字下沿裁切；B02 背景已配置但尚未加载时没有独立占位 / 失败 UI。统一方向的规划将前两类大字体问题纳入 T4，只有行为验证后才能标为修复；背景失败占位继续延期。

## Existing token and theme entry points

- `NarraUi` 位于 `ui/theme/UiTokens.kt`，当前已有页面横向 / 纵向内边距、区块间距、行间距、紧凑间距、卡片 / 小圆角、头像圆角、图标 tile 和导航高度；尚无完整的间距梯度或 Material 形状槽位。
- `NarraMinimumTouchTarget` 已为 48.dp，可作为可操作控件的最小命中契约；不能把它直接当作紧凑内容的视觉行高。
- `ChatAppTheme.resolveAppColorScheme` 已处理 `AppColorTheme`、浅深色和可选动态 `ColorScheme`，共享壳层应复用该入口。
- `RoleplayNoBackgroundSkin`、`RoleplayGlassTokens` 和 `deriveImmersiveGlassPalette` 共同控制 P03 气泡的皮肤、自定义几何和背景合成。真实背景加载状态与线上玻璃开关具有独立契约，不能通过清空 bitmap 或全局主题色代替。
- `RoleplayGlassTokens` 中的 16 / 18 / 20 / 28 / 30dp 为迁移前事实；统一方案逐项迁移默认用途，非试点使用方保留兼容值，不能纯数字批量替换。

## Pilot entry points

- P01：`ui/screen/immersive/ImmersivePhoneShell.kt`，包含底部导航、顶部操作、会话行、联系人入口及若干弹层；现有代码同时出现 `NarraUi`、直接 `RoundedCornerShape(...)`、散落 padding 和空图标语义。
- P02：`ui/screen/settings/AssistantListScreen.kt`，角色列表、空态 / 搜索结果和主要操作属于试点；共享设置组件需要按实际父语义判断图标是否应保持安静。
- P03：`ui/screen/roleplay/RoleplayOnlinePhoneContent.kt`、`ui/component/roleplay/RoleplayDialoguePanel.kt`、`RoleplayMessageBubbles.kt` 和 `RoleplayNoBackgroundSkin.kt`；统一方案迁移实际默认颜色和默认几何，已保存用户数值仍有优先权，见 `design.md`。

## Existing validation

- 截图验证：`.\gradlew.bat :app:verifyRoborazziDebug --tests "com.example.myapplication.ui.screenshot.*" --console=plain`，只比较，不自动生成基准。
- 普通门禁：`.\gradlew.bat :app:testDebugUnitTest --console=plain`、`.\gradlew.bat lintDebug --console=plain`、`.\gradlew.bat assembleDebug --console=plain`。
- Compose / 设备语义检查应优先使用已有 `androidTest` 规则与 test tag / 语义断言；截图 fixture 不能替代真实可操作节点检查。

## Historical decisions

之前的安全网任务明确保留 13 套皮肤、自定义气泡设置、紧凑评论和普通 Chat 入口范围；令牌试点当时只完成规划。2026-10-21 用户选择“统一吧”，当前 `prd.md` / `design.md` 已按该方向收敛，最终摘要仍需审阅后启动实施。
