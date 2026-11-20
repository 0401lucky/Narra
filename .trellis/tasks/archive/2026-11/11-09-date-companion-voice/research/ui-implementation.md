# D5 UI 与导航实施交接

2026-11-19，v2.4.1；原生 trellis-implement `voice_ui`，按 root 冻结合同实施。本记录为源码交接，不表示 JVM/设备已通过。

## 实际入口与接口

- `CompanionVoiceViewModel.factory(owner, settingsStore, repository, sessionFactory, savedState)`：依赖领域 Session/Repository，不接触 DAO、Provider、TTS 或播放器实现。独立角色 voice 草稿/CAS、冲突/未来版本保留与显式重载；收藏和音频缓存用量/清理来自仓储。
- `rememberCompanionVoiceBinding(model, owner, units, automaticCandidates, selection, synchronizeSelection, onOpenLibrary)` 是 Stage/Reader/LIBRARY 共用生产宿主接线。只有 RESUMED 宿主才能 offerAutomatic/play；暂停/离场 stop，恢复首帧用内存静音偏好 copy 重设候选基线。显式 firstBind 防止生命周期效果先于内容绑定时误播旧答；不写这个静音 copy 到设置。
- 会话 `CompanionDisplayBinding.voice` 接当前完整同源 SpeechUnits；Stage 片段菜单可直接点播本拍，原列表工具栏可选择台词/旁白后朗读。正文分段、raw/span 与 D4 本地播放器未改。
- Reader 的原多场次 selection 保持 units/ranges；声音回调经过同一 VM，不缩成当前场次，不 enterScenario。选择面板新增声音 overload，旧二参 `CompanionSpeechSelectionPanel(selection,onDismiss)` 保留。
- `AppRoutes.roleplayCompanionVoice(assistantId)` → `roleplay/companion-voice/{assistantId}`，编码/解码配对。LIBRARY owner 的 scenarioId 为 `voice-library:<assistantId>`，不借用旧场次生成上下文。角色详情、会话和 Reader 均有实际入口，因此删消息/场景后仍可从角色详情进入收藏。
- 翻译 ERROR 提供“重试翻译”与显式“读原文”；PENDING attachment 显示免费补存；无持久副本显示明确重新生成可能计费。界面只显示实际片段和原/朗读文本，说明整段响应/分段串行，不宣称 PCM 或逐字音频时钟。
- 收藏已有音频与“生成并收藏（可能计费）”是两个独立回调；收藏列表播放实际历史音频并标失效出处。手清音频缓存经确认后调用 clearCache，并用 Session.select(null) 撤销普通选区的旧 currentAudio，VM 保留合法用户 selection 供下次显式点播。收藏播放无普通 selection，文件被清理排除，可保留已有回执。没有平行音频有效性布尔。

## 修改范围

新增生产：`viewmodel/CompanionVoiceViewModel.kt`、`ui/screen/roleplay/CompanionVoiceScreen.kt`、`ui/navigation/CompanionVoiceRoutes.kt`。

定向修改：`CompanionStage.kt`、`CompanionReadingScreen.kt`、`CompanionPresentationRoutes.kt`、`RoleplayNavGraph.kt`、`AppRoutes.kt`、`AssistantDetailScreen.kt`、`SettingsAssistantNavRoutes.kt`。`RoleplayScreenContent.kt` 通过已有 DisplayBinding/StageViewport 消费，无需另改正文/布局。

新增测试：`CompanionVoiceViewModelTest.kt`（9 方法）；`CompanionVoiceUiFixture.kt`；`CompanionVoiceScreenTest.kt`（6 方法）；`CompanionVoicePlayerInstrumentedTest.kt`（1 方法）。

## 验证交接

本代理只执行定向 `git diff --check` 与 `rg` 阅读/方法计数；无 whitespace error。Git 仅提示既有换行策略，未改策略或无关文件。

root 统一运行的选择器：

```text
com.example.myapplication.viewmodel.CompanionVoiceViewModelTest
com.example.myapplication.ui.screen.roleplay.CompanionVoiceScreenTest
com.example.myapplication.system.audio.CompanionVoicePlayerInstrumentedTest
```

VM 测试覆盖 active-before-first-bind、后台/恢复基线、跨场旁白显示范围、voice 独立 CAS、冲突/SavedState 草稿、源修订/模式、未来设置/角色删除、清理失败和清理成功回执失效。真实 Compose 经生产 binding→VM→Fake Session 覆盖 Stage 点播/准备状态与显式生成、Reader 实际跨场多选、翻译失败显式选择、独立收藏/清缓存/取消、偏好保存、暂停/恢复。原生 smoke 在前台 ComponentActivity 上用合法合成短 WAV 驱动实际 Android PlayerFactory 的 prepare/start/stop/release。

本代理没有运行 Gradle、formatter、设备、真实模型/TTS；未 record/替换旧截图、改 schema/版本参数/更新 JSON/凭据/全局配置，未提交、推送、发布或归档。Fake UI 回调验证不代表真实 MiMo 质量；原生 smoke 也不代表物理设备音效。最终命令、失败修复和源码 SHA 由 root 记录。

## 后续边界

D6 可使用独立声音收藏 route 与保存的实际音频/文本/语言/来源快照，导出须经仓储查询完整收藏，不把阅读窗口当全部历史。真实 MiMo 质量、物理设备音频焦点/系统路由和新界面视觉接纳仍须单列；本轮未触碰 D4 旧截图基准。
