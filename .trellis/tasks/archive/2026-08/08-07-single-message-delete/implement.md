# 单条消息删除：实施计划

## Completion Definition

普通聊天、RP 离线模式和 RP 在线模式均可经二次确认物理删除单条底层消息；消息、
引用、来源记忆、总摘要和摘要分段在同一 Room 事务中原子清理；RP 撤回保留；附件文件
保留；AC1-AC9 对应验证全部通过，且 Room schema 版本未变化。

## Ordered Checklist

1. **加载规范与锁定基线（AC9）**
   - 使用 `trellis-before-dev` 读取 UI、数据库、错误处理、质量、复用和跨层规范。
   - 复核本任务 research 证据与当前源码，确认没有并行改动已提供同名删除契约。

2. **实现并测试纯消息变换（AC2、AC3、AC5）**
   - 在现有 `ConversationMessageTransforms` 或职责等价的纯 Support 中新增“移除目标并
     清理引用”变换。
   - 覆盖：目标存在/不存在、顶层引用、parts 引用、非目标引用保持、剩余顺序保持、
     一个底层 RP 消息对应多个派生气泡。

3. **新增原子 Store/DAO 契约（AC2、AC3、AC4、AC6、AC8）**
   - `ConversationDao` 增加按会话和消息 id 删除单条记录的查询。
   - `MemoryDao` 增加按 `sourceMessageId` 删除全部来源记忆的查询。
   - `ConversationStore` / `RoomConversationStore` 增加单事务删除方法；事务内写回引用
     变化、删除目标、删除来源记忆、删除摘要/分段并更新时间。
   - 同步更新所有 Store Fake/测试实现，不修改 Entity、schema 或数据库版本。
   - 增加真实 Room 设备测试，验证提交、回滚、Flow 重观察和附件文件保留。

4. **暴露共享 Repository 用例（AC2、AC4、AC8）**
   - `ConversationRepository.deleteMessage` 返回 `Deleted/NotFound`，保留标题和模型。
   - Repository 不顺序调用多个仓储，不吞数据库异常，不返回部分成功。
   - 补充 Repository/Fake 测试：参数传递、幂等、会话不存在和异常传播。

5. **接入普通聊天（AC1、AC2、AC7、AC8）**
   - 为 `ChatMessageCallbacks`、导航绑定、`ChatScreen` 局部确认状态、
     `ChatScreenOverlays` 和 `ChatMessageActionSheet` 增加删除链路。
   - 删除项使用破坏性样式和不可逆文案；取消无写入。
   - `ChatViewModel` 双重校验 `isSending`、会话、目标和非 `LOADING` 状态，异常走
     脱敏错误提示。

6. **接入 RP 全部展示模式（AC1、AC5、AC7、AC8）**
   - 为 `RoleplayMessageCallbacks`、导航绑定、页面确认状态和
     `RoleplayMessageMenuWrapper` 增加删除链路。
   - 显式贯通离线 `RoleplayDialoguePanel` 与在线
     `RoleplayOnlinePhoneContent`；目标使用 `sourceMessageId`。
   - 保持现有撤回回调和菜单项；删除成功后按目标 id 清空当前引用草稿。

7. **验证派生状态与迟到写入（AC4、AC5、AC7）**
   - 验证 Memory/Summary Flow 使 remembered/summary/context 状态归零。
   - 验证删除非当前引用消息不清空 RP 引用草稿。
   - 验证发送中、流式项和 `LOADING` 消息不可删除；后台部件更新不会复活已删消息。

8. **全量质量检查（AC9）**
   - 运行下方 AC 映射测试和项目门禁；JVM 单测单次最多等待 60 秒。
   - 检查 `git diff`、Room schema 与 `CURRENT_VERSION`，只保留本任务业务改动。

## Acceptance-to-validation mapping

- **AC1**：普通聊天与 RP Compose 交互测试，断言菜单、确认、取消和 RP 撤回并存。
- **AC2**：纯变换测试、Repository 测试、Room Store 设备测试和重新观察断言。
- **AC3**：顶层及 parts 引用清理的纯逻辑测试与 Room 持久化断言。
- **AC4**：Room 事务测试，断言所有 scope 的来源记忆、总摘要和分段被删除，Flow 更新。
- **AC5**：`RoleplayMessageUiMapperTest` / `RoleplayViewModelTest`，断言多气泡共同消失及
  引用草稿条件清理。
- **AC6**：设备测试创建代表性本地附件文件，删除消息后断言文件仍存在。
- **AC7**：`ChatViewModelTest`、`RoleplayViewModelTest` 覆盖发送/LOADING 门禁与迟到更新。
- **AC8**：Room 事务故障注入或可控 Fake 异常测试，断言全量回滚和安全错误反馈。
- **AC9**：相关单测、设备测试源码编译/运行、Lint、Debug 构建及 schema diff 检查。

## Validation commands

```powershell
# 纯逻辑与 Repository（每次调用最大等待 60 秒）
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.myapplication.conversation.ConversationMessageTransformsTest" --console=plain
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.myapplication.data.repository.ConversationRepositoryTest" --console=plain
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.myapplication.viewmodel.ChatViewModelTest" --console=plain
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.myapplication.viewmodel.RoleplayViewModelTest" --console=plain
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.myapplication.roleplay.RoleplayMessageUiMapperTest" --console=plain

# 设备/Compose/Room 测试
.\gradlew.bat :app:compileDebugAndroidTestKotlin --console=plain
.\gradlew.bat connectedDebugAndroidTest --console=plain

# 项目门禁
.\gradlew.bat testDebugUnitTest --console=plain
.\gradlew.bat lintDebug --console=plain
.\gradlew.bat assembleDebug --console=plain

# 确认没有 schema/version 变化
git diff -- app/src/main/java/com/example/myapplication/data/local/chat/ChatDatabase.kt app/schemas
```

## Risky files / rollback points

- `ConversationStore.kt`、`RoomConversationStore.kt`、`ConversationDao.kt`、`MemoryDao.kt`：
  删除不可逆，先完成纯变换和 Room 原子回滚测试，再接 UI。
- `ChatMessageWorkUnitSheets.kt`、`ChatScreenOverlays.kt`、
  `RoleplayMessageBubbles.kt`：破坏性入口必须保留确认门禁，不能复用撤回文案。
- `ChatViewModel.kt`、`RoleplayViewModel.kt`：只做校验、调用和反馈；共享删除规则不得
  复制到两个 ViewModel。
- 若实现需要 Entity/schema 变化、无法保证同事务清理或只能实现部分成功，应停止实施
  并返回规划阶段，不以降级方案继续。
