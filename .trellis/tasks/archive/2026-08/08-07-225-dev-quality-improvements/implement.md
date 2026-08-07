# 2.2.5-dev 稳定性与可用性修复：实施计划

## Completion Definition

群聊可靠性、音色 Sheet 滚动和单条消息删除均完成独立验收，三项集成回归与
2.2.5-dev 版本文档同步完成，质量门禁取得真实结果后，父任务才算完成。

## Ordered Checklist

1. **实施前收口（PAC1-PAC4）**
   - 三个子任务分别运行 `trellis-before-dev`，加载与各自范围匹配的规范和 research。
   - 检查工作区并保留既有用户改动。
   - 确认性能任务已独立，不纳入本轮代码、测试和验收依赖。

2. **并行实现群聊可靠性与音色滚动（PAC1、PAC2）**
   - 群聊任务负责 `RoleplaySendActionSupport`、`RoleplayRoundTripExecutor` 及其测试，
     隔离正文提交与后处理并补总流程状态兜底。
   - 音色任务只负责 `VoiceSynthesisSettingsScreen` 及对应 Compose 设备测试，加入单一
     滚动容器和 IME/导航栏安全区。
   - 两路分别完成 `trellis-check` 后再汇总；不得互相回退对方改动。

3. **实现单条消息删除（PAC3）**
   - 先完成纯消息变换、DAO/Store 原子事务和 Repository 契约。
   - 再接普通聊天和 RP 的删除菜单、二次确认、发送状态门禁与引用草稿清理。
   - 重新运行与群聊共用的 RP ViewModel、回调和 UI 测试，解决集成冲突。

4. **同步 2.2.5-dev 文档（PAC4）**
   - 更新 `版本功能添加/优化.md`、`CHANGELOG.md`、`docs/版本策略.md` 和
     `docs/发布候选记录.md`。
   - 性能专项不写入本轮“已完成”说明；可在后续规划中单独记录。
   - 只有拿到最终 APK 链接、哈希和公开可用性后，才更新 `docs/updates/dev.json`。

5. **质量门禁与集成审查（PAC1-PAC4）**
   - 运行三个子任务的窄测、全量 JVM 单测、设备测试源码编译、Lint 和 debug 构建。
   - 有设备时运行 Compose/Room 设备测试并实际验证音色 Sheet 滚动与删除确认流程。
   - 使用 `trellis-check` 检查跨层数据流、取消、错误脱敏、Room 原子性和文件范围。
   - 检查 `git status`，不纳入无关截图、日志或用户改动。

## Validation Commands

```powershell
.\gradlew.bat :app:testDebugUnitTest --console=plain
.\gradlew.bat :app:compileDebugAndroidTestKotlin --console=plain
.\gradlew.bat lintDebug --console=plain
.\gradlew.bat assembleDebug --console=plain
```

有设备或模拟器时：

```powershell
.\gradlew.bat connectedDebugAndroidTest --console=plain
```

每个后台单元测试或 Gradle 调用按 60 秒上限取得结果；超时不能写成通过。

## Risky Files and Review Gates

- `RoleplaySendActionSupport.kt`、`RoleplayRoundTripExecutor.kt`：完成边界和发送终态，
  必须先补失败矩阵测试。
- `VoiceSynthesisSettingsScreen.kt`：只改 Sheet 布局和测试入口，不改设置模型/存储。
- `ConversationStore.kt`、`RoomConversationStore.kt`、`ConversationDao.kt`、`MemoryDao.kt`：
  删除不可逆，必须先验证单事务提交与回滚。
- `ChatViewModel.kt`、`RoleplayViewModel.kt` 及 RP 测试：删除任务在群聊任务完成后接入，
  避免并发写冲突并执行共同回归。

## Rollback Points

- 群聊后处理隔离与状态兜底可分别回退，保留新增回归测试。
- 音色布局可独立回退，不影响设置数据。
- 删除 UI 与底层删除契约分开审查；UI 有问题可隐藏入口，不恢复已删除数据。

## Acceptance Mapping

- PAC1 → 步骤 2、5。
- PAC2 → 步骤 2、5。
- PAC3 → 步骤 3、5。
- PAC4 → 步骤 1、4、5。
