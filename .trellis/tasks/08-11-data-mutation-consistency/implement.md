# 会话 mutation 与跨表一致性：实施计划

## Completion Definition

AC1～AC8 满足：同一会话的旧快照不能覆盖新消息，旧 run 只能清理自己的 Loading，跨表
删除 / 重启可故障回滚，文件副作用与 Room 提交分离，且既有消息删除、撤回和附件规则不回归。

## Ordered Checklist

1. **工作区与契约预检**
   - 读取当前消息删除相关 diff、归档任务和 mutation 规范。
   - 列出 Chat / RP 所有 read → transform → replace 入口及共享门禁。

2. **先写并发回归测试**
   - 构造编辑挂起 → 发送提交 → 编辑恢复，以及相反交错。
   - 覆盖旧 run Loading 清理、删除进行中触发其它 mutation 和不同会话互不阻塞。

3. **建立会话级所有权**
   - 复用 / 扩展现有 Mutex 和进行中状态，在锁内重新读取最新持久化消息。
   - 删除等待前旧快照整体 replace 的路径；finally 只释放自己的句柄。

4. **收口数据库事务**
   - 将会话关联清理纳入单一 `withTransaction`。
   - 将剧情删除和剧情重启的全部 Room 写入纳入原子编排。
   - 保持 Repository / Store 分层和无 schema 变化。

5. **分离文件清理**
   - 数据库提交后才执行图片 / 文件删除。
   - 失败记录脱敏、可重试语义，不改变事务成功结果。

6. **故障注入与质量验证**
   - 对每个 DAO 写入点注入失败并重观察相关 Flow。
   - 运行定向 JVM、Room androidTest 源码编译、Lint 和 assembleDebug。
   - 检查附件文件、RP 撤回、物理删除和不同会话并发行为不变。

## Validation Commands

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.myapplication.viewmodel.ChatViewModelTest" --console=plain
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.myapplication.viewmodel.RoleplayViewModelTest" --console=plain
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.myapplication.data.repository.ConversationRepositoryTest" --console=plain
.\gradlew.bat :app:compileDebugAndroidTestKotlin --console=plain
.\gradlew.bat lintDebug --console=plain
.\gradlew.bat assembleDebug --console=plain
```

单个 JVM 测试命令最大等待 60 秒；真实 Room 故障注入缺设备时标记未取得结果。

## Risky Files / Rollback Points

- `ChatViewModel.kt`、`RoleplayScenarioActionSupport.kt`：不要扩大主类，优先复用现有 Support / Mutex。
- `RoomConversationStore.kt`、`ConversationRepository.kt`：保留现有单条删除事务和返回契约。
- `RoleplayRepository.kt`、相关 DAO：单一事务不能在提交前执行文件删除或网络调用。
- 与既有单条消息删除改动可能重叠；任何冲突先停下审查，禁止覆盖或回退 lucky 的修改。
