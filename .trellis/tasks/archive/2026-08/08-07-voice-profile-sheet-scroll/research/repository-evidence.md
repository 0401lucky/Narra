# 仓库证据：角色音色配置滚动修复

## 已确认现状

- `VoiceSynthesisSettingsScreen.kt:715` 定义 `AssistantVoiceProfileSheet`。
- `VoiceSynthesisSettingsScreen.kt:785` 打开 `ModalBottomSheet`，其直接内容在
  `:786` 使用普通 `Column`；该容器只有宽度和 padding，没有滚动修饰符。
- 动态内容包含五种 `VoiceProfileMode`、声音克隆说明与错误、文本设计输入框等，
  最终“取消/保存”操作行位于 `VoiceSynthesisSettingsScreen.kt:936-950`。
- 草稿字段使用 `rememberSaveable(assistant.id)`，见
  `VoiceSynthesisSettingsScreen.kt:726-746`；布局修复不需要改变设置持久化契约。

## 测试缺口

- 当前 `app/src/androidTest` 未发现针对
  `AssistantVoiceProfileSheet` 或角色音色配置的滚动测试。
- `SettingsScreenTest.kt` 只覆盖设置入口级回调，不能证明该 Sheet 在小屏、长内容、
  大字体或输入法出现时可访问保存按钮。
- 目标 Composable 当前为 `private`；实现时优先从现有页面打开 Sheet，只有窄测确实
  受阻时才提取同包可见的内容 Composable。

## 最小改动边界

- 在现有 Sheet 内引入单一滚动容器、`rememberScrollState()`、`imePadding()` 和
  `navigationBarsPadding()`。
- 保留 `currentDraftProfile()`、`onTestProfile`、`onSaveProfile`、样本复制/删除和
  子级预置音色 Sheet 的现有行为。
- 不修改 MiMo 请求、语音设置模型、文件存储或全局设置页列表。

## 验证重点

- 预置音色、文本设计、声音克隆三种长内容状态均能滚动到操作行。
- 输入法和导航栏出现时保存节点不被永久遮挡。
- 模式切换后草稿仍是最新值，保存回调收到当前草稿。
- 设备测试无法执行时只能报告未验证，不能用编译成功替代交互验收。
