# 角色音色配置滚动修复

## Goal

修复 2.2.5-dev 角色文本设计音色弹窗在小屏和长内容下无法滚动、保存按钮不可达的问题。

## Confirmed evidence

- `VoiceSynthesisSettingsScreen.kt:715` 的 `AssistantVoiceProfileSheet` 在
  `:786` 用普通 `Column` 承载动态内容，保存操作位于 `:949`。
- 页面外层 `VoiceSynthesisSettingsScreen` 已使用 `LazyColumn`；问题集中在角色音色
  `ModalBottomSheet` 的内容容器，而不是全局设置页列表。

## Requirements

- 弹窗内容在有限高度下可垂直滚动，且滚动状态不会因模式切换丢失。
- 底部操作区考虑导航栏和输入法安全区，用户能访问并点击“取消”“保存”。
- 预置音色选择、文本设计、声音克隆三种较长内容状态均不能被截断。
- 不改变现有草稿保存、试听和文件清理语义。

## Acceptance Criteria

- [ ] **AC1**：在小屏、长文本、声音克隆错误提示和输入法出现时，能滚动到保存按钮并触发
      `onSaveProfile`。
- [ ] **AC2**：弹窗关闭、模式切换和重新打开后，草稿状态与现有行为一致。
- [ ] **AC3**：新增或更新 Compose 设备测试，至少覆盖滚动、模式切换和保存回调。
- [ ] **AC4**：相关编译、Lint 和设备测试通过。

## Out of scope

- 重做语音配置页面视觉风格或改变 MiMo 接口、音频文件存储策略。
