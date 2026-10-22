# P04 技术设计

沿用 [父设计](../10-21-ui-token-accessibility-expansion/design.md) 的令牌、兼容、截图和所有权契约。源码依据为 [线下研究](../10-21-ui-token-accessibility-expansion/research/offline-entry.md)，其中每项风险均需运行时测量再定修复。

## 文件所有权与检查点

- P04.1：`RoleplayScreen.kt` 的线下分支 / accent、`RoleplayScreenContent.kt` 的场景声明、`RoleplayDialoguePanel.kt`、`RoleplayDialoguePanelParts.kt` 的线下建议 / 空态；不重写同文件共享覆盖层。按实际失败才纳入 `RoleplayPortraitLayer.kt`、共享输入 / 气泡 / ImmersiveGlass，并回归 P03。
- P04.2：`RoleplayReadingMode.kt` 与阅读专属测试；必要的 `RoleplayLongformText.kt` 修改串行归同一所有者，回归会话气泡及受影响 P05 / 日记。
- 对话区继续使用 conversation identity、uiItemId、contentType 与原贴底策略。页面根据实际高度约束建议 / 快捷面板，不能给大字体硬编码补偿；默认立绘展示意图不变。
- 场景实际 mood scrim 与玻璃参与颜色验证，不以现有线上 scrim 的 B04 fixture 替代。阅读不新增皮肤参数 / 存储，保留现有独立阅读材质和系统安全区。
- 源码守卫登记本批具体声明，并删除已迁移建议 / 空态对应的 legacyLayout 精确例外；其他未迁移例外不误删。

## E04 最小截图矩阵：14 张

| 场景 | 数量 |
| --- | --- |
| 360×800 / 100%：OFFLINE_DIALOGUE、Narra、无图、三主题×浅深 | 6 |
| 线下空态，浅色，固定动画时钟 | 1 |
| 320×640 / 200%：浅色长名+展开建议；深色 LONGFORM+发送中+立绘 | 2 |
| 固定 syntheticBackdrop 成功、实际 scene scrim + 玻璃 | 1 |
| 常规长建议展开，含真实动作 / 标签 | 1 |
| 阅读混排+背景、阅读深色空态、阅读深色窄屏长标题 / 长文 | 3 |

从已有 ScreenshotFixture 复制场景时显式设为线下两模式，增加固定 THOUGHT / LONGFORM / suggestion。动态浅深、错误、生成 / 停止、NOIR / LIQUID_GLASS 与其余皮肤以实际布局 / 颜色和行为回归补足，不新增全组合 PNG。

## 行为与回退

复用 `RoleplaySceneContentTest`、`RoleplayScreenQuickActionsTest` 和 `PilotAccessibilityTest` 测量方法，新增真实 P04 / 阅读宿主断言。检查标题恢复、建议生成 / 应用 / 刷新 / 收起、发送 / 停止、主要快捷项、滚动到底、阅读关闭与返回。展开态测量消息 viewport、至少一行真实正文可读 / 可滚动到达、触控边界和操作互不重叠，不能以几 dp 正数高度代替可用性。

共享 P03 / 材质 / 用户几何回归失败时先修 P04.1，不进入阅读检查点；公共玻璃导致非本批回归时缩回调用点修正。截图 before / after、原基准哈希及人工接纳按父任务流程保留，两个检查点分别可回退，任务整体独立验收。
