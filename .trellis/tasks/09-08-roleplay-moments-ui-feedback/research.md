# 反馈核对证据

核查日期：2026-09-08。代码基线：main / bdfcc03b / 2.2.11-dev。

本文件记录截图、当前源码、现有测试和 Git 历史可支持的结论；没有把静态核对写成设备复现或测试通过。

## R1：线上聊天背景与玻璃气泡关联

| 证据位置 | 观察与含义 |
| --- | --- |
| app/src/main/java/com/example/myapplication/ui/screen/roleplay/RoleplayScreen.kt:307 | 使用 scenario.backgroundUri 构造背景状态。 |
| app/src/main/java/com/example/myapplication/ui/screen/roleplay/RoleplayScreen.kt:507 | ONLINE_PHONE 走独立线上页面，因此用户所说“线上 / 线下”有明确的代码边界。 |
| app/src/main/java/com/example/myapplication/ui/component/roleplay/ImmersiveGlass.kt:97 | hasImage 由 imageBitmap 是否已加载决定，不只是背景 URI 是否非空。 |
| app/src/main/java/com/example/myapplication/ui/screen/roleplay/RoleplayOnlinePhoneContent.kt:1060 | rememberOnlinePhoneColors 根据 hasImage 在玻璃配色和所选主题配色之间切换。 |
| app/src/main/java/com/example/myapplication/ui/screen/roleplay/RoleplayOnlinePhoneContent.kt:1093 | 有背景图时，用户 / 角色气泡分别使用 alpha 为 0.12 / 0.16 的玻璃叠色；无背景图才使用 skinSpec.userBubble / characterBubble。这些数值是叠色 alpha，不是整个最终气泡的总不透明度。 |
| app/src/main/java/com/example/myapplication/ui/component/roleplay/RoleplayNoBackgroundSkin.kt:72 | rememberRoleplayDialogueBubbleStyle 仅在无背景图时使用主题宽度、圆角、内边距、尾巴；有背景图时转为默认样式。 |
| app/src/main/java/com/example/myapplication/model/AppSettings.kt:29 | roleplayNoBackgroundSkin 位于全局 AppSettings；未发现背景图与玻璃气泡独立开关。 |
| app/src/main/java/com/example/myapplication/model/RoleplayNoBackgroundSkinSettings.kt:3 | 现有配置只有主题预设、宽度、圆角、内边距和尾巴，没有解除背景与气泡关联的选项。 |
| app/src/main/java/com/example/myapplication/ui/screen/roleplay/RoleplaySettingsSections.kt:444 | 现有主题设置入口包含主题预览、预设及气泡样式。 |

结论：用户更换背景后，主题气泡被自动换成玻璃效果，与代码吻合。其需求应命名为“线上聊天背景图与玻璃气泡可独立选择”。

实现规划应同时考虑颜色和几何样式两处分支；仅降低或提高某个 alpha，无法完整满足“保留原主题气泡”。主题自身选择液态玻璃时，保留主题样式也可能仍有透明效果，这符合“沿用所选主题”的含义。

### 设计阶段补充核对

- AppSettingsStore.kt:899 使用显式 JSON 字段解码主题设置；新增布尔值必须在这里读取并为缺失字段提供 true 默认值，不能只改数据类。AppSettingsStore.kt:582 已序列化完整主题设置对象。
- SettingsPreferenceDraftSupport.kt:96、SettingsPersistenceCoordinator.kt:59 和 RoleplayNavGraph.kt:679 已按完整主题对象传递草稿和保存值，可复用此链路。
- RoleplayNavGraph.kt:794 的返回回调先保存设置再退出；沿用这一保存行为与失败反馈，不新增另一套即时写入链路。
- RoleplayDialogueBubbles.kt:81 与 :182 的气泡外壳直接根据 hasImage 选择 Surface 或 ImmersiveReadingGlassSurface。必须加入独立的材质选择，避免关闭后仍套用玻璃渲染。
- RoleplayMessageBubbles.kt:593 的宽度解析仍按 hasImage 决策；关闭玻璃效果时也需要回到主题宽度路径。
- RoleplayOnlinePhoneContent.kt:593 将同一 colors 对象传入消息，页面其他控件也使用它；需要单独派生消息颜色，保留页面控件配色。
- RoleplaySettingsSections.kt:677 的重置动作构造默认主题对象；新增开关后应显式保留独立开关值，避免重置圆角等样式时意外重开玻璃效果。
- 可扩展现有 RoleplayNoBackgroundSkinSettingsTest、AppSettingsStoreTest、SettingsPersistenceCoordinatorTest 和 RoleplaySettingsContentTest，复用已有 Fake 与 Compose 测试约定。

## R2：评论预览的大块纵向留白

| 证据位置 | 观察与含义 |
| --- | --- |
| app/src/main/java/com/example/myapplication/ui/screen/moments/MomentsScreen.kt:1005 | 时间线卡片使用 MomentEngagementBlock，并为评论预览提供点击 / 长按回调。 |
| app/src/main/java/com/example/myapplication/ui/screen/moments/MomentsScreen.kt:1201 | 评论容器条目间距使用 NarraUi.compactGap。 |
| app/src/main/java/com/example/myapplication/ui/screen/moments/MomentsScreen.kt:1223 | 可交互预览行设置 heightIn(min = NarraMinimumTouchTarget)。 |
| app/src/main/java/com/example/myapplication/ui/screen/moments/MomentsScreen.kt:1240 | 每条评论还设置上下各 NarraUi.compactGap 的内边距。 |
| app/src/main/java/com/example/myapplication/ui/theme/UiTokens.kt:5 | NarraMinimumTouchTarget = 48.dp。 |
| app/src/main/java/com/example/myapplication/ui/theme/UiTokens.kt:13 | NarraUi.compactGap = 8.dp。 |
| app/src/androidTest/java/com/example/myapplication/ui/screen/moments/MomentsScreenTest.kt:255 | 设备测试明确断言评论预览至少 48dp，并验证长按删除，因此现有测试也固定了当前高度约束。 |

48dp 是预览行最小高度，8dp 内边距参与该高度内的布局，不应把它们简单加成“固定 64dp 行高”。相邻短评论还会受外部 8dp 条目间距影响，因此呈现出明显的纵向空白。

### 回归来源

通过 git blame 与提交前文件对照，以下三处来自同一个提交：

- 提交：7b7903a1，2026-09-07，feat(ui): 完成 Narra 界面重设计与最终回归。
- 预览行由自然内容高度改为显式最小 48dp。
- 新增每条预览的上下 8dp 内边距。
- 容器条目间距由 4dp 改为 8dp。

核对使用的历史对象为 7b7903a1^ 中的 MomentsScreen.kt。此前已有评论单击与长按行为，并不依赖此次新增的显式 48dp 最小布局高度。

相关原有交互约定保存在 .trellis/tasks/archive/2026-09/09-03-moments-comment-delete/prd.md：预览行单击打开详情、长按触发删除确认，第五条以后的评论通过详情页查看。

### 后续验证关注点

- 不能只将容器间距从 8dp 改为 4dp 就假定问题解决；预览行的最小高度和内边距也影响视觉密度。
- 核对四条短评论和多行评论的实际布局，同时验证各行点击与长按的命中，避免紧凑布局造成误操作。
- 现有评论预览高度断言需要与最终可接受的视觉及触达要求一起审视；单纯删除断言不能替代行为验证。
- 不应因此批量缩小应用所有按钮或修改共享 NarraMinimumTouchTarget。

## 证据边界

- 配图失败只在截图中可见，没有错误日志、请求配置或复现条件，不能推断它与本次反馈的两项 UI 行为具有相同原因。
- 初始需求核查阶段未运行 Gradle、模拟器或真机测试，也未修改产品代码；后续实现及验证结果另见 [verification.md](verification.md)。
- 用户于 2026-09-08 确认目标开发版本为下一版 2.2.12-dev，并确认开关全局作用于线上聊天、默认开启；上述源码核查基线仍为 2.2.11-dev。
